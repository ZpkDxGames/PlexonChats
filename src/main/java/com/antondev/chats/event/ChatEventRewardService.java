package com.antondev.chats.event;

import com.antondev.chats.PlexonChats;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Reward boundary for Chat Events. All mutations are invoked on the primary server thread. */
public final class ChatEventRewardService {
    public enum Status { SUCCESS, PARTIAL, FAILED, SKIPPED }
    public record ComponentResult(String component, Status status, String detail, String grantedSummary) { }
    public record RewardResult(List<ComponentResult> components) {
        public RewardResult { components = List.copyOf(components); }
        public String summary() {
            List<String> granted = components.stream()
                    .filter(result -> result.status() == Status.SUCCESS || result.status() == Status.PARTIAL)
                    .map(ComponentResult::grantedSummary).filter(value -> value != null && !value.isBlank()).toList();
            boolean failed = components.stream().anyMatch(result -> result.status() == Status.FAILED || result.status() == Status.PARTIAL);
            String base = granted.isEmpty() ? "no reward granted" : String.join(" + ", granted);
            return failed ? base + " (partial)" : base;
        }
        public boolean hasFailure() { return components.stream().anyMatch(result -> result.status() == Status.FAILED || result.status() == Status.PARTIAL); }
    }

    private final PlexonChats plugin;
    private volatile ChatEventConfig config;
    private boolean vaultChecked;
    private Object economyProvider;
    private Method economyDeposit;
    private boolean keysChecked;
    private Object keysApi;
    private Method keysGrant;
    private Class<?> keyTierClass;
    private Class<?> keySourceClass;

    public ChatEventRewardService(PlexonChats plugin) { this.plugin = plugin; }

    public void reload(ChatEventConfig config) {
        this.config = config;
        refreshIntegrations();
    }

    public void refreshIntegrations() {
        vaultChecked = false;
        economyProvider = null;
        economyDeposit = null;
        keysChecked = false;
        keysApi = null;
        keysGrant = null;
        keyTierClass = null;
        keySourceClass = null;
    }

