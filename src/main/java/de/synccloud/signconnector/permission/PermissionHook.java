package de.synccloud.signconnector.permission;

import de.synccloud.signconnector.SignSystem;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PermissionHook {


    public static boolean has(Player player, String permission){
        SignSystem plugin = JavaPlugin.getPlugin(SignSystem.class);
        return plugin.getPermissionManager().hasPermission(player.getUniqueId(), permission);
    }
}
