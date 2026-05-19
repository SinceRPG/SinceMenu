# Click Actions

Supported click sections:

- `LEFT`
- `RIGHT`
- `SHIFT_LEFT`
- `SHIFT_RIGHT`
- `SHIFT`
- `ANY`

`SHIFT` is used as a fallback for both `SHIFT_LEFT` and `SHIFT_RIGHT` when the exact shift click is not configured.

`ANY` is used only when no specific click action exists.

## Built-In Requirements

- `permission:sincemenu.open`
    - Uses Bukkit permissions.

- `money:100`
    - Uses a Vault-style economy through reflection when available.

- `papi:%player_level%>=10`
    - Applies PlaceholderAPI first, then compares strings or numbers.

## Built-In Actions

- `[message] <green>Hello`
- `[command] give %player_name% diamond 1`
- `[player_command] spawn`
- `[next_page]`
- `[previous_page]`
- `[scroll_up]`
- `[scroll_down]`
- `[open_menu] menu_id`
- `[back_menu]`
- `[close]`
- `[close_all]`

`[close]` closes the active submenu layer first. It closes the whole menu only when the root layer is active.

## Per-Action Requirements

Each action can define its own requirements.

```yaml
clicks:
  LEFT:
    actions:
      - action: "[message] <green>Paid click."
        requirements:
          - "money:100"
      - "[message] <gray>This action has no requirements."
```

Legacy string actions still work. Legacy `click-requirements` are applied to legacy string actions during parsing.
