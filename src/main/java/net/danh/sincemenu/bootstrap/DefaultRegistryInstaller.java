package net.danh.sincemenu.bootstrap;

import net.danh.sincemenu.api.registry.ActionRegistry;
import net.danh.sincemenu.api.registry.IconProviderRegistry;
import net.danh.sincemenu.api.registry.RequirementRegistry;
import net.danh.sincemenu.manager.MenuManager;
import net.danh.sincemenu.provider.*;
import net.danh.sincemenu.requirement.EconomyRequirement;
import net.danh.sincemenu.requirement.PapiStringRequirement;
import net.danh.sincemenu.util.SchedulerAdapter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class DefaultRegistryInstaller {

    private final Plugin plugin;
    private final ActionRegistry actions;
    private final RequirementRegistry requirements;
    private final IconProviderRegistry icons;
    private final MenuManager menuManager;
    private final EconomyRequirement economyRequirement;
    private final PapiStringRequirement papiStringRequirement;
    private final SchedulerAdapter scheduler;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public DefaultRegistryInstaller(
            @NotNull Plugin plugin,
            @NotNull ActionRegistry actions,
            @NotNull RequirementRegistry requirements,
            @NotNull IconProviderRegistry icons,
            @NotNull MenuManager menuManager,
            @NotNull EconomyRequirement economyRequirement,
            @NotNull PapiStringRequirement papiStringRequirement,
            @NotNull SchedulerAdapter scheduler
    ) {
        this.plugin = plugin;
        this.actions = actions;
        this.requirements = requirements;
        this.icons = icons;
        this.menuManager = menuManager;
        this.economyRequirement = economyRequirement;
        this.papiStringRequirement = papiStringRequirement;
        this.scheduler = scheduler;
    }

    public void install() {
        installIconProviders();
        installActions();
        installRequirements();
    }

    private void installIconProviders() {
        icons.register(new VanillaIconProvider());
        if (Bukkit.getPluginManager().getPlugin("MMOItems") != null) {
            icons.register(new MMOItemsProvider());
        }
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            icons.register(new ItemsAdderProvider());
        }
        if (Bukkit.getPluginManager().getPlugin("Nexo") != null) {
            icons.register(new NexoProvider());
        }
        if (Bukkit.getPluginManager().getPlugin("MythicCrucible") != null) {
            icons.register(new MythicCrucibleProvider());
        }
    }

    private void installActions() {
        actions.register("close", context -> menuManager.backMenu(context.player(), context.session()));
        actions.register("close_all", context -> menuManager.closeMenu(context.player()));
        actions.register("next_page", context -> menuManager.nextPage(context.player(), context.session()));
        actions.register("previous_page", context -> menuManager.previousPage(context.player(), context.session()));
        actions.register("scroll_up", context -> menuManager.scrollUp(context.player(), context.session()));
        actions.register("scroll_down", context -> menuManager.scrollDown(context.player(), context.session()));
        actions.register("open_menu", context -> menuManager.openSubMenu(context.player(), context.argument()));
        actions.register("back_menu", context -> menuManager.backMenu(context.player(), context.session()));
        actions.register("message", context -> context.player().sendMessage(miniMessage.deserialize(context.argument())));
        actions.register("command", context -> {
            String command = context.argument().replace("%player_name%", context.player().getName());
            scheduler.runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        });
        actions.register("player_command", context -> context.player().performCommand(context.argument()));
    }

    private void installRequirements() {
        requirements.register("permission", (player, argument) -> player.hasPermission(argument.trim()));
        requirements.register("money", economyRequirement::has);
        requirements.register("papi", papiStringRequirement::test);
        plugin.getLogger().fine("Default SinceMenu registries installed.");
    }
}
