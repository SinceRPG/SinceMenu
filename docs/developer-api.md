# Developer API

Use `HoloMenuAPI.get()` after SinceMenu is enabled.

```java
package com.example.plugin;

import net.danh.sincemenu.api.HoloMenuAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExamplePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        HoloMenuAPI api = HoloMenuAPI.get();

        api.actions().register("my_action", context -> {
            String itemId = context.item().id();
            context.player().sendMessage(Component.text("Clicked " + itemId));
        });
    }
}
```

Icon providers should return a new or cloned `ItemStack`.

Provider failures are caught by SinceMenu so external integrations cannot take down the core menu system.
