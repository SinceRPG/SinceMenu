package net.danh.sincemenu.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.danh.sincemenu.manager.MenuManager;
import net.danh.sincemenu.util.SchedulerAdapter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SinceMenuCommandRegistrar {

    private final JavaPlugin plugin;
    private final SinceMenuCommand command;

    public SinceMenuCommandRegistrar(
            @NotNull JavaPlugin plugin,
            @NotNull MenuManager menuManager,
            @NotNull SchedulerAdapter scheduler,
            @NotNull RuntimeMenuCommandRegistry runtimeMenuCommands,
            @NotNull OnlinePlayerNames playerNames
    ) {
        this.plugin = plugin;
        CommandMessenger messenger = new CommandMessenger(plugin, scheduler);
        this.command = new SinceMenuCommand(plugin, menuManager, scheduler, messenger, runtimeMenuCommands, playerNames);
    }

    public void registerLifecycleHandler() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::register);
    }

    private void register(@NotNull ReloadableRegistrarEvent<Commands> event) {
        LiteralCommandNode<CommandSourceStack> node = command.createNode();
        String description = plugin.getConfig().getString("command-descriptions.admin", "SinceMenu administration command.");
        event.registrar().register(node, description, List.of("smenu"));
    }
}
