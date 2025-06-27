package de.synccloud.signconnector.permission;

import de.synccloud.signconnector.mysql.MySQLConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.*;

public class PermissionManager {

    private final MySQLConfig config;
    private Connection connection;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();


    private final Map<UUID, Set<String>> playerPermissions = new HashMap<>();



    private final JavaPlugin plugin;



    private final Map<UUID, Set<String>> permissionCache = new HashMap<>();

    public PermissionManager(JavaPlugin plugin,MySQLConfig config) {
        this.plugin = plugin;

        this.config = config;
        connect();
    }

    private void connect() {
        try {
            String url = "jdbc:mysql://" + config.host + ":" + config.port + "/" + config.database + "?useSSL=false&autoReconnect=true";
            this.connection = DriverManager.getConnection(url, config.user, config.password);
            System.out.println("[Permissions] Erfolgreich mit der Datenbank verbunden.");
        } catch (SQLException e) {
            System.err.println("[Permissions] Fehler bei der Datenbankverbindung:");
            e.printStackTrace();
        }
    }

    public Set<String> getPermissions(UUID playerUUID){
        return playerPermissions.getOrDefault(playerUUID, Collections.emptySet());
    }

    public void setPermissions(UUID playerUUID, Set<String> permissions){
        playerPermissions.put(playerUUID, permissions);
    }
    public void applyPermissionsToPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Set<String> perms = permissionCache.get(uuid);
        if (perms == null) return;

        // Vorherige Rechte entfernen
        if (attachments.containsKey(uuid)) {
            player.removeAttachment(attachments.get(uuid));
        }

        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(uuid, attachment);

        // Rechte setzen
        for (String perm : perms) {
            if (perm.equals("*")) {
                // '*' = OP geben, damit ALLE Rechte gelten
                player.setOp(true);
            } else {
                attachment.setPermission(perm, true);
            }
        }

