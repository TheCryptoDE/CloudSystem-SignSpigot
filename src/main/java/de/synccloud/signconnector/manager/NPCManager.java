package de.synccloud.signconnector.manager;

import de.synccloud.signconnector.mysql.MySQLManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class NPCManager implements Listener {

    private final JavaPlugin plugin;

    public static HashMap<String, String> names = new HashMap<>();
    private final Map<Integer, String> npcServerMap = new HashMap<>();
    private final NamespacedKey key = new NamespacedKey("signconnector", "server_npc");
    private final MySQLManager mysql;

    public NPCManager(JavaPlugin plugin, MySQLManager mysql) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        this.mysql = mysql;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }


    public void spawnNPC(Location loc, String name, String server) {
        Villager villager = loc.getWorld().spawn(loc, Villager.class);
        names.put(name,server);
        villager.setCustomName("§e" + name);
        villager.setCustomNameVisible(true);
        villager.setInvulnerable(true);
        villager.setAI(false);
        villager.setCollidable(false);
        villager.getPersistentDataContainer().set(key, PersistentDataType.STRING, server);
        npcServerMap.put(villager.getEntityId(), server);
        mysql.insertNPC(name, server, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), loc.getWorld().getName());

    }


    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity entity = e.getRightClicked();
        if (!(entity instanceof Villager villager)) return;

        String server = villager.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (server != null) {
            e.setCancelled(true);
            Player p = e.getPlayer();
            p.sendMessage("§aVerbinde zu §e" + server);
            connectPlayer(p, server);
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
        }
    }

}
