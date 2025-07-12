package de.synccloud.signconnector.listener;

import de.synccloud.signconnector.SignSystem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JumpITServerManager {

    private final SignSystem plugin;
    private List<String> availableServers = new ArrayList<>();
    private boolean listLoaded = false;

    public JumpITServerManager(SignSystem plugin) {
        this.plugin = plugin;
    }

    public void requestServerList(Player player) {
        listLoaded = false;

        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);

        try {
            out.writeUTF("GetServers");
        } catch (IOException e) {
            e.printStackTrace();
        }

        player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
    }

    public void updateServerList(String serverListString) {
        List<String> servers = new ArrayList<>();
        String[] allServers = serverListString.split(", ");
        for (String server : allServers) {
            if (server.startsWith("JumpIT-")) {
                servers.add(server);
            }
        }

        // Sortiere nach Nummer hinter JumpIT-
        servers.sort((a, b) -> {
            try {
                int aNum = Integer.parseInt(a.replace("JumpIT-", ""));
                int bNum = Integer.parseInt(b.replace("JumpIT-", ""));
                return Integer.compare(aNum, bNum);
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });

        this.availableServers = servers;
        this.listLoaded = true;
    }

    public void openServerInventory(Player player) {
        if (!listLoaded) {
            player.sendMessage("§cJumpIT Serverliste wird geladen. Bitte warten...");
            requestServerList(player);
            return;
        }

        int size = Math.max(9, ((availableServers.size() / 9) + 1) * 9);
        Inventory inv = Bukkit.createInventory(null, size, "§aJumpIT Server");

        for (String server : availableServers) {
            ItemStack item = new ItemStack(Material.ENDER_PEARL);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§a" + server);
            item.setItemMeta(meta);
            inv.addItem(item);
        }

        player.openInventory(inv);
    }

    public List getAvaliableServers(){
        return new ArrayList<>(availableServers);
    }
}
