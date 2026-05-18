package net.danh.sincemenu.api;

import net.danh.sincemenu.manager.MenuManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface Action {

    void execute(@NotNull Context context);

    record Context(
            @NotNull Player player,
            @NotNull MenuManager.MenuSession session,
            @NotNull MenuManager.MenuItem item,
            @NotNull String argument,
            @NotNull MenuManager menuManager
    ) {
    }
}