        // Kein * = OP weg
        if (!perms.contains("*")) {
            player.setOp(false);
        }
    }


    public void loadPermissions(UUID uuid) throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }

        Set<String> perms = new HashSet<>();

        // Einzelne Spieler-Permissions laden
        PreparedStatement stmt = connection.prepareStatement("SELECT permission FROM permissions WHERE uuid=?");
        stmt.setString(1, uuid.toString());
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            perms.add(rs.getString("permission").toLowerCase());
        }

        // Gruppenzugehörigkeit laden
        stmt = connection.prepareStatement("SELECT group_name FROM player_groups WHERE uuid=?");
        stmt.setString(1, uuid.toString());
        rs = stmt.executeQuery();
        while (rs.next()) {
            String group = rs.getString("group_name");

            // Gruppen-Permissions laden
            PreparedStatement groupStmt = connection.prepareStatement("SELECT permission FROM group_permissions WHERE group_name=?");
            groupStmt.setString(1, group);
            ResultSet groupRs = groupStmt.executeQuery();
            while (groupRs.next()) {
                perms.add(groupRs.getString("permission").toLowerCase());
            }
            groupRs.close();
            groupStmt.close();
        }
        rs.close();
        stmt.close();

        permissionCache.put(uuid, perms);

        // ✨ Automatisches OP setzen bei '*'
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            if (perms.contains("*")) {
                player.setOp(true);
            } else {
                player.setOp(false);
            }
        }
    }

    public boolean hasPermission(UUID uuid, String perm) {
        Set<String> perms = permissionCache.get(uuid);
        if (perms == null) return false;

        perm = perm.toLowerCase();

        if (perms.contains("*")) return true;
        if (perms.contains(perm)) return true;

        // Wildcard
        String[] parts = perm.split("\\.");
        StringBuilder wildcard = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            wildcard.append(parts[i]).append(".");
            if (perms.contains(wildcard.toString() + "*")) return true;
        }

        return false;
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        return connection;
    }

    // ---------------- Gruppen & Permissions ----------------

    public boolean createGroup(String group) throws SQLException {
        PreparedStatement ps = getConnection().prepareStatement("INSERT INTO permsgroups(name) VALUES(?)");
        ps.setString(1, group);
        return ps.executeUpdate() > 0;
    }

    public boolean deleteGroup(String group) throws SQLException {
        PreparedStatement ps1 = getConnection().prepareStatement("DELETE FROM permsgroups WHERE name=?");
        PreparedStatement ps2 = getConnection().prepareStatement("DELETE FROM group_permissions WHERE group_name=?");
        PreparedStatement ps3 = getConnection().prepareStatement("DELETE FROM player_groups WHERE group_name=?");

        ps1.setString(1, group);
        ps2.setString(1, group);
        ps3.setString(1, group);

        ps1.executeUpdate();
        ps2.executeUpdate();
        ps3.executeUpdate();
        return true;
    }

    public boolean addGroupPermission(String group, String permission) throws SQLException {
        PreparedStatement ps = getConnection().prepareStatement("INSERT INTO group_permissions(group_name, permission) VALUES (?, ?)");
        ps.setString(1, group);
        ps.setString(2, permission.toLowerCase());
        return ps.executeUpdate() > 0;
    }

    public boolean removeGroupPermission(String group, String permission) throws SQLException {
        PreparedStatement ps = getConnection().prepareStatement("DELETE FROM group_permissions WHERE group_name=? AND permission=?");
        ps.setString(1, group);
        ps.setString(2, permission.toLowerCase());
        return ps.executeUpdate() > 0;
    }

    public boolean assignGroup(UUID uuid, String group) throws SQLException {
        PreparedStatement ps = getConnection().prepareStatement("REPLACE INTO player_groups(uuid, group_name) VALUES (?, ?)");
        ps.setString(1, uuid.toString());
        ps.setString(2, group);
        return ps.executeUpdate() > 0;
    }

    public boolean removeGroup(UUID uuid) throws SQLException {
        PreparedStatement ps = getConnection().prepareStatement("DELETE FROM player_groups WHERE uuid=?");
        ps.setString(1, uuid.toString());
        return ps.executeUpdate() > 0;
    }

    public boolean addPlayerPermission(UUID uuid, String permission) throws SQLException {
        PreparedStatement ps = getConnection().prepareStatement("INSERT INTO permissions(uuid, permission) VALUES (?, ?)");
        ps.setString(1, uuid.toString());
        ps.setString(2, permission.toLowerCase());
        return ps.executeUpdate() > 0;
    }

    public boolean removePlayerPermission(UUID uuid, String permission) throws SQLException {
        PreparedStatement ps = getConnection().prepareStatement("DELETE FROM permissions WHERE uuid=? AND permission=?");
        ps.setString(1, uuid.toString());
        ps.setString(2, permission.toLowerCase());
        return ps.executeUpdate() > 0;
    }

    // ---------------- Erweiterungen ----------------

    public List<String> getAllGroups() throws SQLException {
        List<String> groups = new ArrayList<>();
        PreparedStatement ps = getConnection().prepareStatement("SELECT name FROM permsgroups");
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            groups.add(rs.getString("name"));
        }
        return groups;
    }

    public List<String> getGroupPermissions(String group) throws SQLException {
        List<String> perms = new ArrayList<>();
        PreparedStatement ps = getConnection().prepareStatement("SELECT permission FROM group_permissions WHERE group_name=?");
        ps.setString(1, group);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            perms.add(rs.getString("permission"));
        }
        return perms;
    }

    public List<String> getPlayerPermissions(UUID uuid) throws SQLException {
        List<String> perms = new ArrayList<>();
        PreparedStatement ps = getConnection().prepareStatement("SELECT permission FROM permissions WHERE uuid=?");
        ps.setString(1, uuid.toString());
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            perms.add(rs.getString("permission"));
        }
        return perms;
    }

    public String getPlayerGroup(UUID uuid) throws SQLException {
        PreparedStatement ps = getConnection().prepareStatement("SELECT group_name FROM player_groups WHERE uuid=?");
        ps.setString(1, uuid.toString());
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            return rs.getString("group_name");
        }
        return null;
    }
}
