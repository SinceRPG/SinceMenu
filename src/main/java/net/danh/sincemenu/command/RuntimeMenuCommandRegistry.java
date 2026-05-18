package net.danh.sincemenu.command;

import net.danh.sincemenu.manager.MenuManager;
import net.danh.sincemenu.util.SchedulerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RuntimeMenuCommandRegistry {

    private static final String FALLBACK_PREFIX = "sincemenu";

    private final JavaPlugin plugin;
    private final MenuManager menuManager;
    private final SchedulerAdapter scheduler;
    private final CommandMessenger messenger;
    private final OnlinePlayerNames playerNames;
    private final Map<String, Command> registered = new HashMap<>();
    private int primaryCommandCount;

    public RuntimeMenuCommandRegistry(
            @NotNull JavaPlugin plugin,
            @NotNull MenuManager menuManager,
            @NotNull SchedulerAdapter scheduler,
            @NotNull CommandMessenger messenger,
            @NotNull OnlinePlayerNames playerNames
    ) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.scheduler = scheduler;
        this.messenger = messenger;
        this.playerNames = playerNames;
    }

    public void refresh() {
        scheduler.runGlobal(this::refreshOnGlobal);
    }

    public void refreshNowOnGlobal() {
        refreshOnGlobal();
    }

    public void unregisterAll() {
        unregisterAll(true);
    }

    public void unregisterAll(boolean updatePlayers) {
        CommandMap commandMap = Bukkit.getCommandMap();
        Map<String, Command> snapshot = Map.copyOf(registered);
        snapshot.forEach((label, command) -> unregister(commandMap, label, command));
        registered.clear();
        primaryCommandCount = 0;
        if (updatePlayers) {
            updateOnlineCommands();
        }
    }

    private void refreshOnGlobal() {
        unregisterAll(false);
        for (MenuManager.MenuDefinition menu : menuManager.menus().values()) {
            registerMenu(menu);
        }
        updateOnlineCommands();
        plugin.getLogger().info("Registered " + primaryCommandCount + " runtime menu command(s).");
    }

    private void registerMenu(@NotNull MenuManager.MenuDefinition menu) {
        if (menu.commands().isEmpty()) {
            return;
        }
        String primary = normalize(menu.commands().getFirst());
        if (primary.isBlank() || registered.containsKey(primary)) {
            return;
        }
        List<String> aliases = menu.commands().stream()
                .skip(1)
                .map(this::normalize)
                .filter(alias -> !alias.isBlank())
                .distinct()
                .toList();
        String description = plugin.getConfig().getString("command-descriptions.menu", "Open menu {menu}.");
        Command command = new RuntimeMenuCommand(primary, menu, menuManager, scheduler, messenger, playerNames, aliases, description);
        Bukkit.getCommandMap().register(FALLBACK_PREFIX, command);
        registered.put(primary, command);
        aliases.forEach(alias -> registered.put(alias, command));
        primaryCommandCount++;
    }

    private void unregister(@NotNull CommandMap commandMap, @NotNull String label, @NotNull Command command) {
        command.unregister(commandMap);
        removeKnownCommand(label);
        removeKnownCommand(FALLBACK_PREFIX + ":" + label);
    }

    @SuppressWarnings("unchecked")
    private void removeKnownCommand(@NotNull String label) {
        try {
            Field knownCommands = findKnownCommandsField(Bukkit.getCommandMap().getClass());
            knownCommands.setAccessible(true);
            Object value = knownCommands.get(Bukkit.getCommandMap());
            if (value instanceof Map<?, ?> map) {
                ((Map<String, Command>) map).remove(label.toLowerCase(Locale.ROOT));
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private @NotNull Field findKnownCommandsField(@NotNull Class<?> type) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField("knownCommands");
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("knownCommands");
    }

    private void updateOnlineCommands() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.runAtEntity(player, () -> {
                if (player.isOnline()) {
                    player.updateCommands();
                }
            });
        }
    }

    private @NotNull String normalize(@NotNull String command) {
        return command.trim().toLowerCase(Locale.ROOT);
    }
}
