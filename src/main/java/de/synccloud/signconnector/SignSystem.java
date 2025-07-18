package de.synccloud.signconnector;

import de.synccloud.signconnector.command.SetNPCCommand;
import de.synccloud.signconnector.command.SetSignCommand;
import de.synccloud.signconnector.listener.JumpITServerManager;
import de.synccloud.signconnector.listener.NPCListener;
import de.synccloud.signconnector.listener.PermissionListener;
import de.synccloud.signconnector.listener.PlayerJoin;
import de.synccloud.signconnector.manager.NPCManager;
import de.synccloud.signconnector.mysql.MySQLConfig;
import de.synccloud.signconnector.mysql.MySQLManager;
import de.synccloud.signconnector.permission.PermissionManager;
import de.synccloud.signconnector.permission.command.PermissionCommand;
import de.synccloud.signconnector.permission.command.PermissionMessageListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.sql.*;
import java.util.*;

public class SignSystem extends JavaPlugin implements Listener {

    private final Map<Location, String> signServers = new HashMap<>();
    private final Map<String, Integer> serverPlayerCount = new HashMap<>();
    private final Map<Location, Integer> animationFrames = new HashMap<>();
    private final Map<String, Integer> groupMaxPlayers = new HashMap<>();
    private MySQLManager mySQLManager;
    private NPCManager npcManager;
    private JumpITServerManager serverManager;




    private PermissionManager permissionManager;



