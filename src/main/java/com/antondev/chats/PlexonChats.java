package com.antondev.chats;

import com.antondev.chats.api.PlexonChatsAPI;
import com.antondev.chats.api.PlexonChatsApiImpl;
import com.antondev.chats.automessage.AutoMessageManager;
import com.antondev.chats.chat.ChatComponentFactory;
import com.antondev.chats.chat.ChatListener;
import com.antondev.chats.chat.ChatManager;
import com.antondev.chats.chat.ConnectionMessageListener;
import com.antondev.chats.command.*;
import com.antondev.chats.config.ConfigManager;
import com.antondev.chats.diagnostics.ChatDiagnostics;
import com.antondev.chats.gui.ChatGUI;
import com.antondev.chats.gui.GUIListener;
import com.antondev.chats.integration.DiscordBridge;
import com.antondev.chats.integration.core.CoreBridge;
import com.antondev.chats.integration.core.CoreBridgeFactory;
import com.antondev.chats.item.ItemPreviewManager;
import com.antondev.chats.message.PrivateMessageManager;
import com.antondev.chats.placeholder.PlaceholderApiService;
import com.antondev.chats.placeholder.PlaceholderHandler;
import com.antondev.chats.player.PlayerInfoService;
import com.antondev.chats.player.PreferenceStore;
import com.antondev.chats.text.TextService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class PlexonChats extends JavaPlugin implements Listener {
    private final ChatDiagnostics diagnostics = new ChatDiagnostics();
    private ConfigManager configManager;
    private PreferenceStore preferences;
    private ChatManager chatManager;
    private PlaceholderHandler placeholderHandler;
    private PlaceholderApiService placeholderApiService;
    private PlayerInfoService playerInfoService;
    private PrivateMessageManager privateMessageManager;
    private ItemPreviewManager itemPreviewManager;
    private ChatComponentFactory chatComponentFactory;
    private TextService text;
    private ChatGUI chatGUI;
    private AutoMessageManager autoMessages;
    private DiscordBridge discordBridge = DiscordBridge.inactive("DISABLED");
    private MessageCommand messageCommand;
    private PlexonChatsAPI api;
    private CoreBridge coreBridge;
    private BukkitTask cleanupTask;

    @Override
    public void onEnable() {
        try {
            coreBridge = CoreBridgeFactory.resolve(this);
            coreBridge.registerStarting();
            configManager = new ConfigManager(this);
            preferences = new PreferenceStore(this);
            placeholderApiService = new PlaceholderApiService(this);
            playerInfoService = new PlayerInfoService(this);
            text = new TextService(this);
            chatManager = new ChatManager(this);
            privateMessageManager = new PrivateMessageManager();
            itemPreviewManager = new ItemPreviewManager(this);
            placeholderHandler = new PlaceholderHandler(this);
            chatComponentFactory = new ChatComponentFactory(this);
            autoMessages = new AutoMessageManager(this);
            chatGUI = new ChatGUI(this);
            discordBridge = DiscordBridge.create(this);
            autoMessages.reload();

            getServer().getPluginManager().registerEvents(new ChatListener(this), this);
            getServer().getPluginManager().registerEvents(new ConnectionMessageListener(this), this);
            getServer().getPluginManager().registerEvents(new GUIListener(this), this);
            getServer().getPluginManager().registerEvents(this, this);
            register("chat", new ChatCommand(this));
            register("g", new GlobalChatCommand(this));
            register("l", new LocalChatCommand(this));
            register("announce", new AnnouncementCommand(this));
            messageCommand = new MessageCommand(this);
            register("msg", messageCommand);
            register("reply", new ReplyCommand(this, messageCommand));
            register("chatitem", new ChatItemPreviewCommand(this));

            cleanupTask = getServer().getScheduler().runTaskTimer(this, itemPreviewManager::cleanupExpired, 1200, 1200);
            api = new PlexonChatsApiImpl(this);
            getServer().getServicesManager().register(PlexonChatsAPI.class, api, this, ServicePriority.Normal);
            diagnostics.recordReload(true, "startup revision " + configManager.revision());
            publishCoreHealth();
            getLogger().info("PlexonChats " + getPluginMeta().getVersion()
                    + " enabled. Mode: " + coreBridge.mode() + ", DiscordSRV: " + discordBridge.status());
        } catch (Exception | LinkageError exception) {
            diagnostics.recordReload(false, "startup " + exception.getClass().getSimpleName());
            if (coreBridge != null) coreBridge.markFailed("Chat startup failed: " + exception.getClass().getSimpleName());
            getLogger().log(Level.SEVERE, "PlexonChats could not start safely; disabling without partial operation", exception);
            shutdown();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void register(String name, CommandExecutor executor) {
        var command = Objects.requireNonNull(getCommand(name), "Command missing from plugin.yml: " + name);
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) command.setTabCompleter(completer);
    }

    /** Apply a validated configuration as one runtime generation; rollback to the previous snapshot on refresh failure. */
    public boolean reloadPlugin() {
        ConfigManager.Snapshot previous = configManager.snapshot();
        if (!configManager.loadConfig()) {
            diagnostics.recordReload(false, "configuration rejected; revision " + previous.revision());
            return false;
        }
        try {
            refreshRuntimeAfterConfig();
            diagnostics.recordReload(true, "revision " + configManager.revision());
            return true;
        } catch (Exception | LinkageError failure) {
            diagnostics.recordReload(false, failure.getClass().getSimpleName() + "; restored revision " + previous.revision());
            getLogger().log(Level.SEVERE, "Reload failed after validation; restoring the previous runtime generation", failure);
            configManager.restore(previous);
            try {
                refreshRuntimeAfterConfig();
            } catch (Exception | LinkageError rollbackFailure) {
                diagnostics.recordIntegrationFailure("reload-rollback", rollbackFailure);
                if (coreBridge != null) coreBridge.markFailed("Reload rollback failed: " + rollbackFailure.getClass().getSimpleName());
                getLogger().log(Level.SEVERE, "Known-good runtime could not be restored; disabling PlexonChats safely", rollbackFailure);
                Bukkit.getPluginManager().disablePlugin(this);
            }
            return false;
        }
    }

    private void refreshRuntimeAfterConfig() {
        chatGUI.closeAll();
        placeholderApiService.refreshHooks();
        playerInfoService.refreshHooks();
        placeholderHandler.reload();
        text.resetWarnings();
        chatGUI.reload();
        preferences.reloadTimer();
        autoMessages.reload();
        discordBridge.close();
        discordBridge = DiscordBridge.create(this);
        itemPreviewManager.cleanupExpired();
        publishCoreHealth();
    }

    @EventHandler
    public void onOptionalPluginEnable(PluginEnableEvent event) {
        String name = event.getPlugin().getName();
        if (name.equals("DiscordSRV")) {
            discordBridge.close();
            discordBridge = DiscordBridge.create(this);
        } else if (name.equals("PlaceholderAPI")) {
            placeholderApiService.refreshHooks();
        } else if (name.equals("Vault") || name.equals("LuckPerms") || name.equals("PlexonRanks")) {
            playerInfoService.refreshHooks();
        } else return;
        publishCoreHealth();
    }

    @EventHandler
    public void onOptionalPluginDisable(PluginDisableEvent event) {
        String name = event.getPlugin().getName();
        if (name.equals("DiscordSRV")) {
            discordBridge.close();
            discordBridge = DiscordBridge.inactive("NOT_INSTALLED");
        } else if (name.equals("PlaceholderAPI")) {
            placeholderApiService.refreshHooks();
        } else if (name.equals("Vault") || name.equals("LuckPerms") || name.equals("PlexonRanks")) {
            playerInfoService.refreshHooks();
        } else return;
        publishCoreHealth();
    }

    public void publishCoreHealth() {
        if (coreBridge == null || configManager == null || api == null) return;
        List<String> degraded = new ArrayList<>();
        if (configManager.bool("integrations.discordsrv.enabled", false)) {
            String discord = discordBridge.status();
            if (!discord.equals("ACTIVE")) degraded.add("DiscordSRV " + discord);
        }
        String readyDetail = "Chat routing, preferences, scheduler, GUI, API and optional bridge operational";
        if (degraded.isEmpty()) coreBridge.markReady(readyDetail);
        else coreBridge.markDegraded(readyDetail + "; " + String.join(", ", degraded));
    }

    @Override public void onDisable() {
        shutdown();
        getLogger().info("PlexonChats disabled.");
    }

    private void shutdown() {
        if (cleanupTask != null) { cleanupTask.cancel(); cleanupTask = null; }
        if (autoMessages != null) { autoMessages.close(); autoMessages = null; }
        if (discordBridge != null) { discordBridge.close(); discordBridge = DiscordBridge.inactive("DISABLED"); }
        if (chatGUI != null) chatGUI.closeAll();
        if (preferences != null) { preferences.close(); preferences = null; }
        if (chatManager != null) chatManager.clearAll();
        if (privateMessageManager != null) privateMessageManager.clear();
        if (itemPreviewManager != null) itemPreviewManager.clear();
        getServer().getServicesManager().unregisterAll(this);
        api = null;
        if (coreBridge != null) { coreBridge.unregister(); coreBridge = null; }
    }

    public boolean cleanupTaskActive() { return cleanupTask != null && !cleanupTask.isCancelled(); }
    public ChatDiagnostics getDiagnostics() { return diagnostics; }
    public ConfigManager getConfigManager() { return configManager; }
    public PreferenceStore getPreferences() { return preferences; }
    public ChatManager getChatManager() { return chatManager; }
    public PlaceholderHandler getPlaceholderHandler() { return placeholderHandler; }
    public PlayerInfoService getPlayerInfoService() { return playerInfoService; }
    public PlaceholderApiService getPlaceholderApiService() { return placeholderApiService; }
    public PrivateMessageManager getPrivateMessageManager() { return privateMessageManager; }
    public ItemPreviewManager getItemPreviewManager() { return itemPreviewManager; }
    public ChatComponentFactory getChatComponentFactory() { return chatComponentFactory; }
    public TextService getText() { return text; }
    public ChatGUI getChatGUI() { return chatGUI; }
    public AutoMessageManager getAutoMessages() { return autoMessages; }
    public DiscordBridge getDiscordBridge() { return discordBridge; }
    public MessageCommand getMessageCommand() { return messageCommand; }
    public PlexonChatsAPI getApi() { return api; }
    public CoreBridge getCoreBridge() { return coreBridge; }
}
