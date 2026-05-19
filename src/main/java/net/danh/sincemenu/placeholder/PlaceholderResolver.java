package net.danh.sincemenu.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public final class PlaceholderResolver {

    private Method setPlaceholdersMethod;
    private boolean initialized = false;

    public @NotNull String resolve(@NotNull Player player, @NotNull String text) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        if (!initialized) {
            try {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                setPlaceholdersMethod = papi.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            } catch (Throwable ignored) {
            }
            initialized = true;
        }
        if (setPlaceholdersMethod != null) {
            try {
                Object result = setPlaceholdersMethod.invoke(null, player, text);
                return result instanceof String value ? value : text;
            } catch (Throwable ignored) {
            }
        }
        return text;
    }
}