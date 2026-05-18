package net.danh.sincemenu.requirement;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public final class EconomyRequirement {

    public boolean has(@NotNull Player player, @NotNull String rawAmount) {
        try {
            double amount = Double.parseDouble(rawAmount.trim());
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(economyClass);
            if (provider == null) {
                return false;
            }
            Object economy = provider.getProvider();
            Method has = economy.getClass().getMethod("has", OfflinePlayer.class, double.class);
            Object result = has.invoke(economy, player, amount);
            return result instanceof Boolean bool && bool;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
