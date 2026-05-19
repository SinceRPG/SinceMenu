# SinceMenu

SinceMenu is a packet-only SAO-style floating menu plugin for modern Paper and Folia servers. It renders fake
`TextDisplay`, `ItemDisplay`, and invisible `Interaction` entities per viewer with PacketEvents, so menus are
lightweight and do not create persistent Bukkit entities.

## Features

- Packet-only floating 3D menus.
- Text and item display entries.
- Invisible Interaction hitboxes for reliable clicking.
- Per-menu commands that reload at runtime with `/sincemenu reload`.
- Folia-safe player scheduling.
- Pagination with `[next_page]` and `[previous_page]`.
- Per-action requirements.
- PlaceholderAPI text support.
- Optional icon providers for Minecraft, MMOItems, ItemsAdder, Nexo, and MythicCrucible.

## Controls

- Left click / right click: run configured menu actions.
- Shift left / shift right: run shift click actions.
- Drop key: pin or unpin the menu.
- Swap-hand key: close the menu.
- `/sincemenu close`: close the menu by command.

## Menu Command Example

```yaml
title: "Advanced SAO Menu"
commands:
  - advancedmenu
  - saomenu
permission: sincemenu.menu.advanced_menu
refresh-rate: 20
follow-player: true
follow-rate: 2
follow-distance-threshold: 0.35
follow-angle-threshold: 12.0
pinned-return-distance: 10.0
drop-key-toggle-pin: true
close-on-swap-hand: true
hitbox-width: 0.8
hitbox-height: 0.6
```

## Per-Action Requirements

Supported click sections are `LEFT`, `RIGHT`, `SHIFT_LEFT`, `SHIFT_RIGHT`, `SHIFT`, and `ANY`.

```yaml
items:
  example:
    type: TEXT
    page: 1
    text: "<green>Click me"
    offset-x: 0.0
    offset-y: 0.0
    offset-z: 2.5
    clicks:
      LEFT:
        actions:
          - action: "[message] <gold>You paid 100."
            requirements:
              - "money:100"
```

## Build

```bash
./gradlew build --no-configuration-cache
```

The plugin jar is generated in `build/libs`.
