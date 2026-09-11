package com.antondev.chats.command;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.api.PlexonChatsAPI;
import com.antondev.chats.diagnostics.ChatDiagnostics;
import com.antondev.chats.gui.ChatGUIHolder;
import com.antondev.chats.integration.core.CoreBridge;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChatCommand implements CommandExecutor, TabCompleter {
    private final PlexonChats plugin;
    public ChatCommand(PlexonChats plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("plexonchats.use")) { sender.sendMessage(plugin.getConfigManager().getNoPermission()); return true; }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui", "menu" -> open(sender, ChatGUIHolder.Page.MAIN);
            case "admin" -> { if (permission(sender, "plexonchats.manage")) open(sender, ChatGUIHolder.Page.ADMIN); }
            case "reload" -> {
                if (permission(sender, "plexonchats.reload")) {
                    boolean ok = plugin.reloadPlugin();
                    sender.sendMessage(ok ? plugin.getConfigManager().getConfigReloaded() : plugin.getConfigManager().message("config-failed"));
                }
            }
            case "channel", "ch", "c" -> {
                if (args.length < 2) help(sender);
                else changeChannel(sender, args[1]);
            }
            case "local", "global" -> changeChannel(sender, sub);
            case "status" -> {
                if (permission(sender, "plexonchats.manage")) sender.sendMessage(plugin.getConfigManager().message("status", Map.of(
                        "version", plugin.getPluginMeta().getVersion(), "discord", plugin.getDiscordBridge().status(), "scheduler", plugin.getAutoMessages().status())));
            }
            case "diagnostics" -> { if (permission(sender, "plexonchats.manage")) diagnostics(sender); }
            case "automessages", "automsg" -> { if (permission(sender, "plexonchats.automessages")) autoMessages(sender, args); }
            default -> help(sender);
        }
        return true;
    }

    private boolean permission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        sender.sendMessage(plugin.getConfigManager().getNoPermission());
        return false;
    }
    private void open(CommandSender sender, ChatGUIHolder.Page page) {
        if (sender instanceof Player player) plugin.getChatGUI().openPage(player, page);
        else sender.sendMessage(plugin.getConfigManager().getPlayerOnly());
    }
    private void changeChannel(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) { sender.sendMessage(plugin.getConfigManager().getPlayerOnly()); return; }
        ChatChannel channel = ChatChannel.fromName(name);
        if (channel == null) player.sendMessage(plugin.getConfigManager().getUnknownChannel(name));
        else plugin.getChatManager().selectChannel(player, channel);
    }

    private void diagnostics(CommandSender sender) {
        CoreBridge core = plugin.getCoreBridge();
        ChatDiagnostics diagnostics = plugin.getDiagnostics();
        boolean apiRegistered = Bukkit.getServicesManager().getRegistration(PlexonChatsAPI.class) != null;
        long guiSessions = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getOpenInventory().getTopInventory().getHolder() instanceof ChatGUIHolder).count();
        String papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ? "READY" : "NOT INSTALLED";

        sender.sendMessage(Component.text("PlexonChats Diagnostics"));
        diagnostic(sender, "Plugin", plugin.getPluginMeta().getVersion());
        diagnostic(sender, "Paper", Bukkit.getServer().getVersion());
        diagnostic(sender, "Java", System.getProperty("java.version", "unknown"));
        diagnostic(sender, "Mode", core == null ? "STANDALONE" : core.mode());
        diagnostic(sender, "Core plugin/API", core == null ? "- / -" : core.pluginVersion() + " / " + core.apiVersion());
        diagnostic(sender, "Supported Core", CoreBridge.SUPPORTED_API_RANGE);
        diagnostic(sender, "Module", core == null ? "NOT_REGISTERED" : core.registrationState());
        diagnostic(sender, "Core detail", core == null ? "PlexonCore unavailable" : core.detail());
        diagnostic(sender, "Chat ownership", ChatDiagnostics.OWNERSHIP);
        diagnostic(sender, "Native chat observed", String.valueOf(diagnostics.nativeObservedCount()));
        diagnostic(sender, "Public messages delivered", String.valueOf(diagnostics.publicMessagesDeliveredCount()));
        diagnostic(sender, "Recipient deliveries", String.valueOf(diagnostics.recipientDeliveriesCount()));
        diagnostic(sender, "PlexonChatEvent cancellations", String.valueOf(diagnostics.customEventCancellationsCount()));
        diagnostic(sender, "Format failures", String.valueOf(diagnostics.formatFailuresCount()));
        diagnostic(sender, "Config revision", String.valueOf(plugin.getConfigManager().revision()));
        diagnostic(sender, "Last reload", diagnostics.lastReload());
        diagnostic(sender, "Recent integration failure", diagnostics.recentIntegrationFailure());
        diagnostic(sender, "Channels", "LOCAL=" + state(plugin.getConfigManager().isLocalEnabled()) + ", GLOBAL=" + state(plugin.getConfigManager().isGlobalEnabled()));
        diagnostic(sender, "Public API", apiRegistered ? "REGISTERED" : "NOT REGISTERED");
        diagnostic(sender, "PlexonChatEvent", "SYNC / CANCELLABLE / PRE-DELIVERY");
        diagnostic(sender, "Preferences", "players.yml / " + (plugin.getPreferences().writable() ? "writable" : "READ-ONLY") + " / dirty=" + plugin.getPreferences().dirty());
        diagnostic(sender, "Preference writer", plugin.getPreferences().writerState());
        diagnostic(sender, "Preference save task", state(plugin.getPreferences().saveTaskActive()));
        diagnostic(sender, "Auto-messages", plugin.getAutoMessages().status());
        diagnostic(sender, "Auto-message groups", String.valueOf(plugin.getAutoMessages().groupNames().size()));
        diagnostic(sender, "Auto-message task", state(plugin.getAutoMessages().taskActive()));
        diagnostic(sender, "Item preview tokens", String.valueOf(plugin.getItemPreviewManager().size()));
        diagnostic(sender, "Item cleanup task", state(plugin.cleanupTaskActive()));
        diagnostic(sender, "GUI sessions", String.valueOf(guiSessions));
        diagnostic(sender, "PlaceholderAPI", papi);
        diagnostic(sender, "Vault", plugin.getPlayerInfoService().vaultState());
        diagnostic(sender, "LuckPerms", plugin.getPlayerInfoService().luckPermsState());
        diagnostic(sender, "PlexonRanks", plugin.getPlayerInfoService().plexonRanksState());
        diagnostic(sender, "DiscordSRV", plugin.getDiscordBridge().status());
    }

    private static void diagnostic(CommandSender sender, String label, String value) { sender.sendMessage(Component.text(" • " + label + ": " + value)); }
    private static String state(boolean active) { return active ? "ACTIVE" : "INACTIVE"; }

    private void autoMessages(CommandSender sender, String[] args) {
        var manager = plugin.getAutoMessages();
        var config = plugin.getConfigManager();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "list" -> sender.sendMessage(config.message("auto-status", Map.of("state", manager.status(), "groups", String.join(", ", manager.groupNames()))));
            case "pause" -> { manager.pause(); sender.sendMessage(config.message("auto-paused")); }
            case "resume" -> {
                if (!manager.enabled()) sender.sendMessage(config.message("auto-disabled"));
                else { manager.resume(); sender.sendMessage(config.message("auto-resumed")); }
            }
            case "enable", "disable" -> {
                boolean desired = action.equals("enable");
                boolean ok = config.saveSetting("auto-messages.enabled", desired) && plugin.reloadPlugin();
                sender.sendMessage(ok ? Component.text("Auto-messages " + (desired ? "enabled." : "disabled.")) : config.message("config-failed"));
            }
            case "send", "test", "preview" -> {
                if (args.length != 3) sender.sendMessage(Component.text("Usage: /chat automessages " + action + " <group>"));
                else if (!manager.groupNames().contains(args[2])) sender.sendMessage(config.message("auto-not-found", Map.of("group", args[2])));
                else if (action.equals("preview")) manager.preview(args[2], sender);
                else if (action.equals("send") && !manager.enabled()) sender.sendMessage(config.message("auto-disabled"));
                else {
                    int count = action.equals("test") ? manager.testSend(args[2]) : manager.sendNow(args[2]);
                    sender.sendMessage(config.message(count > 0 ? "auto-sent" : "auto-none", Map.of("group", args[2], "count", String.valueOf(count))));
                }
            }
            default -> sender.sendMessage(Component.text("Usage: /chat automessages <list|enable|disable|pause|resume|send|test|preview> [group]"));
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(plugin.getConfigManager().formatMessage("<gradient:#88beff:#b9d8ff><bold>PlexonChats</bold></gradient> <gray>Commands"));
        helpLine(sender, "plexonchats.gui", "/chat gui — Channel and personal settings");
        helpLine(sender, "plexonchats.use", "/chat channel <local|global> — Select a channel");
        helpLine(sender, "plexonchats.global", "/g [message] — Select/use global chat");
        helpLine(sender, "plexonchats.local", "/l [message] — Select/use nearby chat");
        helpLine(sender, "plexonchats.tell", "/msg <player> <message> and /reply <message>");
        helpLine(sender, "plexonchats.announce", "/announce <message> — Broadcast");
        helpLine(sender, "plexonchats.manage", "/chat admin, /chat status and /chat diagnostics — Administration");
        helpLine(sender, "plexonchats.reload", "/chat reload — Transactional configuration reload");
        helpLine(sender, "plexonchats.automessages", "/chat automessages <list|enable|disable|pause|resume|send|test|preview> [group]");
    }
    private void helpLine(CommandSender sender, String permission, String value) { if (sender.hasPermission(permission)) sender.sendMessage(Component.text(" • " + value)); }

    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || !sender.hasPermission("plexonchats.use")) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("help", "channel"));
            if (sender.hasPermission("plexonchats.gui")) options.add("gui");
            if (sender.hasPermission("plexonchats.manage")) options.addAll(List.of("admin", "status", "diagnostics"));
            if (sender.hasPermission("plexonchats.reload")) options.add("reload");
            if (sender.hasPermission("plexonchats.automessages")) options.add("automessages");
        } else if (args.length == 2 && List.of("channel", "ch", "c").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (ChatChannel channel : ChatChannel.values()) {
                if (plugin.getConfigManager().isChannelEnabled(channel) && sender.hasPermission(channel.getPermission())) options.add(channel.name().toLowerCase(Locale.ROOT));
            }
        } else if (List.of("automessages", "automsg").contains(args[0].toLowerCase(Locale.ROOT)) && sender.hasPermission("plexonchats.automessages")) {
            if (args.length == 2) options.addAll(List.of("list", "enable", "disable", "pause", "resume", "send", "test", "preview"));
            else if (args.length == 3 && List.of("send", "test", "preview").contains(args[1].toLowerCase(Locale.ROOT))) options.addAll(plugin.getAutoMessages().groupNames());
        }
        String query = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(query)).toList();
    }
}
