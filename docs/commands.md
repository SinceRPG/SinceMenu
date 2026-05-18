# Commands

## Admin Commands

- `/sincemenu`
  - Permission: `sincemenu.admin`
  - Shows command help.

- `/sincemenu reload`
  - Permission: `sincemenu.admin`
  - Reloads `config.yml`, menu files, and runtime menu commands.

- `/sincemenu open [menu] [player]`
  - Permission: `sincemenu.admin`
  - Opens a menu for yourself or another player.

- `/sincemenu close`
  - Permission: `sincemenu.admin`
  - Closes your current packet menu.

## Direct Menu Commands

Each menu can define its own player-facing commands.

```yaml
commands:
  - advancedmenu
  - saomenu
permission: sincemenu.menu.advanced_menu
```

Direct menu commands are refreshed by `/sincemenu reload`. A server restart is not required after adding or removing a menu command.
