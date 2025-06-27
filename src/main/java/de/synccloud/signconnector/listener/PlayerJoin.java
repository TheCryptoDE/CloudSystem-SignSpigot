package de.synccloud.signconnector.listener;

import de.synccloud.signconnector.SignSystem;
import de.synccloud.signconnector.permission.PermissionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.SQLException;


public class PlayerJoin implements Listener {

    private final PermissionManager manager;


    public PlayerJoin(PermissionManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(SignSystem.getPlugin(SignSystem.class), () -> {
            try {
                manager.loadPermissions(player.getUniqueId());
                Bukkit.getScheduler().runTask(SignSystem.getPlugin(SignSystem.class), () -> {
                    manager.applyPermissionsToPlayer(player);
                });
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}
