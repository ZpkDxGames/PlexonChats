package com.antondev.chats.command;

import com.antondev.chats.PlexonChats;
import com.antondev.chats.event.ChatEventManager;
import com.antondev.chats.event.bingo.BingoRun;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Canonical shared-board Bingo command surface. */
public final class BingoCommand implements CommandExecutor, TabCompleter {
    private final PlexonChats plugin;

    public BingoCommand(PlexonChats plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        ChatEventManager events = plugin.getChatEvents();
        String action = args.length == 0 ? "board" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "board", "view" -> board(sender, events);
            case "claim" -> claim(sender, events);
            case "start" -> {
                if (!permission(sender, "plexonchats.events.manage")) return true;
                sender.sendMessage(Component.text("Bingo start: " + events.startBingo()));
            }
            case "stop" -> {
                if (!permission(sender, "plexonchats.events.manage")) return true;
                sender.sendMessage(Component.text(events.stopBingo() ? "Active Bingo cancelled without reward." : "No active Bingo run."));
            }
            case "status" -> status(sender, events);
            default -> sender.sendMessage(Component.text("Usage: /bingo [claim|start|stop|status]"));
        }
        return true;
    }

    private void board(CommandSender sender, ChatEventManager events) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("Players can view the live board with /bingo; use /bingo status for diagnostics.")); return; }
        if (!permission(sender, "plexonchats.events.bingo.play")) return;
        if (!events.showBingoBoard(player)) sender.sendMessage(Component.text("There is no active Bingo board available to you."));
    }

    private void claim(CommandSender sender, ChatEventManager events) {
        if (!(sender instanceof Player player)) { sender.sendMessage(plugin.getConfigManager().getPlayerOnly()); return; }
        if (!permission(sender, "plexonchats.events.bingo.play")) return;
        BingoRun.ClaimResult result = events.bingoClaim(player);
        if (result.status() == BingoRun.ClaimStatus.NOT_ACTIVE && !"BINGO".equals(events.activeType())) {
            sender.sendMessage(Component.text("There is no active Bingo round."));
        }
    }

    private void status(CommandSender sender, ChatEventManager events) {
        if (!permission(sender, "plexonchats.events.manage")) return;
        sender.sendMessage(Component.text("Bingo status"));
        line(sender, "Phase", events.bingoPhase());
        line(sender, "Run", events.activeType().equals("BINGO") && events.activeRunId() != null ? events.activeRunId().toString() : "-");
        line(sender, "Last call", events.bingoLastDraw());
        line(sender, "Draw count", Integer.toString(events.bingoDrawCount()));
        line(sender, "Remaining pool", Integer.toString(events.bingoRemainingCount()));
        line(sender, "Next call", events.bingoNextDrawMillis() < 0 ? "-" : String.format(Locale.ROOT, "%.1fs", events.bingoNextDrawMillis() / 1000.0));
        line(sender, "Patterns", events.bingoPatterns());
        line(sender, "Reward profile", events.bingoRewardProfile());
        line(sender, "Discord sync", events.bingoDiscordEnabled() ? "ENABLED" : "DISABLED");
        line(sender, "Discord transport", events.discordTransport() + "/" + events.discordTransportStatus());
        line(sender, "Discord message", events.discordMessageState());
    }

    private boolean permission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        sender.sendMessage(plugin.getConfigManager().getNoPermission());
        return false;
    }

    private static void line(CommandSender sender, String key, String value) { sender.sendMessage(Component.text(" • " + key + ": " + value)); }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        ArrayList<String> values = new ArrayList<>();
        if (sender.hasPermission("plexonchats.events.bingo.play")) values.add("claim");
        if (sender.hasPermission("plexonchats.events.manage")) values.addAll(List.of("start", "stop", "status"));
        String query = args[0].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(query)).toList();
    }
}
