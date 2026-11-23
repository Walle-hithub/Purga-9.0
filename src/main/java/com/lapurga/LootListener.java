package com.lapurga;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Boss;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class LootListener implements Listener {

    private final LaPurgaPlugin plugin;
    private final PurgaManager manager;
    private final LootTable lootTable;

    public LootListener(LaPurgaPlugin plugin, PurgaManager manager, LootTable lootTable) {
        this.plugin = plugin;
        this.manager = manager;
        this.lootTable = lootTable;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Monster) || entity instanceof Boss) {
            return;
        }
        if (!manager.isActive(entity.getWorld())) {
            return;
        }
        Player killer = entity.getKiller();
        if (plugin.getConfig().getBoolean("loot.require-player-kill", true) && killer == null) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() > plugin.getConfig().getDouble("loot.drop-chance", 1.0)) {
            return;
        }

        int min = Math.max(1, plugin.getConfig().getInt("loot.min-drops", 1));
        int max = Math.max(min, plugin.getConfig().getInt("loot.max-drops", 2));
        int rolls = random.nextInt(min, max + 1);

        for (int i = 0; i < rolls; i++) {
            ItemStack reward = lootTable.rollReward();
            if (reward != null && !reward.getType().isAir()) {
                event.getDrops().add(reward);

                if (killer != null && isHighValue(reward.getType())) {
                    killer.sendMessage(Component.text("★ ¡Recompensa valiosa de La Purga obtenida!", NamedTextColor.GOLD));
                    killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.8f, 1.2f);
                }
            }
        }
    }

    private boolean isHighValue(Material mat) {
        return mat == Material.TOTEM_OF_UNDYING
                || mat == Material.ENCHANTED_GOLDEN_APPLE
                || mat.name().startsWith("NETHERITE_")
                || mat.name().startsWith("DIAMOND_");
    }
}
