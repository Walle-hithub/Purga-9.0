package com.lapurga;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Hordas nocturnas optimizadas contra lag y crasheos, sin reducir la letalidad ni la dificultad. */
public class SpawnTask extends BukkitRunnable {

    private final LaPurgaPlugin plugin;
    private final PurgaManager manager;
    private ShadowDevourerManager bossManager;

    public void setBossManager(ShadowDevourerManager bossManager) { this.bossManager = bossManager; }
    private int elapsedSeconds = 0;

    public SpawnTask(LaPurgaPlugin plugin, PurgaManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void run() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("spawning.enabled", true)) {
            return;
        }
        int interval = Math.max(1, config.getInt("spawning.interval-seconds", 2));
        if (++elapsedSeconds < interval) {
            return;
        }
        elapsedSeconds = 0;

        List<EntityType> types = parseTypes(config.getStringList("spawning.mob-types"));
        if (types.isEmpty()) {
            return;
        }
        int perCycle = config.getInt("spawning.mobs-per-cycle", 8);
        int cap = config.getInt("spawning.max-hostiles-near-player", 50);
        int globalWorldCap = config.getInt("performance.max-purge-mobs-per-world", 150);
        double min = config.getDouble("spawning.min-distance", 10);
        double max = Math.max(min + 1, config.getDouble("spawning.max-distance", 32));
        boolean onlyNightOrDark = config.getBoolean("spawning.only-night-or-dark", true);

        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (World world : Bukkit.getWorlds()) {
            if (!manager.isActive(world)) {
                continue;
            }

            // Control de límite global por mundo para evitar saturación de TPS y crasheos
            long totalWorldMonsters = world.getEntitiesByClass(Monster.class).size();
            if (totalWorldMonsters >= globalWorldCap) {
                continue; // Pausa temporalmente spawns nuevos si el mundo ya está saturado, protegiendo el servidor
            }

            boolean isNight = world.getEnvironment() != World.Environment.NORMAL || (world.getTime() >= 13000 && world.getTime() <= 23000);
            if (isNight && bossManager != null) {
                bossManager.tryAutoSpawnForPurge(world);
            }

            for (Player player : world.getPlayers()) {
                if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
                    continue;
                }

                if (SafeZoneManager.getInstance().isInsideSafeZone(player.getLocation())) {
                    continue;
                }

                Location pLoc = player.getLocation();
                if (onlyNightOrDark && world.getEnvironment() == World.Environment.NORMAL && !isNight) {
                    if (pLoc.getBlock().getLightLevel() > 7) {
                        continue;
                    }
                }

                long nearby = player.getNearbyEntities(32, 24, 32).stream()
                        .filter(e -> e instanceof Monster).count();
                int toSpawn = (int) Math.min(perCycle, Math.max(0, cap - nearby));

                for (int i = 0; i < toSpawn; i++) {
                    Location spot = findBypassSpot(player, min, max, random);
                    if (spot != null) {
                        if (SafeZoneManager.getInstance().isInsideSafeZone(spot)) {
                            continue;
                        }
                        EntityType chosenType = types.get(random.nextInt(types.size()));
                        Monster spawned = (Monster) world.spawnEntity(spot, chosenType);
                        spawned.setTarget(player);

                        // Optimización de rendimiento para Paper: Activar des-optimización de IA inteligente si está configurado
                        if (config.getBoolean("performance.smart-activation", true)) {
                            spawned.setAware(true); // Se mantienen plenamente conscientes y agresivos
                        }
                    }
                }
            }
        }
    }

    private Location findBypassSpot(Player player, double min, double max, ThreadLocalRandom random) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = min + random.nextDouble() * (max - min);
            int x = origin.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = origin.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            for (int dy = 4; dy >= -4; dy--) {
                int y = origin.getBlockY() + dy;
                Block feet = world.getBlockAt(x, y, z);
                if (!feet.getType().isBurnable()) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return null;
    }

    private List<EntityType> parseTypes(List<String> names) {
        List<EntityType> list = new ArrayList<>();
        for (String name : names) {
            try {
                list.add(EntityType.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {}
        }
        return list;
    }
}
