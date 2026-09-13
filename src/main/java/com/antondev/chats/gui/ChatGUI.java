package com.antondev.chats.gui;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.event.ChatEventConfig;
import com.antondev.chats.event.ChatEventEngine;
import com.antondev.chats.event.ChatEventStatisticsService;
import com.antondev.chats.player.PlayerPreferences;
import com.antondev.chats.text.TextService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Holder-routed GUI with static player/admin pages and dedicated Chat Events administration pages. */
public final class ChatGUI {
    private static final List<Integer> EVENT_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
    private final PlexonChats plugin;
    private final Map<ChatGUIHolder.Page, GuiLayout> layouts = new EnumMap<>(ChatGUIHolder.Page.class);

    public ChatGUI(PlexonChats plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        layouts.put(ChatGUIHolder.Page.MAIN, GuiLayout.read(plugin.getConfigManager().section("gui"), "PlexonChats", plugin.getLogger()::warning));
        layouts.put(ChatGUIHolder.Page.ADMIN, GuiLayout.read(plugin.getConfigManager().section("gui.admin"), "Administration", plugin.getLogger()::warning));
        layouts.put(ChatGUIHolder.Page.CREATOR, GuiLayout.read(plugin.getConfigManager().section("gui.creator"), "About PlexonChats", plugin.getLogger()::warning));
    }

    public void open(Player player) { openMain(player); }
    public void openMain(Player player) { openPage(player, ChatGUIHolder.Page.MAIN); }
    public void openCreator(Player player) { openPage(player, ChatGUIHolder.Page.CREATOR); }

    public void openPage(Player player, ChatGUIHolder.Page page) {
        var config = plugin.getConfigManager();
        if (!config.bool("gui.enabled", true)) { player.sendMessage(config.message("gui-disabled")); return; }
        if (!player.hasPermission("plexonchats.gui")) { player.sendMessage(config.getNoPermission()); return; }

        switch (page) {
            case EVENTS -> { openEventsDashboard(player); return; }
            case EVENT_LIST -> { openEventList(player, 0); return; }
            case EVENT_REWARDS -> { openEventRewards(player); return; }
            case EVENT_BINGO -> { openEventBingo(player); return; }
            case EVENT_STATS -> { openEventStats(player); return; }
            case EVENT_DETAILS -> { player.sendMessage(config.message("gui-button-disabled")); return; }
            default -> { }
        }

        boolean pageAllowed = page != ChatGUIHolder.Page.ADMIN || player.hasPermission("plexonchats.manage");
        if (!pageAllowed) { player.sendMessage(config.getNoPermission()); return; }
        GuiLayout layout = layouts.get(page);
        if (layout == null) { player.sendMessage(config.message("gui-button-disabled")); return; }
        openConfigured(player, page, layout);
    }

    private void openConfigured(Player player, ChatGUIHolder.Page page, GuiLayout layout) {
        var config = plugin.getConfigManager();
        Map<Integer, GuiButton> visible = new LinkedHashMap<>();
        for (GuiButton button : layout.buttons().values()) {
            if (!button.hideWithoutPermission() || allowed(player, button)) visible.put(button.slot(), button);
        }
        ChatGUIHolder holder = new ChatGUIHolder(page, player.getUniqueId(), config.revision(), visible);
        Inventory inventory = Bukkit.createInventory(holder, layout.size(), plugin.getText().render(layout.title(), player));
        holder.setInventory(inventory);
        fillConfigured(inventory, layout.size());
        for (GuiButton button : visible.values()) renderConfiguredButton(player, inventory, button);
        player.openInventory(inventory);
    }

    private void fillConfigured(Inventory inventory, int size) {
        var config = plugin.getConfigManager();
        if (!config.bool("gui.filler.enabled", true)) return;
        Material material = GuiButton.material(config.string("gui.filler.material", "GRAY_STAINED_GLASS_PANE"));
        if (material == null) material = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack filler = item(material, plugin.getText().render(config.string("gui.filler.name", " "), null), List.of(), false);
        boolean all = config.string("gui.filler.mode", "BORDER").equalsIgnoreCase("ALL");
        for (int slot = 0; slot < size; slot++) {
            if (all || slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8) inventory.setItem(slot, filler);
        }
    }

