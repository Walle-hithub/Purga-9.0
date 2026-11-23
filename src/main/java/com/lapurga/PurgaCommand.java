package com.lapurga;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PurgaCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "hardcoremobs.admin";

    private final LaPurgaPlugin plugin;
    private final PurgaManager manager;
    private final LootGuiManager guiManager;
    private final ShadowDevourerManager bossManager;

    public PurgaCommand(LaPurgaPlugin plugin, PurgaManager manager, LootGuiManager guiManager, ShadowDevourerManager bossManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.guiManager = guiManager;
        this.bossManager = bossManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("No tienes permiso para usar este comando.", NamedTextColor.RED));
            return true;
        }

        if (command.getName().equalsIgnoreCase("purgaloot")) {
            if (sender instanceof Player player) {
                guiManager.openLootMenu(player);
            } else {
                sender.sendMessage(Component.text("Este comando solo puede usarse dentro del juego.", NamedTextColor.RED));
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("loot")) {
            if (sender instanceof Player player) {
                guiManager.openLootMenu(player);
            } else {
                sender.sendMessage(Component.text("Este comando solo puede usarse dentro del juego.", NamedTextColor.RED));
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            sender.sendMessage(Component.text("Configuración y botín de LaPurga recargados.", NamedTextColor.GREEN));
            return true;
        }

        // Subcomando de dificultad
        if (args.length > 1 && args[0].equalsIgnoreCase("dificultad")) {
            String newDiff = args[1].toUpperCase(Locale.ROOT);
            if (List.of("FACIL", "NORMAL", "DIFICIL", "EXTREMO", "DEVORADOR").contains(newDiff)) {
                plugin.getConfig().set("difficulty.current", newDiff);
                plugin.saveConfig();
                sender.sendMessage(Component.text("Dificultad de La Purga cambiada a: " + newDiff, NamedTextColor.GOLD));
            } else {
                sender.sendMessage(Component.text("Dificultad inválida. Usa: FACIL, NORMAL, DIFICIL, EXTREMO, DEVORADOR", NamedTextColor.RED));
            }
            return true;
        }

        // Subcomando para invocar manualmente al jefe: /dificultadespecial jefe
        if (args.length > 0 && args[0].equalsIgnoreCase("jefe")) {
            if (sender instanceof Player player) {
                bossManager.spawnBoss(player.getLocation().add(4, 0, 4), player);
            } else {
                sender.sendMessage(Component.text("Este comando solo puede usarse dentro del juego.", NamedTextColor.RED));
            }
            return true;
        }

        // Subcomando de zona segura: /dificultadespecial zona <crear|borrar> <nombre> [radio]
        if (args.length > 1 && args[0].equalsIgnoreCase("zona")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Este comando solo puede usarse dentro del juego.", NamedTextColor.RED));
                return true;
            }
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equalsIgnoreCase("crear") && args.length > 2) {
                String zoneName = args[2];
                double radius = 15.0;
                if (args.length > 3) {
                    try {
                        radius = Double.parseDouble(args[3]);
                    } catch (NumberFormatException ignored) {}
                }
                SafeZoneManager.getInstance().addZone(zoneName, player.getLocation(), radius);
                player.sendMessage(Component.text("¡Zona segura '" + zoneName + "' creada con radio de " + radius + " bloques!", NamedTextColor.GREEN));
                return true;
            } else if (action.equalsIgnoreCase("borrar") && args.length > 2) {
                String zoneName = args[2];
                SafeZoneManager.getInstance().removeZone(zoneName);
                player.sendMessage(Component.text("Zona segura '" + zoneName + "' eliminada.", NamedTextColor.YELLOW));
                return true;
            } else {
                player.sendMessage(Component.text("Uso correcto: /dificultadespecial zona crear <nombre> [radio] o borrar <nombre>", NamedTextColor.RED));
                return true;
            }
        }

        World world;
        if (args.length > 0) {
            world = Bukkit.getWorld(args[0]);
            if (world == null) {
                sender.sendMessage(Component.text("El mundo '" + args[0] + "' no existe.", NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player player) {
            world = player.getWorld();
        } else {
            sender.sendMessage(Component.text("Desde consola debes indicar el mundo: /" + label + " <mundo>", NamedTextColor.RED));
            return true;
        }

        boolean enabled = manager.toggle(world);
        sender.sendMessage(Component.text("La Purga ha sido " + (enabled ? "ACTIVADA" : "DESACTIVADA")
                + " en el mundo '" + world.getName() + "'.", enabled ? NamedTextColor.RED : NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!sender.hasPermission(PERMISSION)) {
            return result;
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : List.of("reload", "loot", "dificultad", "zona", "jefe")) {
                if (sub.startsWith(prefix)) result.add(sub);
            }
            for (World world : Bukkit.getWorlds()) {
                if (world.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(world.getName());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("dificultad")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String diff : List.of("facil", "normal", "dificil", "extremo", "devorador")) {
                if (diff.startsWith(prefix)) result.add(diff);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("zona")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String act : List.of("crear", "borrar")) {
                if (act.startsWith(prefix)) result.add(act);
            }
        }
        return result;
    }
}
