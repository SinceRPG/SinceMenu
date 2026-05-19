package net.danh.sincemenu.api.event;

import net.danh.sincemenu.manager.MenuManager;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class MenuCloseEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final MenuManager.MenuSession session;

    public MenuCloseEvent(@NotNull Player player, @NotNull MenuManager.MenuSession session) {
        super(player);
        this.session = session;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

    public @NotNull MenuManager.MenuSession session() {
        return session;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
