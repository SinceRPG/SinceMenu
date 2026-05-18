package net.danh.sincemenu.api.event;

import net.danh.sincemenu.manager.MenuManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class MenuItemClickEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final MenuManager.MenuSession session;
    private final MenuManager.MenuItem item;
    private final MenuManager.MenuClickType clickType;
    private boolean cancelled;

    public MenuItemClickEvent(
            @NotNull Player player,
            @NotNull MenuManager.MenuSession session,
            @NotNull MenuManager.MenuItem item,
            @NotNull MenuManager.MenuClickType clickType
    ) {
        super(player);
        this.session = session;
        this.item = item;
        this.clickType = clickType;
    }

    public @NotNull MenuManager.MenuSession session() {
        return session;
    }

    public @NotNull MenuManager.MenuItem item() {
        return item;
    }

    public @NotNull MenuManager.MenuClickType clickType() {
        return clickType;
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
