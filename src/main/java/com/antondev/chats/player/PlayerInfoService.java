package com.antondev.chats.player;

import com.antondev.chats.PlexonChats;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.text.DecimalFormat;

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


    public PlayerInfoService(PlexonChats plugin) {
        this.plugin = plugin;
    }

    public Component buildPlayerHover(Player player) {
        return plugin.getText().render(String.join("\n", plugin.getConfigManager()
                .lines("chat-components.player.hover.lines")), player);
    }

    public Component buildRankPrefix(Player player) {
        String rankDisplay = getRankDisplayText(player);
        if (rankDisplay == null || rankDisplay.isBlank()) {
            return Component.empty();
        }

        Component prefix = com.antondev.chats.text.TextService.legacy(rankDisplay);
        return prefix.append(Component.space());
    }

    public String formatPlaytime(Player player) {
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

    public String getRank(Player player) {
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

    public String getBalance(Player player) {
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

        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
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

    public void refreshHooks() {
        vaultChecked = false;
        permissionProvider = null;
        chatProvider = null;
        economyProvider = null;
        permissionPrimaryGroupMethod = null;
        chatGetPrefixMethod = null;
        economyGetBalanceMethod = null;
    }

}
