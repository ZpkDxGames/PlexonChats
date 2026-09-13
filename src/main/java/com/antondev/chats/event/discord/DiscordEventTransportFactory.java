package com.antondev.chats.event.discord;

import com.antondev.chats.PlexonChats;

import java.util.function.Supplier;

/** AUTO transport selector. DiscordSRV is preferred; webhook is the safe fallback. */
public final class DiscordEventTransportFactory {
    private DiscordEventTransportFactory() { }

    public static DiscordEventTransport create(PlexonChats plugin, DiscordEventSettings settings) {
        if (!settings.enabled()) return DiscordEventTransport.unavailable("DISABLED");
        return switch (settings.transport()) {
            case DISCORDSRV -> discordSrv(plugin, settings, false);
            case WEBHOOK -> webhook(settings);
            case AUTO -> auto(plugin, settings);
        };
    }

    private static DiscordEventTransport auto(PlexonChats plugin, DiscordEventSettings settings) {
        DiscordEventTransport discord = discordSrv(plugin, settings, true);
        boolean webhookConfigured = settings.webhook().enabled() && !settings.webhook().url().isBlank();
        return selectAuto(discord, webhookConfigured, () -> webhook(settings));
    }

    /** Package-visible deterministic selection boundary used by transport fallback tests. */
    static DiscordEventTransport selectAuto(DiscordEventTransport discord, boolean webhookConfigured,
                                            Supplier<DiscordEventTransport> webhookSupplier) {
        String status = discord.status();
        if (status.equals("READY") || status.equals("WAITING_FOR_DISCORD")) return discord;
        discord.close();
        if (webhookConfigured) {
            try { return webhookSupplier.get(); }
            catch (RuntimeException failure) {
                return DiscordEventTransport.unavailable("Discord webhook configuration is invalid");
            }
        }
        return DiscordEventTransport.unavailable("DiscordSRV unavailable and webhook is not configured");
    }

    private static DiscordEventTransport discordSrv(PlexonChats plugin, DiscordEventSettings settings, boolean auto) {
        var discordPlugin = plugin.getServer().getPluginManager().getPlugin("DiscordSRV");
        if (discordPlugin == null || !discordPlugin.isEnabled()) {
            return DiscordEventTransport.unavailable(auto ? "NOT_INSTALLED" : "DiscordSRV is not installed");
        }
        try { return new DiscordSrvEventTransport(plugin, settings); }
        catch (RuntimeException | LinkageError failure) {
            return DiscordEventTransport.unavailable("DiscordSRV integration is incompatible: " + failure.getClass().getSimpleName());
        }
    }

    private static DiscordEventTransport webhook(DiscordEventSettings settings) {
        if (!settings.webhook().enabled() || settings.webhook().url().isBlank()) {
            return DiscordEventTransport.unavailable("Discord webhook is not configured");
        }
        try { return new WebhookEventTransport(settings); }
        catch (RuntimeException failure) { return DiscordEventTransport.unavailable("Discord webhook configuration is invalid"); }
    }
}
