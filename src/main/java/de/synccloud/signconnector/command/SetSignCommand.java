package de.synccloud.signconnector.command;

import de.synccloud.signconnector.SignSystem;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSignCommand implements CommandExecutor {

    private final SignSystem plugin;

    public SetSignCommand(SignSystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl ausführen.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("/setsign <ServerBaseName>");
            return true;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || !(targetBlock.getState() instanceof Sign)) {
            player.sendMessage("Du musst auf ein Schild schauen.");
            return true;
        }

        String baseName = args[0];
        String serverName = getNextFreeServerName(baseName);
        plugin.addSign(targetBlock.getLocation(), serverName);
        player.sendMessage("§aSchild für §e" + serverName + " §agesetzt.");
        return true;
    }

    private String getNextFreeServerName(String baseName) {
        int id = 1;
        while (true) {
            String candidate = baseName + "-" + id;
            if (!plugin.getSignServers().containsValue(candidate)) {
                return candidate;
            }
            id++;
        }
    }
}
