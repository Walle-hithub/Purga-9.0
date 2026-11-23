package com.lapurga;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class MobBuffer {

    private final LaPurgaPlugin plugin;
    private final NamespacedKey buffedKey;
    private final NamespacedKey reinforcementKey;
    private final NamespacedKey callsKey;
    private final NamespacedKey explosiveArrowKey;

    public MobBuffer(LaPurgaPlugin plugin) {
        this.plugin = plugin;
        this.buffedKey = new NamespacedKey(plugin, "purga_buffed");
        this.reinforcementKey = new NamespacedKey(plugin, "purga_reinforcement");
        this.callsKey = new NamespacedKey(plugin, "purga_calls");
        this.explosiveArrowKey = new NamespacedKey(plugin, "purga_explosive_arrow");
    }

    public NamespacedKey reinforcementKey() { return reinforcementKey; }
    public NamespacedKey callsKey() { return callsKey; }
    public NamespacedKey explosiveArrowKey() { return explosiveArrowKey; }

    public boolean isBuffed(Entity entity) {
        return entity.getPersistentDataContainer().has(buffedKey, PersistentDataType.BYTE);
    }

    public void buff(LivingEntity entity) {
        if (!(entity instanceof Monster) || entity instanceof Boss || entity instanceof Warden) return;
        if (ShadowDevourerManager.isBoss(entity)) return;
        if (isBuffed(entity)) return;

        entity.getPersistentDataContainer().set(buffedKey, PersistentDataType.BYTE, (byte) 1);
        if (entity instanceof Creeper) return;

        FileConfiguration config = plugin.getConfig();
        String diff = config.getString("difficulty.current", "DIFICIL").toUpperCase();

        double healthMult = config.getDouble("difficulty.levels." + diff + ".health-mult",
                config.getDouble("buffs.health-multiplier", 1.75));
        double damageMult = config.getDouble("difficulty.levels." + diff + ".damage-mult",
                config.getDouble("buffs.damage-multiplier", 1.4));

        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * healthMult);
            entity.setHealth(health.getValue());
        }

        AttributeInstance damage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() * damageMult);
        }

        if (entity instanceof Zombie) {
            AttributeInstance speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(speed.getBaseValue() * config.getDouble("zombies.speed-multiplier", 1.35));
            }
        }

        if (config.getBoolean("buffs.fire-resistance", true)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                    PotionEffect.INFINITE_DURATION, 0, false, false));
        }
    }
}
