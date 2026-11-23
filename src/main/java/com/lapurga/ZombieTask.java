package com.lapurga;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/** Zombis destructores extremos: rompen cualquier refugio (slabs, alfombras, paredes débiles, puertas, cristales). */
public class ZombieTask extends BukkitRunnable {

    private final LaPurgaPlugin plugin;
    private final PurgaManager manager;
    private final MobBuffer buffer;

    public ZombieTask(LaPurgaPlugin plugin, PurgaManager manager, MobBuffer buffer) {
        this.plugin = plugin;
        this.manager = manager;
        this.buffer = buffer;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("zombies.break-blocks", true)) {
            return;
        }
        double chance = plugin.getConfig().getDouble("zombies.break-chance", 0.85);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (World world : Bukkit.getWorlds()) {
            if (!manager.isActive(world)) {
                continue;
            }
            for (Zombie zombie : world.getEntitiesByClass(Zombie.class)) {
                if (!buffer.isBuffed(zombie)) {
                    continue;
                }
                LivingEntity target = zombie.getTarget();
                if (target == null || target.getWorld() != world) {
                    continue;
                }

                // Si el zombi está cerca del objetivo pero separado por bloques (escondite), romperlos de inmediato
                Location zLoc = zombie.getLocation();
                Location tLoc = target.getLocation();
                
                // Romper bloques alrededor de la cabeza, pies o en frente del zombi hacia el jugador
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            Block b = zLoc.clone().add(dx, dy, dz).getBlock();
                            if (isBreakeable(b.getType()) && random.nextDouble() < chance) {
                                Sound sound = b.getBlockData().getSoundGroup().getBreakSound();
                                world.playSound(b.getLocation(), sound, 1.0f, 0.7f);
                                b.breakNaturally();
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isBreakeable(Material mat) {
        if (mat.isAir() || mat == Material.BEDROCK || mat == Material.BARRIER) {
            return false;
        }
        String name = mat.name();
        return name.contains("SLAB")
                || name.contains("CARPET")
                || name.contains("DOOR")
                || name.contains("TRAPDOOR")
                || name.contains("GLASS")
                || name.contains("FENCE")
                || name.contains("LEAVES")
                || name.contains("WOOL")
                || name.contains("DIRT")
                || name.contains("WOOD")
                || name.contains("PLANKS");
    }
}
