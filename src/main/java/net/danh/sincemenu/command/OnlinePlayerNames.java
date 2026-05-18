package net.danh.sincemenu.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class OnlinePlayerNames {

    private final Set<String> names = ConcurrentHashMap.newKeySet();

    public void loadCurrentPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            add(player);
        }
    }

    public void add(@NotNull Player player) {
        names.add(player.getName());
    }

    public void remove(@NotNull Player player) {
        names.remove(player.getName());
    }

    public @NotNull Collection<String> suggest(@NotNull String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return names.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public void clear() {
        names.clear();
    }
}
