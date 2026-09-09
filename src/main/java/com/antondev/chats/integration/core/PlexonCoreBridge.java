package com.antondev.chats.integration.core;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.integration.IntegrationRegistry.IntegrationState;
import com.zpkdxgames.plexoncore.module.ModuleRegistry;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleDescriptor;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleState;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleVersionRange;
import java.time.Instant;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Core-backed bridge, loaded only after CoreBridgeFactory confirms PlexonCore is enabled. */
public final class PlexonCoreBridge implements CoreBridge {
    private static final String INTEGRATION_ID = "PLEXON_CHATS";
    private static final Set<String> CAPABILITIES = Set.of(
            "chat-engine", "local-chat", "global-chat", "private-messages", "chat-api",
            "plexon-chat-event", "player-preferences", "scheduled-messages", "discordsrv-bridge",
            "item-showcase", "minimessage", "placeholderapi", "vault-player-info");
    private static final Set<String> INTEGRATION_CAPABILITIES = Set.of(
            "chat-api", "plexon-chat-event", "global-chat", "local-chat", "private-messages",
            "scheduled-messages", "discordsrv-bridge", "item-previews", "player-preferences");

    private final Plugin plugin;
    private final PlexonCoreAPI core;
    private final CoreVersion version;
    private final boolean compatible;
    private boolean ownsRegistration;
    private String registrationState = "NOT_REGISTERED";
    private String detail = "PlexonCore API resolved";

    public PlexonCoreBridge(JavaPlugin plugin) { this(plugin, resolveApi()); }

    PlexonCoreBridge(Plugin plugin, PlexonCoreAPI core) {
        this.plugin = plugin;
        this.core = core;
        this.version = core.version();
        this.compatible = ModuleVersionRange.parse(SUPPORTED_API_RANGE).contains(version);
        if (!compatible) detail = "Core API " + version.apiVersion() + " is outside supported range " + SUPPORTED_API_RANGE;
    }

    private static PlexonCoreAPI resolveApi() {
        RegisteredServiceProvider<PlexonCoreAPI> registration = Bukkit.getServicesManager().getRegistration(PlexonCoreAPI.class);
        if (registration == null) throw new IllegalStateException("PlexonCore API service is not registered");
        return registration.getProvider();
    }

    @Override public boolean installed() { return true; }
    @Override public boolean available() { return compatible; }
    @Override public boolean compatible() { return compatible; }
    @Override public String pluginVersion() { return version.pluginVersion(); }
    @Override public String apiVersion() { return version.apiVersion(); }
    @Override public String mode() { return compatible && ownsRegistration ? "CORE" : "STANDALONE"; }

    @Override public String registrationState() {
        if (ownsRegistration) return core.modules().find(MODULE_ID).map(descriptor -> descriptor.state().name()).orElse("NOT_REGISTERED");
        return registrationState;
    }

    @Override public String detail() {
        if (ownsRegistration) return core.modules().find(MODULE_ID).map(ModuleDescriptor::detail).orElse(detail);
        return detail;
    }

    @Override public void registerStarting() {
        ModuleDescriptor descriptor = new ModuleDescriptor(
                MODULE_ID, "PlexonChats", plugin.getName(), plugin.getPluginMeta().getVersion(), plugin,
                ModuleVersionRange.parse(SUPPORTED_API_RANGE), CAPABILITIES, ModuleState.STARTING,
                "Initializing PlexonChats", Instant.now());
        ModuleRegistry.RegistrationResult result = core.modules().register(descriptor);
        ModuleDescriptor registered = result.descriptor();
        ownsRegistration = registered != null && registered.plugin() == plugin;
        registrationState = registered == null ? "NOT_REGISTERED" : registered.state().name();
        detail = result.message();
        if (!result.success() && !ownsRegistration) {
            plugin.getLogger().warning("PlexonCore module registration rejected: " + result.message());
        } else if (!compatible) {
            plugin.getLogger().warning("PlexonCore API " + version.apiVersion() + " is incompatible with supported range "
                    + SUPPORTED_API_RANGE + "; chat will continue in standalone compatibility mode.");
        }
    }

    @Override public void markReady(String detail) { update(ModuleState.READY, IntegrationState.READY, detail); }
    @Override public void markDegraded(String detail) { update(ModuleState.DEGRADED, IntegrationState.DEGRADED, detail); }
    @Override public void markFailed(String detail) { update(ModuleState.FAILED, IntegrationState.FAILED, detail); }

    private void update(ModuleState moduleState, IntegrationState integrationState, String newDetail) {
        if (!compatible || !ownsRegistration) return;
        if (version.apiMajor() >= 2) {
            if (!core.modules().updateState(MODULE_ID, plugin, moduleState, newDetail)) {
                ownsRegistration = false;
                registrationState = "NOT_REGISTERED";
                detail = "Core module ownership changed before state update";
                return;
            }
        } else {
            core.modules().updateState(MODULE_ID, moduleState, newDetail);
        }
        core.integrations().publish(INTEGRATION_ID, plugin.getName(), plugin.getPluginMeta().getVersion(),
                integrationState, INTEGRATION_CAPABILITIES, newDetail);
        registrationState = moduleState.name();
        detail = newDetail == null ? "" : newDetail;
    }

    @Override public void unregister() {
        if (!ownsRegistration) return;
        if (version.apiMajor() >= 2) {
            core.modules().unregisterOwnedBy(plugin);
        } else {
            core.modules().find(MODULE_ID).filter(descriptor -> descriptor.plugin() == plugin)
                    .ifPresent(descriptor -> core.modules().unregister(MODULE_ID));
        }
        if (compatible) {
            core.integrations().publish(INTEGRATION_ID, plugin.getName(), plugin.getPluginMeta().getVersion(),
                    IntegrationState.DEGRADED, INTEGRATION_CAPABILITIES, "PlexonChats is disabled");
        }
        ownsRegistration = false;
        registrationState = "UNREGISTERED";
    }

    @Override public ProviderHint providerHint(String integrationId) {
        if (!compatible) return ProviderHint.UNKNOWN;
        return core.integrations().get(integrationId).map(view -> switch (view.state()) {
            case READY -> ProviderHint.PRESENT;
            case MISSING -> ProviderHint.MISSING;
            case DEGRADED -> ProviderHint.DISABLED;
            case INCOMPATIBLE, FAILED -> ProviderHint.UNKNOWN;
        }).orElse(ProviderHint.UNKNOWN);
    }
}
