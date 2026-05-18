package net.danh.sincemenu.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IconProvider {

    @NotNull String prefix();

    @Nullable ItemStack resolve(@NotNull Player viewer, @NotNull String icon);
}
