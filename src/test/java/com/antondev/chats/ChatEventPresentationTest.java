package com.antondev.chats;

import com.antondev.chats.event.ChatEventConfig;
import com.antondev.chats.event.ChatEventPresentation;
import com.antondev.chats.event.ChatEventValidation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChatEventPresentationTest extends PluginTestBase {
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

    @Test void configurableBlankSpacingAndCardsRenderAsActualComponents() {
        ChatEventConfig config = ChatEventConfig.read(plugin.getConfigManager().section("chat-events"));
        ChatEventPresentation renderer = new ChatEventPresentation();
        Map<String, Component> values = standardValues();
        List<Component> rendered = renderer.render(config, "start", values);
        assertEquals(config.presentation().card("start").size() + 2, rendered.size());
        assertEquals("", plain.serialize(rendered.getFirst()));
        assertEquals("", plain.serialize(rendered.getLast()));
        assertTrue(rendered.stream().map(plain::serialize).anyMatch(line -> line.contains("Type something")));
    }

    @Test void migratedStockCardsAreCompactAndInteractive() {
        ChatEventConfig config = ChatEventConfig.read(plugin.getConfigManager().section("chat-events"));
        ChatEventPresentation renderer = new ChatEventPresentation();
        Map<String, Component> values = standardValues();

        List<Component> start = renderer.render(config, "start", values);
        String startText = joined(start);
        assertTrue(startText.contains("✦ Type Rush"));
        assertTrue(startText.contains("HOW TO PLAY"));
        assertTrue(startText.contains("◆ $500"));
        assertTrue(startText.contains("⏱ 30s"));
        assertTrue(startText.contains("first correct answer wins"));
        assertFalse(startText.contains("CHAT EVENT"));
        assertFalse(startText.contains("Reward:"));
        assertFalse(startText.contains("Time:"));
        assertTrue(start.stream().anyMatch(this::hasHover));

        List<Component> winner = renderer.render(config, "winner", values);
        String winnerText = joined(winner);
        assertTrue(winnerText.contains("✔ Type Rush COMPLETE"));
        assertTrue(winnerText.contains("♛ Tonim"));
        assertTrue(winnerText.contains("Answer › answer"));
        assertFalse(winnerText.contains("Total wins:"));
        assertTrue(winner.stream().anyMatch(this::hasHover));

        String timeoutText = joined(renderer.render(config, "timeout", values));
        assertTrue(timeoutText.contains("⌛ Type Rush EXPIRED"));
        assertTrue(timeoutText.contains("Answer › answer"));
        assertFalse(timeoutText.contains("CHAT EVENT ENDED"));

        String cancelledText = joined(renderer.render(config, "cancelled", values));
        assertTrue(cancelledText.contains("✕ Type Rush CANCELLED"));
        assertTrue(cancelledText.contains("Stopped by staff"));
        assertFalse(cancelledText.contains("No reward or win statistic was issued"));
    }

    @Test void dynamicPlayerControlledTextIsInsertedAsLiteralComponentData() {
        ChatEventConfig config = ChatEventConfig.read(plugin.getConfigManager().section("chat-events"));
        ChatEventPresentation renderer = new ChatEventPresentation();
        Map<String, Component> values = standardValues();
        values.put("winner", Component.text("<red><bold>NotMarkup</bold></red>"));
        String combined = renderer.render(config, "winner", values).stream().map(plain::serialize).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(combined.contains("<red><bold>NotMarkup</bold></red>"));
    }

    @Test void invalidSpacingAndMalformedMiniMessageRejectWholeCandidate() throws Exception {
        var file = plugin.getDataFolder().toPath().resolve("config.yml").toFile();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("chat-events.presentation.blank-lines-before", 4);
        assertThrows(IllegalArgumentException.class, () -> ChatEventValidation.validate(yaml.getConfigurationSection("chat-events")));
        yaml.set("chat-events.presentation.blank-lines-before", 1);
        yaml.set("chat-events.presentation.start", List.of("<red>unclosed strict tag"));
        assertThrows(IllegalArgumentException.class, () -> ChatEventValidation.validate(yaml.getConfigurationSection("chat-events")));
    }

    private String joined(List<Component> components) {
        return components.stream().map(plain::serialize).reduce("", (left, right) -> left + "\n" + right);
    }

    private boolean hasHover(Component component) {
        if (component.hoverEvent() != null) return true;
        return component.children().stream().anyMatch(this::hasHover);
    }

    private static Map<String, Component> standardValues() {
        Map<String, Component> values = new LinkedHashMap<>();
        values.put("event_id", Component.text("type-rush"));
        values.put("event_type", Component.text("Type Rush"));
        values.put("event_name", Component.text("Type Rush"));
        values.put("description", Component.text("First correct answer wins."));
        values.put("prompt", Component.text("Type something"));
        values.put("reward", Component.text("$500"));
        values.put("duration_seconds", Component.text("30"));
        values.put("winner", Component.text("Tonim"));
        values.put("elapsed", Component.text("1.00s"));
        values.put("winner_total_wins", Component.text("4"));
        values.put("winner_type_wins", Component.text("2"));
        values.put("answer", Component.text("answer"));
        return values;
    }
}
