package de.synccloud.signconnector.command;

import de.synccloud.signconnector.manager.NPCManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetNPCCommand implements CommandExecutor {

    private final NPCManager npcManager;

    public SetNPCCommand(NPCManager npcManager) {
        this.npcManager = npcManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (args.length != 2) {
            p.sendMessage("§cVerwendung: /setnpc <Name> <Server>");
            return true;
        }

        String name = args[0];
        String server = args[1];
        Location loc = p.getLocation();
        npcManager.spawnNPC(loc, name, server);
        p.sendMessage("§aNPC erstellt.");
        return true;
    }
}
