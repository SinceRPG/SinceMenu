package net.danh.sincemenu.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.danh.sincemenu.manager.MenuManager;
import net.danh.sincemenu.util.SchedulerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class SinceMenuCommand {

    private final JavaPlugin plugin;
    private final MenuManager menuManager;
    private final SchedulerAdapter scheduler;
    private final CommandMessenger messenger;
    private final RuntimeMenuCommandRegistry runtimeMenuCommands;
    private final OnlinePlayerNames playerNames;

    public SinceMenuCommand(
            @NotNull JavaPlugin plugin,
            @NotNull MenuManager menuManager,
            @NotNull SchedulerAdapter scheduler,
            @NotNull CommandMessenger messenger,
            @NotNull RuntimeMenuCommandRegistry runtimeMenuCommands,
            @NotNull OnlinePlayerNames playerNames
    ) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.scheduler = scheduler;
        this.messenger = messenger;
        this.runtimeMenuCommands = runtimeMenuCommands;
        this.playerNames = playerNames;
    }

    public @NotNull LiteralCommandNode<CommandSourceStack> createNode() {
        return Commands.literal("sincemenu")
                .requires(source -> source.getSender().hasPermission("sincemenu.admin"))
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource())))
                .then(Commands.literal("close")
                        .executes(context -> close(context.getSource())))
                .then(Commands.literal("open")
                        .executes(context -> openDefault(context.getSource()))
                        .then(Commands.argument("menu", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    menuManager.menus().keySet().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> openMenu(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "menu"),
                                        null
                                ))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            playerNames.suggest(builder.getRemaining()).forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> openMenu(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "menu"),
                                                StringArgumentType.getString(context, "player")
                                        )))))
                .build();
    }

    private int help(@NotNull CommandSourceStack source) {
        CommandSender sender = source.getSender();
        plugin.getConfig().getStringList("messages.admin-help").stream()
                .map(messenger::deserialize)
                .forEach(message -> messenger.send(sender, message));
        return 1;
    }

    private int reload(@NotNull CommandSourceStack source) {
        CommandSender sender = source.getSender();
        scheduler.runGlobal(() -> {
            plugin.reloadConfig();
            menuManager.closeAll(false);
            menuManager.loadMenus();
            runtimeMenuCommands.refreshNowOnGlobal();
            messenger.send(sender, messenger.configMessage("messages.reloaded", "<green>SinceMenu reloaded."));
        });
        return 1;
    }

    private int close(@NotNull CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player) || !player.isOnline()) {
            messenger.send(sender, messenger.configMessage("messages.players-only", "<red>Only players can open this menu."));
            return 0;
        }
        menuManager.closeMenu(player);
        return 1;
    }

    private int openDefault(@NotNull CommandSourceStack source) {
        return openMenu(source, plugin.getConfig().getString("default-menu", "advanced_menu"), null);
    }

    private int openMenu(@NotNull CommandSourceStack source, @NotNull String menuId, String targetName) {
        CommandSender sender = source.getSender();
        scheduler.runGlobal(() -> openMenuFromGlobal(sender, menuId, targetName));
        return 1;
    }

    private void openMenuFromGlobal(@NotNull CommandSender sender, @NotNull String menuId, String targetName) {
        Player target = targetName == null ? asPlayer(sender) : Bukkit.getPlayerExact(targetName);
        if (target == null) {
            messenger.send(sender, messenger.configMessage("messages.unknown-player", "<red>Unknown player: <white>{player}")
                    .replaceText(builder -> builder.matchLiteral("{player}").replacement(targetName == null ? "console" : targetName)));
            return;
        }
        if (!menuManager.openMenu(target, menuId, 1)) {
            messenger.send(sender, messenger.configMessage("messages.unknown-menu", "<red>Unknown menu: <white>{menu}")
                    .replaceText(builder -> builder.matchLiteral("{menu}").replacement(menuId)));
        }
    }

    private Player asPlayer(@NotNull CommandSender sender) {
        return sender instanceof Player player && player.isOnline() ? player : null;
    }

}
