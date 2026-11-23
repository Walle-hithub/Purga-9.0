package com.lapurga;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Controla qué mundos tienen La Purga activa. */
public class PurgaManager {

    private final LaPurgaPlugin plugin;
    private final MobBuffer buffer;
    private final Set<UUID> activeWorlds = new HashSet<>();
    private ShadowDevourerManager bossManager;

    public void setBossManager(ShadowDevourerManager bossManager) { this.bossManager = bossManager; }

    public PurgaManager(LaPurgaPlugin plugin, MobBuffer buffer) {
        this.plugin = plugin;
        this.buffer = buffer;
    }

    public boolean isActive(World world) {
        return world != null && activeWorlds.contains(world.getUID());
    }

    /** Alterna el estado. Devuelve true si quedó ACTIVADA. */
    public boolean toggle(World world) {
        if (isActive(world)) {
            stop(world);
            return false;
        }
        start(world);
        return true;
    }

    private void start(World world) {
        activeWorlds.add(world.getUID());
        world.setPVP(true);
        if (bossManager != null) bossManager.resetPurgeFlag(world);

        Title title = Title.title(
                Component.text("LA PURGA HA COMENZADO", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Hordas letales, criaturas con poderes y PvP activado. Sobrevive.", NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(600), Duration.ofSeconds(5), Duration.ofSeconds(2)));

        for (Player player : world.getPlayers()) {
            player.showTitle(title);
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("══════ LA PURGA HA COMENZADO ══════", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            player.sendMessage(Component.text("• Los mobs hostiles aparecen en hordas mucho más numerosas.", NamedTextColor.RED));
            player.sendMessage(Component.text("• Son más resistentes y letales: más vida y más daño.", NamedTextColor.RED));
            player.sendMessage(Component.text("• Habilidades especiales: creepers que ciegan, invocan rayos y provocan incendios; "
                    + "zombis veloces que rompen bloques débiles y llaman refuerzos; esqueletos con flechas especiales.", NamedTextColor.RED));
            player.sendMessage(Component.text("• Mejor botín: carbón, hierro y pólvora, pero también diamantes, "
                    + "manzanas de oro, tótems y fragmentos de netherite.", NamedTextColor.GOLD));
            player.sendMessage(Component.text("• ¡EL PVP ESTÁ ACTIVADO! Cuidado con quién te acompaña.", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            player.sendMessage(Component.empty());

            Location loc = player.getLocation();
            player.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 1.0f, 0.7f);
            player.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER, 1.0f, 0.6f);
            player.playSound(loc, Sound.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 0.6f, 0.8f);
        }

        // Los hostiles que ya existían también reciben los buffs
        for (Monster monster : world.getEntitiesByClass(Monster.class)) {
            buffer.buff(monster);
        }
    }

    private void stop(World world) {
        activeWorlds.remove(world.getUID());
        world.setPVP(false);
        if (bossManager != null) bossManager.cleanupWorld(world);

        Title title = Title.title(
                Component.text("LA PURGA HA TERMINADO", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("El PvP ha sido desactivado. Has sobrevivido.", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1)));

        for (Player player : world.getPlayers()) {
            player.showTitle(title);
            player.sendMessage(Component.text("La Purga ha terminado. Los mobs vuelven a la normalidad y el PvP está desactivado.",
                    NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    /** Al apagar el plugin se restaura el PvP de los mundos activos. */
    public void shutdown() {
        for (UUID id : activeWorlds) {
            World world = plugin.getServer().getWorld(id);
            if (world != null) {
                world.setPVP(false);
            }
        }
        activeWorlds.clear();
    }
}
