package com.antondev.chats;

import com.antondev.chats.automessage.MessageRotation;
import com.antondev.chats.chat.MessageLimiter;
import com.antondev.chats.config.AtomicFiles;
import com.antondev.chats.config.ConfigManager;
import com.antondev.chats.config.ConfigUpgrader;
import com.antondev.chats.placeholder.PlaceholderHandler;
import com.antondev.chats.text.ComponentTemplate;
import com.antondev.chats.text.TextService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    private final ComponentTemplate templates = new ComponentTemplate(MiniMessage.miniMessage());
    private static String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }

    @Test void injectedValuesCannotAddTagsOrRecursivelyResolveTokens() {
        String payload = "<click:run_command:'/op attacker'>{player}</click>";
        Component output = templates.render("<gray>{message}", Map.of("message", Component.text(payload), "player", Component.text("Tonim")));
        assertEquals(payload, plain(output));
        assertFalse(hasClick(output));
    }
    @Test void repeatedPlaceholdersAndRichComponentEventsArePreserved() {
        Component item = Component.text("Pickaxe").clickEvent(ClickEvent.runCommand("/chatitem token"));
        Component output = templates.render("{item} / {item}", Map.of("item", item));
        assertEquals("Pickaxe / Pickaxe", plain(output));
        assertTrue(hasClick(output));
    }
    @Test void playerColorsKeepGradientsButNotCommandEvents() {
        Component output = PlaceholderHandler.COLORS.deserialize("<gradient:#ffaa00:#ff00aa>Hello</gradient><click:run_command:'/op me'>no</click>");
        assertTrue(plain(output).startsWith("Hello"));
        assertFalse(hasClick(output));
    }
    @Test void papiValuesCannotInjectMiniMessage() {
        Component output = templates.render("Rank: %example_prefix%", key -> TextService.legacy("<click:run_command:'/op me'>Owner</click>"));
        assertTrue(plain(output).contains("<click:"));
        assertFalse(hasClick(output));
    }
    @Test void legacyHexWorksInExpandedValues() {
        assertEquals("Owner", plain(TextService.legacy("&#12ABCDOwner")));
        assertEquals("Owner", plain(TextService.legacy("&x&1&2&A&B&C&DOwner")));
    }
    @Test void unsafeUrlsAndInvalidCommandsAreRejected() {
        assertNull(TextService.clickEvent("OPEN_URL", "javascript:alert(1)"));
        assertNull(TextService.clickEvent("OPEN_URL", "file:///etc/passwd"));
        assertNull(TextService.clickEvent("RUN_COMMAND", "op me"));
        assertNotNull(TextService.clickEvent("OPEN_URL", "https://example.com"));
        assertNotNull(TextService.clickEvent("SUGGEST_COMMAND", "/msg Tonim "));
    }
    @Test void upgradePreservesCustomValuesAndEmptyCollections() throws Exception {
        var old = new YamlConfiguration();
        old.loadFromString("channels:\n  global:\n    format: 'CUSTOM {message}'\ngui:\n  items: {}\nauto-messages:\n  groups: {}\n");
        var defaults = new YamlConfiguration();
        defaults.loadFromString("config-version: 2\nchannels:\n  global:\n    format: 'DEFAULT {message}'\n    enabled: true\ngui:\n  items:\n    example:\n      slot: 1\nauto-messages:\n  groups:\n    tips:\n      enabled: true\n");
        assertTrue(ConfigUpgrader.upgrade(old, defaults));
        assertEquals("CUSTOM {message}", old.getString("channels.global.format"));
        assertTrue(old.getBoolean("channels.global.enabled"));
        assertTrue(old.getConfigurationSection("gui.items").getKeys(false).isEmpty());
        assertTrue(old.getConfigurationSection("auto-messages.groups").getKeys(false).isEmpty());
        assertFalse(ConfigUpgrader.upgrade(old, defaults));
    }
    @Test void configValidationRejectsMissingMessageAndWrongSectionType() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("channels:\n  global:\n    format: '<red>lost message'\n");
        assertThrows(IllegalArgumentException.class, () -> ConfigManager.validate(yaml));
        yaml.loadFromString("gui: false\n");
        assertThrows(IllegalArgumentException.class, () -> ConfigManager.validate(yaml));
        yaml.loadFromString("config-version: 999\n");
        assertThrows(IllegalArgumentException.class, () -> ConfigManager.validate(yaml));
    }
    @Test void shuffleCoversEveryEntryAndAvoidsRepeatAcrossCycles() {
        var rotation = new MessageRotation<>(List.of("a", "b", "c", "d"), true, new Random(7));
        String previous = null;
        for (int cycle = 0; cycle < 100; cycle++) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                String value = rotation.next();
                assertNotEquals(previous, value);
                seen.add(value);
                previous = value;
            }
            assertEquals(4, seen.size());
        }
    }
    @Test void previewDoesNotAdvanceSequentialRotation() {
        var rotation = new MessageRotation<>(List.of("a", "b"), false, new Random(1));
        assertEquals("a", rotation.peek());
        assertEquals("a", rotation.peek());
        assertEquals("a", rotation.next());
        assertEquals("b", rotation.next());
        assertEquals("a", rotation.next());
    }
    @Test void emptyAndSingleEntryRotationsAreSafe() {
        var empty = new MessageRotation<String>(List.of(), true, new Random());
        assertNull(empty.peek());
        assertNull(empty.next());
        var single = new MessageRotation<>(List.of("only"), true, new Random());
        for (int i = 0; i < 20; i++) assertEquals("only", single.next());
    }
    @Test void rejectedCooldownAttemptsDoNotExtendCooldown() {
        AtomicLong clock = new AtomicLong();
        var limiter = new MessageLimiter(clock::get);
        UUID id = UUID.randomUUID();
        assertEquals(0, limiter.check(id, "hello", 1000, 5000).remainingMillis());
        clock.set(500_000_000L);
        assertEquals(500, limiter.check(id, "next", 1000, 5000).remainingMillis());
        clock.set(1_000_000_000L);
        assertEquals(0, limiter.check(id, "next", 1000, 5000).remainingMillis());
    }
    @Test void duplicateGuardAndQuitCleanupWork() {
        AtomicLong clock = new AtomicLong();
        var limiter = new MessageLimiter(clock::get);
        UUID id = UUID.randomUUID();
        limiter.check(id, " Hello ", 0, 5000);
        clock.set(2_000_000_000L);
        assertTrue(limiter.check(id, "hello", 0, 5000).duplicate());
        limiter.remove(id);
        assertFalse(limiter.check(id, "hello", 0, 5000).duplicate());
    }
    @Test void atomicSaveReplacesWholeFileAndCleansTemporaryFiles(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("data.yml");
        AtomicFiles.write(file, "first: true\n");
        AtomicFiles.write(file, "second: true\n");
        assertEquals("second: true\n", Files.readString(file));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    private static boolean hasClick(Component component) {
        return component.clickEvent() != null || component.children().stream().anyMatch(CoreTest::hasClick);
    }
}
