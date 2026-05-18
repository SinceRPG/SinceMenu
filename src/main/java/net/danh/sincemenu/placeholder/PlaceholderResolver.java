package net.danh.sincemenu.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public final class PlaceholderResolver {

    public @NotNull String resolve(@NotNull Player player, @NotNull String text) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method setPlaceholders = papi.getMethod("setPlaceholders", Player.class, String.class);
            Object result = setPlaceholders.invoke(null, player, text);
            return result instanceof String value ? value : text;
        } catch (Throwable ignored) {
            return text;
        }
    }
}
