package de.synccloud.signconnector.permission.command;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.*;

public class PermissionMessageListener implements PluginMessageListener {

    private final JavaPlugin plugin;


    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public PermissionMessageListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("my:channel")) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subChannel = in.readUTF();
        if (!subChannel.equals("PermsResponse")) return;

        String playerName = in.readUTF();
        if (!player.getName().equals(playerName)) return;

        int size = in.readInt();
        Set<String> perms = new HashSet<>();
        for (int i = 0; i < size; i++) {
            perms.add(in.readUTF());
        }

        // Bereits vorhandene Attachments entfernen
        if (attachments.containsKey(player.getUniqueId())) {
            player.removeAttachment(attachments.get(player.getUniqueId()));
        }

        PermissionAttachment attachment = player.addAttachment(plugin);
        for (String perm : perms) {
            attachment.setPermission(perm, true);
        }

        attachments.put(player.getUniqueId(), attachment);
    }
}
