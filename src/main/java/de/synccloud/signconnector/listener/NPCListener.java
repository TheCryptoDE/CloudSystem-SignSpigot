package de.synccloud.signconnector.listener;

import de.synccloud.signconnector.SignSystem;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Random;

public class NPCListener implements Listener {

    private final SignSystem plugin;
    private final JumpITServerManager serverManager;

    public NPCListener(SignSystem plugin, JumpITServerManager serverManager) {
        this.plugin = plugin;
        this.serverManager = serverManager;
    }

    @EventHandler
    public void onNPCInteract(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.VILLAGER) {
            Villager villager = (Villager) event.getRightClicked();

            // Optional: check Name des Villagers, falls du mehrere NPCs hast
            if (villager.getCustomName() != null && villager.getCustomName().contains("JumpIT")) {


                event.setCancelled(true);
                serverManager.openServerInventory(event.getPlayer());
            }
        }
    }


    @EventHandler
    public void onVillagerLeftClick(EntityDamageByEntityEvent event){
        if (!(event.getDamager() instanceof Player player))return;
        if (!(event.getEntity() instanceof  Villager villager))return;

        if (villager.getCustomName() != null && villager.getCustomName().contains("JumpIT")){
            event.setCancelled(true);

            List<String> available = serverManager.getAvaliableServers();
            if (available.isEmpty()){
                player.sendMessage("§8[§bCloud§8] §7"+"§cNo JumpIT server available.");
                return;
            }

            String server = available.get(new Random().nextInt(available.size()));
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            try {
                out.writeUTF("Connect");
                out.writeUTF(server);
            }catch (IOException exception){
                exception.printStackTrace();
                return;
            }
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            player.sendMessage("§8[§bCloud§8] §7"+"§aYou have been teleported to §b"+server+"§7.");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();  // <-- so holst du den Inventar-Titel

        if (title.equals("§aJumpIT Server")) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            String serverName = clicked.getItemMeta().getDisplayName().replace("§a", "");

            // Server verbinden über BungeeCord PluginMessage
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            try {
                out.writeUTF("Connect");
                out.writeUTF(serverName);
            } catch (IOException e) {
                e.printStackTrace();
            }

            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            player.closeInventory();
        }
    }

}
