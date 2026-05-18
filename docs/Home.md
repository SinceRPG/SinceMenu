# SinceMenu Wiki

SinceMenu is a packet-only floating menu plugin for modern Paper and Folia servers. It renders fake `TextDisplay` and `ItemDisplay` entities per viewer with PacketEvents, so no persistent Bukkit entities are created.

## Commands

Commands are registered through Paper's lifecycle Brigadier API.

| Command | Permission | Description |
| --- | --- | --- |
| `/sincemenu` | `sincemenu.admin` | Shows command help. |
| `/sincemenu reload` | `sincemenu.admin` | Reloads `config.yml` and menu files. |
| `/sincemenu open [menu] [player]` | `sincemenu.admin` | Opens a menu for yourself or another player. |

Menu files can also register direct player commands. These commands are refreshed at runtime by `/sincemenu reload`; a server restart is not required for newly added or removed menu commands.

## Menu Files

Menus live in `plugins/SinceMenu/menus/*.yml`.

Top-level menu keys:

| Key | Description |
| --- | --- |
| `commands` | Player-facing commands that open this menu, for example `advancedmenu` and `saomenu`. |
| `permission` | Permission required to execute this menu's direct command. Defaults to `sincemenu.menu.<menu_id>`. |
| `permission-message` | Message shown by integrations that manually check the menu permission. Brigadier itself hides denied commands. |
| `refresh-rate` | Placeholder/item metadata refresh interval in ticks. |
| `follow-player` | Keeps fake display entities positioned relative to the viewer instead of fixed in the world. |
| `follow-rate` | How often SinceMenu checks whether a followed menu should move. |
| `follow-distance-threshold` | Minimum viewer movement, in blocks, before the menu teleports to a new anchor. |
| `follow-angle-threshold` | Minimum viewer rotation, in degrees, before the menu teleports to a new anchor. |
| `pinned-return-distance` | If a pinned menu is left farther than this distance, it is re-anchored in front of the viewer while staying pinned. |
| `drop-key-toggle-pin` | Press the drop key once to freeze the menu in place; press it again to let it follow the viewer. The drop packet is cancelled while a menu is open. |
| `close-on-swap-hand` | Lets the vanilla swap-hand key close the menu. The swap event is cancelled while a menu is open. |
| `hitbox-width` | Width of the invisible Interaction entity used to make a visual menu item clickable. |
| `hitbox-height` | Height of the invisible Interaction entity used to make a visual menu item clickable. |
| `pages` | Minimum page count. Item pages can raise this automatically. |

Important item keys:

| Key | Description |
| --- | --- |
| `type` | `TEXT` or `ITEM`. |
| `page` | Page number for pagination. |
| `text` | MiniMessage text for `TEXT` items. PlaceholderAPI is applied when installed. |
| `icon` | Provider string such as `minecraft:diamond`, `mmoitems:SWORD:CUTLASS`, `itemsadder:namespace:id`, `nexo:item_id`, or `mythiccrucible:item_id`. |
| `fallback-icon` | Safe vanilla icon when a third-party provider fails. |
| `offset-x/y/z` | Position relative to the player's view direction. |
| `view-requirements` | Requirements checked before rendering the item. |
| `clicks` | Click-type action map. |

## Built-In Requirements

| Requirement | Example | Notes |
| --- | --- | --- |
| `permission` | `permission:sincemenu.open` | Uses Bukkit permissions. |
| `money` | `money:100` | Uses Vault-style economy through reflection when available. |
| `papi` | `papi:%player_level%>=10` | Applies PlaceholderAPI first, then compares strings or numbers. |

## Built-In Actions

| Action | Example |
| --- | --- |
| `[message]` | `[message] <green>Hello %player_name%` |
| `[command]` | `[command] give %player_name% diamond 1` |
| `[player_command]` | `[player_command] spawn` |
| `[next_page]` | `[next_page]` |
| `[previous_page]` | `[previous_page]` |
| `[close]` | `[close]` |

## Click Actions

Supported click sections are `LEFT`, `RIGHT`, `SHIFT_LEFT`, `SHIFT_RIGHT`, `SHIFT`, and `ANY`.

`SHIFT` is used as a fallback for both `SHIFT_LEFT` and `SHIFT_RIGHT` when the exact shift click is not configured. `ANY` is used only when no specific click action exists.

Each action can define its own requirements:

```yaml
clicks:
  LEFT:
    actions:
      - action: "[message] <green>Paid click."
        requirements:
          - "money:100"
      - "[message] <gray>This action has no requirements."
```

## Developer API

Use `HoloMenuAPI.get()` after SinceMenu is enabled:

```java
HoloMenuAPI api = HoloMenuAPI.get();
api.icons().register(myProvider);
api.actions().register("my_action", context -> {
    context.player().sendMessage(Component.text("Clicked " + context.item().id()));
});
```

Third-party icon providers should return a cloned or new `ItemStack` and must not assume they run off-thread. SinceMenu catches provider failures and falls back safely.

## Icon Providers

| Prefix | Plugin | Example |
| --- | --- | --- |
| `minecraft` | Bukkit/Paper | `minecraft:diamond_sword` |
| `mmoitems` | MMOItems | `mmoitems:SWORD:CUTLASS` |
| `itemsadder` | ItemsAdder | `itemsadder:iasurvival:ruby` |
| `nexo` | Nexo | `nexo:ruby_sword` |
| `mythiccrucible` | MythicCrucible | `mythiccrucible:my_crucible_itemid` |
