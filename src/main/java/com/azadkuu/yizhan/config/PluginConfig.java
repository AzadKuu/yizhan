package com.azadkuu.yizhan.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PluginConfig {

    private String serverId;
    private boolean debug;
    private int pollIntervalSeconds;
    private int defaultBufferSeconds;
    private boolean allowCancelShipment;
    private int defaultStationSize;
    private int maxStationSize;
    private String prefix;
    private String shipStartMessage;
    private String shipArrivedMessage;

    private String dbHost;
    private int dbPort;
    private String dbName;
    private String dbUser;
    private String dbPassword;
    private String tablePrefix;
    private int dbPoolSize;
    private boolean dbUseSsl;
    private long dbConnectionTimeoutMs;

    private int mailboxSize;
    private boolean dailyRewardEnabled;
    private String dailyRewardMessage;
    private final List<DailyRewardItem> dailyRewardItems = new ArrayList<>();
    private String currencyKey;

    private final Set<String> blockedNamespaces = new HashSet<>();
    private final Set<String> blockedKeys = new HashSet<>();
    private final Set<String> blockedMaterials = new HashSet<>();
    private final List<AllowedItem> allowedItems = new ArrayList<>();
    private boolean blockCustomModelData;

    public void load(FileConfiguration cfg) {
        this.serverId = cfg.getString("server-id", "server-1");
        this.debug = cfg.getBoolean("debug", false);
        this.pollIntervalSeconds = Math.max(1, cfg.getInt("poll-interval-seconds", 3));
        this.defaultBufferSeconds = Math.max(1, cfg.getInt("default-buffer-seconds", 300));
        this.allowCancelShipment = cfg.getBoolean("allow-cancel-shipment", false);
        this.defaultStationSize = clampSize(cfg.getInt("default-station-size", 27));
        this.maxStationSize = clampSize(cfg.getInt("max-station-size", 45));
        this.prefix = cfg.getString("language.prefix", "&8[&6驿站&8] &r");
        this.shipStartMessage = cfg.getString("messages.ship-start",
                "&a发货成功 &7#%id% &7目的地 &f%to% &7预计 &f%buffer%&7后到达");
        this.shipArrivedMessage = cfg.getString("messages.ship-arrived",
                "&a你的包裹 &7#%id% &a已到达 &f%station% &a，请前往领取");

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
        allowedItems.clear();
        for (Map<?, ?> entry : cfg.getMapList("item-filter.allowed-items")) {
            if (entry == null) {
                continue;
            }
            Object rawKey = entry.get("key");
            Object rawValue = entry.get("value");
            if (rawKey == null || rawValue == null) {
                continue;
            }
            String key = String.valueOf(rawKey).trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                continue;
            }
            allowedItems.add(new AllowedItem(key, String.valueOf(rawValue)));
        }
        blockedMaterials.clear();
        for (String s : cfg.getStringList("item-filter.blocked-materials")) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String m = s.trim().toLowerCase(Locale.ROOT);
            if (m.startsWith("minecraft:")) {
                m = m.substring("minecraft:".length());
            }
            if (!m.isEmpty()) {
                blockedMaterials.add(m);
            }
        }

        this.mailboxSize = clampSize(cfg.getInt("mailbox.size", 45));
        this.dailyRewardEnabled = cfg.getBoolean("daily-reward.enabled", false);
        this.dailyRewardMessage = cfg.getString("daily-reward.message", "&a每日奖励已发放到你的邮箱");
        dailyRewardItems.clear();
        for (Map<?, ?> entry : cfg.getMapList("daily-reward.items")) {
            if (entry == null) {
                continue;
            }
            Object rawMaterial = entry.get("material");
            if (rawMaterial == null) {
                continue;
            }
            int amount = 1;
            Object rawAmount = entry.get("amount");
            if (rawAmount instanceof Number number) {
                amount = number.intValue();
            } else if (rawAmount != null) {
                try {
                    amount = Integer.parseInt(String.valueOf(rawAmount).trim());
                } catch (NumberFormatException ignored) {
                }
            }
            dailyRewardItems.add(new DailyRewardItem(String.valueOf(rawMaterial).trim(), Math.max(1, amount)));
        }
        String currency = cfg.getString("ship-fee.currency-key", "currency");
        this.currencyKey = currency == null || currency.isBlank()
                ? "currency" : currency.trim().toLowerCase(Locale.ROOT);
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

    public boolean isDebug() {
        return debug;
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

    public String getShipStartMessage() {
        return shipStartMessage;
    }

    public String getShipArrivedMessage() {
        return shipArrivedMessage;
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

    public List<AllowedItem> getAllowedItems() {
        return allowedItems;
    }

    public Set<String> getBlockedMaterials() {
        return blockedMaterials;
    }

    public int getMailboxSize() {
        return mailboxSize;
    }

    public boolean isDailyRewardEnabled() {
        return dailyRewardEnabled;
    }

    public String getDailyRewardMessage() {
        return dailyRewardMessage;
    }

    public List<DailyRewardItem> getDailyRewardItems() {
        return dailyRewardItems;
    }

    public String getCurrencyKey() {
        return currencyKey;
    }

    public List<String> describeFilter() {
        List<String> out = new ArrayList<>(blockedNamespaces);
        out.addAll(blockedKeys);
        return out;
    }

    public record AllowedItem(String key, String value) {
    }

    public record DailyRewardItem(String material, int amount) {
    }
}