    public RewardResult grant(Player winner, ChatEventConfig.Definition definition) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Chat Event rewards must run on the primary thread");
        ChatEventConfig current = config;
        if (current == null) return new RewardResult(List.of(new ComponentResult("BUNDLE", Status.FAILED, "reward configuration unavailable", "")));
        ChatEventConfig.RewardProfile profile = current.rewards().get(definition.rewardProfile());
        if (profile == null) return new RewardResult(List.of(new ComponentResult("BUNDLE", Status.FAILED, "reward profile missing", "")));
        List<ComponentResult> results = new ArrayList<>();
        results.add(grantEconomy(winner, profile));
        results.add(grantKeys(winner, profile));
        results.add(grantCommands(winner, definition, profile));
        RewardResult result = new RewardResult(results);
        if (result.hasFailure()) plugin.getDiagnostics().recordIntegrationFailure("chat-events-reward", new IllegalStateException(describeFailure(result)));
        return result;
    }

    private ComponentResult grantEconomy(Player winner, ChatEventConfig.RewardProfile profile) {
        if (!profile.economyEnabled() || profile.economyAmount() == 0) return new ComponentResult("ECONOMY", Status.SKIPPED, "disabled", "");
        hookEconomy();
        if (economyProvider == null || economyDeposit == null) return new ComponentResult("ECONOMY", Status.FAILED, economyState(), "");
        try {
            Class<?> identityType = economyDeposit.getParameterTypes()[0];
            Object identity = Player.class.isAssignableFrom(identityType) ? winner : Bukkit.getOfflinePlayer(winner.getUniqueId());
            Object response = economyDeposit.invoke(economyProvider, identity, profile.economyAmount());
            boolean success = transactionSucceeded(response);
            String detail = success ? "credited" : transactionError(response);
            String summary = success ? "$" + compact(profile.economyAmount()) : "";
            return new ComponentResult("ECONOMY", success ? Status.SUCCESS : Status.FAILED, detail, summary);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getDiagnostics().recordIntegrationFailure("chat-events-vault", unwrap(ex));
            return new ComponentResult("ECONOMY", Status.FAILED, ex.getClass().getSimpleName(), "");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void hookEconomy() {
        if (vaultChecked) return;
        vaultChecked = true;
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return;
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) return;
            economyProvider = registration.getProvider();
            try { economyDeposit = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class); }
            catch (NoSuchMethodException ignored) { economyDeposit = economyClass.getMethod("depositPlayer", Player.class, double.class); }
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ex) {
            economyProvider = null;
            economyDeposit = null;
            plugin.getDiagnostics().recordIntegrationFailure("chat-events-vault", ex);
        }
    }

    private ComponentResult grantKeys(Player winner, ChatEventConfig.RewardProfile profile) {
        if (!profile.keysEnabled() || profile.keyAmount() == 0) return new ComponentResult("PLEXONKEYS", Status.SKIPPED, "disabled", "");
        hookKeys();
        if (keysApi == null || keysGrant == null || keyTierClass == null || keySourceClass == null) {
            return new ComponentResult("PLEXONKEYS", Status.FAILED, keysState(), "");
        }
        try {
            @SuppressWarnings({"unchecked", "rawtypes"}) Object tier = Enum.valueOf((Class<? extends Enum>) keyTierClass.asSubclass(Enum.class), profile.keyTier().toUpperCase(Locale.ROOT));
            @SuppressWarnings({"unchecked", "rawtypes"}) Object source = Enum.valueOf((Class<? extends Enum>) keySourceClass.asSubclass(Enum.class), "API");
            Object returned = keysGrant.invoke(keysApi, winner.getUniqueId(), tier, profile.keyAmount(), source);
            long credited = returned instanceof Number number ? number.longValue() : profile.keyAmount();
            if (credited <= 0) return new ComponentResult("PLEXONKEYS", Status.FAILED, "API credited zero keys", "");
            Status status = credited == profile.keyAmount() ? Status.SUCCESS : Status.PARTIAL;
            return new ComponentResult("PLEXONKEYS", status, "credited " + credited + "/" + profile.keyAmount(), profile.keyTier() + " key x" + credited);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            plugin.getDiagnostics().recordIntegrationFailure("chat-events-plexonkeys", unwrap(ex));
            return new ComponentResult("PLEXONKEYS", Status.FAILED, ex.getClass().getSimpleName(), "");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void hookKeys() {
        if (keysChecked) return;
        keysChecked = true;
        if (!Bukkit.getPluginManager().isPluginEnabled("PlexonKeys")) return;
        try {
            Class<?> apiClass = Class.forName("com.antondev.keys.api.PlexonKeysAPI");
            keyTierClass = Class.forName("com.antondev.keys.model.KeyTier");
            keySourceClass = Class.forName("com.antondev.keys.api.KeySource");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) apiClass);
            if (registration == null) return;
            keysApi = registration.getProvider();
            keysGrant = apiClass.getMethod("grant", UUID.class, keyTierClass, long.class, keySourceClass);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ex) {
            keysApi = null;
            keysGrant = null;
            plugin.getDiagnostics().recordIntegrationFailure("chat-events-plexonkeys", ex);
        }
    }

    private ComponentResult grantCommands(Player winner, ChatEventConfig.Definition definition, ChatEventConfig.RewardProfile profile) {
        if (profile.consoleCommands().isEmpty()) return new ComponentResult("COMMANDS", Status.SKIPPED, "0 configured", "");
        int success = 0;
        for (String source : profile.consoleCommands()) {
            String command = source
                    .replace("{player_name}", winner.getName())
                    .replace("{player_uuid}", winner.getUniqueId().toString())
                    .replace("{event_id}", definition.id())
                    .replace("{event_type}", definition.type().name());
            if (command.startsWith("/")) command = command.substring(1);
            if (command.isBlank() || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) continue;
            try { if (Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) success++; }
            catch (RuntimeException ex) { plugin.getDiagnostics().recordIntegrationFailure("chat-events-command", ex); }
        }
        Status status = success == profile.consoleCommands().size() ? Status.SUCCESS : success == 0 ? Status.FAILED : Status.PARTIAL;
        String summary = success == 0 ? "" : success + " command reward" + (success == 1 ? "" : "s");
        return new ComponentResult("COMMANDS", status, success + "/" + profile.consoleCommands().size() + " successful", summary);
    }

    public String economyState() {
        if (!economyConfigured()) return "DISABLED";
        hookEconomy();
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return "UNAVAILABLE / VAULT NOT INSTALLED";
        return economyProvider == null || economyDeposit == null ? "UNAVAILABLE / NO PROVIDER" : "READY";
    }

    public String keysState() {
        if (!keysConfigured()) return "DISABLED";
        hookKeys();
        if (!Bukkit.getPluginManager().isPluginEnabled("PlexonKeys")) return "NOT INSTALLED";
        return keysApi == null || keysGrant == null ? "INCOMPATIBLE" : "READY";
    }

    private boolean economyConfigured() {
        ChatEventConfig current = config;
        return current != null && current.rewards().values().stream().anyMatch(profile -> profile.economyEnabled() && profile.economyAmount() > 0);
    }
    private boolean keysConfigured() {
        ChatEventConfig current = config;
        return current != null && current.rewards().values().stream().anyMatch(profile -> profile.keysEnabled() && profile.keyAmount() > 0);
    }

    private static boolean transactionSucceeded(Object response) throws ReflectiveOperationException {
        if (response == null) return false;
        Method success = response.getClass().getMethod("transactionSuccess");
        return Boolean.TRUE.equals(success.invoke(response));
    }
    private static String transactionError(Object response) {
        if (response == null) return "provider returned no response";
        try {
            Field field = response.getClass().getField("errorMessage");
            Object value = field.get(response);
            return value == null || String.valueOf(value).isBlank() ? "provider rejected transaction" : String.valueOf(value);
        } catch (ReflectiveOperationException ignored) { return "provider rejected transaction"; }
    }
    private static String compact(double value) { return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value); }
    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof InvocationTargetException invocation && invocation.getCause() != null ? invocation.getCause() : throwable;
    }
    private static String describeFailure(RewardResult result) {
        return result.components().stream().filter(component -> component.status() == Status.FAILED || component.status() == Status.PARTIAL)
                .map(component -> component.component() + ": " + component.detail()).reduce((a, b) -> a + "; " + b).orElse("unknown reward failure");
    }
}
