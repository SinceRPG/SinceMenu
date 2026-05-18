package net.danh.sincemenu.api.registry;

import net.danh.sincemenu.api.IconProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IconProviderRegistry {

    private final Map<String, IconProvider> providers = new ConcurrentHashMap<>();

    public void register(@NotNull IconProvider provider) {
        providers.put(normalize(provider.prefix()), provider);
    }

    public void unregister(@NotNull String prefix) {
        providers.remove(normalize(prefix));
    }

    public @Nullable IconProvider get(@NotNull String prefix) {
        return providers.get(normalize(prefix));
    }

    public @NotNull ItemStack resolveOrFallback(@NotNull Player viewer, @NotNull String icon, @Nullable String fallback) {
        ItemStack resolved = resolve(viewer, icon);
        if (isUsable(resolved)) {
            return resolved;
        }
        if (fallback != null && !fallback.isBlank()) {
            resolved = resolve(viewer, fallback);
            if (isUsable(resolved)) {
                return resolved;
            }
        }
        return new ItemStack(Material.BARRIER);
    }

    public @Nullable ItemStack resolve(@NotNull Player viewer, @NotNull String icon) {
        int split = icon.indexOf(':');
        if (split <= 0) {
            return null;
        }
        IconProvider provider = providers.get(normalize(icon.substring(0, split)));
        if (provider == null) {
            return null;
        }
        try {
            ItemStack item = provider.resolve(viewer, icon);
            return isUsable(item) ? item.clone() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public @NotNull Map<String, IconProvider> snapshot() {
        return Collections.unmodifiableMap(Map.copyOf(providers));
    }

    private static boolean isUsable(@Nullable ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getType().isItem();
    }

    private static @NotNull String normalize(@NotNull String prefix) {
        String clean = prefix.trim().toLowerCase(Locale.ROOT);
        return clean.endsWith(":") ? clean.substring(0, clean.length() - 1) : clean;
    }
}
