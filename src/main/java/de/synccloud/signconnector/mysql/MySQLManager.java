package de.synccloud.signconnector.mysql;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MySQLManager {

    private final File pluginFolder; // Ordner wo das Plugin läuft
    private Connection connection;

    public MySQLManager(File pluginFolder) {
        this.pluginFolder = pluginFolder;
    }

    public void connect() {
        File configFile = new File(pluginFolder, "mysql.json");

        if (!configFile.exists()) {
            System.out.println("[MySQL] mysql.json nicht gefunden im Plugin-Ordner: " + configFile.getAbsolutePath());
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            Gson gson = new GsonBuilder().create();
            MySQLConfig config = gson.fromJson(reader, MySQLConfig.class);

            String host = config.getHost();
            int port = config.getPort();
            String database = config.getDatabase();
            String username = config.getUser();
            String password = config.getPassword();

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";

            if (password == null || password.isEmpty()) {
                connection = DriverManager.getConnection(url, username, "");
            } else {
                connection = DriverManager.getConnection(url, username, password);
            }

            System.out.println("[MySQL] Verbindung erfolgreich hergestellt.");
        } catch (IOException | SQLException e) {
            e.printStackTrace();
            System.out.println("[MySQL] Verbindung fehlgeschlagen.");
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect(); // Versuch neu zu verbinden
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return connection;
    }

    public String getServerByNPCName(String name) {
        String sql = "SELECT server FROM npcs WHERE name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            var rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("server");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[MySQL] Verbindung geschlossen.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static class MySQLConfig {
        private String host;
        private int port;
        private String user;
        private String database;
        private String password;

        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getUser() { return user; }
        public String getDatabase() { return database; }
        public String getPassword() { return password; }
    }
    public void insertNPC(String name, String server, double x, double y, double z, float yaw, float pitch, String world) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO npcs (name, server, x, y, z, yaw, pitch, world) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            );
            ps.setString(1, name);
            ps.setString(2, server);
            ps.setDouble(3, x);
            ps.setDouble(4, y);
            ps.setDouble(5, z);
            ps.setFloat(6, yaw);
            ps.setFloat(7, pitch);
            ps.setString(8, world);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public void createNPCTable() {
        String sql = "CREATE TABLE IF NOT EXISTS npcs (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "name VARCHAR(64) NOT NULL," +
                "server VARCHAR(64) NOT NULL," +
                "x DOUBLE NOT NULL," +
                "y DOUBLE NOT NULL," +
                "z DOUBLE NOT NULL," +
                "yaw FLOAT NOT NULL," +
                "pitch FLOAT NOT NULL," +
                "world VARCHAR(64) NOT NULL" +
                ")";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            System.out.println("[MYSQL] NPC-Tabelle erfolgreich überprüft/erstellt.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
