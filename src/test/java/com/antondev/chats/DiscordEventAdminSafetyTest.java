package com.antondev.chats;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiscordEventAdminSafetyTest extends PluginTestBase {
    @Test void diagnosticsAndDiscordStatusNeverExposeWebhookSecret() throws Exception {
        String secretUrl = "https://discord.example.invalid/api/webhooks/123456/SECRET-TOKEN-XYZ";
        config(yaml -> {
            yaml.set("chat-events.discord.enabled", true);
            yaml.set("chat-events.discord.transport", "WEBHOOK");
            yaml.set("chat-events.discord.webhook.enabled", true);
            yaml.set("chat-events.discord.webhook.url", secretUrl);
        });

        var admin = player("Admin");
        admin.addAttachment(plugin, "plexonchats.manage", true);
        admin.addAttachment(plugin, "plexonchats.events", true);
        admin.addAttachment(plugin, "plexonchats.admin.events.discord", true);

        assertTrue(server.dispatchCommand(admin, "chat diagnostics"));
        assertTrue(server.dispatchCommand(admin, "chat events discord status"));
        String output = drainText(admin);

        assertTrue(output.contains("Discord Event Sync"));
        assertTrue(output.contains("WEBHOOK"));
        assertFalse(output.contains(secretUrl));
        assertFalse(output.contains("SECRET-TOKEN-XYZ"));
        assertFalse(output.contains("123456"), "webhook identity must not be rendered as diagnostics metadata");
    }

    @Test void discordTestCommandRequiresNarrowAdminPermission() throws Exception {
        config(yaml -> {
            yaml.set("chat-events.discord.enabled", false);
            yaml.set("chat-events.discord.transport", "AUTO");
        });
        var staff = player("Staff");
        staff.addAttachment(plugin, "plexonchats.events", true);
        staff.addAttachment(plugin, "plexonchats.manage", true);

        assertTrue(server.dispatchCommand(staff, "chat events discord test"));
        String output = drainText(staff).toLowerCase(java.util.Locale.ROOT);
        assertTrue(output.contains("permission"));
    }

    private static String drainText(org.mockbukkit.mockbukkit.entity.PlayerMock player) {
        StringBuilder out = new StringBuilder();
        Component component;
        while ((component = player.nextComponentMessage()) != null) {
            if (!out.isEmpty()) out.append('\n');
            out.append(plain(component));
        }
        return out.toString();
    }
}
