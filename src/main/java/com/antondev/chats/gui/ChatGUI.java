package com.antondev.chats.gui;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.player.PlayerPreferences;
import com.antondev.chats.text.TextService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChatGUI {
    private final PlexonChats plugin;
    private final Map<ChatGUIHolder.Page, GuiLayout> layouts = new EnumMap<>(ChatGUIHolder.Page.class);
    public ChatGUI(PlexonChats plugin) { this.plugin = plugin; reload(); }
    public void reload() {
        layouts.put(ChatGUIHolder.Page.MAIN, GuiLayout.read(plugin.getConfigManager().section("gui"), "PlexonChats", plugin.getLogger()::warning));
        layouts.put(ChatGUIHolder.Page.ADMIN, GuiLayout.read(plugin.getConfigManager().section("gui.admin"), "Administration", plugin.getLogger()::warning));
        layouts.put(ChatGUIHolder.Page.EVENTS, GuiLayout.read(plugin.getConfigManager().section("gui.events"), "Chat Events", plugin.getLogger()::warning));
        layouts.put(ChatGUIHolder.Page.CREATOR, GuiLayout.read(plugin.getConfigManager().section("gui.creator"), "About PlexonChats", plugin.getLogger()::warning));
    }
    public void open(Player player) { openMain(player); }
    public void openMain(Player player) { openPage(player, ChatGUIHolder.Page.MAIN); }
    public void openCreator(Player player) { openPage(player, ChatGUIHolder.Page.CREATOR); }

    public void openPage(Player player, ChatGUIHolder.Page page) {
        var config = plugin.getConfigManager();
        if (!config.bool("gui.enabled", true)) { player.sendMessage(config.message("gui-disabled")); return; }
        boolean pageAllowed = switch (page) {
            case ADMIN -> player.hasPermission("plexonchats.manage");
            case EVENTS -> player.hasPermission("plexonchats.events.manage");
            default -> true;
        };
        if (!player.hasPermission("plexonchats.gui") || !pageAllowed) {
            player.sendMessage(config.getNoPermission()); return;
        }
        GuiLayout layout = layouts.get(page);
        if (layout == null) { player.sendMessage(config.message("gui-button-disabled")); return; }
        Map<Integer, GuiButton> visible = new LinkedHashMap<>();
        for (GuiButton button : layout.buttons().values()) {
            if (!button.hideWithoutPermission() || allowed(player, button)) visible.put(button.slot(), button);
        }
        ChatGUIHolder holder = new ChatGUIHolder(page, player.getUniqueId(), config.revision(), visible);
        Inventory inventory = Bukkit.createInventory(holder, layout.size(), plugin.getText().render(layout.title(), player));
        holder.setInventory(inventory);
        if (config.bool("gui.filler.enabled", true)) {
            Material material = GuiButton.material(config.string("gui.filler.material", "GRAY_STAINED_GLASS_PANE"));
            if (material == null) material = Material.GRAY_STAINED_GLASS_PANE;
            ItemStack filler = item(material, plugin.getText().render(config.string("gui.filler.name", " "), player), java.util.List.of(), false);
            boolean all = config.string("gui.filler.mode", "BORDER").equalsIgnoreCase("ALL");
            for (int slot = 0; slot < layout.size(); slot++) {
                if (all || slot < 9 || slot >= layout.size() - 9 || slot % 9 == 0 || slot % 9 == 8) inventory.setItem(slot, filler);
            }
        }
        for (GuiButton button : visible.values()) {
            boolean active = active(player, button.action());
            boolean available = available(button.action()) && allowed(player, button);
            String stateKey = !allowed(player, button) ? "locked" : !available ? "disabled"
                    : isToggle(button.action()) ? (active ? "on" : "off") : (active ? "active" : "available");
            Map<String, Component> context = new LinkedHashMap<>();
            context.put("state", config.formatMessage(config.string("gui.state." + stateKey, stateKey)));
            if (plugin.getChatEvents() != null) {
                context.put("events_status", Component.text(plugin.getChatEvents().status()));
                context.put("events_active", Component.text(plugin.getChatEvents().activeId()));
                context.put("events_count", Component.text(Integer.toString(plugin.getChatEvents().configuredCount())));
                context.put("events_scheduler", Component.text(plugin.getChatEvents().schedulerEnabled() ? (plugin.getChatEvents().paused() ? "PAUSED" : "RUNNING") : "DISABLED"));
            }
            String name = active && !button.activeName().isEmpty() ? button.activeName() : button.name();
            var lore = active && !button.activeLore().isEmpty() ? button.activeLore() : button.lore();
            inventory.setItem(button.slot(), item(!available ? button.disabledMaterial() : active ? button.activeMaterial() : button.material(),
                    plugin.getText().render(name, player, context),
                    lore.stream().map(line -> plugin.getText().render(line, player, context).decoration(TextDecoration.ITALIC, false)).toList(),
                    active && available && button.glow()));
        }
        player.openInventory(inventory);
    }

    private static ItemStack item(Material material, Component name, java.util.List<Component> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(glow);
        item.setItemMeta(meta);
        return item;
    }

    public boolean allowed(Player player, GuiButton button) {
        return (button.permission().isBlank() || player.hasPermission(button.permission()))
                && (button.action().requiredPermission().isBlank() || player.hasPermission(button.action().requiredPermission()));
    }
    private boolean available(GuiAction action) {
        return switch (action) {
            case LOCAL -> plugin.getConfigManager().isLocalEnabled();
            case GLOBAL -> plugin.getConfigManager().isGlobalEnabled();
            case TOGGLE_MENTIONS -> plugin.getConfigManager().isMentionsEnabled();
            case TOGGLE_PRIVATE_MESSAGES -> plugin.getConfigManager().bool("private-messages.enabled", true);
            case PAUSE_CHAT_EVENTS -> plugin.getChatEvents() != null && plugin.getChatEvents().enabled() && plugin.getChatEvents().schedulerEnabled() && !plugin.getChatEvents().paused();
            case RESUME_CHAT_EVENTS -> plugin.getChatEvents() != null && plugin.getChatEvents().enabled() && plugin.getChatEvents().schedulerEnabled() && plugin.getChatEvents().paused();
            case START_RANDOM_CHAT_EVENT -> plugin.getChatEvents() != null && plugin.getChatEvents().enabled() && !plugin.getChatEvents().hasActiveEvent();
            case STOP_CHAT_EVENT -> plugin.getChatEvents() != null && plugin.getChatEvents().hasActiveEvent();
            default -> true;
        };
    }
    private boolean active(Player player, GuiAction action) {
        PlayerPreferences pref = plugin.getPreferences().get(player.getUniqueId());
        return switch (action) {
            case LOCAL -> plugin.getChatManager().getPlayerChannel(player) == ChatChannel.LOCAL;
            case GLOBAL -> plugin.getChatManager().getPlayerChannel(player) == ChatChannel.GLOBAL;
            case TOGGLE_MENTIONS -> pref.mentions();
            case TOGGLE_TIPS -> pref.tips();
            case TOGGLE_PRIVATE_MESSAGES -> pref.privateMessages();
            case TOGGLE_AUTO_MESSAGES -> plugin.getAutoMessages().enabled();
            case TOGGLE_CHAT_EVENTS -> plugin.getChatEvents() != null && plugin.getChatEvents().enabled();
            default -> false;
        };
    }
    private static boolean isToggle(GuiAction action) { return action.name().startsWith("TOGGLE_"); }

    public void click(Player player, ChatGUIHolder holder, GuiButton button) {
        var config = plugin.getConfigManager();
        boolean pageAllowed = holder.getPage() != ChatGUIHolder.Page.ADMIN || player.hasPermission("plexonchats.manage");
        if (holder.getPage() == ChatGUIHolder.Page.EVENTS) pageAllowed = player.hasPermission("plexonchats.events.manage");
        if (!player.hasPermission("plexonchats.gui") || !allowed(player, button) || !pageAllowed) {
            player.sendMessage(config.getNoPermission()); return;
        }
        if (!available(button.action())) { player.sendMessage(config.message("gui-button-disabled")); return; }
        var sound = config.resolveSound(config.string("gui.click-sound", "UI_BUTTON_CLICK"));
        if (sound != null && button.action() != GuiAction.NONE) player.playSound(player.getLocation(), sound, .6f, 1f);
        PlayerPreferences pref = plugin.getPreferences().get(player.getUniqueId());
        switch (button.action()) {
            case LOCAL, GLOBAL -> {
                ChatChannel channel = button.action() == GuiAction.LOCAL ? ChatChannel.LOCAL : ChatChannel.GLOBAL;
                if (plugin.getChatManager().selectChannel(player, channel)) {
                    if (config.bool("gui.close-on-channel-select", false)) player.closeInventory();
                    else openPage(player, holder.getPage());
                }
            }
            case TOGGLE_MENTIONS, TOGGLE_TIPS, TOGGLE_PRIVATE_MESSAGES -> {
                PlayerPreferences updated = switch (button.action()) {
                    case TOGGLE_MENTIONS -> pref.toggleMentions();
                    case TOGGLE_TIPS -> pref.toggleTips();
                    default -> pref.togglePrivateMessages();
                };
                plugin.getPreferences().set(player.getUniqueId(), updated);
                openPage(player, holder.getPage());
            }
            case OPEN_ADMIN -> openPage(player, ChatGUIHolder.Page.ADMIN);
            case OPEN_CHAT_EVENTS -> openPage(player, ChatGUIHolder.Page.EVENTS);
            case OPEN_CREATOR -> openCreator(player);
            case OPEN_MAIN -> openMain(player);
            case CLOSE -> player.closeInventory();
            case RELOAD -> {
                boolean ok = plugin.reloadPlugin();
                player.sendMessage(ok ? config.getConfigReloaded() : config.message("config-failed"));
                if (ok) openPage(player, holder.getPage());
            }
            case TOGGLE_AUTO_MESSAGES -> {
                boolean ok = config.saveSetting("auto-messages.enabled", !plugin.getAutoMessages().enabled()) && plugin.reloadPlugin();
                if (!ok) player.sendMessage(config.message("config-failed"));
                openPage(player, holder.getPage());
            }
            case TOGGLE_CHAT_EVENTS -> {
                boolean ok = config.saveSetting("chat-events.enabled", !plugin.getChatEvents().enabled()) && plugin.reloadPlugin();
                if (!ok) player.sendMessage(config.message("config-failed"));
                openPage(player, ChatGUIHolder.Page.EVENTS);
            }
            case PAUSE_CHAT_EVENTS -> { plugin.getChatEvents().pause(); openPage(player, ChatGUIHolder.Page.EVENTS); }
            case RESUME_CHAT_EVENTS -> { plugin.getChatEvents().resume(); openPage(player, ChatGUIHolder.Page.EVENTS); }
            case START_RANDOM_CHAT_EVENT -> {
                var result = plugin.getChatEvents().startRandom(false);
                player.sendMessage(Component.text("Chat Event start: " + result));
                openPage(player, ChatGUIHolder.Page.EVENTS);
            }
            case STOP_CHAT_EVENT -> {
                player.sendMessage(Component.text(plugin.getChatEvents().stop() ? "Chat Event cancelled." : "No active Chat Event."));
                openPage(player, ChatGUIHolder.Page.EVENTS);
            }
            case PREVIEW_FORMAT -> previewFormats(player);
            case PLAYER_COMMAND -> {
                String command = plugin.getText().plain(button.value(), player).strip();
                if (!command.isBlank() && !command.contains("\n") && !command.contains("\r")) {
                    player.closeInventory();
                    player.performCommand(command.startsWith("/") ? command.substring(1) : command);
                }
            }
            case MESSAGE -> player.sendMessage(plugin.getText().render(button.value(), player));
            case LINK -> {
                String url = plugin.getText().plain(button.value(), player);
                if (TextService.isWebUrl(url)) player.sendMessage(config.message("link",
                        Map.of("label", plugin.getText().plain(button.name(), player))).clickEvent(ClickEvent.openUrl(url)));
            }
            case NONE -> { }
        }
    }

    public void previewFormats(Player player) {
        for (ChatChannel channel : ChatChannel.values()) player.sendMessage(plugin.getChatComponentFactory()
                .buildPublicMessage(player, channel, plugin.getConfigManager().formatMessage("<gray>This preview is visible only to you.")));
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof ChatGUIHolder) player.closeInventory();
        }
    }
}
