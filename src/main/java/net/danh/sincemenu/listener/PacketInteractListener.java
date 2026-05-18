package net.danh.sincemenu.listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import net.danh.sincemenu.manager.MenuManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class PacketInteractListener extends PacketListenerAbstract {

    private final MenuManager menuManager;

    public PacketInteractListener(@NotNull MenuManager menuManager) {
        super(PacketListenerPriority.NORMAL);
        this.menuManager = menuManager;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            handleDigging(event);
            return;
        }
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            handleAttack(event);
            return;
        }
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
        if (!menuManager.ownsEntity(player, packet.getEntityId())) {
            return;
        }
        event.setCancelled(true);
        MenuManager.MenuClickType clickType = clickType(packet);
        menuManager.handleClick(player, packet.getEntityId(), clickType);
    }

    private void handleAttack(@NotNull PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        WrapperPlayClientAttack packet = new WrapperPlayClientAttack(event);
        if (!menuManager.ownsEntity(player, packet.getEntityId())) {
            return;
        }
        event.setCancelled(true);
        menuManager.handleAttackClick(player, packet.getEntityId());
    }

    private void handleDigging(@NotNull PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (menuManager.session(player).isEmpty()) {
            return;
        }
        WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
        DiggingAction action = packet.getAction();
        if (action != DiggingAction.DROP_ITEM && action != DiggingAction.DROP_ITEM_STACK) {
            return;
        }
        event.setCancelled(true);
        menuManager.togglePinned(player);
    }

    private @NotNull MenuManager.MenuClickType clickType(@NotNull WrapperPlayClientInteractEntity packet) {
        boolean sneaking = packet.isSneaking().orElse(false);
        WrapperPlayClientInteractEntity.InteractAction action = packet.getAction();
        if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            return sneaking ? MenuManager.MenuClickType.SHIFT_LEFT : MenuManager.MenuClickType.LEFT;
        }
        return sneaking ? MenuManager.MenuClickType.SHIFT_RIGHT : MenuManager.MenuClickType.RIGHT;
    }
}
