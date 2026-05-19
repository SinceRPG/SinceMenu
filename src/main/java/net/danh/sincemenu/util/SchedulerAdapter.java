package net.danh.sincemenu.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class SchedulerAdapter {

    private final Plugin plugin;
    private final boolean folia;

    public SchedulerAdapter(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.folia = hasMethod(Bukkit.class, "getRegionScheduler");
    }

    private static @NotNull Consumer<Object> taskConsumer(@NotNull Runnable runnable) {
        return ignored -> runnable.run();
    }

    private static boolean hasMethod(@NotNull Class<?> owner, @NotNull String name) {
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFolia() {
        return folia;
    }

    public void runGlobal(@NotNull Runnable runnable) {
        invokeScheduler("getGlobalRegionScheduler", "run", new Class<?>[]{Plugin.class, Consumer.class}, plugin, taskConsumer(runnable));
    }

    public @NotNull Scheduled runGlobalTimer(@NotNull Runnable runnable, long delayTicks, long periodTicks) {
        Object task = invokeScheduler(
                "getGlobalRegionScheduler",
                "runAtFixedRate",
                new Class<?>[]{Plugin.class, Consumer.class, long.class, long.class},
                plugin,
                taskConsumer(runnable),
                Math.max(1L, delayTicks),
                Math.max(1L, periodTicks)
        );
        return new ReflectiveScheduled(task);
    }

    public void runAtEntity(@NotNull Entity entity, @NotNull Runnable runnable) {
        try {
            Method schedulerMethod = entity.getClass().getMethod("getScheduler");
            Object scheduler = schedulerMethod.invoke(entity);
            Method run = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            run.invoke(scheduler, plugin, taskConsumer(runnable), (Runnable) () -> {
            });
        } catch (ReflectiveOperationException ex) {
            runGlobal(runnable);
        }
    }

    public @NotNull Scheduled runAtEntityTimer(@NotNull Entity entity, @NotNull Runnable runnable, long delayTicks, long periodTicks) {
        try {
            Method schedulerMethod = entity.getClass().getMethod("getScheduler");
            Object scheduler = schedulerMethod.invoke(entity);
            Method runAtFixedRate = scheduler.getClass().getMethod(
                    "runAtFixedRate",
                    Plugin.class,
                    Consumer.class,
                    Runnable.class,
                    long.class,
                    long.class
            );
            Object task = runAtFixedRate.invoke(
                    scheduler,
                    plugin,
                    taskConsumer(runnable),
                    (Runnable) () -> {
                    },
                    Math.max(1L, delayTicks),
                    Math.max(1L, periodTicks)
            );
            return new ReflectiveScheduled(task);
        } catch (ReflectiveOperationException ex) {
            return runGlobalTimer(runnable, delayTicks, periodTicks);
        }
    }

    public void runAtLocation(@NotNull Location location, @NotNull Runnable runnable) {
        if (location.getWorld() == null) {
            runGlobal(runnable);
            return;
        }
        try {
            Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            Method run = regionScheduler.getClass().getMethod("run", Plugin.class, Location.class, Consumer.class);
            run.invoke(regionScheduler, plugin, location, taskConsumer(runnable));
        } catch (ReflectiveOperationException ex) {
            runGlobal(runnable);
        }
    }

    private Object invokeScheduler(@NotNull String schedulerMethodName, @NotNull String taskMethodName, Class<?>[] types, Object... args) {
        try {
            Object scheduler = Bukkit.class.getMethod(schedulerMethodName).invoke(null);
            Method method = scheduler.getClass().getMethod(taskMethodName, types);
            return method.invoke(scheduler, args);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Paper/Folia scheduler API is unavailable", ex);
        }
    }

    public interface Scheduled {
        void cancel();
    }

    private static final class ReflectiveScheduled implements Scheduled {

        private final Object task;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private ReflectiveScheduled(Object task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            if (task == null || !cancelled.compareAndSet(false, true)) {
                return;
            }
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }
}
