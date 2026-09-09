package com.antondev.chats.integration.core;

/**
 * Optional PlexonCore lifecycle bridge. Implementations must remain safe when PlexonCore is absent.
 */
public interface CoreBridge {
    String SUPPORTED_API_RANGE = ">=1.0 <3.0";
    String MODULE_ID = "chats";

    boolean installed();
    boolean available();
    boolean compatible();
    String pluginVersion();
    String apiVersion();
    String mode();
    String registrationState();
    String detail();
    void registerStarting();
    void markReady(String detail);
    void markDegraded(String detail);
    void markFailed(String detail);
    void unregister();
    ProviderHint providerHint(String integrationId);

    enum ProviderHint { PRESENT, MISSING, DISABLED, UNKNOWN }
}
