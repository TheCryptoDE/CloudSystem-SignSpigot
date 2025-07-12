package de.synccloud.signconnector.command;

import de.synccloud.signconnector.manager.NPCData;
import de.synccloud.signconnector.mysql.MySQLManager;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class VillagerServerConnector implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey key;

    private static MySQLManager mySQLManager;

    public VillagerServerConnector(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "server");

        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
    }

    public static void setMySQLManager(MySQLManager manager) {
        mySQLManager = manager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Villager villager)) return;

        String npcName = villager.getCustomName();
        if (npcName == null) return;

        String strippedName = ChatColor.stripColor(npcName); // "BedWars" aus "§eBedWars"

        NPCData data = getNPCDataByName(strippedName);
        if (data != null) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            player.sendMessage("§aVerbinde zu §e" + data.server + "§a...");
            connectPlayer(player, data.server);
        } else {
            event.getPlayer().sendMessage("§cDieser NPC ist nicht verfügbar.");
        }
    }

    private void connectPlayer(Player player, String server) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            player.sendMessage("§cFehler beim Verbinden zum Server.");
        }
    }

    public NPCData getNPCDataByName(String name) {
        if (mySQLManager == null) {
            System.err.println("[VillagerServerConnector] MySQLManager ist null! Hast du setMySQLManager(...) aufgerufen?");
            return null;
        }

        try (Connection conn = mySQLManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM npcs WHERE name = ?")) {

            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new NPCData(rs.getString("name"), rs.getString("server"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
