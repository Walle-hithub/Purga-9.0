package com.lapurga;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/** Habilidades de los creepers: atracción magnética, ceguera, explosión mayor, rayo e incendio. */
public class CreeperListener implements Listener {

    private final LaPurgaPlugin plugin;
    private final PurgaManager manager;

    public CreeperListener(LaPurgaPlugin plugin, PurgaManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper) || !manager.isActive(creeper.getWorld())) {
            return;
        }
        FileConfiguration config = plugin.getConfig();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (random.nextDouble() < config.getDouble("creepers.bigger-explosion-chance", 0.35)) {
            event.setRadius((float) (event.getRadius() * config.getDouble("creepers.explosion-radius-multiplier", 1.8)));
        }
        if (random.nextDouble() < config.getDouble("creepers.fire-chance", 0.4)) {
            event.setFire(true);
        }

        // Habilidad creativa: Atracción magnética hacia el creeper al encenderse
        if (random.nextDouble() < config.getDouble("creepers.gravity-pull-chance", 0.35)) {
            Location cLoc = creeper.getLocation();
            creeper.getWorld().playSound(cLoc, Sound.ENTITY_ENDERMAN_SCREAM, 1.0f, 0.5f);
            creeper.getWorld().spawnParticle(Particle.REVERSE_PORTAL, cLoc.clone().add(0, 1, 0), 40, 1.5, 1.5, 1.5);
            for (Player p : creeper.getWorld().getNearbyPlayers(cLoc, 8.0)) {
                Vector pull = cLoc.toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.85).setY(0.3);
                p.setVelocity(pull);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper) || !manager.isActive(creeper.getWorld())) {
            return;
        }
        FileConfiguration config = plugin.getConfig();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location loc = event.getLocation();
        World world = loc.getWorld();

        double radius = config.getDouble("creepers.blindness-radius", 10.0);
        int ticks = config.getInt("creepers.blindness-seconds", 8) * 20;
        for (Player player : world.getNearbyPlayers(loc, radius)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0));
        }

        if (random.nextDouble() < config.getDouble("creepers.lightning-chance", 0.3)) {
            world.strikeLightning(loc);
        }

        if (random.nextDouble() < config.getDouble("creepers.fire-chance", 0.4)) {
            int fireRadius = config.getInt("creepers.fire-radius", 3);
            plugin.getServer().getScheduler().runTask(plugin, () -> igniteArea(loc, fireRadius));
        }
    }

    private void igniteArea(Location center, int radius) {
        World world = center.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    Block block = world.getBlockAt(center.getBlockX() + dx, center.getBlockY() + dy, center.getBlockZ() + dz);
                    if (block.getType().isAir() && block.getRelative(0, -1, 0).getType().isSolid()
                            && random.nextDouble() < 0.5) {
                        block.setType(Material.FIRE);
                    }
                }
            }
        }
    }
}
