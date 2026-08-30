package com.antondev.chats;

import com.antondev.chats.automessage.AutoMessageManager;
import com.antondev.chats.chat.ChatComponentFactory;
import com.antondev.chats.chat.ChatListener;
import com.antondev.chats.chat.ChatManager;
import com.antondev.chats.chat.ConnectionMessageListener;
import com.antondev.chats.command.*;
import com.antondev.chats.config.ConfigManager;
import com.antondev.chats.gui.ChatGUI;
import com.antondev.chats.gui.GUIListener;
import com.antondev.chats.integration.DiscordBridge;
import com.antondev.chats.item.ItemPreviewManager;
import com.antondev.chats.message.PrivateMessageManager;
import com.antondev.chats.placeholder.PlaceholderApiService;
import com.antondev.chats.placeholder.PlaceholderHandler;
import com.antondev.chats.player.PlayerInfoService;
import com.antondev.chats.player.PreferenceStore;
import com.antondev.chats.text.TextService;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.Objects;

public class PlexonChats extends JavaPlugin implements Listener {
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
    private BukkitTask cleanupTask;

    @Override public void onEnable() {
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
        MessageCommand messages = new MessageCommand(this);
        register("msg", messages);
        register("reply", new ReplyCommand(this, messages));
        register("chatitem", new ChatItemPreviewCommand(this));
        cleanupTask = getServer().getScheduler().runTaskTimer(this, itemPreviewManager::cleanupExpired, 1200, 1200);
        getLogger().info("PlexonChats " + getPluginMeta().getVersion() + " enabled. DiscordSRV: " + discordBridge.status());
    }

    private void register(String name, CommandExecutor executor) {
        var command = Objects.requireNonNull(getCommand(name));
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) command.setTabCompleter(completer);
    }

    /** Apply only a validated configuration. Recreate each timer/hook exactly once. Main thread only. */
    public boolean reloadPlugin() {
        if (!configManager.loadConfig()) return false;
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
        return true;
    }

    @EventHandler public void onOptionalPluginEnable(PluginEnableEvent event) {
        String name = event.getPlugin().getName();
        if (name.equals("DiscordSRV")) {
            discordBridge.close();
            discordBridge = DiscordBridge.create(this);
        } else if (name.equals("PlaceholderAPI")) placeholderApiService.refreshHooks();
        else if (name.equals("Vault")) playerInfoService.refreshHooks();
    }

    @EventHandler public void onOptionalPluginDisable(PluginDisableEvent event) {
        String name = event.getPlugin().getName();
        if (name.equals("DiscordSRV")) {
            discordBridge.close();
            discordBridge = DiscordBridge.inactive("NOT_INSTALLED");
        } else if (name.equals("PlaceholderAPI")) placeholderApiService.refreshHooks();
        else if (name.equals("Vault")) playerInfoService.refreshHooks();
    }

    @Override public void onDisable() {
        if (cleanupTask != null) cleanupTask.cancel();
        if (autoMessages != null) autoMessages.close();
        discordBridge.close();
        if (chatGUI != null) chatGUI.closeAll();
        if (preferences != null) preferences.close();
        if (chatManager != null) chatManager.clearAll();
        if (privateMessageManager != null) privateMessageManager.clear();
        if (itemPreviewManager != null) itemPreviewManager.clear();
        getLogger().info("PlexonChats disabled.");
    }

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
}
