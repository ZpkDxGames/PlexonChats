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
import java.util.UUID;

/** Runtime-only player presentation metadata. No storage load is ever performed from chat. */
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

    private boolean luckPermsChecked;
    private Object luckPerms;
    private Method luckPermsGetUserManager;
    private Method userManagerGetUser;
    private Method userGetCachedData;
    private Method userGetPrimaryGroup;
    private Method cachedDataGetMetaData;
    private Method cachedMetaGetPrefix;

    public PlayerInfoService(PlexonChats plugin) { this.plugin = plugin; }

    public Component buildPlayerHover(Player player) {
        return plugin.getText().render(String.join("\n", plugin.getConfigManager().lines("chat-components.player.hover.lines")), player);
    }

    public Component buildRankPrefix(Player player) {
        String rankDisplay = getRankDisplayText(player);
        if (rankDisplay == null || rankDisplay.isBlank()) return Component.empty();
        return com.antondev.chats.text.TextService.legacy(rankDisplay).append(Component.space());
    }

    public String formatPlaytime(Player player) {
        long seconds = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    public String getRank(Player player) {
        Object user = cachedLuckPermsUser(player.getUniqueId());
        if (user != null && userGetPrimaryGroup != null) {
            try {
                Object group = userGetPrimaryGroup.invoke(user);
                if (group != null && !String.valueOf(group).isBlank()) return String.valueOf(group);
            } catch (ReflectiveOperationException ignored) { }
        }
        hookVaultIfNeeded();
        if (permissionProvider == null || permissionPrimaryGroupMethod == null) return "N/A";
        try {
            Object rank = permissionPrimaryGroupMethod.invoke(permissionProvider, player);
            return rank == null ? "N/A" : String.valueOf(rank);
        } catch (ReflectiveOperationException ex) { return "N/A"; }
    }

    private String getRankDisplayText(Player player) {
        Object user = cachedLuckPermsUser(player.getUniqueId());
        if (user != null && userGetCachedData != null && cachedDataGetMetaData != null && cachedMetaGetPrefix != null) {
            try {
                Object cachedData = userGetCachedData.invoke(user);
                Object meta = cachedDataGetMetaData.invoke(cachedData);
                Object value = cachedMetaGetPrefix.invoke(meta);
                if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
            } catch (ReflectiveOperationException ignored) { }
        }

        hookVaultIfNeeded();
        if (chatProvider != null && chatGetPrefixMethod != null) {
            try {
                Object value = chatGetPrefixMethod.invoke(chatProvider, player);
                if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
            } catch (ReflectiveOperationException ignored) { }
        }

        String group = getRank(player);
        return group.equalsIgnoreCase("N/A") ? "" : "§7[§f" + group + "§7]";
    }

    public String getBalance(Player player) {
        hookVaultIfNeeded();
        if (economyProvider == null || economyGetBalanceMethod == null) return "N/A";
        try {
            Object balance = economyGetBalanceMethod.invoke(economyProvider, player);
            if (!(balance instanceof Number number)) return "N/A";
            return "$" + balanceFormat.format(number.doubleValue());
        } catch (ReflectiveOperationException ex) { return "N/A"; }
    }

    private Object cachedLuckPermsUser(UUID uuid) {
        hookLuckPermsIfNeeded();
        if (luckPerms == null || luckPermsGetUserManager == null || userManagerGetUser == null) return null;
        try {
            Object manager = luckPermsGetUserManager.invoke(luckPerms);
            return userManagerGetUser.invoke(manager, uuid); // cached-only: deliberately never calls loadUser
        } catch (ReflectiveOperationException ex) { return null; }
    }

    private void hookLuckPermsIfNeeded() {
        if (luckPermsChecked) return;
        luckPermsChecked = true;
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return;
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            luckPerms = providerClass.getMethod("get").invoke(null);
            Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
            Class<?> userManagerClass = Class.forName("net.luckperms.api.model.user.UserManager");
            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            Class<?> cachedDataClass = Class.forName("net.luckperms.api.cacheddata.CachedDataManager");
            Class<?> cachedMetaClass = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            luckPermsGetUserManager = luckPermsClass.getMethod("getUserManager");
            userManagerGetUser = userManagerClass.getMethod("getUser", UUID.class);
            userGetCachedData = userClass.getMethod("getCachedData");
            userGetPrimaryGroup = userClass.getMethod("getPrimaryGroup");
            cachedDataGetMetaData = cachedDataClass.getMethod("getMetaData");
            cachedMetaGetPrefix = cachedMetaClass.getMethod("getPrefix");
        } catch (ReflectiveOperationException | LinkageError ex) {
            luckPerms = null;
            plugin.getDiagnostics().recordIntegrationFailure("LuckPerms", ex);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void hookVaultIfNeeded() {
        if (vaultChecked) return;
        vaultChecked = true;
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return;
        try {
            Class<?> permissionClass = Class.forName("net.milkbowl.vault.permission.Permission");
            RegisteredServiceProvider<?> permProvider = Bukkit.getServicesManager().getRegistration((Class) permissionClass);
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
            RegisteredServiceProvider<?> chatServiceProvider = Bukkit.getServicesManager().getRegistration((Class) chatClass);
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
            RegisteredServiceProvider<?> ecoProvider = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (ecoProvider != null) {
                economyProvider = ecoProvider.getProvider();
                try { economyGetBalanceMethod = economyClass.getMethod("getBalance", OfflinePlayer.class); }
                catch (NoSuchMethodException ex) { economyGetBalanceMethod = economyClass.getMethod("getBalance", Player.class); }
            }
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            economyProvider = null;
            economyGetBalanceMethod = null;
        }
    }

    public String luckPermsState() {
        hookLuckPermsIfNeeded();
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return "NOT INSTALLED";
        return luckPerms == null ? "INCOMPATIBLE" : "READY / CACHED USER DATA";
    }
    public String vaultState() {
        hookVaultIfNeeded();
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return "NOT INSTALLED";
        return permissionProvider != null || chatProvider != null || economyProvider != null ? "READY" : "NO PROVIDER";
    }
    public String plexonRanksState() {
        return Bukkit.getPluginManager().isPluginEnabled("PlexonRanks") ? "AVAILABLE / PRESENTATION ONLY" : "NOT INSTALLED";
    }

    public void refreshHooks() {
        vaultChecked = false;
        permissionProvider = null;
        chatProvider = null;
        economyProvider = null;
        permissionPrimaryGroupMethod = null;
        chatGetPrefixMethod = null;
        economyGetBalanceMethod = null;
        luckPermsChecked = false;
        luckPerms = null;
        luckPermsGetUserManager = null;
        userManagerGetUser = null;
        userGetCachedData = null;
        userGetPrimaryGroup = null;
        cachedDataGetMetaData = null;
        cachedMetaGetPrefix = null;
    }
}
