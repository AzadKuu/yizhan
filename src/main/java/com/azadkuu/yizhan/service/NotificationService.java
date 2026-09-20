package com.azadkuu.yizhan.service;

import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.model.Notification;
import com.azadkuu.yizhan.model.Station;
import com.azadkuu.yizhan.storage.Storage;
import com.azadkuu.yizhan.util.Msg;
import com.azadkuu.yizhan.util.TimeUtil;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    public void flushAll(Collection<? extends Player> players) {
        if (players == null || players.isEmpty()) {
            return;
        }
        List<UUID> uuids = new ArrayList<>();
        for (Player p : players) {
            uuids.add(p.getUniqueId());
        }
        Map<UUID, List<Notification>> batch = storage.claimNotificationsBatch(uuids);
        for (Player player : players) {
            List<Notification> pending = batch.get(player.getUniqueId());
            if (pending == null || pending.isEmpty()) {
                continue;
            }
            for (Notification notification : pending) {
                Msg.send(player, config.getPrefix(), notification.getMessage());
            }
        }
    }

    public String shipStart(long id, String to, int bufferSeconds) {
        return config.getShipStartMessage()
                .replace("%id%", Long.toString(id))
                .replace("%to%", resolveStationTitle(to))
                .replace("%buffer%", TimeUtil.formatSeconds(bufferSeconds));
    }

    public String shipArrived(long id, String station) {
        return config.getShipArrivedMessage()
                .replace("%id%", Long.toString(id))
                .replace("%station%", resolveStationTitle(station));
    }

    public String shipWaiting(long id, String station) {
        return config.getShipWaitingMessage()
                .replace("%id%", Long.toString(id))
                .replace("%station%", resolveStationTitle(station));
    }

    public String shipDiscarded(long id, String station) {
        int hours = Math.max(1, config.getShipmentDiscardAfterHours());
        return config.getShipDiscardedMessage()
                .replace("%id%", Long.toString(id))
                .replace("%station%", resolveStationTitle(station))
                .replace("%hours%", Integer.toString(hours));
    }

    private String resolveStationTitle(String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return "";
        }
        Station station = storage.getStation(stationId);
        if (station == null || station.getTitle() == null || station.getTitle().isBlank()) {
            return stationId;
        }
        return station.getTitle();
    }
}
