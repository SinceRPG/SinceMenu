package net.danh.sincemenu.provider;

import net.danh.sincemenu.api.IconProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;

public final class MythicCrucibleProvider implements IconProvider {

    @Override
    public @NotNull String prefix() {
        return "mythiccrucible";
    }

    @Override
    public @Nullable ItemStack resolve(@NotNull Player viewer, @NotNull String icon) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicCrucible")) {
            return null;
        }
        String[] parts = icon.split(":", 2);
        if (parts.length != 2 || parts[1].isBlank()) {
            return null;
        }
        return resolveModern(parts[1], 1);
    }

    private @Nullable ItemStack resolveModern(@NotNull String itemId, int amount) {
        try {
            Class<?> mythicBukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Method inst = mythicBukkit.getMethod("inst");
            Object plugin = inst.invoke(null);
            Object manager = plugin.getClass().getMethod("getItemManager").invoke(plugin);
            ItemStack direct = invokeItemStack(manager, itemId);
            if (direct != null) {
                return direct;
            }
            Object mythicItem = unwrapOptional(manager.getClass().getMethod("getItem", String.class).invoke(manager, itemId));
            return mythicItem == null ? null : buildItemStack(mythicItem, amount);
        } catch (Throwable ignored) {
            return resolveLegacy(itemId);
        }
    }

    private @Nullable ItemStack resolveLegacy(@NotNull String itemId) {
        try {
            Plugin mythicMobs = Bukkit.getPluginManager().getPlugin("MythicMobs");
            if (mythicMobs == null) {
                return null;
            }
            Object manager = mythicMobs.getClass().getMethod("getItemManager").invoke(mythicMobs);
            ItemStack direct = invokeItemStack(manager, itemId);
            if (direct != null) {
                return direct;
            }
            Object mythicItem = unwrapOptional(manager.getClass().getMethod("getItem", String.class).invoke(manager, itemId));
            return mythicItem == null ? null : buildItemStack(mythicItem, 1);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private @Nullable ItemStack invokeItemStack(@NotNull Object manager, @NotNull String itemId) {
        try {
            Method getItemStack = manager.getClass().getMethod("getItemStack", String.class);
            Object result = getItemStack.invoke(manager, itemId);
            return result instanceof ItemStack itemStack ? itemStack : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private @Nullable ItemStack buildItemStack(@NotNull Object mythicItem, int amount) {
        try {
            Object abstractItem = mythicItem.getClass().getMethod("generateItemStack", int.class).invoke(mythicItem, amount);
            if (abstractItem instanceof ItemStack itemStack) {
                return itemStack;
            }
            for (String methodName : new String[]{"getBukkitStack", "toBukkitItemStack", "build"}) {
                try {
                    Object result = abstractItem.getClass().getMethod(methodName).invoke(abstractItem);
                    if (result instanceof ItemStack itemStack) {
                        return itemStack;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private @Nullable Object unwrapOptional(@Nullable Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return value;
    }
}
