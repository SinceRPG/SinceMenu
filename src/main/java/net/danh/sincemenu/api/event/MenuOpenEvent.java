package net.danh.sincemenu.api.event;

import net.danh.sincemenu.manager.MenuManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class MenuOpenEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final MenuManager.MenuDefinition menu;
    private final int page;
    private boolean cancelled;

    public MenuOpenEvent(@NotNull Player player, @NotNull MenuManager.MenuDefinition menu, int page) {
        super(player);
        this.menu = menu;
        this.page = page;
    }

    public @NotNull MenuManager.MenuDefinition menu() {
        return menu;
    }

    public int page() {
        return page;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
