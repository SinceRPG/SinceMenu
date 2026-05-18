package net.danh.sincemenu.command;

import net.danh.sincemenu.util.SchedulerAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class CommandMessenger {

    private final JavaPlugin plugin;
    private final SchedulerAdapter scheduler;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public CommandMessenger(@NotNull JavaPlugin plugin, @NotNull SchedulerAdapter scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    public void send(@NotNull CommandSender sender, @NotNull Component message) {
        if (sender instanceof Player player) {
            scheduler.runAtEntity(player, () -> {
                if (player.isOnline()) {
                    player.sendMessage(message);
                }
            });
            return;
        }
        scheduler.runGlobal(() -> sender.sendMessage(message));
    }

    public @NotNull Component configMessage(@NotNull String path, @NotNull String fallback) {
        String raw = plugin.getConfig().getString(path, fallback);
        return miniMessage.deserialize(raw == null ? fallback : raw);
    }

    public @NotNull Component deserialize(@NotNull String message) {
        return miniMessage.deserialize(message);
    }
}
