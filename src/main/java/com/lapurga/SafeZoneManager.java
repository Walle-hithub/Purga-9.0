package com.lapurga;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Gestiona las zonas seguras donde los mobs no pueden aparecer ni atacar. */
public class SafeZoneManager {

    private static SafeZoneManager instance;
    private final LaPurgaPlugin plugin;
    private final File file;
    private final Map<String, SafeZone> zones = new HashMap<>();

    public static class SafeZone {
        public String name;
        public UUID worldId;
        public double x, y, z;
        public double radius;

        public SafeZone(String name, Location center, double radius) {
            this.name = name;
            this.worldId = center.getWorld().getUID();
            this.x = center.getX();
            this.y = center.getY();
            this.z = center.getZ();
            this.radius = radius;
        }

        public boolean contains(Location loc) {
            if (loc == null || loc.getWorld() == null) return false;
            if (!loc.getWorld().getUID().equals(worldId)) return false;
            double dx = loc.getX() - x;
            double dz = loc.getZ() - z;
            return (dx * dx + dz * dz) <= (radius * radius);
        }
    }

    public SafeZoneManager(LaPurgaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "safe_zones.yml");
        instance = this;
        load();
    }

    public static SafeZoneManager getInstance() {
        return instance;
    }

    public void load() {
        zones.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (cfg.contains("zones")) {
            for (String key : cfg.getConfigurationSection("zones").getKeys(false)) {
                String wName = cfg.getString("zones." + key + ".world");
                World w = Bukkit.getWorld(wName);
                if (w != null) {
                    double x = cfg.getDouble("zones." + key + ".x");
                    double y = cfg.getDouble("zones." + key + ".y");
                    double z = cfg.getDouble("zones." + key + ".z");
                    double r = cfg.getDouble("zones." + key + ".radius");
                    zones.put(key.toLowerCase(), new SafeZone(key, new Location(w, x, y, z), r));
                }
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, SafeZone> entry : zones.entrySet()) {
            String path = "zones." + entry.getKey() + ".";
            SafeZone z = entry.getValue();
            World w = Bukkit.getWorld(z.worldId);
            if (w != null) {
                cfg.set(path + "world", w.getName());
                cfg.set(path + "x", z.x);
                cfg.set(path + "y", z.y);
                cfg.set(path + "z", z.z);
                cfg.set(path + "radius", z.radius);
            }
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar safe_zones.yml: " + e.getMessage());
        }
    }

    public void addZone(String name, Location center, double radius) {
        zones.put(name.toLowerCase(), new SafeZone(name, center, radius));
        save();
    }

    public void removeZone(String name) {
        zones.remove(name.toLowerCase());
        save();
    }

    public boolean isInsideSafeZone(Location loc) {
        for (SafeZone zone : zones.values()) {
            if (zone.contains(loc)) {
                return true;
            }
        }
        return false;
    }
}
