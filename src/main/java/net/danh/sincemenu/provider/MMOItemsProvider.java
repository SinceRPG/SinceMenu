package net.danh.sincemenu.provider;

import net.danh.sincemenu.api.IconProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public final class MMOItemsProvider implements IconProvider {

    private Object pluginInstance;
    private Method getItemMethod;
    private boolean initialized;

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
            if (!initialized) {
                Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
                pluginInstance = invokeStatic(mmoItemsClass, "plugin");
                if (pluginInstance == null) {
                    pluginInstance = Bukkit.getPluginManager().getPlugin("MMOItems");
                }
                getItemMethod = findMethod(pluginInstance.getClass(), "getItem", String.class, String.class);
                initialized = true;
            }
            if (getItemMethod == null || pluginInstance == null) return null;
            Object result = getItemMethod.invoke(pluginInstance, parts[1], parts[2]);
            return result instanceof ItemStack itemStack ? itemStack : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}