package de.synccloud.signconnector.mysql;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;

public class MySQLConfig {

    public String host;
    public int port;
    public String user;
    public String database;
    public String password;

    public static MySQLConfig loadFromFile(File file) {
        try (FileReader reader = new FileReader(file)) {
            return new Gson().fromJson(reader, MySQLConfig.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getHost() {
        return host;
    }

    public String getPassword() {
        return password;
    }

    public String getUser() {
        return user;
    }
}