    private void renderConfiguredButton(Player player, Inventory inventory, GuiButton button) {
        var config = plugin.getConfigManager();
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

    public void openEventsDashboard(Player player) {
        if (!eventAccess(player)) return;
        var events = plugin.getChatEvents();
        Map<Integer, GuiButton> buttons = new LinkedHashMap<>();
        buttons.put(10, dynamic("master", 10, GuiAction.TOGGLE_CHAT_EVENTS, Material.LEVER, "Master toggle", ""));
        buttons.put(12, dynamic("scheduler", 12, events.paused() ? GuiAction.RESUME_CHAT_EVENTS : GuiAction.PAUSE_CHAT_EVENTS,
                Material.CLOCK, events.paused() ? "Resume scheduler" : "Pause scheduler", ""));
        buttons.put(14, dynamic("start", 14, GuiAction.START_RANDOM_CHAT_EVENT, Material.FIREWORK_ROCKET, "Start random event", ""));
        buttons.put(16, dynamic("stop", 16, GuiAction.STOP_CHAT_EVENT, Material.BARRIER, "Stop active event", ""));
        buttons.put(20, dynamic("definitions", 20, GuiAction.OPEN_EVENT_LIST, Material.BOOKSHELF, "Event browser", ""));
        buttons.put(22, dynamic("bingo", 22, GuiAction.OPEN_EVENT_BINGO, Material.MAP, "Bingo", ""));
        buttons.put(24, dynamic("rewards", 24, GuiAction.OPEN_EVENT_REWARDS, Material.GOLD_INGOT, "Rewards", ""));
        buttons.put(30, dynamic("stats", 30, GuiAction.OPEN_EVENT_STATS, Material.PLAYER_HEAD, "Statistics", ""));
        buttons.put(32, dynamic("reload", 32, GuiAction.RELOAD, Material.REPEATER, "Reload", ""));
        buttons.put(38, dynamic("back", 38, player.hasPermission("plexonchats.manage") ? GuiAction.OPEN_ADMIN : GuiAction.OPEN_MAIN, Material.ARROW, "Back", ""));
        buttons.put(42, dynamic("close", 42, GuiAction.CLOSE, Material.BARRIER, "Close", ""));

        ChatGUIHolder holder = dynamicHolder(ChatGUIHolder.Page.EVENTS, player, buttons, "", 0);
        Inventory inventory = Bukkit.createInventory(holder, 45, plugin.getText().render("<gradient:#ffd66b:#ff9f43><bold>Chat Events Dashboard</bold></gradient>", player));
        holder.setInventory(inventory);
        fillBorder(inventory);

        ChatEventStatisticsService stats = events.statistics();
        List<Component> statusLore = List.of(
                text("Master", events.enabled() ? "ENABLED" : "DISABLED"),
                text("Scheduler", events.schedulerEnabled() ? (events.paused() ? "PAUSED" : "RUNNING") : "DISABLED"),
                text("Active", events.activeId() + (events.hasActiveEvent() ? " / " + events.activeType() : "")),
                text("Remaining", events.remainingMillis() < 0 ? "-" : String.format(Locale.ROOT, "%.1fs", events.remainingMillis() / 1000.0)),
                text("Eligible definitions", Integer.toString(events.eligibleScheduledCount())),
                text("Latest winner", events.lastWinner()),
                text("Recorded wins", Long.toString(stats.totalRecordedWins())),
                text("Database", stats.state().name()),
                text("Vault", events.economyState()),
                text("PlexonKeys", events.keysState()));
        inventory.setItem(4, item(Material.BELL, Component.text("Live status", NamedTextColor.AQUA, TextDecoration.BOLD), statusLore, events.hasActiveEvent()));
        putDynamic(player, inventory, buttons.get(10), List.of(Component.text("Persistent feature state.", NamedTextColor.GRAY), Component.text(events.enabled() ? "Enabled" : "Disabled", events.enabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        putDynamic(player, inventory, buttons.get(12), List.of(Component.text("Runtime scheduler control.", NamedTextColor.GRAY)));
        putDynamic(player, inventory, buttons.get(14), List.of(Component.text("Starts one eligible event immediately.", NamedTextColor.GRAY)));
        putDynamic(player, inventory, buttons.get(16), List.of(Component.text("Cancels the current run without reward or statistics.", NamedTextColor.GRAY)));
        putDynamic(player, inventory, buttons.get(20), List.of(Component.text(events.configuredCount() + " configured definitions", NamedTextColor.GRAY), Component.text("Browse, inspect, preview and start events.", NamedTextColor.GRAY)));
        putDynamic(player, inventory, buttons.get(22), List.of(text("Phase", events.bingoPhase()), text("Participants", Integer.toString(events.bingoParticipantCount())), text("Draws", Integer.toString(events.bingoDrawCount()))));
        putDynamic(player, inventory, buttons.get(24), List.of(Component.text("Inspect reward profiles and usage.", NamedTextColor.GRAY)));
        putDynamic(player, inventory, buttons.get(30), List.of(text("Total wins", Long.toString(stats.totalRecordedWins())), text("Cached players", Integer.toString(stats.cachedPlayerCount()))));
        putDynamic(player, inventory, buttons.get(32), List.of(Component.text("Transactional config reload.", NamedTextColor.GRAY)));
        putDynamic(player, inventory, buttons.get(38), List.of());
        putDynamic(player, inventory, buttons.get(42), List.of());
        player.openInventory(inventory);
    }

    public void openEventList(Player player, int requestedPage) {
        if (!eventAccess(player)) return;
        List<ChatEventConfig.Definition> definitions = new ArrayList<>(plugin.getChatEvents().definitions().values());
        int pageCount = Math.max(1, (definitions.size() + EVENT_SLOTS.size() - 1) / EVENT_SLOTS.size());
        int page = Math.clamp(requestedPage, 0, pageCount - 1);
        Map<Integer, GuiButton> buttons = new LinkedHashMap<>();
        int from = page * EVENT_SLOTS.size();
        int to = Math.min(definitions.size(), from + EVENT_SLOTS.size());
        for (int index = from; index < to; index++) {
            ChatEventConfig.Definition definition = definitions.get(index);
            int slot = EVENT_SLOTS.get(index - from);
            buttons.put(slot, dynamic("event-" + definition.id(), slot, GuiAction.EVENT_DEFINITION, typeMaterial(definition.type()), definition.name(), definition.id()));
        }
        if (page > 0) buttons.put(45, dynamic("previous", 45, GuiAction.PAGE_PREVIOUS, Material.ARROW, "Previous page", ""));
        buttons.put(49, dynamic("dashboard", 49, GuiAction.OPEN_CHAT_EVENTS, Material.COMPASS, "Dashboard", ""));
        if (page + 1 < pageCount) buttons.put(53, dynamic("next", 53, GuiAction.PAGE_NEXT, Material.ARROW, "Next page", ""));
        ChatGUIHolder holder = dynamicHolder(ChatGUIHolder.Page.EVENT_LIST, player, buttons, "", page);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Chat Events • " + (page + 1) + "/" + pageCount, NamedTextColor.GOLD));
        holder.setInventory(inventory);
        fillBorder(inventory);
        for (int index = from; index < to; index++) {
            ChatEventConfig.Definition definition = definitions.get(index);
            GuiButton button = buttons.get(EVENT_SLOTS.get(index - from));
            List<Component> lore = List.of(
                    text("ID", definition.id()),
                    text("Type", definition.type().name()),
                    text("State", definition.enabled() ? "ENABLED" : "DISABLED"),
                    text("Weight", Integer.toString(definition.weight())),
                    text("Duration", definition.type() == ChatEventEngine.Type.BINGO ? definition.bingo().timeoutSeconds() + "s" : definition.durationSeconds() + "s"),
                    text("Reward", definition.rewardProfile()),
                    text("Cooldown", definition.cooldownSeconds() + "s"),
                    text("Minimum online", Integer.toString(definition.minOnline())),
                    text("Channels", definition.acceptedChannels().toString()),
                    text("Cooldown remaining", plugin.getChatEvents().cooldownRemainingSeconds(definition.id()) + "s"),
                    Component.empty(),
                    Component.text("Left click: details", NamedTextColor.YELLOW),
                    Component.text("Right click: preview", NamedTextColor.AQUA),
                    Component.text("Shift-left: start now", NamedTextColor.GREEN));
            putDynamic(player, inventory, button, lore);
        }
        if (buttons.containsKey(45)) putDynamic(player, inventory, buttons.get(45), List.of());
        putDynamic(player, inventory, buttons.get(49), List.of());
        if (buttons.containsKey(53)) putDynamic(player, inventory, buttons.get(53), List.of());
        player.openInventory(inventory);
    }

    public void openEventDetails(Player player, String id) {
        if (!eventAccess(player)) return;
        ChatEventConfig.Definition definition = plugin.getChatEvents().definition(id);
        if (definition == null) { player.sendMessage(Component.text("That event definition no longer exists.", NamedTextColor.RED)); openEventList(player, 0); return; }
        Map<Integer, GuiButton> buttons = new LinkedHashMap<>();
        buttons.put(11, dynamic("preview", 11, GuiAction.EVENT_PREVIEW, Material.SPYGLASS, "Preview", id));
        buttons.put(15, dynamic("start", 15, GuiAction.EVENT_START, Material.FIREWORK_ROCKET, "Start event", id));
        buttons.put(18, dynamic("back", 18, GuiAction.OPEN_EVENT_LIST, Material.ARROW, "Event browser", ""));
        buttons.put(22, dynamic("dashboard", 22, GuiAction.OPEN_CHAT_EVENTS, Material.COMPASS, "Dashboard", ""));
        buttons.put(26, dynamic("close", 26, GuiAction.CLOSE, Material.BARRIER, "Close", ""));
        ChatGUIHolder holder = dynamicHolder(ChatGUIHolder.Page.EVENT_DETAILS, player, buttons, id, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text(definition.name(), NamedTextColor.GOLD));
        holder.setInventory(inventory);
        fillBorder(inventory);
        List<Component> lore = new ArrayList<>();
        lore.add(text("ID", definition.id()));
        lore.add(text("Type", definition.type().name()));
        lore.add(text("Enabled", Boolean.toString(definition.enabled())));
        lore.add(text("Weight", Integer.toString(definition.weight())));
        lore.add(text("Reward profile", definition.rewardProfile()));
        lore.add(text("Cooldown", definition.cooldownSeconds() + "s"));
        lore.add(text("Cooldown remaining", plugin.getChatEvents().cooldownRemainingSeconds(id) + "s"));
        lore.add(text("Minimum online", Integer.toString(definition.minOnline())));
        lore.add(text("Accepted channels", definition.acceptedChannels().toString()));
        if (definition.type() == ChatEventEngine.Type.BINGO) {
            lore.add(text("Join window", definition.bingo().joinSeconds() + "s"));
            lore.add(text("Minimum participants", Integer.toString(definition.bingo().minParticipants())));
            lore.add(text("Draw interval", definition.bingo().drawIntervalSeconds() + "s"));
            lore.add(text("Patterns", definition.bingo().winPatterns().toString()));
        } else lore.add(text("Duration", definition.durationSeconds() + "s"));
        inventory.setItem(13, item(typeMaterial(definition.type()), Component.text(definition.name(), NamedTextColor.AQUA, TextDecoration.BOLD), lore, definition.enabled()));
        putDynamic(player, inventory, buttons.get(11), List.of(Component.text("Render a safe private preview.", NamedTextColor.GRAY)));
        putDynamic(player, inventory, buttons.get(15), List.of(Component.text("Manual start still obeys state, cooldown and eligibility checks.", NamedTextColor.GRAY)));
        putDynamic(player, inventory, buttons.get(18), List.of());
        putDynamic(player, inventory, buttons.get(22), List.of());
        putDynamic(player, inventory, buttons.get(26), List.of());
        player.openInventory(inventory);
    }

    public void openEventRewards(Player player) {
        if (!eventAccess(player)) return;
        ChatEventConfig current = ChatEventConfig.read(plugin.getConfigManager().section("chat-events"));
        Map<Integer, GuiButton> buttons = new LinkedHashMap<>();
        buttons.put(40, dynamic("back", 40, GuiAction.OPEN_CHAT_EVENTS, Material.ARROW, "Dashboard", ""));
        ChatGUIHolder holder = dynamicHolder(ChatGUIHolder.Page.EVENT_REWARDS, player, buttons, "", 0);
        Inventory inventory = Bukkit.createInventory(holder, 45, Component.text("Chat Event Rewards", NamedTextColor.GOLD));
        holder.setInventory(inventory);
        fillBorder(inventory);
        int index = 0;
        for (var entry : current.rewards().entrySet()) {
            if (index >= 21) break;
            int slot = EVENT_SLOTS.get(index++);
            ChatEventConfig.RewardProfile reward = entry.getValue();
            long usage = current.definitions().values().stream().filter(definition -> definition.rewardProfile().equals(entry.getKey())).count();
            inventory.setItem(slot, item(Material.GOLD_INGOT, Component.text(entry.getKey(), NamedTextColor.GOLD, TextDecoration.BOLD), List.of(
                    text("Vault cash", reward.economyEnabled() ? String.format(Locale.ROOT, "$%.2f", reward.economyAmount()) : "disabled"),
                    text("PlexonKeys", reward.keysEnabled() ? reward.keyTier() + " x" + reward.keyAmount() : "disabled"),
                    text("Console commands", Integer.toString(reward.consoleCommands().size())),
                    text("Referenced by", usage + " event(s)")), false));
        }
        putDynamic(player, inventory, buttons.get(40), List.of());
        player.openInventory(inventory);
    }

    public void openEventStats(Player player) {
        if (!eventAccess(player)) return;
        var stats = plugin.getChatEvents().statistics();
        Map<Integer, GuiButton> buttons = new LinkedHashMap<>();
        buttons.put(40, dynamic("back", 40, GuiAction.OPEN_CHAT_EVENTS, Material.ARROW, "Dashboard", ""));
        ChatGUIHolder holder = dynamicHolder(ChatGUIHolder.Page.EVENT_STATS, player, buttons, "", 0);
        Inventory inventory = Bukkit.createInventory(holder, 45, Component.text("Chat Event Statistics", NamedTextColor.GOLD));
        holder.setInventory(inventory);
        fillBorder(inventory);
        ChatEventStatisticsService.PlayerStats own = stats.get(player.getUniqueId(), player.getName());
        List<Component> ownLore = new ArrayList<>();
        ownLore.add(text("Total wins", Long.toString(own.totalWins())));
        for (ChatEventEngine.Type type : ChatEventEngine.Type.values()) ownLore.add(text(type.name(), Long.toString(own.typeWins(type.name()))));
        ownLore.add(text("Last win", own.lastWinAt() == null ? "-" : Instant.ofEpochMilli(own.lastWinAt()).toString()));
        inventory.setItem(4, item(Material.NETHER_STAR, Component.text("Your statistics", NamedTextColor.AQUA, TextDecoration.BOLD), ownLore, false));
        List<ChatEventStatisticsService.PlayerStats> top = stats.getTopPlayers(10);
        for (int index = 0; index < top.size(); index++) {
            var value = top.get(index);
            int slot = 10 + index + (index >= 7 ? 2 : 0);
            inventory.setItem(slot, item(Material.PLAYER_HEAD, Component.text((index + 1) + ". " + value.playerName(), NamedTextColor.YELLOW),
                    List.of(text("Total wins", Long.toString(value.totalWins())), text("Bingo wins", Long.toString(value.typeWins("BINGO")))), false));
        }
        inventory.setItem(36, item(Material.WRITABLE_BOOK, Component.text("Database", NamedTextColor.WHITE), List.of(
                text("State", stats.state().name()), text("Recorded wins", Long.toString(stats.totalRecordedWins())),
                text("Cached players", Integer.toString(stats.cachedPlayerCount())), text("Pending writes", Integer.toString(stats.pendingWrites()))), false));
        putDynamic(player, inventory, buttons.get(40), List.of());
        player.openInventory(inventory);
    }

    public void openEventBingo(Player player) {
        if (!eventAccess(player)) return;
        var events = plugin.getChatEvents();
        Map<Integer, GuiButton> buttons = new LinkedHashMap<>();
        UUID run = events.activeRunId();
        if (run != null && events.activeType().equals("BINGO")) {
            buttons.put(20, dynamic("card", 20, GuiAction.PLAYER_COMMAND, Material.MAP, "Open my card", "chat events bingo card"));
            if (events.bingoPhase().equals("JOINING")) buttons.put(24, dynamic("join", 24, GuiAction.PLAYER_COMMAND, Material.LIME_DYE, "Join Bingo", "chat events bingo join " + run));
        }
        buttons.put(38, dynamic("definitions", 38, GuiAction.OPEN_EVENT_LIST, Material.BOOKSHELF, "Event browser", ""));
        buttons.put(40, dynamic("back", 40, GuiAction.OPEN_CHAT_EVENTS, Material.ARROW, "Dashboard", ""));
        ChatGUIHolder holder = dynamicHolder(ChatGUIHolder.Page.EVENT_BINGO, player, buttons, "", 0);
        Inventory inventory = Bukkit.createInventory(holder, 45, Component.text("Bingo", NamedTextColor.GOLD, TextDecoration.BOLD));
        holder.setInventory(inventory);
        fillBorder(inventory);
        inventory.setItem(13, item(Material.BELL, Component.text("Bingo status", NamedTextColor.GOLD, TextDecoration.BOLD), List.of(
                text("Phase", events.bingoPhase()), text("Run", run == null ? "-" : run.toString()),
                text("Participants", Integer.toString(events.bingoParticipantCount())), text("Draws", Integer.toString(events.bingoDrawCount())),
                text("Last draw", events.bingoLastDraw()), text("Remaining numbers", Integer.toString(events.bingoRemainingCount()))), events.bingoPhase().equals("ACTIVE")));
        if (buttons.containsKey(20)) putDynamic(player, inventory, buttons.get(20), List.of(Component.text("Participant-only card rendering.", NamedTextColor.GRAY)));
        if (buttons.containsKey(24)) putDynamic(player, inventory, buttons.get(24), List.of(Component.text("Join this exact run before the join window closes.", NamedTextColor.GRAY)));
        putDynamic(player, inventory, buttons.get(38), List.of(Component.text("Find and start configured Bingo definitions.", NamedTextColor.GRAY)));
        putDynamic(player, inventory, buttons.get(40), List.of());
        player.openInventory(inventory);
    }

    private ChatGUIHolder dynamicHolder(ChatGUIHolder.Page page, Player player, Map<Integer, GuiButton> buttons, String context, int pageIndex) {
        return new ChatGUIHolder(page, player.getUniqueId(), plugin.getConfigManager().revision(), buttons, context, pageIndex, plugin.getChatEvents().activeRunId());
    }

    private void fillBorder(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot < 9 || slot >= inventory.getSize() - 9 || slot % 9 == 0 || slot % 9 == 8) inventory.setItem(slot, filler);
        }
    }

    private static GuiButton dynamic(String id, int slot, GuiAction action, Material material, String name, String value) {
        return new GuiButton(id, slot, action, material, material, Material.GRAY_DYE, name, List.of(), "", List.of(), value, "", false, false);
    }

    private void putDynamic(Player player, Inventory inventory, GuiButton button, List<Component> lore) {
        boolean allowed = allowed(player, button);
        Material material = allowed && available(button.action()) ? button.material() : button.disabledMaterial();
        NamedTextColor color = allowed ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY;
        inventory.setItem(button.slot(), item(material, Component.text(button.name(), color, TextDecoration.BOLD), lore, false));
    }

    private static Component text(String key, String value) {
        return Component.text(key + " › ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    private static Material typeMaterial(ChatEventEngine.Type type) {
        return switch (type) {
            case TYPE -> Material.NAME_TAG;
            case UNSCRAMBLE -> Material.PAPER;
            case MATH -> Material.COMPARATOR;
            case TRIVIA -> Material.BOOK;
            case REVERSE -> Material.COMPASS;
            case BINGO -> Material.MAP;
        };
    }

    private static ItemStack item(Material material, Component name, List<Component> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        meta.setEnchantmentGlintOverride(glow);
        item.setItemMeta(meta);
        return item;
    }

    public boolean allowed(Player player, GuiButton button) {
        return (button.permission().isBlank() || player.hasPermission(button.permission()))
                && (button.action().requiredPermission().isBlank() || player.hasPermission(button.action().requiredPermission()));
    }

    private boolean eventAccess(Player player) {
        if (!player.hasPermission("plexonchats.events")) { player.sendMessage(plugin.getConfigManager().getNoPermission()); return false; }
        return true;
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

    public void click(Player player, ChatGUIHolder holder, GuiButton button) { click(player, holder, button, ClickType.LEFT); }

    public void click(Player player, ChatGUIHolder holder, GuiButton button, ClickType clickType) {
        var config = plugin.getConfigManager();
        boolean eventPage = switch (holder.getPage()) {
            case EVENTS, EVENT_LIST, EVENT_DETAILS, EVENT_REWARDS, EVENT_BINGO, EVENT_STATS -> true;
            default -> false;
        };
        boolean pageAllowed = holder.getPage() != ChatGUIHolder.Page.ADMIN || player.hasPermission("plexonchats.manage");
        if (eventPage) pageAllowed = player.hasPermission("plexonchats.events");
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
                    else reopen(player, holder);
                }
            }
            case TOGGLE_MENTIONS, TOGGLE_TIPS, TOGGLE_PRIVATE_MESSAGES -> {
                PlayerPreferences updated = switch (button.action()) {
                    case TOGGLE_MENTIONS -> pref.toggleMentions();
                    case TOGGLE_TIPS -> pref.toggleTips();
                    default -> pref.togglePrivateMessages();
                };
                plugin.getPreferences().set(player.getUniqueId(), updated);
                reopen(player, holder);
            }
            case OPEN_ADMIN -> openPage(player, ChatGUIHolder.Page.ADMIN);
            case OPEN_CHAT_EVENTS -> openEventsDashboard(player);
            case OPEN_EVENT_LIST -> openEventList(player, holder.getPage() == ChatGUIHolder.Page.EVENT_LIST ? holder.getPageIndex() : 0);
            case OPEN_EVENT_REWARDS -> openEventRewards(player);
            case OPEN_EVENT_BINGO -> openEventBingo(player);
            case OPEN_EVENT_STATS -> openEventStats(player);
            case OPEN_EVENT_DETAILS -> openEventDetails(player, button.value());
            case EVENT_DEFINITION -> {
                if (clickType == ClickType.SHIFT_LEFT) {
                    if (!player.hasPermission("plexonchats.events.manage")) { player.sendMessage(config.getNoPermission()); return; }
                    player.sendMessage(Component.text("Chat Event start: " + plugin.getChatEvents().start(button.value())));
                    openEventList(player, holder.getPageIndex());
                } else if (clickType.isRightClick()) {
                    if (!player.hasPermission("plexonchats.events.manage")) { player.sendMessage(config.getNoPermission()); return; }
                    plugin.getChatEvents().preview(button.value(), player);
                } else openEventDetails(player, button.value());
            }
            case EVENT_PREVIEW -> plugin.getChatEvents().preview(button.value(), player);
            case EVENT_START -> {
                player.sendMessage(Component.text("Chat Event start: " + plugin.getChatEvents().start(button.value())));
                openEventDetails(player, button.value());
            }
            case PAGE_PREVIOUS -> openEventList(player, Math.max(0, holder.getPageIndex() - 1));
            case PAGE_NEXT -> openEventList(player, holder.getPageIndex() + 1);
            case OPEN_CREATOR -> openCreator(player);
            case OPEN_MAIN -> openMain(player);
            case CLOSE -> player.closeInventory();
            case RELOAD -> {
                boolean ok = plugin.reloadPlugin();
                player.sendMessage(ok ? config.getConfigReloaded() : config.message("config-failed"));
                if (ok) openEventsDashboard(player);
            }
            case TOGGLE_AUTO_MESSAGES -> {
                boolean ok = config.saveSetting("auto-messages.enabled", !plugin.getAutoMessages().enabled()) && plugin.reloadPlugin();
                if (!ok) player.sendMessage(config.message("config-failed"));
                reopen(player, holder);
            }
            case TOGGLE_CHAT_EVENTS -> {
                boolean ok = config.saveSetting("chat-events.enabled", !plugin.getChatEvents().enabled()) && plugin.reloadPlugin();
                if (!ok) player.sendMessage(config.message("config-failed"));
                openEventsDashboard(player);
            }
            case PAUSE_CHAT_EVENTS -> { plugin.getChatEvents().pause(); openEventsDashboard(player); }
            case RESUME_CHAT_EVENTS -> { plugin.getChatEvents().resume(); openEventsDashboard(player); }
            case START_RANDOM_CHAT_EVENT -> {
                var result = plugin.getChatEvents().startRandom(false);
                player.sendMessage(Component.text("Chat Event start: " + result));
                openEventsDashboard(player);
            }
            case STOP_CHAT_EVENT -> {
                UUID snapshot = holder.getActiveRunId();
                UUID current = plugin.getChatEvents().activeRunId();
                if (snapshot != null && !snapshot.equals(current)) { openEventsDashboard(player); return; }
                player.sendMessage(Component.text(plugin.getChatEvents().stop() ? "Chat Event cancelled." : "No active Chat Event."));
                openEventsDashboard(player);
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

    public void reopen(Player player, ChatGUIHolder holder) {
        switch (holder.getPage()) {
            case EVENT_LIST -> openEventList(player, holder.getPageIndex());
            case EVENT_DETAILS -> openEventDetails(player, holder.getContext());
            case EVENT_REWARDS -> openEventRewards(player);
            case EVENT_BINGO -> openEventBingo(player);
            case EVENT_STATS -> openEventStats(player);
            default -> openPage(player, holder.getPage());
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
