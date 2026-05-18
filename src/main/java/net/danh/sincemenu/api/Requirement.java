package net.danh.sincemenu.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface Requirement {

    boolean test(@NotNull Player player, @NotNull String argument);
}
