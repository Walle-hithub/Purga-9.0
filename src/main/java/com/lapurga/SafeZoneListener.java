package com.lapurga;

import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;

public class SafeZoneListener implements Listener {

    @EventHandler
    public void onTarget(EntityTargetEvent event) {
        if (event.getEntity() instanceof Monster && event.getTarget() instanceof Player player) {
            if (SafeZoneManager.getInstance().isInsideSafeZone(player.getLocation())) {
                event.setCancelled(true);
                event.setTarget(null);
            }
        }
    }
}
