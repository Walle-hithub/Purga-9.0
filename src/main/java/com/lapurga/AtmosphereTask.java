package com.lapurga;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Atmósfera de Noche Roja extrema (sin efecto de oscuridad) con partículas y sonidos. */
public class AtmosphereTask extends BukkitRunnable {

    private final LaPurgaPlugin plugin;
    private final PurgaManager manager;

    public AtmosphereTask(LaPurgaPlugin plugin, PurgaManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("atmosphere.red-night-enabled", true)) {
            return;
        }

        for (World world : Bukkit.getWorlds()) {
            if (!manager.isActive(world)) {
                continue;
            }

            long time = world.getTime();
            boolean isNight = time >= 13000 && time <= 23000;

            for (Player player : world.getPlayers()) {
                if (player.isDead()) continue;

                if (isNight) {
                    // Partículas rojas masivas simulando cielo de sangre (sin Darkness ni Blindness)
                    player.spawnParticle(Particle.ASH, player.getLocation().add(0, 2, 0), 40, 15, 8, 15, 0.01);
                    player.spawnParticle(Particle.DUST_COLOR_TRANSITION, player.getLocation().add(0, 1.5, 0), 15, 8, 4, 8, 0,
                            new Particle.DustTransition(org.bukkit.Color.fromRGB(255, 0, 0), org.bukkit.Color.fromRGB(50, 0, 0), 2.5f));

                    if (Math.random() < 0.06) {
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.AMBIENT, 0.5f, 0.3f);
                        player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, SoundCategory.AMBIENT, 0.2f, 0.5f);
                    }
                }
            }
        }
    }
}
