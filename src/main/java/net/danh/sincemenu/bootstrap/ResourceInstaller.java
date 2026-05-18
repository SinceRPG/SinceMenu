package net.danh.sincemenu.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class ResourceInstaller {

    private final JavaPlugin plugin;

    public ResourceInstaller(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void installDefaults() {
        plugin.saveDefaultConfig();
        File menus = new File(plugin.getDataFolder(), "menus");
        if (!menus.exists() && !menus.mkdirs()) {
            plugin.getLogger().warning("Could not create menus directory.");
        }
        File advanced = new File(menus, "advanced_menu.yml");
        if (!advanced.exists()) {
            plugin.saveResource("menus/advanced_menu.yml", false);
        }
    }
}
