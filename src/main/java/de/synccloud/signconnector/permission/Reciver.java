package de.synccloud.signconnector.permission;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.HashSet;
import java.util.Set;

public class Reciver implements PluginMessageListener {

    private final JavaPlugin plugin;

    public Reciver(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("my:channel")) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subChannel = in.readUTF();
        if (!subChannel.equals("PermsResponse")) return;

        String playerName = in.readUTF();
        int size = in.readInt();
        Set<String> perms = new HashSet<>();
        for (int i = 0; i < size; i++) {
            perms.add(in.readUTF());
        }

        if (player.getName().equals(playerName)) {
            PermissionAttachment attachment = player.addAttachment(plugin);
            for (String perm : perms) {
                attachment.setPermission(perm, true);
            }
        }
    }
}
