package net.danh.sincemenu.api.registry;

import net.danh.sincemenu.api.Requirement;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RequirementRegistry {

    private final Map<String, Requirement> requirements = new ConcurrentHashMap<>();

    private static @NotNull String normalize(@NotNull String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }

    public void register(@NotNull String key, @NotNull Requirement requirement) {
        requirements.put(normalize(key), requirement);
    }

    public void unregister(@NotNull String key) {
        requirements.remove(normalize(key));
    }

    public @Nullable Requirement get(@NotNull String key) {
        return requirements.get(normalize(key));
    }

    public boolean test(@NotNull Player player, @NotNull String raw) {
        int split = raw.indexOf(':');
        String key = split == -1 ? raw : raw.substring(0, split);
        String argument = split == -1 ? "" : raw.substring(split + 1);
        Requirement requirement = get(key);
        return requirement != null && requirement.test(player, argument);
    }

    public boolean testAll(@NotNull Player player, @NotNull Iterable<String> rawRequirements) {
        for (String raw : rawRequirements) {
            if (raw != null && !raw.isBlank() && !test(player, raw)) {
                return false;
            }
        }
        return true;
    }

    public @NotNull Map<String, Requirement> snapshot() {
        return Collections.unmodifiableMap(Map.copyOf(requirements));
    }
}
