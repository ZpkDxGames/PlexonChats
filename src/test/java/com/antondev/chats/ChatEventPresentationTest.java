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
        yaml.set("chat-events.presentation.start", List.of("<click:run_command:'unterminated>bad</click>"));
        assertThrows(IllegalArgumentException.class, () -> ChatEventValidation.validate(yaml.getConfigurationSection("chat-events")));
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
