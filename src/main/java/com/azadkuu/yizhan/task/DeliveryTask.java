package com.azadkuu.yizhan.task;

import com.azadkuu.yizhan.YizhanPlugin;
import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.gui.GuiManager;
import com.azadkuu.yizhan.model.DeliveryResult;
import com.azadkuu.yizhan.model.Shipment;
import com.azadkuu.yizhan.model.Station;
import com.azadkuu.yizhan.service.NotificationService;
import com.azadkuu.yizhan.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class DeliveryTask implements Runnable {

    private final YizhanPlugin plugin;
    private final PluginConfig config;
    private final Storage storage;
    private final GuiManager guiManager;
    private final NotificationService notificationService;
    private final int batchSize;

    public DeliveryTask(YizhanPlugin plugin, PluginConfig config, Storage storage, GuiManager guiManager,
                        NotificationService notificationService, int batchSize) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.guiManager = guiManager;
        this.notificationService = notificationService;
        this.batchSize = batchSize;
    }

    @Override
    public void run() {
        returnStuckShipments();
        deliverDueShipments();
    }

    private void returnStuckShipments() {
        List<Shipment> stuck;
        try {
            long deadline = System.currentTimeMillis() - config.getShipmentReturnAfterSeconds() * 1000L;
            stuck = storage.listShipmentsStuckFull(batchSize, deadline);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("poll stuck shipments failed: " + ex.getMessage());
            return;
        }
        for (Shipment shipment : stuck) {
            boolean returned;
            try {
                returned = storage.returnShipment(shipment.getId(), config.getMailboxSize());
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("return shipment #" + shipment.getId() + " failed: " + ex.getMessage());
                continue;
            }
            if (!returned) {
                continue;
            }
            UUID owner = shipment.getOwner();
            plugin.getLogger().warning("shipment #" + shipment.getId() + " 目标驿站 " + shipment.getToStation()
                    + " 已满超时，包裹已退回发件人邮箱 (owner=" + owner + ")");
            if (owner != null) {
                try {
                    notificationService.notify(owner,
                            notificationService.shipReturned(shipment.getId(), shipment.getToStation()));
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("push return notification failed: " + ex.getMessage());
                }
            }
            flushOwner(owner);
        }
    }

    private void deliverDueShipments() {
        List<Shipment> due;
        try {
            due = storage.listDueShipments(batchSize);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("poll due shipments failed: " + ex.getMessage());
            return;
        }
        if (due.isEmpty()) {
            return;
        }
        for (Shipment shipment : due) {
            DeliveryResult result;
            try {
                result = storage.deliverShipment(shipment.getId());
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("deliver shipment #" + shipment.getId() + " failed: " + ex.getMessage());
                continue;
            }
            if (result == DeliveryResult.WAITING_FULL) {
                handleWaitingFull(shipment);
                continue;
            }
            if (result != DeliveryResult.DELIVERED) {
                continue;
            }
            final UUID owner = shipment.getOwner();
            final String message = notificationService.shipArrived(shipment.getId(), shipment.getToStation());
            try {
                notificationService.notify(owner, message);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("push arrival notification failed: " + ex.getMessage());
            }
            plugin.getLogger().info("shipment #" + shipment.getId() + " from " + shipment.getFromStation()
                    + " delivered to " + shipment.getToStation() + " (owner=" + owner + ")");
            Station target;
            try {
                target = storage.getStation(shipment.getToStation());
            } catch (RuntimeException ex) {
                target = null;
            }
            final Station finalTarget = target;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (finalTarget != null) {
                    guiManager.refreshStation(finalTarget);
                }
                if (owner != null) {
                    Player player = Bukkit.getPlayer(owner);
                    if (player != null && player.isOnline()) {
                        notificationService.flush(player);
                    }
                }
            });
        }
    }

    private void handleWaitingFull(Shipment shipment) {
        boolean first;
        try {
            first = storage.markShipmentFull(shipment.getId());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("mark shipment #" + shipment.getId() + " full failed: " + ex.getMessage());
            return;
        }
        if (!first) {
            return;
        }
        UUID owner = shipment.getOwner();
        plugin.getLogger().warning("shipment #" + shipment.getId() + " 目标驿站 " + shipment.getToStation()
                + " 已满，等待空位 (owner=" + owner + ")");
        if (owner != null) {
            try {
                notificationService.notify(owner,
                        notificationService.shipWaiting(shipment.getId(), shipment.getToStation()));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("push waiting notification failed: " + ex.getMessage());
            }
        }
        flushOwner(owner);
    }

    private void flushOwner(UUID owner) {
        if (owner == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline()) {
                notificationService.flush(player);
            }
        });
    }
}
