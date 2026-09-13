package com.antondev.chats.command;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.api.PlexonChatsAPI;
import com.antondev.chats.diagnostics.ChatDiagnostics;
import com.antondev.chats.event.ChatEventManager;
import com.antondev.chats.event.ChatEventStatisticsService;
import com.antondev.chats.event.bingo.BingoSession;
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
import java.util.UUID;

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
                        "version", plugin.getPluginMeta().getVersion(), "discord", plugin.getDiscordBridge().status(),
                        "scheduler", plugin.getAutoMessages().status(), "events", plugin.getChatEvents().status())));
            }
            case "diagnostics" -> { if (permission(sender, "plexonchats.manage")) diagnostics(sender); }
            case "automessages", "automsg" -> { if (permission(sender, "plexonchats.automessages")) autoMessages(sender, args); }
            case "events", "event" -> chatEvents(sender, args);
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
        ChatEventManager events = plugin.getChatEvents();
        ChatEventStatisticsService stats = events.statistics();

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
        diagnostic(sender, "Chat Events", events.enabled() ? "ENABLED" : "DISABLED");
        diagnostic(sender, "Chat Events scheduler", events.schedulerEnabled() ? (events.paused() ? "PAUSED" : "RUNNING") : "DISABLED");
        diagnostic(sender, "Chat Events task", state(events.taskActive()));
        diagnostic(sender, "Configured events", String.valueOf(events.configuredCount()));
        diagnostic(sender, "Eligible scheduled events", String.valueOf(events.eligibleScheduledCount()));
        diagnostic(sender, "Active event", events.activeId() + (events.hasActiveEvent() ? "/" + events.activeType() : ""));
        diagnostic(sender, "Active run", events.activeRunId() == null ? "-" : events.activeRunId().toString());
        diagnostic(sender, "Active event remaining", events.remainingMillis() < 0 ? "-" : String.format(Locale.ROOT, "%.1fs", events.remainingMillis() / 1000.0));
        diagnostic(sender, "Last event", events.lastEvent());
        diagnostic(sender, "Last winner", events.lastWinner());
        diagnostic(sender, "Economy rewards", events.economyState());
        diagnostic(sender, "PlexonKeys rewards", events.keysState());
        diagnostic(sender, "Recent Chat Events failure", events.recentFailure());
        diagnostic(sender, "Chat Events DB", stats.state().name());
        diagnostic(sender, "Chat Events DB file", stats.file().toString());
        diagnostic(sender, "Chat Events DB schema", String.valueOf(stats.schemaVersion()));
        diagnostic(sender, "Chat Events DB executor", stats.executorState());
        diagnostic(sender, "Chat Events DB pending writes", String.valueOf(stats.pendingWrites()));
        diagnostic(sender, "Chat Events DB last write", stats.lastWriteSuccessText());
        diagnostic(sender, "Chat Events DB last failure", stats.lastFailure());
        diagnostic(sender, "Recorded Chat Event wins", String.valueOf(stats.totalRecordedWins()));
        diagnostic(sender, "Cached event players", String.valueOf(stats.cachedPlayerCount()));
        diagnostic(sender, "Bingo enabled", String.valueOf(events.definitions().values().stream().anyMatch(value -> value.type().name().equals("BINGO") && value.bingo().enabled())));
        diagnostic(sender, "Bingo phase", events.bingoPhase());
        diagnostic(sender, "Bingo participants", String.valueOf(events.bingoParticipantCount()));
        diagnostic(sender, "Bingo draws", String.valueOf(events.bingoDrawCount()));
        diagnostic(sender, "Bingo last draw", events.bingoLastDraw());
        diagnostic(sender, "Bingo remaining pool", String.valueOf(events.bingoRemainingCount()));
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

    private void chatEvents(CommandSender sender, String[] args) {
        if (!permission(sender, "plexonchats.events")) return;
        ChatEventManager events = plugin.getChatEvents();
        if (args.length == 1) {
            if (sender instanceof Player player) plugin.getChatGUI().openPage(player, ChatGUIHolder.Page.EVENTS);
            else eventStatus(sender, events);
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "status" -> eventStatus(sender, events);
            case "list" -> {
                sender.sendMessage(Component.text("Configured Chat Events"));
                events.listStatus().forEach(line -> sender.sendMessage(Component.text(" • " + line)));
            }
            case "stats" -> eventStats(sender, args, events);
            case "leaderboard" -> eventLeaderboard(sender, events);
            case "bingo" -> bingo(sender, args, events);
            case "enable", "disable" -> {
                if (!permission(sender, "plexonchats.events.manage")) return;
                boolean desired = action.equals("enable");
                boolean ok = plugin.getConfigManager().saveSetting("chat-events.enabled", desired) && plugin.reloadPlugin();
                sender.sendMessage(Component.text(ok ? "Chat Events " + (desired ? "enabled." : "disabled.") : "Chat Events configuration change failed."));
            }
            case "pause" -> { if (permission(sender, "plexonchats.events.manage")) { events.pause(); sender.sendMessage(Component.text("Chat Events automatic scheduling paused for this runtime.")); } }
            case "resume" -> { if (permission(sender, "plexonchats.events.manage")) { events.resume(); sender.sendMessage(Component.text("Chat Events automatic scheduling resumed.")); } }
            case "start" -> {
                if (!permission(sender, "plexonchats.events.manage")) return;
                if (args.length < 3) { sender.sendMessage(Component.text("Usage: /chat events start <event-id|random>")); return; }
                ChatEventManager.StartStatus result = args[2].equalsIgnoreCase("random") ? events.startRandom(false) : events.start(args[2]);
                sender.sendMessage(Component.text("Chat Event start: " + result));
            }
            case "stop" -> {
                if (permission(sender, "plexonchats.events.manage")) sender.sendMessage(Component.text(events.stop() ? "Active Chat Event cancelled without reward." : "No active Chat Event."));
            }
            case "preview" -> {
                if (!permission(sender, "plexonchats.events.manage")) return;
                if (args.length < 3) sender.sendMessage(Component.text("Usage: /chat events preview <event-id>"));
                else events.preview(args[2], sender);
            }
            default -> sender.sendMessage(Component.text("Usage: /chat events [status|list|stats|leaderboard|bingo|enable|disable|pause|resume|start|stop|preview]"));
        }
    }

    private void eventStatus(CommandSender sender, ChatEventManager events) {
        sender.sendMessage(Component.text("Chat Events status"));
        diagnostic(sender, "Master", events.enabled() ? "ENABLED" : "DISABLED");
        diagnostic(sender, "Scheduler configured", events.schedulerEnabled() ? "ENABLED" : "DISABLED");
        diagnostic(sender, "Runtime pause", events.paused() ? "PAUSED" : "RUNNING");
        diagnostic(sender, "Coordinator task", events.taskActive() ? "ACTIVE" : "INACTIVE");
        diagnostic(sender, "Active", events.activeId() + (events.hasActiveEvent() ? "/" + events.activeType() : ""));
        diagnostic(sender, "Remaining", events.remainingMillis() < 0 ? "-" : events.remainingMillis() + "ms");
        diagnostic(sender, "Eligible definitions", String.valueOf(events.eligibleScheduledCount()));
        diagnostic(sender, "Last event", events.lastEvent());
        diagnostic(sender, "Last winner", events.lastWinner());
        diagnostic(sender, "Economy", events.economyState());
        diagnostic(sender, "PlexonKeys", events.keysState());
    }

    private void eventStats(CommandSender sender, String[] args, ChatEventManager events) {
        ChatEventStatisticsService.PlayerStats stats;
        if (args.length >= 3) {
            if (!permission(sender, "plexonchats.events.stats.others")) return;
            Player online = Bukkit.getPlayerExact(args[2]);
            stats = online != null ? events.statistics().get(online.getUniqueId(), online.getName()) : events.statistics().getByName(args[2]);
            if (stats == null) { sender.sendMessage(Component.text("No Chat Event statistics found for " + args[2] + ".")); return; }
        } else {
            if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Usage: /chat events stats <player>")); return; }
            stats = events.statistics().get(player.getUniqueId(), player.getName());
        }
        sender.sendMessage(Component.text("Chat Event statistics — " + stats.playerName()));
        diagnostic(sender, "Total wins", Long.toString(stats.totalWins()));
        for (var type : com.antondev.chats.event.ChatEventEngine.Type.values()) diagnostic(sender, type.name() + " wins", Long.toString(stats.typeWins(type.name())));
        diagnostic(sender, "Last win", stats.lastWinAt() == null ? "-" : java.time.Instant.ofEpochMilli(stats.lastWinAt()).toString());
    }

    private void eventLeaderboard(CommandSender sender, ChatEventManager events) {
        sender.sendMessage(Component.text("Chat Event leaderboard"));
        List<ChatEventStatisticsService.PlayerStats> top = events.statistics().getTopPlayers(10);
        if (top.isEmpty()) { sender.sendMessage(Component.text(" • No wins recorded yet.")); return; }
        for (int index = 0; index < top.size(); index++) {
            ChatEventStatisticsService.PlayerStats stats = top.get(index);
            sender.sendMessage(Component.text(" " + (index + 1) + ". " + stats.playerName() + " — " + stats.totalWins() + " wins"));
        }
    }

    private void bingo(CommandSender sender, String[] args, ChatEventManager events) {
        String sub = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "join" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(plugin.getConfigManager().getPlayerOnly()); return; }
                if (!permission(sender, "plexonchats.events.bingo.play")) return;
                if (args.length < 4) { sender.sendMessage(Component.text("Usage: /chat events bingo join <run-id>")); return; }
                try { events.bingoJoin(player, UUID.fromString(args[3])); }
                catch (IllegalArgumentException ex) { sender.sendMessage(Component.text("That Bingo run ID is invalid.")); }
            }
            case "card" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(plugin.getConfigManager().getPlayerOnly()); return; }
                if (!permission(sender, "plexonchats.events.bingo.play")) return;
                if (!events.showBingoCard(player)) sender.sendMessage(Component.text("You are not participating in an active Bingo round."));
            }
            case "mark" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(plugin.getConfigManager().getPlayerOnly()); return; }
                if (!permission(sender, "plexonchats.events.bingo.play")) return;
                if (args.length < 5) { sender.sendMessage(Component.text("That Bingo action is incomplete.")); return; }
                try { events.bingoMark(player, UUID.fromString(args[3]), Integer.parseInt(args[4])); }
                catch (IllegalArgumentException ex) { sender.sendMessage(Component.text("That Bingo action is invalid.")); }
            }
            case "status" -> {
                if (!permission(sender, "plexonchats.events.manage")) return;
                diagnostic(sender, "Bingo phase", events.bingoPhase());
                diagnostic(sender, "Participants", String.valueOf(events.bingoParticipantCount()));
                diagnostic(sender, "Draw count", String.valueOf(events.bingoDrawCount()));
                diagnostic(sender, "Last draw", events.bingoLastDraw());
                diagnostic(sender, "Remaining pool", String.valueOf(events.bingoRemainingCount()));
            }
            case "participants" -> {
                if (!permission(sender, "plexonchats.events.manage")) return;
                sender.sendMessage(Component.text("Bingo participants: " + (events.bingoParticipants().isEmpty() ? "none" : String.join(", ", events.bingoParticipants()))));
            }
            default -> sender.sendMessage(Component.text("Usage: /chat events bingo <join|card|status|participants>"));
        }
    }

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
        helpLine(sender, "plexonchats.events", "/chat events — Event dashboard, stats and leaderboard");
        helpLine(sender, "plexonchats.events.manage", "/chat events <enable|disable|pause|resume|start|stop|preview> — Chat Events administration");
    }
    private void helpLine(CommandSender sender, String permission, String value) { if (sender.hasPermission(permission)) sender.sendMessage(Component.text(" • " + value)); }

    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || !sender.hasPermission("plexonchats.use")) return List.of();
        List<String> options = new ArrayList<>();
        String root = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            options.addAll(List.of("help", "channel"));
            if (sender.hasPermission("plexonchats.gui")) options.add("gui");
            if (sender.hasPermission("plexonchats.manage")) options.addAll(List.of("admin", "status", "diagnostics"));
            if (sender.hasPermission("plexonchats.reload")) options.add("reload");
            if (sender.hasPermission("plexonchats.automessages")) options.add("automessages");
            if (sender.hasPermission("plexonchats.events")) options.add("events");
        } else if (args.length == 2 && List.of("channel", "ch", "c").contains(root)) {
            for (ChatChannel channel : ChatChannel.values()) {
                if (plugin.getConfigManager().isChannelEnabled(channel) && sender.hasPermission(channel.getPermission())) options.add(channel.name().toLowerCase(Locale.ROOT));
            }
        } else if (List.of("automessages", "automsg").contains(root) && sender.hasPermission("plexonchats.automessages")) {
            if (args.length == 2) options.addAll(List.of("list", "enable", "disable", "pause", "resume", "send", "test", "preview"));
            else if (args.length == 3 && List.of("send", "test", "preview").contains(args[1].toLowerCase(Locale.ROOT))) options.addAll(plugin.getAutoMessages().groupNames());
        } else if (List.of("events", "event").contains(root) && sender.hasPermission("plexonchats.events")) {
            if (args.length == 2) {
                options.addAll(List.of("status", "list", "stats", "leaderboard", "bingo"));
                if (sender.hasPermission("plexonchats.events.manage")) options.addAll(List.of("enable", "disable", "pause", "resume", "start", "stop", "preview"));
            } else if (args.length == 3) {
                String action = args[1].toLowerCase(Locale.ROOT);
                if (action.equals("start") && sender.hasPermission("plexonchats.events.manage")) { options.add("random"); options.addAll(plugin.getChatEvents().eventIds()); }
                else if (action.equals("preview") && sender.hasPermission("plexonchats.events.manage")) options.addAll(plugin.getChatEvents().eventIds());
                else if (action.equals("bingo")) {
                    if (sender.hasPermission("plexonchats.events.bingo.play")) options.add("card");
                    if (sender.hasPermission("plexonchats.events.manage")) options.addAll(List.of("status", "participants"));
                }
            }
        }
        String query = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(query)).toList();
    }
}
