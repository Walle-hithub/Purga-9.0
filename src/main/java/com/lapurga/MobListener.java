package com.lapurga;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/** Habilidades creativas de mobs en La Purga: salto, robo de vida, telarañas, multishot, cambio de posición y maldiciones. */
public class MobListener implements Listener {

    private final LaPurgaPlugin plugin;
    private final PurgaManager manager;
    private final MobBuffer buffer;

    public MobListener(LaPurgaPlugin plugin, PurgaManager manager, MobBuffer buffer) {
        this.plugin = plugin;
        this.manager = manager;
        this.buffer = buffer;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Monster) || !manager.isActive(entity.getWorld())) {
            return;
        }
        buffer.buff(entity);

        FileConfiguration config = plugin.getConfig();
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
                && ThreadLocalRandom.current().nextDouble() < config.getDouble("spawning.natural-duplicate-chance", 0.6)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!entity.isValid() || !manager.isActive(entity.getWorld())) {
                    return;
                }
                long nearby = entity.getNearbyEntities(16, 8, 16).stream().filter(e -> e instanceof Monster).count();
                if (nearby < config.getInt("spawning.natural-duplicate-local-cap", 25)) {
                    entity.getWorld().spawnEntity(entity.getLocation(), entity.getType());
                }
            });
        }
    }

    /** Habilidades al atacar o recibir daño (Zombis, Arañas, Endermen, Brujas). */
    @EventHandler(ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        World world = event.getEntity().getWorld();
        if (!manager.isActive(world)) {
            return;
        }
        FileConfiguration config = plugin.getConfig();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 1. ZOMBI RECIBE DAÑO: Refuerzos + Salto agresivo hacia el jugador
        if (event.getEntity() instanceof Zombie zombie && buffer.isBuffed(zombie)) {
            Player attacker = resolvePlayer(event.getDamager());
            if (attacker != null) {
                handleZombieReinforcements(zombie, attacker, config, random);
                if (random.nextDouble() < config.getDouble("zombies.leap-chance", 0.25)) {
                    Vector leap = attacker.getLocation().toVector().subtract(zombie.getLocation().toVector()).normalize().multiply(0.9).setY(0.45);
                    zombie.setVelocity(leap);
                    world.playSound(zombie.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.8f, 1.4f);
                }
            }
        }

        // 2. ZOMBI GOLPEA A JUGADOR: Robo de vida (Lifesteal)
        if (event.getDamager() instanceof Zombie zombie && event.getEntity() instanceof Player && buffer.isBuffed(zombie)) {
            double heal = event.getFinalDamage() * config.getDouble("zombies.lifesteal-percent", 0.30);
            AttributeInstance maxHp = zombie.getAttribute(Attribute.MAX_HEALTH);
            if (maxHp != null && heal > 0) {
                zombie.setHealth(Math.min(maxHp.getValue(), zombie.getHealth() + heal));
                world.spawnParticle(Particle.HEART, zombie.getLocation().add(0, 1.8, 0), 3, 0.3, 0.3, 0.3);
            }
        }

        // 3. ARAÑA GOLPEA A JUGADOR: Trampa de telaraña + Salto venenoso
        if (event.getDamager() instanceof Spider spider && event.getEntity() instanceof Player player && buffer.isBuffed(spider)) {
            if (random.nextDouble() < config.getDouble("spiders.web-trap-chance", 0.40)) {
                Block feet = player.getLocation().getBlock();
                if (feet.getType().isAir()) {
                    feet.setType(Material.COBWEB);
                    // Quitar telaraña automáticamente tras 5 segundos para no llenar el mapa
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (feet.getType() == Material.COBWEB) feet.setType(Material.AIR);
                    }, 100L);
                }
            }
            if (random.nextDouble() < config.getDouble("spiders.venom-leap-chance", 0.30)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1));
                spider.setVelocity(new Vector(0, 0.5, 0));
            }
        }

        // 4. ENDERMAN GOLPEA A JUGADOR: Intercambio de posición (Swap Teleport) + Náusea
        if (event.getDamager() instanceof Enderman enderman && event.getEntity() instanceof Player player && buffer.isBuffed(enderman)) {
            if (random.nextDouble() < config.getDouble("endermen.swap-teleport-chance", 0.35)) {
                Location pLoc = player.getLocation().clone();
                Location eLoc = enderman.getLocation().clone();
                player.teleport(eLoc);
                enderman.teleport(pLoc);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0));
                world.playSound(pLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f);
                world.spawnParticle(Particle.PORTAL, eLoc, 30, 0.5, 1.0, 0.5);
            }
        }

        // 5. BRUJA RECIBE DAÑO: Aura de rayo oscuro
        if (event.getEntity() instanceof Witch witch && event.getDamager() instanceof Player player && buffer.isBuffed(witch)) {
            if (random.nextDouble() < config.getDouble("witches.curse-aura-chance", 0.40)) {
                world.strikeLightningEffect(player.getLocation());
                player.damage(3.0, witch);
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
            }
        }
    }

    /** Esqueletos: Flechas elementales, Multishot (ráfaga de 3) y Flechas explosivas. */
    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof AbstractSkeleton skeleton) || !manager.isActive(skeleton.getWorld())
                || !buffer.isBuffed(skeleton)) {
            return;
        }
        FileConfiguration config = plugin.getConfig();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Entity projectile = event.getProjectile();

        if (projectile instanceof Arrow arrow) {
            if (random.nextDouble() < config.getDouble("skeletons.special-arrow-chance", 0.45)) {
                arrow.setDamage(arrow.getDamage() * config.getDouble("skeletons.arrow-damage-multiplier", 1.5));
                switch (random.nextInt(4)) {
                    case 0 -> arrow.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 1), true);
                    case 1 -> arrow.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 100, 0), true);
                    case 2 -> arrow.addCustomEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 0), true);
                    default -> arrow.addCustomEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0), true);
                }
                arrow.setFireTicks(100);
            }

            // Flecha explosiva ligera
            if (random.nextDouble() < config.getDouble("skeletons.explosive-arrow-chance", 0.15)) {
                arrow.getPersistentDataContainer().set(buffer.explosiveArrowKey(), PersistentDataType.BYTE, (byte) 1);
            }

            // Multishot: dispara 2 flechas adicionales en abanico
            if (random.nextDouble() < config.getDouble("skeletons.multishot-chance", 0.30)) {
                Vector baseVel = arrow.getVelocity();
                for (double angle : new double[]{-0.18, 0.18}) {
                    Arrow extra = skeleton.launchProjectile(Arrow.class);
                    extra.setVelocity(rotateY(baseVel, angle));
                    extra.setDamage(arrow.getDamage() * 0.75);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArrowHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow
                && arrow.getPersistentDataContainer().has(buffer.explosiveArrowKey(), PersistentDataType.BYTE)) {
            Location loc = arrow.getLocation();
            loc.getWorld().createExplosion(loc, 1.5f, false, false);
            arrow.remove();
        }
    }

    private Vector rotateY(Vector v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vector(v.getX() * cos - v.getZ() * sin, v.getY(), v.getX() * sin + v.getZ() * cos);
    }

    private void handleZombieReinforcements(Zombie zombie, Player attacker, FileConfiguration config, ThreadLocalRandom random) {
        PersistentDataContainer data = zombie.getPersistentDataContainer();
        if (data.has(buffer.reinforcementKey(), PersistentDataType.BYTE)) return;

        int used = data.getOrDefault(buffer.callsKey(), PersistentDataType.INTEGER, 0);
        if (used >= config.getInt("zombies.max-reinforcement-calls", 2)) return;
        if (random.nextDouble() >= config.getDouble("zombies.reinforcements-chance", 0.25)) return;

        data.set(buffer.callsKey(), PersistentDataType.INTEGER, used + 1);
        World world = zombie.getWorld();
        Location base = zombie.getLocation();
        int count = config.getInt("zombies.reinforcements-count", 2);
        for (int i = 0; i < count; i++) {
            Location spot = base.clone().add(random.nextDouble(-1, 1), 0, random.nextDouble(-1, 1));
            world.spawn(spot, Zombie.class, helper -> {
                helper.getPersistentDataContainer().set(buffer.reinforcementKey(), PersistentDataType.BYTE, (byte) 1);
                helper.setTarget(attacker);
            });
        }
        world.spawnParticle(Particle.SOUL, base.clone().add(0, 1, 0), 25, 0.6, 0.8, 0.6, 0.02);
        world.playSound(base, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0f, 0.7f);
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
}
