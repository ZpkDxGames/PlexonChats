package com.antondev.chats;

import com.antondev.chats.api.PlexonChatEvent;
import com.antondev.chats.chat.ChatListener;
import com.antondev.chats.command.MessageCommand;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatRoutingTest extends PluginTestBase {
    @Test void configuredFormatAndNicknameHoverAreActuallyUsed() throws Exception {
        var tonim = player("Tonim");
        config(yaml -> {
            yaml.set("channels.global.format", "<gold>CUSTOM {player} in {world}: {message}");
            yaml.set("chat-components.player.hover.lines", java.util.List.of("<aqua>Custom hover for {player_name}"));
        });
        var component = plugin.getChatComponentFactory().buildPublicMessage(tonim, ChatChannel.GLOBAL, Component.text("hello"));
        assertTrue(plain(component).startsWith("CUSTOM Tonim in "));
        String json = GsonComponentSerializer.gson().serialize(component);
        assertTrue(json.contains("Custom hover for"));
        assertTrue(json.contains("/msg Tonim "));
    }
    @Test void deniedShortcutDoesNotFallBackToLocal() {
        var sender = player("Sender");
        var recipient = player("Recipient");
        sender.addAttachment(plugin, "plexonchats.global", false);
        plugin.getChatManager().route(sender, "!not allowed", null);
        assertNull(next(recipient));
        assertTrue(next(sender).contains("permission"));
    }
    @Test void localDeliveryRespectsWorldAndRadius() {
        var sender = player("Sender");
        var nearby = player("Nearby");
        var far = player("Faraway");
        var anotherWorld = player("OtherWorld");
        far.teleport(new Location(sender.getWorld(), 500, 64, 0));
        anotherWorld.teleport(new Location(server.addSimpleWorld("nether"), 0, 64, 0));
        plugin.getChatManager().sendPublic(sender, ChatChannel.LOCAL, "nearby only");
        assertTrue(next(nearby).contains("nearby only"));
        assertNull(next(far));
        assertNull(next(anotherWorld));
    }
    @Test void nativeChatUsesModeratedMessageAndExistingViewers() {
        var sender = player("Sender");
        var allowed = player("Allowed");
        var excluded = player("Excluded");
        var event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(sender);
        when(event.message()).thenReturn(Component.text("moderated message"));
        when(event.originalMessage()).thenReturn(Component.text("original message"));
        when(event.viewers()).thenReturn(new HashSet<Audience>(Set.of(sender, allowed)));
        new ChatListener(plugin).onPlayerChat(event);
        assertTrue(next(allowed).contains("moderated message"));
        assertNull(next(excluded));
        verify(event).setCancelled(true);
    }
    @Test void moderationApiCanCancelShortcutMessages() {
        var sender = player("Sender");
        var recipient = player("Recipient");
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler public void block(PlexonChatEvent event) { event.setCancelled(true); }
        }, plugin);
        plugin.getChatManager().sendPublic(sender, ChatChannel.GLOBAL, "blocked");
        assertNull(next(recipient));
    }
    @Test void publicRecipientsPermissionIsEnforced() throws Exception {
        var sender = player("Sender");
        var recipient = player("Recipient");
        config(yaml -> yaml.set("channels.global.receive-permission", "server.global.read"));
        sender.addAttachment(plugin, "server.global.read", true);
        plugin.getChatManager().sendPublic(sender, ChatChannel.GLOBAL, "restricted");
        assertTrue(next(sender).contains("restricted"));
        assertNull(next(recipient));
    }
    @Test void disabledChannelsCannotBeSelected() throws Exception {
        var sender = player("Sender");
        config(yaml -> yaml.set("channels.global.enabled", false));
        assertFalse(plugin.getChatManager().selectChannel(sender, ChatChannel.GLOBAL));
        assertEquals(ChatChannel.LOCAL, plugin.getChatManager().getPlayerChannel(sender));
        assertTrue(next(sender).contains("disabled"));
    }
    @Test void privateMentionCannotNotifyUninvolvedPlayer() {
        var sender = player("Sender");
        var recipient = player("Recipient");
        var third = player("ThirdPerson");
        new MessageCommand(plugin).sendPrivateMessage(sender, recipient, "hello @ThirdPerson");
        assertTrue(next(recipient).contains("@ThirdPerson"));
        assertNull(next(third));
        assertNull(third.nextActionBar());
    }
    @Test void mentionPreferenceDisablesNotifications() {
        var sender = player("Sender");
        var recipient = player("Recipient");
        plugin.getPreferences().set(recipient.getUniqueId(), plugin.getPreferences().get(recipient.getUniqueId()).toggleMentions());
        plugin.getChatManager().sendPublic(sender, ChatChannel.GLOBAL, "hello @Recipient");
        assertTrue(next(recipient).contains("@Recipient"));
        assertNull(recipient.nextActionBar());
    }
    @Test void privateOptOutIsRespectedAndStaffBypassIsExplicit() {
        var sender = player("Sender");
        var recipient = player("Recipient");
        plugin.getPreferences().set(recipient.getUniqueId(), plugin.getPreferences().get(recipient.getUniqueId()).togglePrivateMessages());
        var command = new MessageCommand(plugin);
        command.sendPrivateMessage(sender, recipient, "blocked PM");
        assertNull(next(recipient));
        assertTrue(next(sender).contains("not accepting"));
        sender.addAttachment(plugin, "plexonchats.bypass.private", true);
        command.sendPrivateMessage(sender, recipient, "staff PM");
        assertTrue(next(recipient).contains("staff PM"));
    }
    @Test void emptyPrefixOnlyMessageIsRejected() {
        var sender = player("Sender");
        var recipient = player("Recipient");
        plugin.getChatManager().route(sender, "!", null);
        assertNull(next(recipient));
        assertTrue(next(sender).contains("enter a message"));
    }
    @Test void playerFormatPermissionPreventsTagsFromBecomingFormatting() {
        var sender = player("Sender");
        sender.addAttachment(plugin, "plexonchats.formatting", false);
        var processed = plugin.getPlaceholderHandler().processMessage(sender, "<red>literal</red>");
        assertEquals("<red>literal</red>", plain(processed.component()));
    }

    @Test void disablingNicknameHoverDoesNotInheritAnotherPluginsHover() throws Exception {
        var sender = player("Sender");
        sender.displayName(Component.text("Nick").hoverEvent(Component.text("Unwanted hover")));
        config(yaml -> yaml.set("chat-components.player.hover.enabled", false));
        String json = GsonComponentSerializer.gson().serialize(plugin.getChatComponentFactory().buildPlayerComponent(sender));
        assertFalse(json.contains("Unwanted hover"));
        assertFalse(json.contains("hoverEvent"));
    }

    @Test void namesInsideItemComponentsDoNotTriggerMentionAlerts() throws Exception {
        // Native item-hover serialization requires a real Paper server; test token processing here.
        config(yaml -> yaml.set("item-display.hover-item", false));
        var sender = player("Sender");
        player("Recipient");
        var item = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_SWORD);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("@Recipient [item]"));
        item.setItemMeta(meta);
        sender.getInventory().setItemInMainHand(item);
        var processed = plugin.getPlaceholderHandler().processMessage(sender, "Look: [item]");
        assertTrue(processed.mentionedPlayers().isEmpty());
        assertTrue(plain(processed.component()).contains("@Recipient [item]"));
        String json = GsonComponentSerializer.gson().serialize(processed.component());
        assertTrue(json.contains("/chatitem "));
    }

    @Test void handItemTriggerHasPriorityOverMatchingUsername() throws Exception {
        config(yaml -> yaml.set("item-display.hover-item", false));
        var sender = player("Sender");
        player("hand");
        var item = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND);
        sender.getInventory().setItemInMainHand(item);
        var processed = plugin.getPlaceholderHandler().processMessage(sender, "@hand");
        assertTrue(processed.mentionedPlayers().isEmpty());
        assertTrue(GsonComponentSerializer.gson().serialize(processed.component()).contains("/chatitem "));
    }
}
