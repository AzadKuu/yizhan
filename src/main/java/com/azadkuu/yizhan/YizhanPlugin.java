package com.azadkuu.yizhan;

import com.azadkuu.yizhan.command.YizhanCommand;
import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.gui.GuiManager;
import com.azadkuu.yizhan.listener.InteractListener;
import com.azadkuu.yizhan.listener.InventoryListener;
import com.azadkuu.yizhan.listener.PlayerJoinListener;
import com.azadkuu.yizhan.service.ItemFilter;
import com.azadkuu.yizhan.service.NotificationService;
import com.azadkuu.yizhan.service.TransportService;
import com.azadkuu.yizhan.storage.MysqlStorage;
import com.azadkuu.yizhan.storage.Storage;
import com.azadkuu.yizhan.task.DeliveryTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class YizhanPlugin extends JavaPlugin {

    private PluginConfig config;
    private Storage storage;
    private ItemFilter itemFilter;
    private TransportService transport;
    private NotificationService notificationService;
    private GuiManager guiManager;
    private int deliveryTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = new PluginConfig();
        this.config.load(getConfig());

        this.storage = new MysqlStorage(config);
        try {
            storage.init();
        } catch (Exception ex) {
            getLogger().severe("数据库初始化失败，插件将被禁用: " + ex.getMessage());
            ex.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.itemFilter = new ItemFilter(config, msg -> getLogger().info("[debug] " + msg));
        this.transport = new TransportService(config, storage);
        this.notificationService = new NotificationService(config, storage);
        this.guiManager = new GuiManager(config, storage, transport, itemFilter);

        YizhanCommand command = new YizhanCommand(this, config, storage, transport, guiManager, itemFilter);
        PluginCommand pluginCommand = getCommand("yizhan");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new InteractListener(config, storage, guiManager), this);
        getServer().getPluginManager().registerEvents(
                new InventoryListener(this, config, storage, itemFilter, transport, notificationService, guiManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(this, notificationService), this);

        long interval = 20L * config.getPollIntervalSeconds();
        this.deliveryTaskId = getServer().getScheduler()
                .runTaskTimerAsynchronously(this,
                        new DeliveryTask(this, storage, guiManager, notificationService, 64), interval, interval)
                .getTaskId();

        getLogger().info("Yizhan 已启用, server-id=" + config.getServerId()
                + ", 轮询间隔=" + config.getPollIntervalSeconds() + "s"
                + ", 默认缓冲=" + config.getDefaultBufferSeconds() + "s"
                + ", debug=" + config.isDebug());
        logItemFilter();
    }

    @Override
    public void onDisable() {
        if (deliveryTaskId != -1) {
            getServer().getScheduler().cancelTask(deliveryTaskId);
            deliveryTaskId = -1;
        }
        if (storage != null) {
            storage.close();
            storage = null;
        }
        getLogger().info("Yizhan 已禁用");
    }

    public void reload() {
        reloadConfig();
        config.load(getConfig());
        getLogger().info("配置已重载, debug=" + config.isDebug());
        logItemFilter();
    }

    private void logItemFilter() {
        getLogger().info("物品过滤配置: namespaces=" + config.getBlockedNamespaces()
                + ", keys=" + config.getBlockedKeys()
                + ", block-custom-model-data=" + config.isBlockCustomModelData());
    }

    public PluginConfig getPluginConfig() {
        return config;
    }

    public Storage getStorage() {
        return storage;
    }
}
