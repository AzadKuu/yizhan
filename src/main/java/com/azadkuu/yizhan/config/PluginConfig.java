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
    private int shipmentDiscardAfterHours;
    private int defaultStationSize;
    private int maxStationSize;
    private String prefix;
    private String shipStartMessage;
    private String shipArrivedMessage;
    private String shipWaitingMessage;
    private String shipDiscardedMessage;
    private String mailReceivedMessage;
    private String mailReceivedFromMessage;
    private String mailSentMessage;
    private String itemBlockedMessage;
    private String itemBlockedInSendMessage;
    private String shipEmptyMessage;
    private String noRouteMessage;
    private String shipFailedMessage;
    private String stationNotFoundMessage;
    private String stationCapacityExceededMessage;
    private String claimSuccessMessage;
    private String mailboxEmptyMessage;
    private String claimFailedMessage;
    private String receiveOnlyMessage;
    private String feeSlotOccupiedMessage;
    private String feeInsufficientMessage;
    private String routeSingleMessage;
    private String routeSwitchedMessage;
    private String reclaimFailedMessage;
    private String reclaimSuccessMessage;
    private String reclaimPendingMessage;
    private String reclaimNoneMessage;
    private String saveConflictMessage;
    private String saveFailedMessage;
    private String noPermissionMessage;
    private String playerOnlyMessage;
    private String stationNotExistMessage;
    private String stationExistsMessage;
    private String noItemInHandMessage;
    private String amountInvalidMessage;
    private String amountPositiveMessage;
    private String playerNotFoundMessage;
    private String itemInvalidMessage;
    private String configReloadedMessage;

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
    private String currencyValue;
    private String currencyName;

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
        this.shipmentDiscardAfterHours = Math.max(1, cfg.getInt("shipment.discard-after-hours", 24));
        this.defaultStationSize = clampSize(cfg.getInt("default-station-size", 27));
        this.maxStationSize = clampSize(cfg.getInt("max-station-size", 45));
        this.prefix = cfg.getString("language.prefix", "&8[&6驿站&8] &r");
        this.shipStartMessage = cfg.getString("messages.ship-start",
                "&a发货成功 &7#%id% &7目的地 &f%to% &7预计 &f%buffer%&7后到达");
        this.shipArrivedMessage = cfg.getString("messages.ship-arrived",
                "&a你的包裹 &7#%id% &a已到达 &f%station% &a，请前往领取");
        this.shipWaitingMessage = cfg.getString("messages.ship-waiting",
                "&e目标驿站 &f%station% &e已满，包裹 &7#%id% &e正在等待空位，请提醒对方清理收件箱");
        this.shipDiscardedMessage = cfg.getString("messages.ship-discarded",
                "&c目标驿站 &f%station% &c已满超过 &f%hours% &c小时，包裹 &7#%id% &c投递失败已暂存，请联系管理员领取");
        this.mailReceivedMessage = cfg.getString("messages.mail-received",
                "&a你收到了邮件: &f%amount% &a个 &f%item%");
        this.mailReceivedFromMessage = cfg.getString("messages.mail-received-from",
                "&a你收到了邮件: &f%amount% &a个 &f%item%&a，来自 &f%sender%");
        this.mailSentMessage = cfg.getString("messages.mail-sent",
                "&a已发送 &f%amount% &a个 &f%item% &a到 &f%target% &a的邮箱");
        this.itemBlockedMessage = cfg.getString("messages.item-blocked", " &c被识别为自定义物品，禁止运输");
        this.itemBlockedInSendMessage = cfg.getString("messages.item-blocked-in-send", "&c发货区存在自定义物品 &f%item% &c，已阻止发货");
        this.shipEmptyMessage = cfg.getString("messages.ship-empty", "&7发货区是空的");
        this.noRouteMessage = cfg.getString("messages.no-route", "&c本站未配置路由，无法发货");
        this.shipFailedMessage = cfg.getString("messages.ship-failed", "&c发货失败，请稍后重试");
        this.stationNotFoundMessage = cfg.getString("messages.station-not-found", "&c目的地驿站 &f%station% &c不存在");
        this.stationCapacityExceededMessage = cfg.getString("messages.station-capacity-exceeded",
                "&c目标驿站 &f%station% &c容量不足：剩余 &f%free% &c格，本次需要 &f%need% &c格（收件箱已用 &f%used% &c，在途 &f%pending% &c）。请先清理目标驿站收件箱");
        this.claimSuccessMessage = cfg.getString("messages.claim-success", "&a已领取 &f%amount% &a组物品");
        this.mailboxEmptyMessage = cfg.getString("messages.mailbox-empty", "&7邮箱是空的");
        this.claimFailedMessage = cfg.getString("messages.claim-failed", "&c领取失败，请稍后重试");
        this.receiveOnlyMessage = cfg.getString("messages.receive-only", "&c收件箱只能取出，不能放入");
        this.feeSlotOccupiedMessage = cfg.getString("messages.fee-slot-occupied", "&7快递费槽已有物品，请先取出");
        this.feeInsufficientMessage = cfg.getString("messages.fee-insufficient", "&c快递费不足，需要 &f%need% &c个，当前只有 &f%have% &c个");
        this.routeSingleMessage = cfg.getString("messages.route-single", "&7当前只有一个可用路由");
        this.routeSwitchedMessage = cfg.getString("messages.route-switched", "&7当前目的地已切换为 &f%station%");
        this.reclaimFailedMessage = cfg.getString("messages.reclaim-failed", "&c重新领取失败，请稍后重试");
        this.reclaimSuccessMessage = cfg.getString("messages.reclaim-success", "&a已补入 &f%amount% &a件邮件");
        this.reclaimPendingMessage = cfg.getString("messages.reclaim-pending", "&7，仍有 &f%pending% &7件待领取（邮箱已满）");
        this.reclaimNoneMessage = cfg.getString("messages.reclaim-none", "&7没有可补入的邮件，请先清出邮箱空位");
        this.saveConflictMessage = cfg.getString("messages.save-conflict", "&c收件箱内容已被其他操作更新，本次更改未保存");
        this.saveFailedMessage = cfg.getString("messages.save-failed", "&c保存收件箱失败");
        this.noPermissionMessage = cfg.getString("messages.no-permission", "&c没有权限");
        this.playerOnlyMessage = cfg.getString("messages.player-only", "&c该命令只能由玩家执行");
        this.stationNotExistMessage = cfg.getString("messages.station-not-exist", "&c驿站 &f%station% &c不存在");
        this.stationExistsMessage = cfg.getString("messages.station-exists", "&c驿站 &f%station% &c已存在");
        this.noItemInHandMessage = cfg.getString("messages.no-item-in-hand", "&c主手没有物品");
        this.amountInvalidMessage = cfg.getString("messages.amount-invalid", "&c数量必须是整数");
        this.amountPositiveMessage = cfg.getString("messages.amount-positive", "&c数量必须大于 0");
        this.playerNotFoundMessage = cfg.getString("messages.player-not-found", "&c找不到玩家 &f%player% &c（可用玩家名或 UUID；从未进服的玩家请用 UUID）");
        this.itemInvalidMessage = cfg.getString("messages.item-invalid", "&c无效的物品ID或代号: &f%item%");
        this.configReloadedMessage = cfg.getString("messages.config-reloaded", "&a配置已重载");

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
        String currencyValue = cfg.getString("ship-fee.currency-value", "");
        this.currencyValue = currencyValue == null || currencyValue.isBlank()
                ? null : currencyValue.trim();
        String currencyName = cfg.getString("ship-fee.currency-name", "货币物品");
        this.currencyName = currencyName == null || currencyName.isBlank()
                ? "货币物品" : currencyName;
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

    public String getShipWaitingMessage() {
        return shipWaitingMessage;
    }

    public String getShipDiscardedMessage() {
        return shipDiscardedMessage;
    }

    public String getMailReceivedMessage() {
        return mailReceivedMessage;
    }

    public String getMailReceivedFromMessage() {
        return mailReceivedFromMessage;
    }

    public String getMailSentMessage() {
        return mailSentMessage;
    }

    public String getItemBlockedMessage() { return itemBlockedMessage; }
    public String getItemBlockedInSendMessage() { return itemBlockedInSendMessage; }
    public String getShipEmptyMessage() { return shipEmptyMessage; }
    public String getNoRouteMessage() { return noRouteMessage; }
    public String getShipFailedMessage() { return shipFailedMessage; }
    public String getStationNotFoundMessage() { return stationNotFoundMessage; }
    public String getStationCapacityExceededMessage() { return stationCapacityExceededMessage; }
    public String getClaimSuccessMessage() { return claimSuccessMessage; }
    public String getMailboxEmptyMessage() { return mailboxEmptyMessage; }
    public String getClaimFailedMessage() { return claimFailedMessage; }
    public String getReceiveOnlyMessage() { return receiveOnlyMessage; }
    public String getFeeSlotOccupiedMessage() { return feeSlotOccupiedMessage; }
    public String getFeeInsufficientMessage() { return feeInsufficientMessage; }
    public String getRouteSingleMessage() { return routeSingleMessage; }
    public String getRouteSwitchedMessage() { return routeSwitchedMessage; }
    public String getReclaimFailedMessage() { return reclaimFailedMessage; }
    public String getReclaimSuccessMessage() { return reclaimSuccessMessage; }
    public String getReclaimPendingMessage() { return reclaimPendingMessage; }
    public String getReclaimNoneMessage() { return reclaimNoneMessage; }
    public String getSaveConflictMessage() { return saveConflictMessage; }
    public String getSaveFailedMessage() { return saveFailedMessage; }
    public String getNoPermissionMessage() { return noPermissionMessage; }
    public String getPlayerOnlyMessage() { return playerOnlyMessage; }
    public String getStationNotExistMessage() { return stationNotExistMessage; }
    public String getStationExistsMessage() { return stationExistsMessage; }
    public String getNoItemInHandMessage() { return noItemInHandMessage; }
    public String getAmountInvalidMessage() { return amountInvalidMessage; }
    public String getAmountPositiveMessage() { return amountPositiveMessage; }
    public String getPlayerNotFoundMessage() { return playerNotFoundMessage; }
    public String getItemInvalidMessage() { return itemInvalidMessage; }
    public String getConfigReloadedMessage() { return configReloadedMessage; }

    public int getShipmentDiscardAfterHours() {
        return shipmentDiscardAfterHours;
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

    public String getCurrencyValue() {
        return currencyValue;
    }

    public String getCurrencyName() {
        return currencyName;
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
