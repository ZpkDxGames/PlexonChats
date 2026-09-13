package com.antondev.chats.command;

import com.antondev.chats.PlexonChats;
import com.antondev.chats.event.ChatEventManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Joinable participant-owned Bingo command surface. */
public final class BingoCommand implements CommandExecutor, TabCompleter {
    private final PlexonChats plugin;
    public BingoCommand(PlexonChats plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        ChatEventManager events = plugin.getChatEvents();
        String action = args.length == 0 ? "view" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "view", "board", "card" -> view(sender, events);
            case "join" -> { Player player = player(sender); if (player != null && permission(sender, "plexonchats.events.bingo.play")) events.bingoJoin(player); }
            case "leave" -> { Player player = player(sender); if (player != null && permission(sender, "plexonchats.events.bingo.play")) events.bingoLeave(player); }
            case "claim" -> claim(sender, events, args);
            case "mark" -> mark(sender, events, args);
            case "start" -> start(sender, events, args);
            case "stop" -> { if (permission(sender, "plexonchats.events.manage")) sender.sendMessage(Component.text(events.stopBingo() ? "Bingo cancelled without reward or statistic." : "There is no Bingo lobby or active round.")); }
            case "status" -> status(sender, events);
            default -> sender.sendMessage(Component.text("Usage: /bingo [join|leave|claim|start [now]|stop|status]"));
        }
        return true;
    }

    private void view(CommandSender sender, ChatEventManager events) {
        Player player = player(sender);
        if (player == null || !permission(sender, "plexonchats.events.bingo.play")) return;
        if (!events.showBingoBoard(player)) sender.sendMessage(Component.text(events.bingoPhase().equals("LOBBY") ? "Join this Bingo lobby first with /bingo join." : "You do not have a current Bingo card."));
    }

    private void claim(CommandSender sender, ChatEventManager events, String[] args) {
        Player player = player(sender);
        if (player == null || !permission(sender, "plexonchats.events.bingo.play")) return;
        if (args.length >= 2) {
            try { events.bingoClaim(player, UUID.fromString(args[1])); }
            catch (IllegalArgumentException ex) { player.sendActionBar(Component.text("That Bingo claim link is invalid.")); }
        } else events.bingoClaim(player);
    }

    private void mark(CommandSender sender, ChatEventManager events, String[] args) {
        Player player = player(sender);
        if (player == null || !permission(sender, "plexonchats.events.bingo.play")) return;
        if (args.length != 3) { player.sendActionBar(Component.text("That Bingo mark link is invalid.")); return; }
        try { events.bingoMark(player, UUID.fromString(args[1]), Integer.parseInt(args[2])); }
        catch (IllegalArgumentException ex) { player.sendActionBar(Component.text("That Bingo mark link is invalid.")); }
    }

    private void start(CommandSender sender, ChatEventManager events, String[] args) {
        if (!permission(sender, "plexonchats.events.manage")) return;
        ChatEventManager.StartStatus result = args.length >= 2 && args[1].equalsIgnoreCase("now") ? events.startBingoNow() : events.startBingo();
        sender.sendMessage(Component.text(switch (result) {
            case STARTED -> args.length >= 2 && args[1].equalsIgnoreCase("now") ? "Bingo fast-start accepted." : "Bingo join lobby opened.";
            case ALREADY_ACTIVE -> "A Bingo round or another Chat Event is already active.";
            case NOT_ENOUGH_PLAYERS -> "At least one participant must join before Bingo can activate.";
            case DISABLED -> "Chat Events are disabled.";
            case NOT_FOUND -> "No enabled Bingo definition is configured.";
            case EVENT_DISABLED -> "Bingo is disabled.";
            default -> "Bingo could not be started: " + result.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".";
        }));
    }

    private void status(CommandSender sender, ChatEventManager events) {
        if (!permission(sender, "plexonchats.events.manage")) return;
        sender.sendMessage(Component.text("Bingo status"));
        line(sender, "Phase", events.bingoPhase());
        line(sender, "Run ID", events.activeType().equals("BINGO") && events.activeRunId() != null ? events.activeRunId().toString() : "-");
        line(sender, "Source", events.bingoSource());
        line(sender, "Lobby remaining", events.bingoLobbyRemainingMillis() < 0 ? "-" : String.format(Locale.ROOT, "%.1fs", events.bingoLobbyRemainingMillis() / 1000.0));
        line(sender, "Participants joined", Integer.toString(events.bingoParticipantCount()));
        line(sender, "Participants online", Integer.toString(events.bingoOnlineParticipantCount()));
        line(sender, "Last call", events.bingoLastDraw());
        line(sender, "Draw count", Integer.toString(events.bingoDrawCount()));
        line(sender, "Remaining pool", Integer.toString(events.bingoRemainingCount()));
        line(sender, "Next call", events.bingoNextDrawMillis() < 0 ? "-" : String.format(Locale.ROOT, "%.1fs", events.bingoNextDrawMillis() / 1000.0));
        line(sender, "Winning patterns", events.bingoPatterns());
        line(sender, "Reward profile", events.bingoRewardProfile());
        line(sender, "Discord state", events.bingoDiscordEnabled() ? events.discordTransport() + "/" + events.discordTransportStatus() + "/" + events.discordMessageState() : "DISABLED");
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        sender.sendMessage(plugin.getConfigManager().getPlayerOnly());
        return null;
    }
    private boolean permission(CommandSender sender, String permission) { if (sender.hasPermission(permission)) return true; sender.sendMessage(plugin.getConfigManager().getNoPermission()); return false; }
    private static void line(CommandSender sender, String key, String value) { sender.sendMessage(Component.text(" • " + key + ": " + value)); }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            ArrayList<String> values = new ArrayList<>();
            if (sender.hasPermission("plexonchats.events.bingo.play")) values.addAll(List.of("join", "leave", "claim"));
            if (sender.hasPermission("plexonchats.events.manage")) values.addAll(List.of("start", "stop", "status"));
            String query = args[0].toLowerCase(Locale.ROOT);
            return values.stream().filter(value -> value.startsWith(query)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start") && sender.hasPermission("plexonchats.events.manage")) return List.of("now");
        return List.of();
    }
}
