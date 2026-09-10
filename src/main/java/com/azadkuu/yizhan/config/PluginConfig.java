package com.azadkuu.yizhan.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PluginConfig {

    private String serverId;
    private int pollIntervalSeconds;
    private int defaultBufferSeconds;
    private boolean allowCancelShipment;
    private int defaultStationSize;
    private int maxStationSize;
    private String prefix;

    private String dbHost;
    private int dbPort;
    private String dbName;
    private String dbUser;
    private String dbPassword;
    private String tablePrefix;
    private int dbPoolSize;
    private boolean dbUseSsl;
    private long dbConnectionTimeoutMs;

    private final Set<String> blockedNamespaces = new HashSet<>();
    private final Set<String> blockedKeys = new HashSet<>();
    private boolean blockCustomModelData;

    public void load(FileConfiguration cfg) {
        this.serverId = cfg.getString("server-id", "server-1");
        this.pollIntervalSeconds = Math.max(1, cfg.getInt("poll-interval-seconds", 3));
        this.defaultBufferSeconds = Math.max(1, cfg.getInt("default-buffer-seconds", 300));
        this.allowCancelShipment = cfg.getBoolean("allow-cancel-shipment", false);
        this.defaultStationSize = clampSize(cfg.getInt("default-station-size", 27));
        this.maxStationSize = clampSize(cfg.getInt("max-station-size", 45));
        this.prefix = cfg.getString("language.prefix", "&8[&6驿站&8] &r");

        this.dbHost = cfg.getString("database.host", "127.0.0.1");
        this.dbPort = cfg.getInt("database.port", 3306);
        this.dbName = cfg.getString("database.database", "yizhan");
        this.dbUser = cfg.getString("database.user", "root");
        this.dbPassword = cfg.getString("database.password", "");
        this.tablePrefix = cfg.getString("database.table-prefix", "yz_");
        this.dbPoolSize = Math.max(1, cfg.getInt("database.pool-size", 6));
        this.dbUseSsl = cfg.getBoolean("database.use-ssl", false);
        this.dbConnectionTimeoutMs = cfg.getLong("database.connection-timeout-ms", 10000L);

        blockedNamespaces.clear();
        blockedKeys.clear();
        List<String> ns = cfg.getStringList("item-filter.blocked-namespaces");
        for (String s : ns) {
            if (s != null && !s.isBlank()) {
                blockedNamespaces.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        List<String> keys = cfg.getStringList("item-filter.blocked-keys");
        for (String s : keys) {
            if (s != null && !s.isBlank()) {
                blockedKeys.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        this.blockCustomModelData = cfg.getBoolean("item-filter.block-custom-model-data", false);
    }

    private int clampSize(int value) {
        if (value < 9) {
            return 9;
        }
        if (value > 45) {
            return 45;
        }
        int rows = (value + 8) / 9;
        return rows * 9;
    }

    public String getServerId() {
        return serverId;
    }

    public int getPollIntervalSeconds() {
        return pollIntervalSeconds;
    }

    public int getDefaultBufferSeconds() {
        return defaultBufferSeconds;
    }

    public boolean isAllowCancelShipment() {
        return allowCancelShipment;
    }

    public int getDefaultStationSize() {
        return defaultStationSize;
    }

    public int getMaxStationSize() {
        return maxStationSize;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDbHost() {
        return dbHost;
    }

    public int getDbPort() {
        return dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public int getDbPoolSize() {
        return dbPoolSize;
    }

    public boolean isDbUseSsl() {
        return dbUseSsl;
    }

    public long getDbConnectionTimeoutMs() {
        return dbConnectionTimeoutMs;
    }

    public Set<String> getBlockedNamespaces() {
        return blockedNamespaces;
    }

    public Set<String> getBlockedKeys() {
        return blockedKeys;
    }

    public boolean isBlockCustomModelData() {
        return blockCustomModelData;
    }

    public List<String> describeFilter() {
        List<String> out = new ArrayList<>(blockedNamespaces);
        out.addAll(blockedKeys);
        return out;
    }
}
