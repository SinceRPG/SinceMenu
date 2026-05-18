package net.danh.sincemenu.provider;

import net.danh.sincemenu.api.IconProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public final class NexoProvider implements IconProvider {

    @Override
    public @NotNull String prefix() {
        return "nexo";
    }

    @Override
    public @Nullable ItemStack resolve(@NotNull Player viewer, @NotNull String icon) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Nexo")) {
            return null;
        }
        String[] parts = icon.split(":", 2);
        if (parts.length != 2 || parts[1].isBlank()) {
            return null;
        }
        try {
            Class<?> nexoItems = Class.forName("com.nexomc.nexo.api.NexoItems");
            Method itemFromId = nexoItems.getMethod("itemFromId", String.class);
            Object itemBuilder = itemFromId.invoke(null, parts[1]);
            if (itemBuilder == null) {
                return null;
            }
            Method build = itemBuilder.getClass().getMethod("build");
            Object result = build.invoke(itemBuilder);
            return result instanceof ItemStack itemStack ? itemStack : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