    @Override
    public void onEnable() {

        File mysqlFile = new File("plugins/CloudBridge/mysql.json");
        MySQLConfig config = MySQLConfig.loadFromFile(mysqlFile);
        mySQLManager = new MySQLManager(this.getDataFolder());
        mySQLManager.connect();
        mySQLManager.createNPCTable();
        


        if (config == null){
            getLogger().severe("Keine MySQL-Konfiguration gefunden. Abbruch.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        permissionManager = new PermissionManager(this,config);
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "my:channel", new PermissionMessageListener(this));
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "my:channel");
        getServer().getPluginManager().registerEvents(new PermissionListener(permissionManager), this);
        serverManager = new JumpITServerManager(this);

        // PluginMessage Listener registrieren (um Antwort auf GetServers zu bekommen)
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", (channel, player, message) -> {
            if (!channel.equals("BungeeCord")) return;

            try (var in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(message))) {
                String subChannel = in.readUTF();
                if (subChannel.equals("GetServers")) {
                    String serverList = in.readUTF();
                    new BukkitRunnable() {

                        @Override
                        public void run() {
                            serverManager.updateServerList(serverList);
                        }
                    }.runTaskTimer(this,0,20*2);
                    serverManager.updateServerList(serverList);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // PluginMessage Sender registrieren
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        //
        getServer().getPluginManager().registerEvents(new NPCListener(this, serverManager), this);



        Bukkit.getPluginManager().registerEvents(this, this);
   //     VillagerServerConnector.setMySQLManager(mySQLManager);

     //   Bukkit.getPluginManager().registerEvents(new VillagerServerConnector(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoin(permissionManager), this);
        getCommand("setsign").setExecutor(new SetSignCommand(this));
        getCommand("group").setExecutor(new PermissionCommand(permissionManager));
        getCommand("perm").setExecutor(new PermissionCommand(permissionManager));


        NPCManager npcManager = new NPCManager(this, mySQLManager);
        getCommand("setnpc").setExecutor(new SetNPCCommand(npcManager));

        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", (channel, player, message) -> {
            if (!channel.equals("BungeeCord")) return;

            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
                String subchannel = in.readUTF();

                if (subchannel.equals("PlayerCount")) {
                    if (in.available() < 2) {

                        return;
                    }

                    try {
                        String server = in.readUTF();
                        int count = in.readInt();
                        serverPlayerCount.put(server, count);
                    } catch (IOException e) {
                    }
                }
            } catch (IOException e) {
            }
        });


        loadNPCsFromDatabase();
        loadSignsFromDatabase();
        initializeSignsInWorld();   // Neu: Schilder mit Grundtext setzen
      //  loadSigns();
        startUpdater();
        startLoadingAnimation();
    }



    @Override
    public void onDisable() {
        mySQLManager.disconnect();

        saveSigns();
    }


    public PermissionManager getPermissionManager() {
        return permissionManager;
    }


    public void loadNPCsFromDatabase() {
        try (Connection connection = mySQLManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT * FROM npcs");
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String name = rs.getString("name");
                // Server holen wir jetzt erst in spawnNPC, also hier nicht nötig

                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                float yaw = rs.getFloat("yaw");
                float pitch = rs.getFloat("pitch");
                String worldName = rs.getString("world");

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    System.out.println("Welt nicht gefunden: " + worldName);
                    continue;
                }

                Location loc = new Location(world, x, y, z, yaw, pitch);
                spawnNPC(loc, name, false);  // Server wird in spawnNPC aus der DB geholt, kein erneutes speichern
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void spawnNPC(Location loc, String name, boolean saveToDatabase) {
        // Server aus MySQL holen anhand des NPC-Namens
        String server = mySQLManager.getServerByNPCName(name);

        if (server == null) {
            System.out.println("[NPC] Kein Servername in MySQL für NPC '" + name + "' gefunden.");
            return;
        }

        Villager villager = loc.getWorld().spawn(loc, Villager.class);
        villager.setCustomName("§e" + name);
        villager.setCustomNameVisible(true);
        villager.setInvulnerable(true);
        villager.setAI(false);
        villager.setCollidable(false);
        villager.setProfession(Villager.Profession.NITWIT);
        villager.setVillagerLevel(1);
        villager.setSilent(true);
        villager.setVillagerType(Villager.Type.PLAINS);

        NamespacedKey key = new NamespacedKey(this, "npc_server");
        villager.getPersistentDataContainer().set(key, PersistentDataType.STRING, server);

        if (saveToDatabase) {
            mySQLManager.insertNPC(name, server, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), loc.getWorld().getName());
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
                SignSystem.this.requestServerCounts();
                SignSystem.this.updateSigns();
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

               // getLogger().info("Update Sign at " + loc + " for server " + server + ": count=" + count);

                // Prüfe ob Server gestartet ist
                boolean started = isServerStarted(server);
                if (!started) {
                    animationFrames.putIfAbsent(loc, 0);
                    continue;
                }

                int maxPlayers = groupMaxPlayers.getOrDefault(baseName.toLowerCase(), -1);
                if (maxPlayers == -1) {
                    maxPlayers = getMaxPlayersFromDatabase(baseName);
                    if (maxPlayers == -1) maxPlayers = 20;
                    groupMaxPlayers.put(baseName.toLowerCase(), maxPlayers);
                }



                if (count == -1 || count >= maxPlayers) {
                    String nextServer = findNextAvailableServer(baseName, server);
                    if (nextServer != null) {
                        signServers.put(loc, nextServer);
                        saveSigns();
                        server = nextServer;
                        count = serverPlayerCount.getOrDefault(server, -1);
                    } else {
                        animationFrames.putIfAbsent(loc, 0);
                        continue;
                    }
                }

                if (loc.getBlock().getState() instanceof Sign sign) {
                    if (count >= 0) {
                        sign.setLine(0, "§a" + server);
                        sign.setLine(1, "§7ONLINE §a✓");
                        sign.setLine(2, "§eSpieler");
                        sign.setLine(3, "§b" + count + "/" + maxPlayers);
                        sign.update();
                        animationFrames.remove(loc);
                    } else {
                        animationFrames.putIfAbsent(loc, 0);
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

    public void connectPlayer(Player player, String server) {
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
    public Map<String, Integer> getServerPlayerCounts() {
        return serverPlayerCount;
    }
}
