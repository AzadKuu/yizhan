package com.azadkuu.yizhan.service;

import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.model.Notification;
import com.azadkuu.yizhan.storage.Storage;
import com.azadkuu.yizhan.util.Msg;
import com.azadkuu.yizhan.util.TimeUtil;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class NotificationService {

    private final PluginConfig config;
    private final Storage storage;

    public NotificationService(PluginConfig config, Storage storage) {
        this.config = config;
        this.storage = storage;
    }

    public void notify(UUID player, String message) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        storage.pushNotification(player, message);
    }

    public void flush(Player player) {
        List<Notification> pending = storage.claimNotifications(player.getUniqueId());
        for (Notification notification : pending) {
            Msg.send(player, config.getPrefix(), notification.getMessage());
        }
    }

    public String shipStart(long id, String to, int bufferSeconds) {
        return config.getShipStartMessage()
                .replace("%id%", Long.toString(id))
                .replace("%to%", to == null ? "" : to)
                .replace("%buffer%", TimeUtil.formatSeconds(bufferSeconds));
    }

    public String shipArrived(long id, String station) {
        return config.getShipArrivedMessage()
                .replace("%id%", Long.toString(id))
                .replace("%station%", station == null ? "" : station);
    }

    public String shipWaiting(long id, String station) {
        return config.getShipWaitingMessage()
                .replace("%id%", Long.toString(id))
                .replace("%station%", station == null ? "" : station);
    }

    public String shipDiscarded(long id, String station) {
        int hours = Math.max(1, config.getShipmentDiscardAfterHours());
        return config.getShipDiscardedMessage()
                .replace("%id%", Long.toString(id))
                .replace("%station%", station == null ? "" : station)
                .replace("%hours%", Integer.toString(hours));
    }
}
