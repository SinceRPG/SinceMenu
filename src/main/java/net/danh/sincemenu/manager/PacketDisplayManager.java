package net.danh.sincemenu.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
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
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class PacketDisplayManager {

    private static final int DISPLAY_BILLBOARD_INDEX = 15;
    private static final int DISPLAY_SCALE_INDEX = 12;
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
    private final Map<UUID, HoverDisplay> hoverDisplays = new ConcurrentHashMap<>();

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
        if (!session.hasAnchor()) {
            session.updateAnchor(player.getEyeLocation());
        }
        List<MenuManager.MenuLayer> layers = session.layers();
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            MenuManager.MenuLayer layer = layers.get(layerIndex);
            for (MenuManager.MenuItem item : layer.menu().visibleItems(layer)) {
                if (!requirementRegistry.testAll(player, item.viewRequirements())) {
                    continue;
                }
                spawnItem(player, session, layerIndex, item);
            }
        }
    }

    public void refreshSession(@NotNull Player player, @NotNull MenuManager.MenuSession session) {
        if (!player.isOnline()) {
            return;
        }
        List<MenuManager.MenuLayer> layers = session.layers();
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            MenuManager.MenuLayer layer = layers.get(layerIndex);
            for (MenuManager.MenuItem item : layer.menu().visibleItems(layer)) {
                MenuManager.RenderedItem renderedItem = session.renderedItem(layerIndex, item.id());
                boolean visible = requirementRegistry.testAll(player, item.viewRequirements());
                if (!visible) {
                    if (renderedItem != null) {
                        destroyItem(player, session, renderedItem);
                    }
                    continue;
                }
                if (renderedItem == null) {
                    spawnItem(player, session, layerIndex, item);
                    continue;
                }
                send(player, metadataPacket(player, renderedItem.displayEntityId(), layer.menu(), item));
            }
        }
    }

    public void moveSession(@NotNull Player player, @NotNull MenuManager.MenuSession session) {
        moveSession(player, session, false);
    }

    public void moveSession(@NotNull Player player, @NotNull MenuManager.MenuSession session, boolean force) {
        if (!player.isOnline()) {
            return;
        }
        Location eye = player.getEyeLocation();
        if (!force && !session.shouldMove(eye)) {
            return;
        }
        if (!force) {
            session.updateAnchor(eye);
        }
        for (MenuManager.RenderedItem renderedItem : List.copyOf(session.renderedItems().values())) {
            MenuManager.MenuLayer layer = session.layers().get(renderedItem.layerIndex());
            Location position = calculatePosition(player, session, layer, renderedItem.layerIndex(), renderedItem.item());
            Location hitboxPosition = calculateHitboxPosition(player, session, layer, renderedItem.layerIndex(), renderedItem.item());
            send(player, new WrapperPlayServerEntityTeleport(
                    renderedItem.displayEntityId(),
                    new Vector3d(position.x(), position.y(), position.z()),
                    position.getYaw(),
                    position.getPitch(),
                    false
            ));
            send(player, new WrapperPlayServerEntityTeleport(
                    renderedItem.interactionEntityId(),
                    new Vector3d(hitboxPosition.x(), hitboxPosition.y(), hitboxPosition.z()),
                    hitboxPosition.getYaw(),
                    hitboxPosition.getPitch(),
                    false
            ));
        }
    }

    public void destroySession(@NotNull Player player, @NotNull MenuManager.MenuSession session) {
        for (MenuManager.RenderedItem renderedItem : List.copyOf(session.renderedItems().values())) {
            send(player, new WrapperPlayServerDestroyEntities(renderedItem.displayEntityId(), renderedItem.interactionEntityId()));
        }
        destroyHover(player.getUniqueId(), player);
        session.clearBindings();
    }

    public void clearSessionState(@NotNull UUID playerId, @NotNull MenuManager.MenuSession session) {
        hoverDisplays.remove(playerId);
        session.clearBindings();
    }

    public void respawnSession(@NotNull Player player, @NotNull MenuManager.MenuSession session) {
        destroySession(player, session);
        spawnSession(player, session);
    }

    public boolean owns(@NotNull Player player, int entityId) {
        return menuManager.session(player)
                .map(session -> session.entityItems().containsKey(entityId))
                .orElse(false);
    }

    public void syncHeldSlot(@NotNull Player player, @NotNull MenuManager.MenuSession session) {
        send(player, new WrapperPlayServerHeldItemChange(session.lastHeldSlot()));
    }

    public void updateHover(@NotNull Player player, @NotNull MenuManager.MenuSession session) {
        HoverTarget target = hoverTarget(player, session);
        if (target == null || target.item().lore().isEmpty()) {
            destroyHover(player);
            return;
        }
        String key = target.layerIndex() + ":" + target.item().id();
        HoverDisplay current = hoverDisplays.get(player.getUniqueId());
        Location position = calculateLorePosition(player, session, target.layer(), target.layerIndex(), target.item());
        if (current != null && current.key().equals(key)) {
            send(player, loreMetadataPacket(player, current.entityId(), target.layer().menu(), target.item()));
            send(player, new WrapperPlayServerEntityTeleport(
                    current.entityId(),
                    new Vector3d(position.x(), position.y(), position.z()),
                    position.getYaw(),
                    position.getPitch(),
                    false
            ));
            return;
        }
        destroyHover(player);
        int entityId = nextEntityId();
        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(UUID.randomUUID()),
                EntityTypes.TEXT_DISPLAY,
                new Vector3d(position.x(), position.y(), position.z()),
                position.getPitch(),
                position.getYaw(),
                position.getYaw(),
                0,
                Optional.of(new Vector3d(0.0D, 0.0D, 0.0D))
        );
        send(player, spawn);
        send(player, loreMetadataPacket(player, entityId, target.layer().menu(), target.item()));
        hoverDisplays.put(player.getUniqueId(), new HoverDisplay(entityId, key));
    }

    private void spawnItem(
            @NotNull Player player,
            @NotNull MenuManager.MenuSession session,
            int layerIndex,
            @NotNull MenuManager.MenuItem item
    ) {
        int entityId = nextEntityId();
        int interactionEntityId = nextEntityId();
        MenuManager.MenuLayer layer = session.layers().get(layerIndex);
        Location position = calculatePosition(player, session, layer, layerIndex, item);
        Location hitboxPosition = calculateHitboxPosition(player, session, layer, layerIndex, item);
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
        send(player, metadataPacket(player, entityId, layer.menu(), item));
        send(player, interaction);
        send(player, interactionMetadataPacket(interactionEntityId, layer.menu(), item));
        session.bind(new MenuManager.RenderedItem(entityId, interactionEntityId, layerIndex, item));
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
            @NotNull MenuManager.MenuDefinition menu,
            @NotNull MenuManager.MenuItem item
    ) {
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(DISPLAY_BILLBOARD_INDEX, EntityDataTypes.BYTE, billboard(item.billboard())));
        double scale = displayScale(menu, item);
        metadata.add(new EntityData<>(DISPLAY_SCALE_INDEX, EntityDataTypes.VECTOR3F, new Vector3f((float) scale, (float) scale, (float) scale)));
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
            metadata.add(new EntityData<>(27, EntityDataTypes.BYTE, (byte) 0x01));
        }
        return new WrapperPlayServerEntityMetadata(entityId, metadata);
    }

    private @NotNull WrapperPlayServerEntityMetadata loreMetadataPacket(
            @NotNull Player player,
            int entityId,
            @NotNull MenuManager.MenuDefinition menu,
            @NotNull MenuManager.MenuItem item
    ) {
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(DISPLAY_BILLBOARD_INDEX, EntityDataTypes.BYTE, billboard(item.billboard())));
        metadata.add(new EntityData<>(DISPLAY_SCALE_INDEX, EntityDataTypes.VECTOR3F, new Vector3f(
                (float) menu.loreScale(),
                (float) menu.loreScale(),
                (float) menu.loreScale()
        )));
        String joinedLore = String.join("<newline>", item.lore());
        Component component = menuManager.miniMessage().deserialize(placeholders.resolve(player, joinedLore));
        metadata.add(new EntityData<>(TEXT_OR_ITEM_INDEX, EntityDataTypes.ADV_COMPONENT, component));
        metadata.add(new EntityData<>(TEXT_BACKGROUND_INDEX, EntityDataTypes.INT, menu.loreBackgroundColor()));
        metadata.add(new EntityData<>(27, EntityDataTypes.BYTE, (byte) 0x03));
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

    private @NotNull Location calculatePosition(
            @NotNull Player player,
            @NotNull MenuManager.MenuSession session,
            @NotNull MenuManager.MenuLayer layer,
            int layerIndex,
            @NotNull MenuManager.MenuItem item
    ) {
        Location anchor = session.hasAnchor() ? session.getAnchorLocation(player.getWorld()) : player.getEyeLocation();
        Vector forward = anchor.getDirection().normalize();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize().multiply(-1.0D);
        if (!isFinite(right)) {
            right = new Vector(1, 0, 0);
        }
        Vector up = right.clone().crossProduct(forward).normalize();
        double cascadeOffset = (layerIndex - session.activeLayerIndex()) * layer.menu().submenuShiftX();
        int visibleSlot = layer.menu().visibleSlot(layer, item);
        double itemOffsetY = visibleSlot >= 0
                ? layer.menu().scrollStartY() - visibleSlot * layer.menu().scrollItemSpacing()
                : item.offsetY();
        Vector offset = right.multiply(item.offsetX() + cascadeOffset)
                .add(up.multiply(itemOffsetY))
                .add(forward.multiply(item.offsetZ()));
        return anchor.add(offset);
    }

    private @NotNull Location calculateLorePosition(
            @NotNull Player player,
            @NotNull MenuManager.MenuSession session,
            @NotNull MenuManager.MenuLayer layer,
            int layerIndex,
            @NotNull MenuManager.MenuItem item
    ) {
        Location anchor = session.hasAnchor() ? session.getAnchorLocation(player.getWorld()) : player.getEyeLocation();
        Vector forward = anchor.getDirection().normalize();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize().multiply(-1.0D);
        if (!isFinite(right)) {
            right = new Vector(1, 0, 0);
        }
        Vector up = right.clone().crossProduct(forward).normalize();
        Location itemPosition = calculatePosition(player, session, layer, layerIndex, item);
        Vector offset = right.multiply(layer.menu().loreOffsetX())
                .add(up.multiply(layer.menu().loreOffsetY()))
                .add(forward.multiply(-layer.menu().loreOffsetZ()));
        return itemPosition.add(offset);
    }

    private @NotNull Location calculateHitboxPosition(
            @NotNull Player player,
            @NotNull MenuManager.MenuSession session,
            @NotNull MenuManager.MenuLayer layer,
            int layerIndex,
            @NotNull MenuManager.MenuItem item
    ) {
        Location position = calculatePosition(player, session, layer, layerIndex, item);
        position.subtract(0.0D, hitboxHeight(layer.menu(), item) * 0.5D, 0.0D);
        return position;
    }

    private double hitboxWidth(@NotNull MenuManager.MenuDefinition menu, @NotNull MenuManager.MenuItem item) {
        return item.hitboxWidth() > 0.0D ? item.hitboxWidth() : menu.hitboxWidth();
    }

    private double hitboxHeight(@NotNull MenuManager.MenuDefinition menu, @NotNull MenuManager.MenuItem item) {
        return item.hitboxHeight() > 0.0D ? item.hitboxHeight() : menu.hitboxHeight();
    }

    private double displayScale(@NotNull MenuManager.MenuDefinition menu, @NotNull MenuManager.MenuItem item) {
        if (item.scale() > 0.0D) {
            return item.scale();
        }
        return item.type() == MenuManager.DisplayType.ITEM ? menu.itemScale() : menu.textScale();
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

    private @Nullable HoverTarget hoverTarget(@NotNull Player player, @NotNull MenuManager.MenuSession session) {
        Location eye = player.getEyeLocation();
        Vector origin = eye.toVector();
        Vector direction = eye.getDirection().normalize();
        HoverTarget best = null;
        double bestDistance = Double.MAX_VALUE;
        List<MenuManager.MenuLayer> layers = session.layers();
        for (MenuManager.RenderedItem renderedItem : session.renderedItems().values()) {
            int layerIndex = renderedItem.layerIndex();
            if (layerIndex < 0 || layerIndex >= layers.size()) {
                continue;
            }
            MenuManager.MenuLayer layer = layers.get(layerIndex);
            MenuManager.MenuItem item = renderedItem.item();
            Location position = calculatePosition(player, session, layer, layerIndex, item);
            Vector toItem = position.toVector().subtract(origin);
            double distance = toItem.dot(direction);
            if (distance <= 0.0D || distance > layer.menu().hoverMaxDistance() || distance >= bestDistance) {
                continue;
            }
            Vector closest = origin.clone().add(direction.clone().multiply(distance));
            double missDistance = closest.distance(position.toVector());
            double radius = Math.max(hitboxWidth(layer.menu(), item), hitboxHeight(layer.menu(), item)) * 0.5D;
            if (missDistance <= radius) {
                best = new HoverTarget(layerIndex, layer, item);
                bestDistance = distance;
            }
        }
        return best;
    }

    private void destroyHover(@NotNull Player player) {
        destroyHover(player.getUniqueId(), player);
    }

    private void destroyHover(@NotNull UUID playerId, @Nullable Player player) {
        HoverDisplay current = hoverDisplays.remove(playerId);
        if (current != null) {
            if (player != null && player.isOnline()) {
                send(player, new WrapperPlayServerDestroyEntities(current.entityId()));
            }
        }
    }

    private void send(@NotNull Player player, @NotNull PacketWrapper<?> packet) {
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Throwable ex) {
            plugin.getLogger().fine("Failed to send packet to " + player.getName() + ": " + ex.getMessage());
        }
    }

    private record HoverTarget(
            int layerIndex,
            @NotNull MenuManager.MenuLayer layer,
            @NotNull MenuManager.MenuItem item
    ) {
    }

    private record HoverDisplay(int entityId, @NotNull String key) {
    }

}