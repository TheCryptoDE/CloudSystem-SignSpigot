package de.synccloud.signconnector;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;

public class SignSystem extends JavaPlugin implements Listener {

    private final Map<Location, String> signServers = new HashMap<>();
    private final Map<String, Integer> serverPlayerCount = new HashMap<>();
    private final Map<Location, Integer> animationFrames = new HashMap<>();
    private final Map<String, Integer> groupMaxPlayers = new HashMap<>();
    private MySQLManager mySQLManager;



    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new playerjoin(), this);
        getCommand("setsign").setExecutor(new SetSignCommand(this));
        mySQLManager = new MySQLManager(this.getDataFolder());
        mySQLManager.connect();
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", (channel, player, message) -> {
            if (!channel.equals("BungeeCord")) return;

            try {
                var in = new DataInputStream(new ByteArrayInputStream(message));
                String subchannel = in.readUTF();

                if (subchannel.equals("PlayerCount")) {
                    if (in.available() >= 2) { // Es muss noch mindestens 2 Bytes für das nächste UTF geben
                        String server = in.readUTF();
                        int count = in.readInt();
                        serverPlayerCount.put(server, count);
                    } else {

                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        loadSignsFromDatabase();
        initializeSignsInWorld();   // Neu: Schilder mit Grundtext setzen
      //  loadSigns();
        startUpdater();
        startLoadingAnimation();
    }

    @Override
    public void onDisable() {
        saveSigns();
    }

    private void loadSigns() {
        File file = new File(getDataFolder(), "signs.yml");
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            Location loc = stringToLocation(cfg.getString(key + ".location"));
            String server = cfg.getString(key + ".server");

            // Prüfen ob Server existiert
            if (isServerInDatabase(server)) {
                signServers.put(loc, server);
            } else {
                getLogger().warning("Server " + server + " nicht in der Datenbank gefunden. Schild wird auf Suche gesetzt.");
                animationFrames.put(loc, 0); // Starte Ladeanimation
            }
        }
    }

    private boolean isServerInDatabase(String serverName) {
        try {
            PreparedStatement ps = mySQLManager.getConnection().prepareStatement(
                    "SELECT COUNT(*) FROM servers WHERE name = ?"
            );
            ps.setString(1, serverName);
            ResultSet rs = ps.executeQuery();
            boolean exists = rs.next() && rs.getInt(1) > 0;
            rs.close();
            ps.close();
            return exists;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }



    private void loadGroupMaxPlayers() {
        Connection connection = mySQLManager.getConnection();

        if (connection == null) {
            getLogger().severe("MySQL Verbindung ist null. Abbruch beim Laden der Gruppen.");
            return;
        }

        String query = "SELECT group_name, max_players FROM groups";
        try (PreparedStatement ps = connection.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String groupName = rs.getString("group_name").toLowerCase();
                int maxPlayers = rs.getInt("max_players");
                groupMaxPlayers.put(groupName, maxPlayers);
                getLogger().info("MaxPlayers geladen für Gruppe " + groupName + ": " + maxPlayers);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }





    private void saveSigns() {
        File file = new File(getDataFolder(), "signs.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        int i = 0;
        for (Map.Entry<Location, String> entry : signServers.entrySet()) {
            cfg.set("sign" + i + ".location", locationToString(entry.getKey()));
            cfg.set("sign" + i + ".server", entry.getValue());
            i++;
        }
        try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    private void startUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                requestServerCounts();
                updateSigns();
            }
        }.runTaskTimerAsynchronously(this, 20, 20*5);
    }

    private void requestServerCounts() {
        for (String baseName : getAllBaseNames()) {
            for (int i = 1; i <= 100; i++) {
                String server = baseName + "-" + i;

                ByteArrayOutputStream b = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(b);
                try {
                    out.writeUTF("PlayerCount");
                    out.writeUTF(server);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Bukkit.getServer().sendPluginMessage(this, "BungeeCord", b.toByteArray());
            }
        }
    }

    private Set<String> getAllBaseNames() {
        Set<String> baseNames = new HashSet<>();
        for (String server : signServers.values()) {
            baseNames.add(getBaseName(server));
        }
        return baseNames;
    }



    private void updateSigns() {
        Bukkit.getScheduler().runTask(this, () -> {
            for (Map.Entry<Location, String> entry : new HashMap<>(signServers).entrySet()) {
                Location loc = entry.getKey();
                String server = entry.getValue();
                int count = serverPlayerCount.getOrDefault(server, -1);
                String baseName = getBaseName(server);

                // Prüfe ob Server gestartet ist (in started_servers)
                if (!isServerStarted(server)) {
                    // Server nicht gestartet → Ladeanimation starten
                    if (!animationFrames.containsKey(loc)) {
                        animationFrames.put(loc, 0);
                    }
                    continue;  // Kein Update auf dem Schild, Animation läuft
                }

                // Server ist gestartet, dann maxPlayers holen
                int maxPlayers = getMaxPlayersFromDatabase(baseName);
                if (maxPlayers == -1) {
                    maxPlayers = 20;
                }

                // Prüfe ob Server voll ist oder offline (count == -1)
                if (count == -1 || count >= maxPlayers) {
                    String nextServer = findNextAvailableServer(baseName, server);
                    if (nextServer != null) {
                        signServers.put(loc, nextServer);
                        saveSigns();
                        server = nextServer;
                        count = serverPlayerCount.getOrDefault(server, -1);
                    } else {
                        // Kein Server verfügbar → Animation starten
                        if (!animationFrames.containsKey(loc)) {
                            animationFrames.put(loc, 0);
                        }
                        continue; // Keine weiteren Updates auf dem Schild
                    }
                }

                // Server ist online und nicht voll → Update Schild
                if (loc.getBlock().getState() instanceof Sign sign) {
                    if (count >= 0) {
                        sign.setLine(0, "§a" + server);
                        sign.setLine(1, "§7ONLINE §a✓");
                        sign.setLine(2, "§eSpieler");
                        sign.setLine(3, "§b" + count + "/" + maxPlayers);
                        sign.update();
                        animationFrames.remove(loc);
                    } else {
                        if (!animationFrames.containsKey(loc)) {
                            animationFrames.put(loc, 0);
                        }
                    }
                }
            }
        });
    }

    private void loadSignsFromDatabase() {
        Connection conn = mySQLManager.getConnection();
        if (conn == null) {
            getLogger().severe("Keine MySQL-Verbindung zum Laden der Signs.");
            return;
        }

        try (PreparedStatement ps = conn.prepareStatement("SELECT world, x, y, z, server FROM signs");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                String server = rs.getString("server");

                Location loc = new Location(Bukkit.getWorld(world), x, y, z);
                if (loc.getWorld() == null) {
                    getLogger().warning("Welt " + world + " nicht gefunden. Schild an " + loc + " wird übersprungen.");
                    continue;
                }
                signServers.put(loc, server);
            }

            getLogger().info("Signs aus der Datenbank geladen: " + signServers.size());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void initializeSignsInWorld() {
        Bukkit.getScheduler().runTask(this, () -> {
            for (Map.Entry<Location, String> entry : signServers.entrySet()) {
                Location loc = entry.getKey();

                    if (!animationFrames.containsKey(loc)) {
                        animationFrames.put(loc, 0);
                }
            }
        });
    }





    private boolean isServerStarted(String serverName) {
        try {
            PreparedStatement ps = mySQLManager.getConnection().prepareStatement(
                    "SELECT COUNT(*) FROM started_servers WHERE server_name = ?"
            );
            ps.setString(1, serverName);
            ResultSet rs = ps.executeQuery();
            boolean started = rs.next() && rs.getInt(1) > 0;
            rs.close();
            ps.close();
            return started;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }




    private void startLoadingAnimation() {
        new BukkitRunnable() {
            final String[] frames = {
                    "§7Loading",
                    "§7Loading.",
                    "§7Loading..",
                    "§7Loading...",
                    "§7Loading..",
                    "§7Loading.",
                    "§7Loading"
            };

            @Override
            public void run() {
                Bukkit.getScheduler().runTask(SignSystem.this, () -> {
                    for (Location loc : animationFrames.keySet()) {
                        if (loc.getBlock().getState() instanceof Sign sign) {
                            int frame = animationFrames.get(loc);
                            sign.setLine(0, "§7SUCHE");
                            sign.setLine(1, "§7" + frames[frame]);
                            sign.setLine(2, "");
                            sign.setLine(3, "");
                            sign.update();
                            frame = (frame + 1) % frames.length;
                            animationFrames.put(loc, frame);
                        }
                    }
                });
            }
        }.runTaskTimer(this, 0, 10);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        if (!(e.getClickedBlock().getState() instanceof Sign sign)) return;

        Location loc = e.getClickedBlock().getLocation();
        if (signServers.containsKey(loc)) {
            String server = signServers.get(loc);
            Player p = e.getPlayer();
            p.sendMessage("§aVerbinde zu §e" + server);
            connectPlayer(p, server);
        }
    }

    private void connectPlayer(Player player, String server) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        try {
            out.writeUTF("Connect");
            out.writeUTF(server);
        } catch (IOException e) {
            e.printStackTrace();
        }
        player.sendPluginMessage(this, "BungeeCord", b.toByteArray());
    }

    public void addSign(Location loc, String server) {
        signServers.put(loc, server);
        saveSignToDatabase(loc, server);
        saveSigns(); // Falls du noch YAML speichern willst, sonst kannst du das weglassen
    }

    private void saveSignToDatabase(Location loc, String server) {
        Connection conn = mySQLManager.getConnection();
        if (conn == null) {
            getLogger().severe("Keine MySQL-Verbindung, Sign konnte nicht gespeichert werden!");
            return;
        }

        try {
            // Prüfen, ob das Sign schon existiert (Update statt Insert)
            PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM signs WHERE world = ? AND x = ? AND y = ? AND z = ?"
            );
            checkStmt.setString(1, loc.getWorld().getName());
            checkStmt.setInt(2, loc.getBlockX());
            checkStmt.setInt(3, loc.getBlockY());
            checkStmt.setInt(4, loc.getBlockZ());
            ResultSet rs = checkStmt.executeQuery();

            boolean exists = false;
            if (rs.next()) {
                exists = rs.getInt(1) > 0;
            }
            rs.close();
            checkStmt.close();

            if (exists) {
                // Update vorhandenes Sign
                PreparedStatement updateStmt = conn.prepareStatement(
                        "UPDATE signs SET server = ? WHERE world = ? AND x = ? AND y = ? AND z = ?"
                );
                updateStmt.setString(1, server);
                updateStmt.setString(2, loc.getWorld().getName());
                updateStmt.setInt(3, loc.getBlockX());
                updateStmt.setInt(4, loc.getBlockY());
                updateStmt.setInt(5, loc.getBlockZ());
                updateStmt.executeUpdate();
                updateStmt.close();
            } else {
                // Neues Sign speichern
                PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO signs (world, x, y, z, server) VALUES (?, ?, ?, ?, ?)"
                );
                insertStmt.setString(1, loc.getWorld().getName());
                insertStmt.setInt(2, loc.getBlockX());
                insertStmt.setInt(3, loc.getBlockY());
                insertStmt.setInt(4, loc.getBlockZ());
                insertStmt.setString(5, server);
                insertStmt.executeUpdate();
                insertStmt.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    public Map<Location, String> getSignServers() {
        return signServers;
    }

    private String getBaseName(String serverName) {
        if (serverName.contains("-")) {
            return serverName.split("-")[0];
        }
        return serverName;
    }

    private String findNextAvailableServer(String baseName, String currentServer) {
        int maxPlayers = getMaxPlayersFromDatabase(baseName.toLowerCase());
        if (maxPlayers == -1) {
            getLogger().warning("Konnte maxPlayers für Gruppe " + baseName + " nicht aus der Datenbank lesen.");
            maxPlayers = 2; // Fallback
        }

        for (int i = 1; i <= 100; i++) {
            String candidate = baseName + "-" + i;
            Integer playerCount = serverPlayerCount.get(candidate);
            if (playerCount != null && playerCount < maxPlayers) {
                if (!candidate.equals(currentServer)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private int getMaxPlayersFromDatabase(String groupName) {
        int maxPlayers = -1;
        try {
            PreparedStatement ps = mySQLManager.getConnection().prepareStatement(
                    "SELECT max_players FROM groups WHERE group_name = ?"
            );
            ps.setString(1, groupName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                maxPlayers = rs.getInt("max_players");
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return maxPlayers;
    }





    private String locationToString(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location stringToLocation(String s) {
        String[] parts = s.split(",");
        return new Location(Bukkit.getWorld(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }
    public MySQLManager getMySQLManager() {
        return mySQLManager;
    }
}
