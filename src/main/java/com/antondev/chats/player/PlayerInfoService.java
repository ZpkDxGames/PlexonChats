package com.antondev.chats.player;

import com.antondev.chats.PlexonChats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.regex.Pattern;

/**
 * Provides player information used in chat hovers.
 * Vault integration is optional and loaded via reflection.
 */
public class PlayerInfoService {

    private final PlexonChats plugin;
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.00");

    private boolean vaultChecked;
    private Object permissionProvider;
    private Object chatProvider;
    private Object economyProvider;
    private Method permissionPrimaryGroupMethod;
    private Method chatGetPrefixMethod;
    private Method economyGetBalanceMethod;

    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("(?i)[&§][0-9A-FK-ORX]");

    public PlayerInfoService(PlexonChats plugin) {
        this.plugin = plugin;
    }

    public Component buildPlayerHover(Player player) {
        MiniMessage mm = plugin.getConfigManager().getMiniMessage();
        String playtime = formatPlaytime(player);
        String rank = stripLegacyColors(getRankDisplayText(player));
        String balance = getBalance(player);

        String hover = "<b><gradient:#00e6ff:#00ffac>Player Info</gradient></b>\n"
                + "<gray>Name: <white>" + player.getName() + "\n"
                + "<gray>World: <white>" + player.getWorld().getName() + "\n"
                + "<gray>Playtime: <white>" + playtime + "\n"
                + "<gray>Rank: <white>" + rank + "\n"
                + "<gray>Balance: <white>" + balance + "\n"
                + "<dark_gray>Click to private message";

        return mm.deserialize(hover);
    }

    public Component buildRankPrefix(Player player) {
        String rankDisplay = getRankDisplayText(player);
        if (rankDisplay == null || rankDisplay.isBlank()) {
            return Component.empty();
        }

        Component prefix = LegacyComponentSerializer.legacySection()
                .deserialize(rankDisplay.replace('&', '§'));
        return prefix.append(Component.space());
    }

    private String formatPlaytime(Player player) {
        long seconds = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private String getRank(Player player) {
        hookVaultIfNeeded();
        if (permissionProvider == null || permissionPrimaryGroupMethod == null) {
            return "N/A";
        }

        try {
            Object rank = permissionPrimaryGroupMethod.invoke(permissionProvider, player);
            return rank == null ? "N/A" : String.valueOf(rank);
        } catch (ReflectiveOperationException ex) {
            return "N/A";
        }
    }

    private String getRankDisplayText(Player player) {
        hookVaultIfNeeded();

        if (chatProvider != null && chatGetPrefixMethod != null) {
            try {
                Object value = chatGetPrefixMethod.invoke(chatProvider, player);
                if (value != null && !String.valueOf(value).isBlank()) {
                    return String.valueOf(value).trim();
                }
            } catch (ReflectiveOperationException ignored) {
                // Fallback to permission rank name below
            }
        }

        String group = getRank(player);
        if (group.equalsIgnoreCase("N/A")) {
            return "";
        }
        return "§7[§f" + group + "§7]";
    }

    private String getBalance(Player player) {
        hookVaultIfNeeded();
        if (economyProvider == null || economyGetBalanceMethod == null) {
            return "N/A";
        }

        try {
            Object balance = economyGetBalanceMethod.invoke(economyProvider, player);
            if (!(balance instanceof Number number)) {
                return "N/A";
            }
            return "$" + balanceFormat.format(number.doubleValue());
        } catch (ReflectiveOperationException ex) {
            return "N/A";
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void hookVaultIfNeeded() {
        if (vaultChecked) {
            return;
        }
        vaultChecked = true;

        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }

        try {
            Class<?> permissionClass = Class.forName("net.milkbowl.vault.permission.Permission");
            RegisteredServiceProvider<?> permProvider = Bukkit.getServicesManager()
                    .getRegistration((Class) permissionClass);
            if (permProvider != null) {
                permissionProvider = permProvider.getProvider();
                permissionPrimaryGroupMethod = permissionClass.getMethod("getPrimaryGroup", Player.class);
            }
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            permissionProvider = null;
            permissionPrimaryGroupMethod = null;
        }

        try {
            Class<?> chatClass = Class.forName("net.milkbowl.vault.chat.Chat");
            RegisteredServiceProvider<?> chatServiceProvider = Bukkit.getServicesManager()
                    .getRegistration((Class) chatClass);
            if (chatServiceProvider != null) {
                chatProvider = chatServiceProvider.getProvider();
                chatGetPrefixMethod = chatClass.getMethod("getPlayerPrefix", Player.class);
            }
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            chatProvider = null;
            chatGetPrefixMethod = null;
        }

        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> ecoProvider = Bukkit.getServicesManager()
                    .getRegistration((Class) economyClass);
            if (ecoProvider != null) {
                economyProvider = ecoProvider.getProvider();
                try {
                    economyGetBalanceMethod = economyClass.getMethod("getBalance", OfflinePlayer.class);
                } catch (NoSuchMethodException ex) {
                    economyGetBalanceMethod = economyClass.getMethod("getBalance", Player.class);
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            economyProvider = null;
            economyGetBalanceMethod = null;
        }
    }

    private String stripLegacyColors(String input) {
        if (input == null || input.isBlank()) {
            return "N/A";
        }
        String stripped = LEGACY_COLOR_PATTERN.matcher(input).replaceAll("").trim();
        return stripped.isBlank() ? "N/A" : stripped;
    }
}
