package com.lapurga;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Menú GUI de 54 espacios para editar el botín garantizado de La Purga. */
public class LootGuiManager implements Listener {

    private final LaPurgaPlugin plugin;
    private final LootTable lootTable;
    private final Component GUI_TITLE = Component.text("Botín Garantizado - La Purga", NamedTextColor.DARK_RED, TextDecoration.BOLD);

    public LootGuiManager(LaPurgaPlugin plugin, LootTable lootTable) {
        this.plugin = plugin;
        this.lootTable = lootTable;
    }

    public void openLootMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);

        // Cargar ítems actuales en los primeros 45 espacios (filas 1 a 5)
        List<ItemStack> savedItems = lootTable.getCustomGuiItems();
        for (int i = 0; i < Math.min(savedItems.size(), 45); i++) {
            inv.setItem(i, savedItems.get(i).clone());
        }

        // Fila 6 (slots 45 a 53): Panel informativo y decorativo
        ItemStack glass = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.displayName(Component.text(" "));
            glass.setItemMeta(glassMeta);
        }
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, glass);
        }

        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(Component.text("¿Cómo funciona este menú?", NamedTextColor.GOLD, TextDecoration.BOLD));
            infoMeta.lore(List.of(
                    Component.text("• Coloca cualquier ítem en los espacios de arriba.", NamedTextColor.GRAY),
                    Component.text("• Conserva encantamientos, nombres y cantidades.", NamedTextColor.GRAY),
                    Component.text("• Al cerrar el menú, se guarda automáticamente.", NamedTextColor.GREEN),
                    Component.text("• Estos ítems saldrán al matar mobs en La Purga.", NamedTextColor.YELLOW)
            ));
            info.setItemMeta(infoMeta);
        }
        inv.setItem(49, info);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(GUI_TITLE)) {
            return;
        }
        // Evitar que muevan o saquen el panel decorativo de la fila inferior (slots 45-53)
        if (event.getRawSlot() >= 45 && event.getRawSlot() < 54) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().title().equals(GUI_TITLE)) {
            return;
        }
        Inventory inv = event.getInventory();
        List<ItemStack> itemsToSave = new ArrayList<>();

        for (int i = 0; i < 45; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.getType().isAir()) {
                itemsToSave.add(item.clone());
            }
        }

        lootTable.saveCustomGuiItems(itemsToSave);
        if (event.getPlayer() instanceof Player player) {
            player.sendMessage(Component.text("¡Botín de La Purga guardado! (" + itemsToSave.size() + " ítems registrados).", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        }
    }
}
