package net.danh.sincemenu.provider;

import net.danh.sincemenu.api.IconProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public final class ItemsAdderProvider implements IconProvider {

    private Method getInstanceMethod;
    private Method getItemStackMethod;
    private boolean initialized;

    @Override
    public @NotNull String prefix() {
        return "itemsadder";
    }

    @Override
    public @Nullable ItemStack resolve(@NotNull Player viewer, @NotNull String icon) {
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return null;
        }
        String[] parts = icon.split(":", 3);
        if (parts.length != 3) {
            return null;
        }
        String namespacedId = parts[1] + ":" + parts[2];
        try {
            if (!initialized) {
                Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
                getInstanceMethod = customStackClass.getMethod("getInstance", String.class);
                getItemStackMethod = customStackClass.getMethod("getItemStack");
                initialized = true;
            }
            if (getInstanceMethod == null || getItemStackMethod == null) return null;
            Object customStack = getInstanceMethod.invoke(null, namespacedId);
            if (customStack == null) return null;
            Object result = getItemStackMethod.invoke(customStack);
            return result instanceof ItemStack itemStack ? itemStack : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}