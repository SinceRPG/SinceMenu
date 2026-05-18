package net.danh.sincemenu;

import com.github.retrooper.packetevents.PacketEvents;
import net.danh.sincemenu.api.HoloMenuAPI;
import net.danh.sincemenu.api.registry.ActionRegistry;
import net.danh.sincemenu.api.registry.IconProviderRegistry;
import net.danh.sincemenu.api.registry.RequirementRegistry;
import net.danh.sincemenu.bootstrap.DefaultRegistryInstaller;
import net.danh.sincemenu.bootstrap.ResourceInstaller;
import net.danh.sincemenu.command.SinceMenuCommandRegistrar;
import net.danh.sincemenu.command.CommandMessenger;
import net.danh.sincemenu.command.OnlinePlayerNames;
import net.danh.sincemenu.command.RuntimeMenuCommandRegistry;
import net.danh.sincemenu.listener.PacketInteractListener;
import net.danh.sincemenu.listener.PlayerListener;
import net.danh.sincemenu.manager.MenuManager;
import net.danh.sincemenu.manager.PacketDisplayManager;
import net.danh.sincemenu.placeholder.PlaceholderResolver;
import net.danh.sincemenu.requirement.EconomyRequirement;
import net.danh.sincemenu.requirement.PapiStringRequirement;
import net.danh.sincemenu.util.SchedulerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class SinceMenu extends JavaPlugin {

    private SchedulerAdapter scheduler;
    private MenuManager menuManager;
    private HoloMenuAPI api;
    private PacketInteractListener packetInteractListener;
    private RuntimeMenuCommandRegistry runtimeMenuCommands;
    private OnlinePlayerNames onlinePlayerNames;

    @Override
    public void onEnable() {
        new ResourceInstaller(this).installDefaults();

        ActionRegistry actionRegistry = new ActionRegistry();
        RequirementRegistry requirementRegistry = new RequirementRegistry();
        IconProviderRegistry iconProviderRegistry = new IconProviderRegistry();
        PlaceholderResolver placeholders = new PlaceholderResolver();
        onlinePlayerNames = new OnlinePlayerNames();

        scheduler = new SchedulerAdapter(this);
        menuManager = new MenuManager(this, scheduler, actionRegistry, requirementRegistry);
        CommandMessenger messenger = new CommandMessenger(this, scheduler);
        runtimeMenuCommands = new RuntimeMenuCommandRegistry(this, menuManager, scheduler, messenger, onlinePlayerNames);

        PacketDisplayManager packetDisplayManager = new PacketDisplayManager(
                this,
                requirementRegistry,
                iconProviderRegistry,
                menuManager,
                placeholders
        );
        menuManager.attachDisplayManager(packetDisplayManager);

        new DefaultRegistryInstaller(
                this,
                actionRegistry,
                requirementRegistry,
                iconProviderRegistry,
                menuManager,
                new EconomyRequirement(),
                new PapiStringRequirement(placeholders),
                scheduler
        ).install();

        menuManager.loadMenus();
        onlinePlayerNames.loadCurrentPlayers();
        registerListeners();
        new SinceMenuCommandRegistrar(this, menuManager, scheduler, runtimeMenuCommands, onlinePlayerNames).registerLifecycleHandler();
        runtimeMenuCommands.refresh();

        api = new HoloMenuAPI(actionRegistry, requirementRegistry, iconProviderRegistry, menuManager);
        HoloMenuAPI.install(api);
        getLogger().info("SinceMenu enabled with " + menuManager.menus().size() + " menu(s). Folia=" + scheduler.isFolia());
    }

    @Override
    public void onDisable() {
        uninstallApi();
        shutdownMenus();
        unregisterRuntimeMenuCommands();
        clearPlayerCache();
        unregisterPacketListener();
    }

    private void registerListeners() {
        packetInteractListener = new PacketInteractListener(menuManager);
        PacketEvents.getAPI().getEventManager().registerListener(packetInteractListener);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(menuManager, onlinePlayerNames), this);
    }

    private void uninstallApi() {
        if (api != null) {
            HoloMenuAPI.uninstall(api);
            api = null;
        }
    }

    private void shutdownMenus() {
        if (menuManager != null) {
            menuManager.shutdown();
        }
    }

    private void unregisterPacketListener() {
        if (packetInteractListener == null) {
            return;
        }
        try {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetInteractListener);
        } catch (Throwable ignored) {
        }
        packetInteractListener = null;
    }

    private void unregisterRuntimeMenuCommands() {
        if (runtimeMenuCommands != null) {
            runtimeMenuCommands.unregisterAll(false);
            runtimeMenuCommands = null;
        }
    }

    private void clearPlayerCache() {
        if (onlinePlayerNames != null) {
            onlinePlayerNames.clear();
            onlinePlayerNames = null;
        }
    }
}
