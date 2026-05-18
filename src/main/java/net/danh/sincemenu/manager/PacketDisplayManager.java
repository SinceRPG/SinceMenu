package net.danh.sincemenu.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.danh.sincemenu.SinceMenu;
import net.danh.sincemenu.api.registry.IconProviderRegistry;
import net.danh.sincemenu.api.registry.RequirementRegistry;
import net.danh.sincemenu.placeholder.PlaceholderResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class PacketDisplayManager {

    private static final int DISPLAY_BILLBOARD_INDEX = 15;
    private static final int TEXT_OR_ITEM_INDEX = 23;
    private static final int TEXT_BACKGROUND_INDEX = 25;
    private static final int INTERACTION_WIDTH_INDEX = 8;
    private static final int INTERACTION_HEIGHT_INDEX = 9;
    private static final int INTERACTION_RESPONSE_INDEX = 10;

    private final SinceMenu plugin;
    private final RequirementRegistry requirementRegistry;
    private final IconProviderRegistry iconProviderRegistry;
    private final MenuManager menuManager;
    private final PlaceholderResolver placeholders;
    private final AtomicInteger entityIds;

    public PacketDisplayManager(
            @NotNull SinceMenu plugin,
            @NotNull RequirementRegistry requirementRegistry,
            @NotNull IconProviderRegistry iconProviderRegistry,
            @NotNull MenuManager menuManager,
            @NotNull PlaceholderResolver placeholders
    ) {
        this.plugin = plugin;
        this.requirementRegistry = requirementRegistry;
        this.iconProviderRegistry = iconProviderRegistry;
        this.menuManager = menuManager;
        this.placeholders = placeholders;
        this.entityIds = new AtomicInteger(plugin.getConfig().getInt("entity-id-start", 9_000_000));
    }

    public void spawnSession(@NotNull Player player, @NotNull MenuManager.MenuSession session) {
        session.updateAnchor(player.getEyeLocation());
        for (MenuManager.MenuItem item : session.menu().pageItems(session.page())) {
            if (!requirementRegistry.testAll(player, item.viewRequirements())) {
                continue;
            }
            spawnItem(player, session, item);
        }
    }

    public void refreshSession(@NotNull Player player, @NotNull MenuManager.MenuSession session) {
        if (!player.isOnline()) {
            return;
        }
        for (MenuManager.MenuItem item : session.menu().pageItems(session.page())) {
            MenuManager.RenderedItem renderedItem = session.renderedItem(item.id());
            boolean visible = requirementRegistry.testAll(player, item.viewRequirements());
            if (!visible) {
                if (renderedItem != null) {
                    destroyItem(player, session, renderedItem);
                }
                continue;
            }
            if (renderedItem == null) {
                spawnItem(player, session, item);
                continue;
            }
            send(player, metadataPacket(player, renderedItem.displayEntityId(), item));
        }
    }

    public void moveSession(@NotNull Player player, @NotNull MenuManager.MenuSession session) {
        if (!player.isOnline()) {
            return;
        }
        Location eye = player.getEyeLocation();
        if (!session.shouldMove(eye)) {
            return;
        }
        session.updateAnchor(eye);
        for (MenuManager.RenderedItem renderedItem : List.copyOf(session.renderedItems().values())) {
            Location position = calculatePosition(player, renderedItem.item());
            Location hitboxPosition = calculateHitboxPosition(player, renderedItem.item(), session.menu());
            send(player, new WrapperPlayServerEntityTeleport(
                    renderedItem.displayEntityId(),
                    new Vector3d(position.x(), position.y(), position.z()),
                    position.getYaw(),
                    position.getPitch(),
                    player.isOnGround()
            ));
            send(player, new WrapperPlayServerEntityTeleport(
                    renderedItem.interactionEntityId(),
                    new Vector3d(hitboxPosition.x(), hitboxPosition.y(), hitboxPosition.z()),
                    hitboxPosition.getYaw(),
                    hitboxPosition.getPitch(),
                    player.isOnGround()
            ));
        }
    }

    public void destroySession(@NotNull Player player, @NotNull MenuManager.MenuSession session) {
        for (MenuManager.RenderedItem renderedItem : List.copyOf(session.renderedItems().values())) {
            send(player, new WrapperPlayServerDestroyEntities(renderedItem.displayEntityId(), renderedItem.interactionEntityId()));
        }
        session.clearBindings();
    }

    public boolean owns(@NotNull Player player, int entityId) {
        return menuManager.session(player)
                .map(session -> session.entityItems().containsKey(entityId))
                .orElse(false);
    }

    private void spawnItem(
            @NotNull Player player,
            @NotNull MenuManager.MenuSession session,
            @NotNull MenuManager.MenuItem item
    ) {
        int entityId = nextEntityId();
        int interactionEntityId = nextEntityId();
        Location position = calculatePosition(player, item);
        Location hitboxPosition = calculateHitboxPosition(player, item, session.menu());
        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(UUID.randomUUID()),
                item.type() == MenuManager.DisplayType.ITEM ? EntityTypes.ITEM_DISPLAY : EntityTypes.TEXT_DISPLAY,
                new Vector3d(position.x(), position.y(), position.z()),
                position.getPitch(),
                position.getYaw(),
                position.getYaw(),
                0,
                Optional.of(new Vector3d(0.0D, 0.0D, 0.0D))
        );
        WrapperPlayServerSpawnEntity interaction = new WrapperPlayServerSpawnEntity(
                interactionEntityId,
                Optional.of(UUID.randomUUID()),
                EntityTypes.INTERACTION,
                new Vector3d(hitboxPosition.x(), hitboxPosition.y(), hitboxPosition.z()),
                hitboxPosition.getPitch(),
                hitboxPosition.getYaw(),
                hitboxPosition.getYaw(),
                0,
                Optional.of(new Vector3d(0.0D, 0.0D, 0.0D))
        );
        send(player, spawn);
        send(player, metadataPacket(player, entityId, item));
        send(player, interaction);
        send(player, interactionMetadataPacket(interactionEntityId, session.menu(), item));
        session.bind(new MenuManager.RenderedItem(entityId, interactionEntityId, item));
    }

    private void destroyItem(
            @NotNull Player player,
            @NotNull MenuManager.MenuSession session,
            @NotNull MenuManager.RenderedItem renderedItem
    ) {
        send(player, new WrapperPlayServerDestroyEntities(renderedItem.displayEntityId(), renderedItem.interactionEntityId()));
        session.unbind(renderedItem);
    }

    private @NotNull WrapperPlayServerEntityMetadata metadataPacket(
            @NotNull Player player,
            int entityId,
            @NotNull MenuManager.MenuItem item
    ) {
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(DISPLAY_BILLBOARD_INDEX, EntityDataTypes.BYTE, billboard(item.billboard())));
        if (item.type() == MenuManager.DisplayType.ITEM) {
            ItemStack icon = iconProviderRegistry.resolveOrFallback(player, item.icon(), item.fallbackIcon());
            metadata.add(new EntityData<>(
                    TEXT_OR_ITEM_INDEX,
                    EntityDataTypes.ITEMSTACK,
                    SpigotConversionUtil.fromBukkitItemStack(icon)
            ));
        } else {
            Component component = menuManager.miniMessage().deserialize(placeholders.resolve(player, item.text()));
            metadata.add(new EntityData<>(TEXT_OR_ITEM_INDEX, EntityDataTypes.ADV_COMPONENT, component));
            metadata.add(new EntityData<>(TEXT_BACKGROUND_INDEX, EntityDataTypes.INT, item.backgroundColor()));
        }
        return new WrapperPlayServerEntityMetadata(entityId, metadata);
    }

    private @NotNull WrapperPlayServerEntityMetadata interactionMetadataPacket(
            int entityId,
            @NotNull MenuManager.MenuDefinition menu,
            @NotNull MenuManager.MenuItem item
    ) {
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(INTERACTION_WIDTH_INDEX, EntityDataTypes.FLOAT, (float) hitboxWidth(menu, item)));
        metadata.add(new EntityData<>(INTERACTION_HEIGHT_INDEX, EntityDataTypes.FLOAT, (float) hitboxHeight(menu, item)));
        metadata.add(new EntityData<>(INTERACTION_RESPONSE_INDEX, EntityDataTypes.BOOLEAN, true));
        return new WrapperPlayServerEntityMetadata(entityId, metadata);
    }

    private @NotNull Location calculatePosition(@NotNull Player player, @NotNull MenuManager.MenuItem item) {
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize().multiply(-1.0D);
        if (!isFinite(right)) {
            right = new Vector(1, 0, 0);
        }
        Vector up = right.clone().crossProduct(forward).normalize();
        Vector offset = right.multiply(item.offsetX())
                .add(up.multiply(item.offsetY()))
                .add(forward.multiply(item.offsetZ()));
        return eye.add(offset);
    }

    private @NotNull Location calculateHitboxPosition(
            @NotNull Player player,
            @NotNull MenuManager.MenuItem item,
            @NotNull MenuManager.MenuDefinition menu
    ) {
        Location position = calculatePosition(player, item);
        position.subtract(0.0D, hitboxHeight(menu, item) * 0.5D, 0.0D);
        return position;
    }

    private double hitboxWidth(@NotNull MenuManager.MenuDefinition menu, @NotNull MenuManager.MenuItem item) {
        return item.hitboxWidth() > 0.0D ? item.hitboxWidth() : menu.hitboxWidth();
    }

    private double hitboxHeight(@NotNull MenuManager.MenuDefinition menu, @NotNull MenuManager.MenuItem item) {
        return item.hitboxHeight() > 0.0D ? item.hitboxHeight() : menu.hitboxHeight();
    }

    private boolean isFinite(@NotNull Vector vector) {
        return Double.isFinite(vector.getX()) && Double.isFinite(vector.getY()) && Double.isFinite(vector.getZ());
    }

    private byte billboard(@NotNull String raw) {
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "FIXED" -> 0;
            case "VERTICAL" -> 1;
            case "HORIZONTAL" -> 2;
            default -> 3;
        };
    }

    private int nextEntityId() {
        return entityIds.getAndUpdate(current -> current == Integer.MAX_VALUE ? plugin.getConfig().getInt("entity-id-start", 9_000_000) : current + 1);
    }

    private void send(@NotNull Player player, @NotNull PacketWrapper<?> packet) {
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Throwable ex) {
            plugin.getLogger().fine("Failed to send packet to " + player.getName() + ": " + ex.getMessage());
        }
    }

}
