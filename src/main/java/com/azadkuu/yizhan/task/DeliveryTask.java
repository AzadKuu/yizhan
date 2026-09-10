package com.azadkuu.yizhan.task;

import com.azadkuu.yizhan.YizhanPlugin;
import com.azadkuu.yizhan.gui.GuiManager;
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
    private final Storage storage;
    private final GuiManager guiManager;
    private final NotificationService notificationService;
    private final int batchSize;

    public DeliveryTask(YizhanPlugin plugin, Storage storage, GuiManager guiManager,
                        NotificationService notificationService, int batchSize) {
        this.plugin = plugin;
        this.storage = storage;
        this.guiManager = guiManager;
        this.notificationService = notificationService;
        this.batchSize = batchSize;
    }

    @Override
    public void run() {
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
            boolean delivered;
            try {
                delivered = storage.deliverShipment(shipment.getId());
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("deliver shipment #" + shipment.getId() + " failed: " + ex.getMessage());
                continue;
            }
            if (!delivered) {
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
}
