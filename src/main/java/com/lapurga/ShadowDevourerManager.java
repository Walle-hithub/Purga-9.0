package com.lapurga;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Jefe Especial: EL DEVORADOR DE SOMBRAS (900 HP).
 * Compatible 100% con Java y Bedrock (Geyser) mediante atributo SCALE nativo,
 * BossBar morada, partículas de vórtice oscuro y espada mítica con poder activo.
 */
public class ShadowDevourerManager implements Listener {

    private static NamespacedKey BOSS_KEY;
    private static NamespacedKey SWORD_KEY;
    private static NamespacedKey MINION_KEY;

    private final LaPurgaPlugin plugin;
    private final PurgaManager purgaManager;

    private final Map<UUID, UUID> activeBossByWorld = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Set<UUID> spawnedThisPurge = new HashSet<>();
    private final Map<UUID, Long> swordCooldowns = new HashMap<>();

    public ShadowDevourerManager(LaPurgaPlugin plugin, PurgaManager purgaManager) {
        this.plugin = plugin;
        this.purgaManager = purgaManager;
        BOSS_KEY = new NamespacedKey(plugin, "shadow_devourer_boss");
        SWORD_KEY = new NamespacedKey(plugin, "shadow_devourer_sword");
        MINION_KEY = new NamespacedKey(plugin, "shadow_devourer_minion");
        startBossTask();
    }

