package net.danh.sincemenu.provider;

import net.danh.sincemenu.api.IconProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public final class MMOItemsProvider implements IconProvider {

    @Override
    public @NotNull String prefix() {
        return "mmoitems";
    }

    @Override
    public @Nullable ItemStack resolve(@NotNull Player viewer, @NotNull String icon) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            return null;
        }
        String[] parts = icon.split(":", 3);
        if (parts.length != 3) {
            return null;
        }
        try {
            Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Object plugin = invokeStatic(mmoItemsClass, "plugin");
            if (plugin == null) {
                Plugin bukkitPlugin = Bukkit.getPluginManager().getPlugin("MMOItems");
                plugin = bukkitPlugin;
            }
            Method getItem = findMethod(plugin.getClass(), "getItem", String.class, String.class);
            Object result = getItem.invoke(plugin, parts[1], parts[2]);
            return result instanceof ItemStack itemStack ? itemStack : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Object invokeStatic(@NotNull Class<?> owner, @NotNull String name) {
        try {
            Method method = owner.getMethod(name);
            return method.invoke(null);
        } catch (Throwable ignored) {
            try {
                return owner.getField(name).get(null);
            } catch (Throwable ignoredAgain) {
                return null;
            }
        }
    }

    private static @NotNull Method findMethod(@NotNull Class<?> owner, @NotNull String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Class<?> current = owner;
        while (current != null) {
            try {
                Method method = current.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }
}
