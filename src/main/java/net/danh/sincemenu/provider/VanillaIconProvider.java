package net.danh.sincemenu.provider;

import net.danh.sincemenu.api.IconProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class VanillaIconProvider implements IconProvider {

    @Override
    public @NotNull String prefix() {
        return "minecraft";
    }

    @Override
    public @Nullable ItemStack resolve(@NotNull Player viewer, @NotNull String icon) {
        String key = icon.substring("minecraft:".length()).trim().toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(key);
        if (material == null || !material.isItem()) {
            return null;
        }
        return new ItemStack(material);
    }
}
