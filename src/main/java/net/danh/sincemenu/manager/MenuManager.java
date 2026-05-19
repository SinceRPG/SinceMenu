package net.danh.sincemenu.manager;

import net.danh.sincemenu.SinceMenu;
import net.danh.sincemenu.api.Action;
import net.danh.sincemenu.api.event.MenuCloseEvent;
import net.danh.sincemenu.api.event.MenuItemClickEvent;
import net.danh.sincemenu.api.event.MenuOpenEvent;
import net.danh.sincemenu.api.registry.ActionRegistry;
import net.danh.sincemenu.api.registry.RequirementRegistry;
import net.danh.sincemenu.util.SchedulerAdapter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuManager {

    private final SinceMenu plugin;
    private final SchedulerAdapter scheduler;
    private final ActionRegistry actionRegistry;
    private final RequirementRegistry requirementRegistry;
    private final Map<String, MenuDefinition> menus = new ConcurrentHashMap<>();
    private final Map<UUID, MenuSession> sessions = new ConcurrentHashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private PacketDisplayManager displayManager;

    public MenuManager(
            @NotNull SinceMenu plugin,
            @NotNull SchedulerAdapter scheduler,
            @NotNull ActionRegistry actionRegistry,
            @NotNull RequirementRegistry requirementRegistry
    ) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.actionRegistry = actionRegistry;
        this.requirementRegistry = requirementRegistry;
    }

    private static @NotNull ParsedAction parseAction(@NotNull String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            int end = trimmed.indexOf(']');
            return new ParsedAction(trimmed.substring(1, end), trimmed.substring(end + 1).trim());
        }
        int split = trimmed.indexOf(' ');
        if (split == -1) {
            return new ParsedAction(trimmed, "");
        }
        return new ParsedAction(trimmed.substring(0, split), trimmed.substring(split + 1).trim());
    }

    private static <E extends Enum<E>> @NotNull E parseEnum(@NotNull Class<E> type, @Nullable String raw, @NotNull E fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public void attachDisplayManager(@NotNull PacketDisplayManager displayManager) {
        this.displayManager = displayManager;
    }

    public void loadMenus() {
        Map<String, MenuDefinition> loaded = new HashMap<>();
        File directory = new File(plugin.getDataFolder(), "menus");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create menus directory.");
        }
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            menus.clear();
            return;
        }
        for (File file : files) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                String id = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                loaded.put(id, parseMenu(id, config));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Failed to load menu " + file.getName() + ": " + ex.getMessage());
            }
        }
        menus.clear();
        menus.putAll(loaded);
    }

    public boolean openMenu(@NotNull Player player, @NotNull String menuId, int requestedPage) {
        return openMenu(player, menuId, requestedPage, false);
    }

    private boolean openMenu(@NotNull Player player, @NotNull String menuId, int requestedPage, boolean pinned) {
        MenuDefinition menu = menus.get(menuId.toLowerCase(Locale.ROOT));
        if (menu == null || displayManager == null) {
            return false;
        }
        int page = Math.max(1, Math.min(requestedPage, menu.pages()));
        scheduler.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            MenuOpenEvent event = new MenuOpenEvent(player, menu, page);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            closeMenuInternal(player, false);
            MenuSession session = new MenuSession(player.getUniqueId(), menu, page, pinned);
            session.setLastHeldSlot(player.getInventory().getHeldItemSlot());
            sessions.put(player.getUniqueId(), session);
            displayManager.spawnSession(player, session);
            if (menu.refreshRate() > 0) {
                SchedulerAdapter.Scheduled task = scheduler.runAtEntityTimer(
                        player,
                        () -> refresh(player),
                        menu.refreshRate(),
                        menu.refreshRate()
                );
                session.setRefreshTask(task);
            }
            if (menu.followPlayer() && menu.followRate() > 0) {
                SchedulerAdapter.Scheduled task = scheduler.runAtEntityTimer(
                        player,
                        () -> move(player),
                        menu.followRate(),
                        menu.followRate()
                );
                session.setMoveTask(task);
            }
            if (menu.hoverLore() && menu.hoverRate() > 0) {
                SchedulerAdapter.Scheduled task = scheduler.runAtEntityTimer(
                        player,
                        () -> updateHover(player),
                        menu.hoverRate(),
                        menu.hoverRate()
                );
                session.setHoverTask(task);
            }
        });
        return true;
    }

    public void closeMenu(@NotNull Player player) {
        scheduler.runAtEntity(player, () -> closeMenuInternal(player, true));
    }

    public void cleanupPlayer(@NotNull Player player) {
        closeMenuInternal(player, false);
    }

    public void shutdown() {
        for (UUID uuid : Set.copyOf(sessions.keySet())) {
            MenuSession session = sessions.remove(uuid);
            if (session == null) {
                continue;
            }
            session.cancelTasks();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && displayManager != null) {
                displayManager.destroySession(player, session);
            } else {
                clearPacketState(uuid, session);
            }
        }
    }

    public void closeAll(boolean fireEvent) {
        for (UUID uuid : Set.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                scheduler.runAtEntity(player, () -> closeMenuInternal(player, fireEvent));
            } else {
                MenuSession session = sessions.remove(uuid);
                if (session != null) {
                    session.cancelTasks();
                    clearPacketState(uuid, session);
                }
            }
        }
    }

    private void clearPacketState(@NotNull UUID uuid, @NotNull MenuSession session) {
        if (displayManager != null) {
            displayManager.clearSessionState(uuid, session);
            return;
        }
        session.clearBindings();
    }

    public void refresh(@NotNull Player player) {
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null || displayManager == null) {
            return;
        }
        if (!player.isOnline()) {
            cleanupPlayer(player);
            return;
        }
        displayManager.refreshSession(player, session);
    }

    public void move(@NotNull Player player) {
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null || displayManager == null) {
            return;
        }
        if (!player.isOnline()) {
            cleanupPlayer(player);
            return;
        }
        displayManager.moveSession(player, session);
    }

    public void updateHover(@NotNull Player player) {
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null || displayManager == null) {
            return;
        }
        if (!player.isOnline()) {
            cleanupPlayer(player);
            return;
        }
        displayManager.updateHover(player, session);
    }

    public Optional<MenuSession> session(@NotNull Player player) {
        return Optional.ofNullable(sessions.get(player.getUniqueId()));
    }

    public @Nullable ClickTarget targetForEntity(@NotNull Player player, int entityId) {
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return null;
        }
        MenuItem item = session.clickItems().get(entityId);
        if (item == null) {
            item = session.entityItems().get(entityId);
        }
        return item == null ? null : new ClickTarget(session, item);
    }

    public boolean ownsEntity(@NotNull Player player, int entityId) {
        MenuSession session = sessions.get(player.getUniqueId());
        return session != null && (session.clickItems().containsKey(entityId) || session.entityItems().containsKey(entityId));
    }

    public void togglePinned(@NotNull Player player) {
        scheduler.runAtEntity(player, () -> {
            MenuSession session = sessions.get(player.getUniqueId());
            if (session != null && session.menu().dropKeyTogglePin()) {
                boolean pinned = session.togglePinned();
                String path = pinned ? "messages.menu-pinned" : "messages.menu-unpinned";
                String fallback = pinned ? "<gray>Menu pinned." : "<gray>Menu follow restored.";
                String raw = plugin.getConfig().getString(path, fallback);
                player.sendMessage(miniMessage.deserialize(raw == null ? fallback : raw));
            }
        });
    }

    public void handleSwapClose(@NotNull Player player) {
        MenuSession session = sessions.get(player.getUniqueId());
        if (session != null && session.menu().closeOnSwapHand()) {
            backMenu(player, session);
        }
    }

    public void handleHotbarScroll(@NotNull Player player, int selectedSlot) {
        scheduler.runAtEntity(player, () -> {
            MenuSession session = sessions.get(player.getUniqueId());
            if (session == null) {
                return;
            }
            int direction = session.scrollDirection(selectedSlot);
            if (direction != 0) {
                scrollActiveLayer(player, session, direction);
            }
            session.setLastHeldSlot(player.getInventory().getHeldItemSlot());
            if (displayManager != null) {
                displayManager.syncHeldSlot(player, session);
            }
        });
    }

    public void handleClick(@NotNull Player player, int entityId, @NotNull MenuClickType clickType) {
        scheduler.runAtEntity(player, () -> handleClickNow(player, entityId, clickType));
    }

    public void handleAttackClick(@NotNull Player player, int entityId) {
        scheduler.runAtEntity(player, () -> {
            MenuClickType clickType = player.isSneaking() ? MenuClickType.SHIFT_LEFT : MenuClickType.LEFT;
            handleClickNow(player, entityId, clickType);
        });
    }

    private void handleClickNow(@NotNull Player player, int entityId, @NotNull MenuClickType clickType) {
        ClickTarget target = targetForEntity(player, entityId);
        if (target == null) {
            return;
        }
        MenuItemClickEvent event = new MenuItemClickEvent(player, target.session, target.item, clickType);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        for (MenuAction menuAction : target.item.actions(clickType)) {
            if (requirementRegistry.testAll(player, menuAction.requirements())) {
                executeAction(player, target.session, target.item, menuAction.raw());
            }
        }
    }

    public @NotNull Map<String, MenuDefinition> menus() {
        return Collections.unmodifiableMap(menus);
    }

    public @NotNull Optional<MenuDefinition> menu(@NotNull String id) {
        return Optional.ofNullable(menus.get(id.toLowerCase(Locale.ROOT)));
    }

    public void nextPage(@NotNull Player player, @NotNull MenuSession session) {
        int next = session.page() + 1;
        if (next > session.menu().pages()) {
            next = 1;
        }
        int nextPage = next;
        scheduler.runAtEntity(player, () -> replaceActiveLayer(player, session, session.menu(), nextPage));
    }

    public void previousPage(@NotNull Player player, @NotNull MenuSession session) {
        int previous = session.page() - 1;
        if (previous < 1) {
            previous = session.menu().pages();
        }
        int previousPage = previous;
        scheduler.runAtEntity(player, () -> replaceActiveLayer(player, session, session.menu(), previousPage));
    }

    public void scrollUp(@NotNull Player player, @NotNull MenuSession session) {
        scheduler.runAtEntity(player, () -> scrollActiveLayer(player, session, -1));
    }

    public void scrollDown(@NotNull Player player, @NotNull MenuSession session) {
        scheduler.runAtEntity(player, () -> scrollActiveLayer(player, session, 1));
    }

    public void openSubMenu(@NotNull Player player, @NotNull String menuId) {
        MenuDefinition menu = menus.get(menuId.toLowerCase(Locale.ROOT));
        if (menu == null || displayManager == null) {
            return;
        }
        scheduler.runAtEntity(player, () -> {
            MenuSession session = sessions.get(player.getUniqueId());
            if (session == null) {
                openMenu(player, menu.id(), 1);
                return;
            }
            session.pushLayer(menu, 1);
            displayManager.respawnSession(player, session);
        });
    }

    public void backMenu(@NotNull Player player, @NotNull MenuSession session) {
        scheduler.runAtEntity(player, () -> {
            if (session.layerCount() <= 1) {
                closeMenuInternal(player, true);
                return;
            }
            session.popLayer();
            if (displayManager != null) {
                displayManager.respawnSession(player, session);
            }
        });
    }

    private void replaceActiveLayer(
            @NotNull Player player,
            @NotNull MenuSession session,
            @NotNull MenuDefinition menu,
            int page
    ) {
        if (sessions.get(player.getUniqueId()) != session || displayManager == null) {
            return;
        }
        session.replaceActiveLayer(menu, page);
        displayManager.respawnSession(player, session);
    }

    private void scrollActiveLayer(@NotNull Player player, @NotNull MenuSession session, int direction) {
        if (sessions.get(player.getUniqueId()) != session || displayManager == null || direction == 0) {
            return;
        }
        if (session.scrollActiveLayer(direction)) {
            displayManager.respawnSession(player, session);
        }
    }

    private void closeMenuInternal(@NotNull Player player, boolean fireEvent) {
        MenuSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.cancelTasks();
        if (displayManager != null) {
            displayManager.destroySession(player, session);
        }
        if (fireEvent) {
            Bukkit.getPluginManager().callEvent(new MenuCloseEvent(player, session));
        }
    }

    private void executeAction(@NotNull Player player, @NotNull MenuSession session, @NotNull MenuItem item, @NotNull String raw) {
        ParsedAction parsed = parseAction(raw);
        Action action = actionRegistry.get(parsed.key());
        if (action == null) {
            return;
        }
        action.execute(new Action.Context(player, session, item, parsed.argument(), this));
    }

    private @NotNull MenuDefinition parseMenu(@NotNull String id, @NotNull YamlConfiguration config) {
        String title = config.getString("title", id);
        List<String> commands = config.getStringList("commands").stream()
                .map(command -> command.trim().toLowerCase(Locale.ROOT))
                .filter(command -> !command.isBlank())
                .distinct()
                .toList();
        String permission = config.getString(
                "permission",
                plugin.getConfig().getString("menu-defaults.permission-pattern", "sincemenu.menu.{menu}").replace("{menu}", id)
        );
        String permissionMessage = config.getString(
                "permission-message",
                plugin.getConfig().getString("menu-defaults.permission-message", "<red>You do not have permission to open this menu.")
        );
        int refreshRate = Math.max(0, config.getInt("refresh-rate", plugin.getConfig().getInt("menu-defaults.refresh-rate", 20)));
        boolean followPlayer = config.getBoolean("follow-player", true);
        int followRate = Math.max(1, config.getInt("follow-rate", plugin.getConfig().getInt("menu-defaults.follow-rate", 2)));
        double followDistanceThreshold = Math.max(0.0D, config.getDouble(
                "follow-distance-threshold",
                plugin.getConfig().getDouble("menu-defaults.follow-distance-threshold", 0.35D)
        ));
        double followAngleThreshold = Math.max(0.0D, config.getDouble(
                "follow-angle-threshold",
                plugin.getConfig().getDouble("menu-defaults.follow-angle-threshold", 12.0D)
        ));
        double pinnedReturnDistance = Math.max(0.1D, config.getDouble(
                "pinned-return-distance",
                plugin.getConfig().getDouble("menu-defaults.pinned-return-distance", 10.0D)
        ));
        boolean followRotation = config.getBoolean(
                "follow-rotation",
                plugin.getConfig().getBoolean("menu-defaults.follow-rotation", false)
        );
        int scrollVisibleItems = Math.max(0, config.getInt(
                "scroll-visible-items",
                plugin.getConfig().getInt("menu-defaults.scroll-visible-items", 6)
        ));
        double scrollStartY = config.getDouble(
                "scroll-start-y",
                plugin.getConfig().getDouble("menu-defaults.scroll-start-y", 0.75D)
        );
        double scrollItemSpacing = Math.max(0.01D, config.getDouble(
                "scroll-item-spacing",
                plugin.getConfig().getDouble("menu-defaults.scroll-item-spacing", 0.35D)
        ));
        double submenuShiftX = config.getDouble(
                "submenu-shift-x",
                plugin.getConfig().getDouble("menu-defaults.submenu-shift-x", 1.35D)
        );
        boolean hoverLore = config.getBoolean(
                "hover-lore",
                plugin.getConfig().getBoolean("menu-defaults.hover-lore", true)
        );
        int hoverRate = Math.max(1, config.getInt(
                "hover-rate",
                plugin.getConfig().getInt("menu-defaults.hover-rate", 2)
        ));
        double loreOffsetX = config.getDouble(
                "lore-offset-x",
                plugin.getConfig().getDouble("menu-defaults.lore-offset-x", 0.95D)
        );
        double loreOffsetY = config.getDouble(
                "lore-offset-y",
                plugin.getConfig().getDouble("menu-defaults.lore-offset-y", 0.1D)
        );
        double loreOffsetZ = config.getDouble(
                "lore-offset-z",
                plugin.getConfig().getDouble("menu-defaults.lore-offset-z", 0.05D)
        );
        int loreBackgroundColor = (int) Long.decode(String.valueOf(config.get(
                "lore-background-color",
                plugin.getConfig().get("menu-defaults.lore-background-color", "0xAA001B2E")
        ))).longValue();
        double hoverMaxDistance = Math.max(0.1D, config.getDouble(
                "hover-max-distance",
                plugin.getConfig().getDouble("menu-defaults.hover-max-distance", 3.5D)
        ));
        double itemScale = Math.max(0.01D, config.getDouble(
                "item-scale",
                plugin.getConfig().getDouble("menu-defaults.item-scale", 0.28D)
        ));
        double textScale = Math.max(0.01D, config.getDouble(
                "text-scale",
                plugin.getConfig().getDouble("menu-defaults.text-scale", 0.55D)
        ));
        double loreScale = Math.max(0.01D, config.getDouble(
                "lore-scale",
                plugin.getConfig().getDouble("menu-defaults.lore-scale", 0.42D)
        ));
        boolean dropKeyTogglePin = config.getBoolean("drop-key-toggle-pin", plugin.getConfig().getBoolean("menu-defaults.drop-key-toggle-pin", true));
        boolean closeOnSwapHand = config.getBoolean("close-on-swap-hand", plugin.getConfig().getBoolean("menu-defaults.close-on-swap-hand", true));
        double hitboxWidth = Math.max(0.1D, config.getDouble("hitbox-width", plugin.getConfig().getDouble("menu-defaults.hitbox-width", 1.6D)));
        double hitboxHeight = Math.max(0.1D, config.getDouble("hitbox-height", plugin.getConfig().getDouble("menu-defaults.hitbox-height", 1.0D)));
        int pages = Math.max(1, config.getInt("pages", 1));
        List<MenuItem> items = new ArrayList<>();
        ConfigurationSection root = config.getConfigurationSection("items");
        if (root != null) {
            for (String itemId : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(itemId);
                if (section != null) {
                    MenuItem item = parseItem(itemId, section);
                    items.add(item);
                    pages = Math.max(pages, item.page());
                }
            }
        }
        return new MenuDefinition(
                id,
                title,
                List.copyOf(commands),
                permission == null ? "" : permission,
                permissionMessage == null ? "" : permissionMessage,
                refreshRate,
                followPlayer,
                followRate,
                followDistanceThreshold,
                followAngleThreshold,
                pinnedReturnDistance,
                followRotation,
                scrollVisibleItems,
                scrollStartY,
                scrollItemSpacing,
                submenuShiftX,
                hoverLore,
                hoverRate,
                loreOffsetX,
                loreOffsetY,
                loreOffsetZ,
                loreBackgroundColor,
                hoverMaxDistance,
                itemScale,
                textScale,
                loreScale,
                dropKeyTogglePin,
                closeOnSwapHand,
                hitboxWidth,
                hitboxHeight,
                pages,
                List.copyOf(items)
        );
    }

    private @NotNull MenuItem parseItem(@NotNull String id, @NotNull ConfigurationSection section) {
        DisplayType type = parseEnum(DisplayType.class, section.getString("type", "TEXT"), DisplayType.TEXT);
        int page = Math.max(1, section.getInt("page", 1));
        String text = section.getString("text", "");
        String icon = section.getString("icon", "minecraft:stone");
        String fallbackIcon = section.getString("fallback-icon");
        double offsetX = section.getDouble("offset-x", 0.0D);
        double offsetY = section.getDouble("offset-y", 0.0D);
        double offsetZ = section.getDouble("offset-z", 2.0D);
        double hitboxWidth = Math.max(0.0D, section.getDouble("hitbox-width", 0.0D));
        double hitboxHeight = Math.max(0.0D, section.getDouble("hitbox-height", 0.0D));
        double scale = Math.max(0.0D, section.getDouble("scale", 0.0D));
        boolean scrollable = section.getBoolean("scrollable", true);
        List<String> lore = section.getStringList("lore");
        int backgroundColor = (int) Long.decode(String.valueOf(section.get("background-color", "0x00000000"))).longValue();
        String billboard = section.getString("billboard", "CENTER");
        List<String> viewRequirements = section.getStringList("view-requirements");
        List<String> legacyClickRequirements = section.getStringList("click-requirements");
        Map<MenuClickType, List<MenuAction>> actions = new EnumMap<>(MenuClickType.class);
        ConfigurationSection clicks = section.getConfigurationSection("clicks");
        if (clicks != null) {
            for (String clickKey : clicks.getKeys(false)) {
                MenuClickType clickType = parseEnum(MenuClickType.class, clickKey, MenuClickType.LEFT);
                actions.put(clickType, parseActions(clicks, clickKey, legacyClickRequirements));
            }
        }
        return new MenuItem(
                id,
                type,
                page,
                text,
                icon,
                fallbackIcon,
                offsetX,
                offsetY,
                offsetZ,
                hitboxWidth,
                hitboxHeight,
                scale,
                scrollable,
                List.copyOf(lore),
                backgroundColor,
                billboard,
                List.copyOf(viewRequirements),
                Map.copyOf(actions)
        );
    }

    private @NotNull List<MenuAction> parseActions(
            @NotNull ConfigurationSection clicks,
            @NotNull String clickKey,
            @NotNull List<String> legacyRequirements
    ) {
        List<?> rawActions = clicks.getList(clickKey + ".actions", List.of());
        List<MenuAction> parsed = new ArrayList<>();
        for (Object rawAction : rawActions) {
            if (rawAction instanceof String actionText) {
                parsed.add(new MenuAction(actionText, List.copyOf(legacyRequirements)));
                continue;
            }
            if (rawAction instanceof Map<?, ?> map) {
                Object action = map.get("action");
                if (!(action instanceof String actionText) || actionText.isBlank()) {
                    continue;
                }
                parsed.add(new MenuAction(actionText, parseRequirementObject(map.get("requirements"))));
            }
        }
        return List.copyOf(parsed);
    }

    private @NotNull List<String> parseRequirementObject(@Nullable Object rawRequirements) {
        if (!(rawRequirements instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> requirements = new ArrayList<>();
        for (Object requirement : iterable) {
            if (requirement instanceof String text && !text.isBlank()) {
                requirements.add(text);
            }
        }
        return List.copyOf(requirements);
    }

    public @NotNull MiniMessage miniMessage() {
        return miniMessage;
    }

    public enum DisplayType {
        TEXT,
        ITEM
    }

    public enum MenuClickType {
        LEFT,
        RIGHT,
        SHIFT,
        SHIFT_LEFT,
        SHIFT_RIGHT,
        ANY
    }

    private record ParsedAction(@NotNull String key, @NotNull String argument) {
    }

    public record ClickTarget(@NotNull MenuSession session, @NotNull MenuItem item) {
    }

    public record MenuDefinition(
            @NotNull String id,
            @NotNull String title,
            @NotNull List<String> commands,
            @NotNull String permission,
            @NotNull String permissionMessage,
            int refreshRate,
            boolean followPlayer,
            int followRate,
            double followDistanceThreshold,
            double followAngleThreshold,
            double pinnedReturnDistance,
            boolean followRotation,
            int scrollVisibleItems,
            double scrollStartY,
            double scrollItemSpacing,
            double submenuShiftX,
            boolean hoverLore,
            int hoverRate,
            double loreOffsetX,
            double loreOffsetY,
            double loreOffsetZ,
            int loreBackgroundColor,
            double hoverMaxDistance,
            double itemScale,
            double textScale,
            double loreScale,
            boolean dropKeyTogglePin,
            boolean closeOnSwapHand,
            double hitboxWidth,
            double hitboxHeight,
            int pages,
            @NotNull List<MenuItem> items
    ) {
        public @NotNull List<MenuItem> pageItems(int page) {
            return items.stream().filter(item -> item.page() == page).toList();
        }

        public @NotNull List<MenuItem> visibleItems(@NotNull MenuLayer layer) {
            List<MenuItem> pageItems = pageItems(layer.page());
            if (scrollVisibleItems <= 0) {
                return pageItems;
            }
            List<MenuItem> fixed = pageItems.stream().filter(item -> !item.scrollable()).toList();
            List<MenuItem> scrollable = pageItems.stream().filter(MenuItem::scrollable).toList();
            int start = Math.max(0, Math.min(layer.scrollIndex(), maxScrollIndex(layer.page())));
            int end = Math.min(scrollable.size(), start + scrollVisibleItems);
            List<MenuItem> visible = new ArrayList<>(fixed.size() + Math.max(0, end - start));
            visible.addAll(scrollable.subList(start, end));
            visible.addAll(fixed);
            return List.copyOf(visible);
        }

        public int visibleSlot(@NotNull MenuLayer layer, @NotNull MenuItem item) {
            if (!item.scrollable() || scrollVisibleItems <= 0) {
                return -1;
            }
            List<MenuItem> scrollable = pageItems(layer.page()).stream().filter(MenuItem::scrollable).toList();
            return scrollable.indexOf(item) - layer.scrollIndex();
        }

        public int maxScrollIndex(int page) {
            if (scrollVisibleItems <= 0) {
                return 0;
            }
            int itemCount = (int) pageItems(page).stream().filter(MenuItem::scrollable).count();
            return Math.max(0, itemCount - scrollVisibleItems);
        }
    }

    public record MenuItem(
            @NotNull String id,
            @NotNull DisplayType type,
            int page,
            @NotNull String text,
            @NotNull String icon,
            @Nullable String fallbackIcon,
            double offsetX,
            double offsetY,
            double offsetZ,
            double hitboxWidth,
            double hitboxHeight,
            double scale,
            boolean scrollable,
            @NotNull List<String> lore,
            int backgroundColor,
            @NotNull String billboard,
            @NotNull List<String> viewRequirements,
            @NotNull Map<MenuClickType, List<MenuAction>> actions
    ) {
        public @NotNull List<MenuAction> actions(@NotNull MenuClickType clickType) {
            List<MenuAction> direct = actions.get(clickType);
            if (direct != null && !direct.isEmpty()) {
                return direct;
            }
            if (clickType == MenuClickType.SHIFT_LEFT || clickType == MenuClickType.SHIFT_RIGHT) {
                List<MenuAction> shift = actions.get(MenuClickType.SHIFT);
                if (shift != null && !shift.isEmpty()) {
                    return shift;
                }
            }
            return actions.getOrDefault(MenuClickType.ANY, List.of());
        }
    }

    public record MenuAction(
            @NotNull String raw,
            @NotNull List<String> requirements
    ) {
    }

    public static final class MenuSession {

        private final UUID playerId;
        private final List<MenuLayer> layers = new ArrayList<>();
        private final Map<Integer, MenuItem> displayItems = new ConcurrentHashMap<>();
        private final Map<Integer, MenuItem> clickItems = new ConcurrentHashMap<>();
        private final Map<String, RenderedItem> renderedItems = new ConcurrentHashMap<>();
        private volatile SchedulerAdapter.Scheduled refreshTask;
        private volatile SchedulerAdapter.Scheduled moveTask;
        private volatile SchedulerAdapter.Scheduled hoverTask;
        private volatile boolean pinned;
        private volatile int lastHeldSlot;
        private volatile double anchorX;
        private volatile double anchorY;
        private volatile double anchorZ;
        private volatile float anchorYaw;
        private volatile float anchorPitch;
        private volatile boolean hasAnchor;

        public MenuSession(@NotNull UUID playerId, @NotNull MenuDefinition menu, int page, boolean pinned) {
            this.playerId = playerId;
            this.pinned = pinned;
            this.layers.add(new MenuLayer(menu, page));
        }

        public @NotNull UUID playerId() {
            return playerId;
        }

        public @NotNull MenuDefinition menu() {
            return activeLayer().menu();
        }

        public int page() {
            return activeLayer().page();
        }

        public @NotNull List<MenuLayer> layers() {
            return List.copyOf(layers);
        }

        public int activeLayerIndex() {
            return layers.size() - 1;
        }

        public int layerCount() {
            return layers.size();
        }

        public @NotNull MenuLayer activeLayer() {
            return layers.get(activeLayerIndex());
        }

        public void pushLayer(@NotNull MenuDefinition menu, int page) {
            layers.add(new MenuLayer(menu, Math.max(1, Math.min(page, menu.pages()))));
        }

        public void replaceActiveLayer(@NotNull MenuDefinition menu, int page) {
            layers.set(activeLayerIndex(), new MenuLayer(menu, Math.max(1, Math.min(page, menu.pages()))));
        }

        public void popLayer() {
            if (layers.size() > 1) {
                layers.remove(activeLayerIndex());
            }
        }

        public boolean scrollActiveLayer(int direction) {
            return activeLayer().scroll(direction);
        }

        public @NotNull Map<Integer, MenuItem> entityItems() {
            return displayItems;
        }

        public @NotNull Map<Integer, MenuItem> clickItems() {
            return clickItems;
        }

        public @NotNull Map<String, RenderedItem> renderedItems() {
            return renderedItems;
        }

        public void bind(@NotNull RenderedItem renderedItem) {
            renderedItems.put(renderKey(renderedItem.layerIndex(), renderedItem.item().id()), renderedItem);
            displayItems.put(renderedItem.displayEntityId(), renderedItem.item());
            clickItems.put(renderedItem.interactionEntityId(), renderedItem.item());
        }

        public void unbind(@NotNull RenderedItem renderedItem) {
            renderedItems.remove(renderKey(renderedItem.layerIndex(), renderedItem.item().id()));
            displayItems.remove(renderedItem.displayEntityId());
            clickItems.remove(renderedItem.interactionEntityId());
        }

        public @Nullable RenderedItem renderedItem(int layerIndex, @NotNull String itemId) {
            return renderedItems.get(renderKey(layerIndex, itemId));
        }

        public void clearBindings() {
            renderedItems.clear();
            displayItems.clear();
            clickItems.clear();
        }

        public void setRefreshTask(@NotNull SchedulerAdapter.Scheduled refreshTask) {
            this.refreshTask = refreshTask;
        }

        public void cancelTasks() {
            SchedulerAdapter.Scheduled task = refreshTask;
            refreshTask = null;
            if (task != null) {
                task.cancel();
            }
            SchedulerAdapter.Scheduled movement = moveTask;
            moveTask = null;
            if (movement != null) {
                movement.cancel();
            }
            SchedulerAdapter.Scheduled hover = hoverTask;
            hoverTask = null;
            if (hover != null) {
                hover.cancel();
            }
        }

        public void setMoveTask(@NotNull SchedulerAdapter.Scheduled moveTask) {
            this.moveTask = moveTask;
        }

        public void setHoverTask(@NotNull SchedulerAdapter.Scheduled hoverTask) {
            this.hoverTask = hoverTask;
        }

        public void setLastHeldSlot(int lastHeldSlot) {
            this.lastHeldSlot = normalizeSlot(lastHeldSlot);
        }

        public int lastHeldSlot() {
            return lastHeldSlot;
        }

        public int scrollDirection(int selectedSlot) {
            int normalized = normalizeSlot(selectedSlot);
            int previous = lastHeldSlot;
            lastHeldSlot = normalized;
            if (normalized == previous) {
                return 0;
            }
            int forward = (normalized - previous + 9) % 9;
            int backward = (previous - normalized + 9) % 9;
            return forward <= backward ? 1 : -1;
        }

        public boolean shouldMove(@NotNull Location eye) {
            double distanceSquared = distanceSquared(eye);
            MenuDefinition activeMenu = menu();
            if (pinned) {
                double returnDistance = activeMenu.pinnedReturnDistance();
                return distanceSquared >= returnDistance * returnDistance;
            }
            if (!hasAnchor) {
                return true;
            }
            double threshold = activeMenu.followDistanceThreshold();
            if (distanceSquared >= threshold * threshold) {
                return true;
            }
            return activeMenu.followRotation() && (
                    angleDelta(anchorYaw, eye.getYaw()) >= activeMenu.followAngleThreshold()
                            || angleDelta(anchorPitch, eye.getPitch()) >= activeMenu.followAngleThreshold()
            );
        }

        public void updateAnchor(@NotNull Location eye) {
            anchorX = eye.getX();
            anchorY = eye.getY();
            anchorZ = eye.getZ();
            anchorYaw = eye.getYaw();
            anchorPitch = eye.getPitch();
            hasAnchor = true;
        }

        public boolean hasAnchor() {
            return hasAnchor;
        }

        public @NotNull Location getAnchorLocation(@NotNull org.bukkit.World world) {
            return new Location(world, anchorX, anchorY, anchorZ, anchorYaw, anchorPitch);
        }

        private double distanceSquared(@NotNull Location eye) {
            double dx = eye.getX() - anchorX;
            double dy = eye.getY() - anchorY;
            double dz = eye.getZ() - anchorZ;
            return dx * dx + dy * dy + dz * dz;
        }

        private double angleDelta(float first, float second) {
            double delta = Math.abs(first - second) % 360.0D;
            return delta > 180.0D ? 360.0D - delta : delta;
        }

        private @NotNull String renderKey(int layerIndex, @NotNull String itemId) {
            return layerIndex + ":" + itemId;
        }

        private int normalizeSlot(int slot) {
            return Math.max(0, Math.min(8, slot));
        }

        public boolean togglePinned() {
            pinned = !pinned;
            return pinned;
        }

        public boolean isPinned() {
            return pinned;
        }
    }

    public record RenderedItem(
            int displayEntityId,
            int interactionEntityId,
            int layerIndex,
            @NotNull MenuItem item
    ) {
    }

    public static final class MenuLayer {

        private final MenuDefinition menu;
        private final int page;
        private int scrollIndex;

        private MenuLayer(@NotNull MenuDefinition menu, int page) {
            this.menu = menu;
            this.page = page;
        }

        public @NotNull MenuDefinition menu() {
            return menu;
        }

        public int page() {
            return page;
        }

        public int scrollIndex() {
            return scrollIndex;
        }

        private boolean scroll(int direction) {
            int next = Math.max(0, Math.min(menu.maxScrollIndex(page), scrollIndex + direction));
            if (next == scrollIndex) {
                return false;
            }
            scrollIndex = next;
            return true;
        }
    }
}