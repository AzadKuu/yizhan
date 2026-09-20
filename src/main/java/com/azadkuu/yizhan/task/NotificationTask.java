package com.azadkuu.yizhan.task;

import com.azadkuu.yizhan.YizhanPlugin;
import com.azadkuu.yizhan.service.NotificationService;
import org.bukkit.Bukkit;

public class NotificationTask implements Runnable {

    private final YizhanPlugin plugin;
    private final NotificationService notificationService;

    public NotificationTask(YizhanPlugin plugin, NotificationService notificationService) {
        this.plugin = plugin;
        this.notificationService = notificationService;
    }

    @Override
    public void run() {
        try {
            notificationService.flushAll(Bukkit.getOnlinePlayers());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("flush notifications failed: " + ex.getMessage());
        }
    }
}
