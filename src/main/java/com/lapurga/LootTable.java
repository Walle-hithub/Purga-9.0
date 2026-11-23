package com.lapurga;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Gestiona tanto el botín aleatorio valioso como el botín personalizado del menú GUI. */
public class LootTable {

    private final LaPurgaPlugin plugin;
    private final File customLootFile;
    private List<ItemStack> customGuiItems = new ArrayList<>();

    public LootTable(LaPurgaPlugin plugin) {
        this.plugin = plugin;
        this.customLootFile = new File(plugin.getDataFolder(), "custom_loot.yml");
    }

    public void load() {
        customGuiItems.clear();
        if (!customLootFile.exists()) {
            // Crear ítems por defecto útiles la primera vez
            customGuiItems.add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
            customGuiItems.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
            customGuiItems.add(new ItemStack(Material.DIAMOND, 4));
            saveCustomGuiItems(customGuiItems);
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(customLootFile);
        List<?> list = cfg.getList("items");
        if (list != null) {
            for (Object obj : list) {
                if (obj instanceof ItemStack item) {
                    customGuiItems.add(item);
                }
            }
        }
    }

    public void saveCustomGuiItems(List<ItemStack> items) {
        this.customGuiItems = new ArrayList<>(items);
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("items", this.customGuiItems);
        try {
            cfg.save(customLootFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar custom_loot.yml: " + e.getMessage());
        }
    }

    public List<ItemStack> getCustomGuiItems() {
        return customGuiItems;
    }

    /** Genera un ítem valioso aleatorio garantizado (incluyendo armaduras/armas encantadas o del menú GUI). */
    public ItemStack rollReward() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double guiChance = plugin.getConfig().getDouble("loot.custom-gui-loot-chance", 0.45);

        // Si hay ítems en el menú GUI, hay alta probabilidad de soltar uno de ellos
        if (!customGuiItems.isEmpty() && random.nextDouble() < guiChance) {
            ItemStack chosen = customGuiItems.get(random.nextInt(customGuiItems.size()));
            return chosen.clone();
        }

        // Si no sale del menú GUI, genera recompensas épicas/raras predefinidas que valen la pena
        int wCommon = plugin.getConfig().getInt("loot.tier-weights.common", 50);
        int wRare = plugin.getConfig().getInt("loot.tier-weights.rare", 35);
        int wEpic = plugin.getConfig().getInt("loot.tier-weights.epic", 15);
        int total = Math.max(1, wCommon + wRare + wEpic);
        int roll = random.nextInt(total);

        if (roll < wEpic) {
            return createEpicReward(random);
        } else if (roll < wEpic + wRare) {
            return createRareReward(random);
        } else {
            return createUsefulCommonReward(random);
        }
    }

    private ItemStack createEpicReward(ThreadLocalRandom random) {
        return switch (random.nextInt(5)) {
            case 0 -> new ItemStack(Material.TOTEM_OF_UNDYING, 1);
            case 1 -> new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1);
            case 2 -> createEnchantedGear(Material.NETHERITE_CHESTPLATE, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3);
            case 3 -> createEnchantedGear(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5, Enchantment.LOOTING, 3);
            default -> new ItemStack(Material.NETHERITE_INGOT, 1);
        };
    }

    private ItemStack createRareReward(ThreadLocalRandom random) {
        return switch (random.nextInt(5)) {
            case 0 -> new ItemStack(Material.GOLDEN_APPLE, random.nextInt(2, 5));
            case 1 -> new ItemStack(Material.DIAMOND, random.nextInt(3, 7));
            case 2 -> createEnchantedGear(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 3, Enchantment.UNBREAKING, 3);
            case 3 -> createEnchantedGear(Material.DIAMOND_BOOTS, Enchantment.FEATHER_FALLING, 4, Enchantment.PROTECTION, 3);
            default -> new ItemStack(Material.EXPERIENCE_BOTTLE, random.nextInt(8, 17));
        };
    }

    private ItemStack createUsefulCommonReward(ThreadLocalRandom random) {
        return switch (random.nextInt(4)) {
            case 0 -> new ItemStack(Material.IRON_INGOT, random.nextInt(4, 10));
            case 1 -> new ItemStack(Material.GOLD_INGOT, random.nextInt(4, 9));
            case 2 -> new ItemStack(Material.COOKED_BEEF, random.nextInt(6, 14));
            default -> new ItemStack(Material.ENDER_PEARL, random.nextInt(2, 5));
        };
    }

    private ItemStack createEnchantedGear(Material mat, Enchantment e1, int lvl1, Enchantment e2, int lvl2) {
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(e1, lvl1, true);
            meta.addEnchant(e2, lvl2, true);
            item.setItemMeta(meta);
        }
        return item;
    }
}
