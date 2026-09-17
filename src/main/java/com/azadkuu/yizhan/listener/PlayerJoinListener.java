package com.azadkuu.yizhan.listener;

import com.azadkuu.yizhan.YizhanPlugin;
import com.azadkuu.yizhan.service.MailboxService;
import com.azadkuu.yizhan.service.NotificationService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final YizhanPlugin plugin;
    private final NotificationService notificationService;
    private final MailboxService mailboxService;

    public PlayerJoinListener(YizhanPlugin plugin, NotificationService notificationService,
                              MailboxService mailboxService) {
        this.plugin = plugin;
        this.notificationService = notificationService;
        this.mailboxService = mailboxService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                notificationService.flush(player);
                mailboxService.grantDailyReward(player);
            }
        }, 20L);
    }
}
