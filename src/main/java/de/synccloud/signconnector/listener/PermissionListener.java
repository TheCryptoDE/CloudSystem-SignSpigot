package de.synccloud.signconnector.listener;

import de.synccloud.signconnector.permission.PermissionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.SQLException;

public class PermissionListener implements Listener {

    private final PermissionManager manager;


    public PermissionListener(PermissionManager manager){
        this.manager = manager;
    }

    @EventHandler
    public void onJoinPermission(PlayerJoinEvent event){
        try {
            manager.loadPermissions(event.getPlayer().getUniqueId());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
