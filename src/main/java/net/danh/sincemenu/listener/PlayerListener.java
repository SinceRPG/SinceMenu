package net.danh.sincemenu.listener;

import net.danh.sincemenu.command.OnlinePlayerNames;
import net.danh.sincemenu.manager.MenuManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.jetbrains.annotations.NotNull;

public final class PlayerListener implements Listener {

    private final MenuManager menuManager;
    private final OnlinePlayerNames playerNames;

    public PlayerListener(
            @NotNull MenuManager menuManager,
            @NotNull OnlinePlayerNames playerNames
    ) {
        this.menuManager = menuManager;
        this.playerNames = playerNames;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        playerNames.add(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerNames.remove(event.getPlayer());
        menuManager.cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (menuManager.session(event.getPlayer()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        menuManager.handleSwapClose(event.getPlayer());
    }
}
