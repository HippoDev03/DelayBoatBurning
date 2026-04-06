package net.mwtw.delayBoatBuring;

import org.bukkit.Bukkit;
import org.bukkit.entity.Boat;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DelayBoatBuring extends JavaPlugin implements Listener {
    private long boatRemoveDelayTicks;
    private boolean logBoatDamage;
    private final Set<UUID> pendingBoatRemovals = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        boatRemoveDelayTicks = Math.max(0L, getConfig().getLong("boat-remove-delay-ticks", 100L));
        logBoatDamage = getConfig().getBoolean("log-boat-damage", true);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("DelayBoatBuring enabled. Delay ticks: " + boatRemoveDelayTicks + ", logging: " + logBoatDamage);
        if (logBoatDamage) {
            getLogger().info("Boat damage logging enabled. Delay ticks: " + boatRemoveDelayTicks);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBoatDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Boat boat)) return;
        if (!isFireOrLavaDamage(event.getCause())) return;

        UUID boatId = boat.getUniqueId();
        if (pendingBoatRemovals.contains(boatId)) {
            event.setCancelled(true);
            boat.setFireTicks(0);
            return;
        }

        if (logBoatDamage) {
            getLogger().info(
                    "Boat " + boatId + " damage cause: " + event.getCause() + ", cancelled: " + event.isCancelled());
        }
        event.setCancelled(true); // stop instant destruction
        boat.setFireTicks(0);
        scheduleDelayedRemoval(boat, "damage:" + event.getCause());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBoatCombust(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof Boat boat)) return;
        if (pendingBoatRemovals.contains(boat.getUniqueId())) {
            event.setCancelled(true);
            boat.setFireTicks(0);
            return;
        }
        if (logBoatDamage) {
            getLogger().info("Boat " + boat.getUniqueId() + " combust event. Duration: " + event.getDuration() + ", cancelled: " + event.isCancelled());
        }
        event.setCancelled(true);
        boat.setFireTicks(0);
        scheduleDelayedRemoval(boat, "combust");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBoatDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;

        UUID boatId = boat.getUniqueId();
        if (pendingBoatRemovals.contains(boatId)) {
            event.setCancelled(true);
            boat.setFireTicks(0);
            return;
        }

        boolean fireOrLavaState = boat.getFireTicks() > 0 || isBoatInLava(boat);
        if (!fireOrLavaState) {
            return;
        }

        if (logBoatDamage) {
            getLogger().info("Boat " + boatId + " fire/lava destroy intercepted. Attacker: " + event.getAttacker());
        }
        event.setCancelled(true);
        boat.setFireTicks(0);
        scheduleDelayedRemoval(boat, "vehicle-destroy-fire-lava");
    }

    private void scheduleDelayedRemoval(Boat boat, String reason) {
        UUID boatId = boat.getUniqueId();
        if (!pendingBoatRemovals.add(boatId)) {
            if (logBoatDamage) {
                getLogger().info("Boat " + boatId + " already has delayed removal pending. Trigger: " + reason);
            }
            return;
        }
        if (logBoatDamage) {
            getLogger().info("Delayed removal scheduled for boat " + boatId + " in " + boatRemoveDelayTicks + " ticks. Trigger: " + reason);
        }
        boat.setInvulnerable(true);
        boat.setFireTicks(0);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            pendingBoatRemovals.remove(boatId);
            if (!boat.isDead() && boat.isValid()) {
                boat.remove(); // or damage it / explode / etc.
                if (logBoatDamage) {
                    getLogger().info("Boat " + boatId + " removed after delay.");
                }
            } else if (logBoatDamage) {
                getLogger().info("Boat " + boatId + " no longer valid before delayed removal.");
            }
        }, boatRemoveDelayTicks);
    }

    private boolean isFireOrLavaDamage(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA;
    }

    private boolean isBoatInLava(Boat boat) {
        return boat.getLocation().getBlock().isLiquid()
                && boat.getLocation().getBlock().getType().name().contains("LAVA");
    }
}
