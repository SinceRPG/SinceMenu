package net.danh.sincemenu.api;

import net.danh.sincemenu.api.registry.ActionRegistry;
import net.danh.sincemenu.api.registry.IconProviderRegistry;
import net.danh.sincemenu.api.registry.RequirementRegistry;
import net.danh.sincemenu.manager.MenuManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class HoloMenuAPI {

    private static volatile HoloMenuAPI instance;

    private final ActionRegistry actionRegistry;
    private final RequirementRegistry requirementRegistry;
    private final IconProviderRegistry iconProviderRegistry;
    private final MenuManager menuManager;

    public HoloMenuAPI(
            @NotNull ActionRegistry actionRegistry,
            @NotNull RequirementRegistry requirementRegistry,
            @NotNull IconProviderRegistry iconProviderRegistry,
            @NotNull MenuManager menuManager
    ) {
        this.actionRegistry = actionRegistry;
        this.requirementRegistry = requirementRegistry;
        this.iconProviderRegistry = iconProviderRegistry;
        this.menuManager = menuManager;
    }

    public static void install(@NotNull HoloMenuAPI api) {
        instance = Objects.requireNonNull(api, "api");
    }

    public static void uninstall(@NotNull HoloMenuAPI api) {
        if (instance == api) {
            instance = null;
        }
    }

    public static @NotNull HoloMenuAPI get() {
        HoloMenuAPI api = instance;
        if (api == null) {
            throw new IllegalStateException("SinceMenu API is not available");
        }
        return api;
    }

    public @NotNull ActionRegistry actions() {
        return actionRegistry;
    }

    public @NotNull RequirementRegistry requirements() {
        return requirementRegistry;
    }

    public @NotNull IconProviderRegistry icons() {
        return iconProviderRegistry;
    }

    public boolean openMenu(@NotNull Player player, @NotNull String menuId) {
        return menuManager.openMenu(player, menuId, 1);
    }

    public boolean openMenu(@NotNull Player player, @NotNull String menuId, int page) {
        return menuManager.openMenu(player, menuId, page);
    }

    public void closeMenu(@NotNull Player player) {
        menuManager.closeMenu(player);
    }
}
