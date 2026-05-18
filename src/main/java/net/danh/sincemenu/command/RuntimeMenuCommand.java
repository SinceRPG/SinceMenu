package net.danh.sincemenu.command;

import net.danh.sincemenu.manager.MenuManager;
import net.danh.sincemenu.util.SchedulerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class RuntimeMenuCommand extends Command {

    private final MenuManager menuManager;
    private final SchedulerAdapter scheduler;
    private final CommandMessenger messenger;
    private final OnlinePlayerNames playerNames;
    private final MenuManager.MenuDefinition menu;

    public RuntimeMenuCommand(
            @NotNull String label,
            @NotNull MenuManager.MenuDefinition menu,
            @NotNull MenuManager menuManager,
            @NotNull SchedulerAdapter scheduler,
            @NotNull CommandMessenger messenger,
            @NotNull OnlinePlayerNames playerNames,
            @NotNull List<String> aliases,
            @NotNull String description
    ) {
        super(label, description.replace("{menu}", menu.id()), "/" + label + " [player]", aliases);
        this.menu = menu;
        this.menuManager = menuManager;
        this.scheduler = scheduler;
        this.messenger = messenger;
        this.playerNames = playerNames;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!hasMenuPermission(sender)) {
            return true;
        }
        if (args.length > 0) {
            if (!sender.hasPermission("sincemenu.admin")) {
                messenger.send(sender, messenger.configMessage("messages.no-permission", "<red>You do not have permission."));
                return true;
            }
            openTarget(sender, args[0]);
            return true;
        }
        if (!(sender instanceof Player player) || !player.isOnline()) {
            messenger.send(sender, messenger.configMessage("messages.players-only", "<red>Only players can open this menu."));
            return true;
        }
        open(sender, player);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1 && sender.hasPermission("sincemenu.admin")) {
            return List.copyOf(playerNames.suggest(args[0]));
        }
        return List.of();
    }

    private void openTarget(@NotNull CommandSender sender, @NotNull String targetName) {
        scheduler.runGlobal(() -> {
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                messenger.send(sender, messenger.configMessage("messages.unknown-player", "<red>Unknown player: <white>{player}")
                        .replaceText(builder -> builder.matchLiteral("{player}").replacement(targetName)));
                return;
            }
            open(sender, target);
        });
    }

    private void open(@NotNull CommandSender sender, @NotNull Player target) {
        if (!menuManager.openMenu(target, menu.id(), 1)) {
            messenger.send(sender, messenger.configMessage("messages.unknown-menu", "<red>Unknown menu: <white>{menu}")
                    .replaceText(builder -> builder.matchLiteral("{menu}").replacement(menu.id())));
        }
    }

    private boolean hasMenuPermission(@NotNull CommandSender sender) {
        if (menu.permission().isBlank() || sender.hasPermission(menu.permission())) {
            return true;
        }
        messenger.send(sender, messenger.deserialize(menu.permissionMessage()));
        return false;
    }
}
