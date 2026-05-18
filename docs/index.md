# SinceMenu Wiki

SinceMenu is a packet-only floating menu plugin for modern Paper and Folia servers.

It renders fake `TextDisplay`, `ItemDisplay`, and invisible `Interaction` entities per viewer with PacketEvents. Menus are lightweight and do not create persistent Bukkit entities.

## Pages

- [Commands](commands.md)
- [Configuration](configuration.md)
- [Click Actions](click-actions.md)
- [Icon Providers](icon-providers.md)
- [Developer API](developer-api.md)

## Controls

- Left click: runs `LEFT` actions.
- Right click: runs `RIGHT` actions.
- Shift left click: runs `SHIFT_LEFT` actions, or `SHIFT` fallback actions.
- Shift right click: runs `SHIFT_RIGHT` actions, or `SHIFT` fallback actions.
- Drop key: pins or unpins the menu when `drop-key-toggle-pin` is enabled.
- Swap-hand key: closes the menu when `close-on-swap-hand` is enabled.
- `/sincemenu close`: closes the current menu.

Esc cannot close a pure packet-only 3D menu because vanilla clients do not send an Esc packet during normal gameplay.
