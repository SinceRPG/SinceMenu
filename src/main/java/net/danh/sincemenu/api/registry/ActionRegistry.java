package net.danh.sincemenu.api.registry;

import net.danh.sincemenu.api.Action;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ActionRegistry {

    private final Map<String, Action> actions = new ConcurrentHashMap<>();

    public void register(@NotNull String key, @NotNull Action action) {
        actions.put(normalize(key), action);
    }

    public void unregister(@NotNull String key) {
        actions.remove(normalize(key));
    }

    public @Nullable Action get(@NotNull String key) {
        return actions.get(normalize(key));
    }

    public @NotNull Map<String, Action> snapshot() {
        return Collections.unmodifiableMap(Map.copyOf(actions));
    }

    private static @NotNull String normalize(@NotNull String key) {
        String clean = key.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("[") && clean.endsWith("]")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return clean;
    }
}