    public static boolean isBoss(Entity entity) {
        return BOSS_KEY != null && entity != null
                && entity.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.BYTE);
    }

    public void resetPurgeFlag(World world) {
        if (world != null) {
            spawnedThisPurge.remove(world.getUID());
        }
    }

    public void cleanupWorld(World world) {
        if (world == null) return;
        UUID wId = world.getUID();
        spawnedThisPurge.remove(wId);
        UUID bossId = activeBossByWorld.remove(wId);
        if (bossId != null) {
            Entity e = Bukkit.getEntity(bossId);
            if (e != null) e.remove();
            BossBar bar = bossBars.remove(bossId);
            if (bar != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.hideBossBar(bar);
                }
            }
        }
    }

    public void shutdown() {
        for (UUID wId : new HashSet<>(activeBossByWorld.keySet())) {
            World w = Bukkit.getWorld(wId);
            if (w != null) cleanupWorld(w);
        }
    }

    /** Spawnea al jefe automáticamente una única vez por Purga en dificultad DEVORADOR. */
    public boolean tryAutoSpawnForPurge(World world) {
        if (world == null || !purgaManager.isActive(world)) return false;
        String diff = plugin.getConfig().getString("difficulty.current", "DIFICIL").toUpperCase(Locale.ROOT);
        if (!diff.equals("DEVORADOR")) return false;
        if (spawnedThisPurge.contains(world.getUID())) return false;

        List<Player> candidates = world.getPlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR && !p.isDead())
                .filter(p -> !SafeZoneManager.getInstance().isInsideSafeZone(p.getLocation()))
                .toList();
        if (candidates.isEmpty()) return false;

        Player target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Location spawnLoc = findBossSpot(target.getLocation());
        if (spawnLoc == null) return false;

        spawnBoss(spawnLoc, target);
        return true;
    }

    public Spider spawnBoss(Location loc, Player initialTarget) {
        World world = loc.getWorld();
        if (world == null) return null;

        // Limpiar jefe previo si existiera
        cleanupWorld(world);
        spawnedThisPurge.add(world.getUID());

        double maxHp = plugin.getConfig().getDouble("boss.health", 900.0);
        double scale = plugin.getConfig().getDouble("boss.scale", 3.5);

        Spider boss = world.spawn(loc, Spider.class, spider -> {
            spider.getPersistentDataContainer().set(BOSS_KEY, PersistentDataType.BYTE, (byte) 1);
            spider.customName(Component.text("NIVEL DE AMENAZA: PURGA ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                    .append(Component.text("☠ EL DEVORADOR DE SOMBRAS", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)));
            spider.setCustomNameVisible(true);
            spider.setRemoveWhenFarAway(false);

            AttributeInstance hpAttr = spider.getAttribute(Attribute.MAX_HEALTH);
            if (hpAttr != null) {
                hpAttr.setBaseValue(maxHp);
                spider.setHealth(maxHp);
            }
            AttributeInstance dmgAttr = spider.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmgAttr != null) {
                dmgAttr.setBaseValue(16.0);
            }
            AttributeInstance spdAttr = spider.getAttribute(Attribute.MOVEMENT_SPEED);
            if (spdAttr != null) {
                spdAttr.setBaseValue(0.36);
            }
            AttributeInstance scaleAttr = spider.getAttribute(Attribute.SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(scale);
            }
            AttributeInstance kbAttr = spider.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kbAttr != null) {
                kbAttr.setBaseValue(0.95);
            }

            spider.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
            spider.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false));
            if (initialTarget != null) {
                spider.setTarget(initialTarget);
            }
        });

        activeBossByWorld.put(world.getUID(), boss.getUniqueId());
        BossBar bar = BossBar.bossBar(
                Component.text("EL DEVORADOR DE SOMBRAS — " + (int) maxHp + " HP", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                1.0f,
                BossBar.Color.PURPLE,
                BossBar.Overlay.NOTCHED_10
        );
        bossBars.put(boss.getUniqueId(), bar);

        for (Player p : world.getPlayers()) {
            p.showBossBar(bar);
            p.sendMessage(Component.empty());
            p.sendMessage(Component.text("☠ ¡EL DEVORADOR DE SOMBRAS HA EMERGIDO DEL VACÍO! (900 HP)", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            p.sendMessage(Component.text("Derrota al titán de La Purga para reclamar su Espada del Vacío.", NamedTextColor.LIGHT_PURPLE));
            p.sendMessage(Component.empty());
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 1.0f, 0.5f);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 1.0f, 0.6f);
        }

        return boss;
    }

    private Location findBossSpot(Location origin) {
        World world = origin.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 12; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 14 + random.nextDouble() * 10;
            int x = origin.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = origin.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location candidate = new Location(world, x + 0.5, y, z + 0.5);
            if (!SafeZoneManager.getInstance().isInsideSafeZone(candidate)) {
                return candidate;
            }
        }
        return origin.clone().add(12, 1, 0);
    }

    /** Tarea periódica: partículas de vórtice morado/negro, actualización de BossBar y habilidades de jefe. */
    private void startBossTask() {
        new BukkitRunnable() {
            int tickCounter = 0;

            @Override
            public void run() {
                tickCounter++;
                int abilityTicks = Math.max(2, plugin.getConfig().getInt("boss.ability-interval-seconds", 6)) * 2;

                Iterator<Map.Entry<UUID, UUID>> it = activeBossByWorld.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, UUID> entry = it.next();
                    World world = Bukkit.getWorld(entry.getKey());
                    Entity ent = Bukkit.getEntity(entry.getValue());

                    if (world == null || !(ent instanceof Spider boss) || !boss.isValid() || boss.isDead() || !purgaManager.isActive(world)) {
                        BossBar bar = bossBars.remove(entry.getValue());
                        if (bar != null) {
                            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
                        }
                        if (ent != null && ent.isValid() && !purgaManager.isActive(world)) {
                            ent.remove();
                        }
                        it.remove();
                        continue;
                    }

                    // Actualizar BossBar
                    BossBar bar = bossBars.get(boss.getUniqueId());
                    AttributeInstance maxHpAttr = boss.getAttribute(Attribute.MAX_HEALTH);
                    double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 900.0;
                    double curHp = Math.max(0.0, boss.getHealth());
                    if (bar != null) {
                        bar.progress((float) Math.min(1.0, curHp / maxHp));
                        bar.name(Component.text("EL DEVORADOR DE SOMBRAS — " + (int) Math.ceil(curHp) + " / " + (int) maxHp + " HP",
                                NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
                        for (Player p : world.getPlayers()) {
                            p.showBossBar(bar);
                        }
                    }

                    // Efecto visual continuo: Vórtice de sombras moradas y negras (100% compatible Java + Bedrock)
                    Location center = boss.getLocation().add(0, 1.5, 0);
                    world.spawnParticle(Particle.PORTAL, center, 45, 2.2, 1.5, 2.2, 0.4);
                    world.spawnParticle(Particle.REVERSE_PORTAL, center, 30, 1.8, 1.2, 1.8, 0.15);
                    world.spawnParticle(Particle.DRAGON_BREATH, center, 15, 1.5, 1.0, 1.5, 0.02);
                    world.spawnParticle(Particle.SQUID_INK, center, 10, 1.5, 1.0, 1.5, 0.02);

                    // Habilidad especial cada X segundos
                    if (tickCounter % abilityTicks == 0) {
                        executeBossAbility(boss);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    private void executeBossAbility(Spider boss) {
        World world = boss.getWorld();
        Location loc = boss.getLocation();
        double radius = plugin.getConfig().getDouble("boss.vortex-radius", 14.0);
        List<Player> nearby = world.getNearbyPlayers(loc, radius).stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR && !p.isDead())
                .filter(p -> !SafeZoneManager.getInstance().isInsideSafeZone(p.getLocation()))
                .toList();

        if (nearby.isEmpty()) return;

        int ability = ThreadLocalRandom.current().nextInt(3);
        switch (ability) {
            case 0 -> {
                // Vórtice Gravitatorio: atrae jugadores hacia el Devorador y aplica Wither
                world.playSound(loc, Sound.ENTITY_ENDERMAN_SCREAM, 1.5f, 0.4f);
                for (Player p : nearby) {
                    Vector pull = loc.toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.1).setY(0.35);
                    p.setVelocity(pull);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1));
                    p.sendActionBar(Component.text("¡El vórtice del Devorador te atrae!", NamedTextColor.LIGHT_PURPLE));
                }
            }
            case 1 -> {
                // Enjambre del Vacío: invoca 3 arañas de cueva rápidas
                world.playSound(loc, Sound.ENTITY_SPIDER_AMBIENT, 1.5f, 0.5f);
                for (int i = 0; i < 3; i++) {
                    Location spot = loc.clone().add(ThreadLocalRandom.current().nextDouble(-2, 2), 0, ThreadLocalRandom.current().nextDouble(-2, 2));
                    world.spawn(spot, CaveSpider.class, minion -> {
                        minion.getPersistentDataContainer().set(MINION_KEY, PersistentDataType.BYTE, (byte) 1);
                        minion.customName(Component.text("Cría del Vacío", NamedTextColor.DARK_PURPLE));
                        minion.setTarget(nearby.get(0));
                    });
                }
            }
            default -> {
                // Prisión de Sombras: telaraña + rayo oscuro sobre un jugador
                Player target = nearby.get(ThreadLocalRandom.current().nextInt(nearby.size()));
                world.strikeLightningEffect(target.getLocation());
                target.damage(6.0, boss);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
                if (target.getLocation().getBlock().getType().isAir()) {
                    var b = target.getLocation().getBlock();
                    b.setType(Material.COBWEB);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (b.getType() == Material.COBWEB) b.setType(Material.AIR);
                    }, 100L);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamage(EntityDamageEvent event) {
        if (isBoss(event.getEntity())) {
            // Inmune a sofocación, caídas y fuego
            if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION
                    || event.getCause() == EntityDamageEvent.DamageCause.FALL
                    || event.getCause() == EntityDamageEvent.DamageCause.FIRE
                    || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                    || event.getCause() == EntityDamageEvent.DamageCause.LIGHTNING) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBossDeath(EntityDeathEvent event) {
        if (!isBoss(event.getEntity())) return;

        LivingEntity boss = event.getEntity();
        World world = boss.getWorld();
        event.getDrops().clear();
        event.setDroppedExp(500);

        // Soltar la espada mítica del Devorador de Sombras + botín extra
        event.getDrops().add(createDevourerSword());
        event.getDrops().add(new ItemStack(Material.TOTEM_OF_UNDYING, 2));
        event.getDrops().add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 3));
        event.getDrops().add(new ItemStack(Material.NETHERITE_INGOT, 2));

        BossBar bar = bossBars.remove(boss.getUniqueId());
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        }
        activeBossByWorld.remove(world.getUID());

        world.playSound(boss.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.5f, 0.8f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation().add(0, 1, 0), 3);

        for (Player p : world.getPlayers()) {
            p.sendMessage(Component.empty());
            p.sendMessage(Component.text("★ ¡EL DEVORADOR DE SOMBRAS HA SIDO DERROTADO!", NamedTextColor.GOLD, TextDecoration.BOLD));
            p.sendMessage(Component.text("Ha soltado la legendaria 'Filo del Devorador de Sombras' en el suelo.", NamedTextColor.LIGHT_PURPLE));
            p.sendMessage(Component.empty());
        }
    }

    /** Crea la espada mítica recompensada por derrotar al jefe. */
    public ItemStack createDevourerSword() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD, 1);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Filo del Devorador de Sombras", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Forjada con la esencia del titán de La Purga.", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("⚡ Habilidad Pasiva: ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                            .append(Component.text("Robo de Vida (25%) al golpear.", NamedTextColor.WHITE)),
                    Component.text("🌀 Poder Activo (Clic Derecho): ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
                            .append(Component.text("Onda del Vacío", NamedTextColor.GOLD)),
                    Component.text("   Libera un pulso oscuro que daña (12 HP),", NamedTextColor.GRAY),
                    Component.text("   marchita y repele a todos los enemigos cercanos.", NamedTextColor.GRAY),
                    Component.text("   (Enfriamiento: 8 segundos)", NamedTextColor.DARK_GRAY)
            ));
            meta.addEnchant(Enchantment.SHARPNESS, 6, true);
            meta.addEnchant(Enchantment.LOOTING, 4, true);
            meta.addEnchant(Enchantment.UNBREAKING, 5, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(SWORD_KEY, PersistentDataType.BYTE, (byte) 1);
            sword.setItemMeta(meta);
        }
        return sword;
    }

    private boolean isDevourerSword(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_SWORD || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(SWORD_KEY, PersistentDataType.BYTE);
    }

    /** Poder pasivo de la espada: Robo de vida (Lifesteal) al golpear enemigos. */
    @EventHandler(ignoreCancelled = true)
    public void onSwordHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isDevourerSword(hand)) return;

        double heal = event.getFinalDamage() * 0.25;
        AttributeInstance maxHp = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null && heal > 0) {
            player.setHealth(Math.min(maxHp.getValue(), player.getHealth() + heal));
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 2, 0.3, 0.2, 0.3);
        }
    }

    /** Poder activo de la espada (Clic Derecho): Onda del Vacío que daña y repele enemigos en área. */
    @EventHandler
    public void onSwordUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isDevourerSword(hand)) return;

        long now = System.currentTimeMillis();
        long lastUse = swordCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long cooldownMs = 8000L;

        if (now - lastUse < cooldownMs) {
            long left = (cooldownMs - (now - lastUse)) / 1000L + 1;
            player.sendActionBar(Component.text("⏳ Onda del Vacío recargando (" + left + "s)...", NamedTextColor.DARK_PURPLE));
            return;
        }

        swordCooldowns.put(player.getUniqueId(), now);
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1, 0);

        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.3f);
        world.spawnParticle(Particle.PORTAL, center, 80, 4.0, 1.5, 4.0, 0.5);
        world.spawnParticle(Particle.DRAGON_BREATH, center, 40, 3.5, 1.0, 3.5, 0.05);

        int hitCount = 0;
        // Ampliamos el radio a 8.5 bloques y permitimos tanto Monster como cualquier LivingEntity hostil (excepto el propio jugador)
        for (Entity ent : world.getNearbyEntities(center, 8.5, 5.0, 8.5)) {
            if (ent instanceof LivingEntity living && living != player && !(living instanceof Player)) {
                if (ShadowDevourerManager.isBoss(living)) continue; // No dañar al propio jefe con la espada
                
                living.damage(14.0, player);
                living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 120, 1));
                
                // Vector de repulsión potente alejándose del jugador
                Vector push = living.getLocation().toVector().subtract(player.getLocation().toVector());
                if (push.lengthSquared() == 0) {
                    push = new Vector(0, 0.5, 0);
                } else {
                    push.normalize().multiply(1.6).setY(0.55);
                }
                living.setVelocity(push);
                
                // Partículas individuales en cada mob golpeado
                world.spawnParticle(Particle.WITCH, living.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.02);
                hitCount++;
            }
        }

        player.sendActionBar(Component.text("🌀 ¡Onda del Vacío desatada! (" + hitCount + " enemigos golpeados)", NamedTextColor.LIGHT_PURPLE));
    }
}
