package de.synccloud.signconnector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
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
        return connection;
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
}
