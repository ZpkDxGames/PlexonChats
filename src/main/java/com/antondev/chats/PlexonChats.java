package com.antondev.chats;

import com.antondev.chats.chat.ChatListener;
import com.antondev.chats.chat.ChatManager;
import com.antondev.chats.chat.ChatComponentFactory;
import com.antondev.chats.command.AnnouncementCommand;
import com.antondev.chats.command.ChatItemPreviewCommand;
import com.antondev.chats.command.ChatCommand;
import com.antondev.chats.command.GlobalChatCommand;
import com.antondev.chats.command.LocalChatCommand;
import com.antondev.chats.command.MessageCommand;
import com.antondev.chats.command.ReplyCommand;
import com.antondev.chats.config.ConfigManager;
import com.antondev.chats.gui.GUIListener;
import com.antondev.chats.item.ItemPreviewManager;
import com.antondev.chats.message.PrivateMessageManager;
import com.antondev.chats.placeholder.PlaceholderApiService;
import com.antondev.chats.placeholder.PlaceholderHandler;
import com.antondev.chats.player.PlayerInfoService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class PlexonChats extends JavaPlugin {

    private ConfigManager configManager;
    private ChatManager chatManager;
    private PlaceholderHandler placeholderHandler;
    private PlaceholderApiService placeholderApiService;
    private PlayerInfoService playerInfoService;
    private PrivateMessageManager privateMessageManager;
    private ItemPreviewManager itemPreviewManager;
    private ChatComponentFactory chatComponentFactory;

    @Override
    public void onEnable() {
        // Initialize managers
        configManager = new ConfigManager(this);
        chatManager = new ChatManager(this);
        placeholderHandler = new PlaceholderHandler(this);
        placeholderApiService = new PlaceholderApiService(this);
        playerInfoService = new PlayerInfoService(this);
        privateMessageManager = new PrivateMessageManager();
        itemPreviewManager = new ItemPreviewManager();
        chatComponentFactory = new ChatComponentFactory(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        // Register commands
        ChatCommand chatCommand = new ChatCommand(this);
        Objects.requireNonNull(getCommand("chat")).setExecutor(chatCommand);
        Objects.requireNonNull(getCommand("chat")).setTabCompleter(chatCommand);

        GlobalChatCommand globalCommand = new GlobalChatCommand(this);
        Objects.requireNonNull(getCommand("g")).setExecutor(globalCommand);
        Objects.requireNonNull(getCommand("g")).setTabCompleter(globalCommand);

        LocalChatCommand localCommand = new LocalChatCommand(this);
        Objects.requireNonNull(getCommand("l")).setExecutor(localCommand);
        Objects.requireNonNull(getCommand("l")).setTabCompleter(localCommand);

        AnnouncementCommand announcementCommand = new AnnouncementCommand(this);
        Objects.requireNonNull(getCommand("announce")).setExecutor(announcementCommand);
        Objects.requireNonNull(getCommand("announce")).setTabCompleter(announcementCommand);

        MessageCommand messageCommand = new MessageCommand(this);
        Objects.requireNonNull(getCommand("msg")).setExecutor(messageCommand);
        Objects.requireNonNull(getCommand("msg")).setTabCompleter(messageCommand);

        ReplyCommand replyCommand = new ReplyCommand(this, messageCommand);
        Objects.requireNonNull(getCommand("reply")).setExecutor(replyCommand);
        Objects.requireNonNull(getCommand("reply")).setTabCompleter(replyCommand);

        ChatItemPreviewCommand itemPreviewCommand = new ChatItemPreviewCommand(this);
        Objects.requireNonNull(getCommand("chatitem")).setExecutor(itemPreviewCommand);
        Objects.requireNonNull(getCommand("chatitem")).setTabCompleter(itemPreviewCommand);

        getLogger().info("PlexonChats v" + getPluginMeta().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (chatManager != null) {
            chatManager.clearAll();
        }
        if (privateMessageManager != null) {
            privateMessageManager.clear();
        }
        if (itemPreviewManager != null) {
            itemPreviewManager.clear();
        }
        getLogger().info("PlexonChats disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ChatManager getChatManager() {
        return chatManager;
    }

    public PlaceholderHandler getPlaceholderHandler() {
        return placeholderHandler;
    }

    public PlayerInfoService getPlayerInfoService() {
        return playerInfoService;
    }

    public PlaceholderApiService getPlaceholderApiService() {
        return placeholderApiService;
    }

    public PrivateMessageManager getPrivateMessageManager() {
        return privateMessageManager;
    }

    public ItemPreviewManager getItemPreviewManager() {
        return itemPreviewManager;
    }

    public ChatComponentFactory getChatComponentFactory() {
        return chatComponentFactory;
    }
}
