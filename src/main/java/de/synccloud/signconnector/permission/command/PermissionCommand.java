package de.synccloud.signconnector.permission.command;

import de.synccloud.signconnector.permission.PermissionManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class PermissionCommand implements CommandExecutor {

    private final PermissionManager manager;

    public PermissionCommand(PermissionManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        try {
            switch (sub) {
                case "create":
                    if (args.length == 2) {
                        manager.createGroup(args[1]);
                        sender.sendMessage("§aGruppe '" + args[1] + "' wurde erstellt.");
                    } else {
                        sender.sendMessage("§cVerwendung: /perm create <group>");
                    }
                    break;

                case "delete":
                    if (args.length == 2) {
                        manager.deleteGroup(args[1]);
                        sender.sendMessage("§aGruppe '" + args[1] + "' wurde gelöscht.");
                    } else {
                        sender.sendMessage("§cVerwendung: /perm delete <group>");
                    }
                    break;

                case "addperm":
                    if (args.length == 3) {
                        manager.addGroupPermission(args[1], args[2]);
                        sender.sendMessage("§aPermission '" + args[2] + "' wurde Gruppe '" + args[1] + "' hinzugefügt.");

                        // Alle Spieler in dieser Gruppe neu laden
                        Bukkit.getOnlinePlayers().forEach(p -> {
                            try {
                                if (manager.getPlayerGroup(p.getUniqueId()) != null &&
                                        manager.getPlayerGroup(p.getUniqueId()).equalsIgnoreCase(args[1])) {
                                    manager.loadPermissions(p.getUniqueId());
                                    manager.applyPermissionsToPlayer(p);
                                }
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }
                        });

                    } else {
                        sender.sendMessage("§cVerwendung: /perm addperm <group> <permission>");
                    }
                    break;

                case "removeperm":
                    if (args.length == 3) {
                        manager.removeGroupPermission(args[1], args[2]);
                        sender.sendMessage("§aPermission '" + args[2] + "' wurde aus Gruppe '" + args[1] + "' entfernt.");

                        // Alle Spieler in dieser Gruppe neu laden
                        Bukkit.getOnlinePlayers().forEach(p -> {
                            try {
                                if (manager.getPlayerGroup(p.getUniqueId()) != null &&
                                        manager.getPlayerGroup(p.getUniqueId()).equalsIgnoreCase(args[1])) {
                                    manager.loadPermissions(p.getUniqueId());
                                    manager.applyPermissionsToPlayer(p);
                                }
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }
                        });

                    } else {
                        sender.sendMessage("§cVerwendung: /perm removeperm <group> <permission>");
                    }
                    break;

                case "addplayer":
                    if (args.length == 3) {
                        OfflinePlayer player = Bukkit.getOfflinePlayer(args[1]);
                        UUID uuid = player.getUniqueId();
                        manager.assignGroup(uuid, args[2]);
                        manager.loadPermissions(uuid);

                        if (player.isOnline()) {
                            manager.applyPermissionsToPlayer(player.getPlayer());
                        }

                        sender.sendMessage("§aSpieler '" + args[1] + "' wurde Gruppe '" + args[2] + "' zugewiesen.");
                    } else {
                        sender.sendMessage("§cVerwendung: /perm addplayer <player> <group>");
                    }
                    break;

                case "removeplayer":
                    if (args.length == 2) {
                        OfflinePlayer player = Bukkit.getOfflinePlayer(args[1]);
                        UUID uuid = player.getUniqueId();
                        manager.removeGroup(uuid);
                        manager.loadPermissions(uuid);

                        if (player.isOnline()) {
                            manager.applyPermissionsToPlayer(player.getPlayer());
                        }

                        sender.sendMessage("§aSpieler '" + args[1] + "' wurde aus seiner Gruppe entfernt.");
                    } else {
                        sender.sendMessage("§cVerwendung: /perm removeplayer <player>");
                    }
                    break;

                case "list":
                    List<String> groups = manager.getAllGroups();
                    if (groups.isEmpty()) {
                        sender.sendMessage("§7Keine Gruppen gefunden.");
                    } else {
                        sender.sendMessage("§aVerfügbare Gruppen:");
                        for (String group : groups) {
                            List<String> perms = manager.getGroupPermissions(group);
                            sender.sendMessage(" §8- §f" + group + " §7(" + perms.size() + " Rechte)");
                        }
                    }
                    break;

                case "player":
                    if (args.length == 4) {
                        OfflinePlayer player = Bukkit.getOfflinePlayer(args[1]);
                        UUID uuid = player.getUniqueId();
                        String action = args[2].toLowerCase();
                        String permission = args[3];

                        switch (action) {
                            case "add":
                                manager.addPlayerPermission(uuid, permission);
                                manager.loadPermissions(uuid);

                                if (player.isOnline()) {
                                    manager.applyPermissionsToPlayer(player.getPlayer());
                                }

                                sender.sendMessage("§aPermission '" + permission + "' wurde Spieler '" + args[1] + "' hinzugefügt.");
                                break;

                            case "remove":
                                manager.removePlayerPermission(uuid, permission);
                                manager.loadPermissions(uuid);

                                if (player.isOnline()) {
                                    manager.applyPermissionsToPlayer(player.getPlayer());
                                }

                                sender.sendMessage("§aPermission '" + permission + "' wurde Spieler '" + args[1] + "' entfernt.");
                                break;

                            default:
                                sender.sendMessage("§cVerwendung: /perm player <player> <add|remove> <permission>");
                                break;
                        }
                    } else {
                        sender.sendMessage("§cVerwendung: /perm player <player> <add|remove> <permission>");
                    }
                    break;

                default:
                    sendHelp(sender);
                    break;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sender.sendMessage("§cEin Datenbankfehler ist aufgetreten.");
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8[§bPermission§8] §7Verfügbare Befehle:");
        sender.sendMessage(" §8/§fperm create <group>");
        sender.sendMessage(" §8/§fperm delete <group>");
        sender.sendMessage(" §8/§fperm addperm <group> <permission>");
        sender.sendMessage(" §8/§fperm removeperm <group> <permission>");
        sender.sendMessage(" §8/§fperm addplayer <player> <group>");
        sender.sendMessage(" §8/§fperm removeplayer <player>");
        sender.sendMessage(" §8/§fperm list");
        sender.sendMessage(" §8/§fperm player <player> <add|remove> <permission>");
    }
}
