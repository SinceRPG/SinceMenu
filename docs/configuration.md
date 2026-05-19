# Configuration

Menus live in `plugins/SinceMenu/menus`.

## Top-Level Menu Keys

- `title`
  - Human-readable menu title.

- `commands`
  - Direct player commands that open this menu.

- `permission`
  - Permission required to use direct menu commands.

- `permission-message`
  - MiniMessage text sent when a player lacks the menu permission.

- `refresh-rate`
  - Placeholder and item metadata refresh interval in ticks.

- `follow-player`
  - Keeps fake menu entities positioned relative to the viewer.

- `follow-rate`
  - How often SinceMenu checks whether a followed menu should move.

- `follow-distance-threshold`
  - Minimum viewer movement, in blocks, before the menu moves to a new anchor.

- `follow-angle-threshold`
  - Minimum viewer rotation, in degrees, before the menu moves to a new anchor.

- `follow-rotation`
  - When false, a standing player can look around without moving the open menu.

- `pinned-return-distance`
  - If a pinned menu is left farther than this distance, it is re-anchored in front of the viewer while staying pinned.

- `scroll-visible-items`
  - Number of scrollable items rendered from the current page. Mouse wheel and scroll actions move this viewport.

- `scroll-start-y`, `scroll-item-spacing`
  - Vertical layout used for the visible scrollable item viewport.

- `submenu-shift-x`
  - Horizontal distance used to push older menu layers left when `[open_menu]` opens a submenu.

- `hover-lore`, `hover-rate`, `hover-max-distance`
  - Controls the packet text display that appears when the player looks at a rendered item with lore.

- `lore-offset-x`, `lore-offset-y`, `lore-offset-z`, `lore-background-color`
  - Position and background styling for hover lore text.

- `item-scale`, `text-scale`, `lore-scale`
  - Display entity scale values. Item displays should stay small because their vanilla default is one block wide.

- `drop-key-toggle-pin`
  - Press the drop key once to pin the menu. Press it again to follow normally.

- `close-on-swap-hand`
  - Lets the vanilla swap-hand key close the menu while cancelling the swap event.

- `hitbox-width`
  - Default width for invisible Interaction hitboxes.

- `hitbox-height`
  - Default height for invisible Interaction hitboxes.

- `pages`
  - Minimum page count. Item pages can raise this automatically.

## Item Keys

- `type`
  - `TEXT` or `ITEM`.

- `page`
  - Page number for pagination.

- `text`
  - MiniMessage text for `TEXT` items. PlaceholderAPI is applied when installed.

- `icon`
  - Icon provider string, for example `minecraft:diamond`, `mmoitems:SWORD:CUTLASS`, `itemsadder:namespace:id`, `nexo:item_id`, or `mythiccrucible:item_id`.

- `fallback-icon`
  - Safe vanilla icon when a third-party provider fails.

- `offset-x`, `offset-y`, `offset-z`
  - Position relative to the player's view direction.

- `hitbox-width`, `hitbox-height`
  - Optional per-item hitbox override.

- `scale`
  - Optional per-item display scale override.

- `scrollable`
  - Whether this item is part of the mouse-wheel viewport. Set controls like close/back buttons to `false`.

- `lore`
  - MiniMessage lines shown while the player looks at this item.

- `view-requirements`
  - Requirements checked before rendering the item.

- `clicks`
  - Click action map.
