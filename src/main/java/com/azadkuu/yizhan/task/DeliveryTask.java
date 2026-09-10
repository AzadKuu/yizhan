package com.azadkuu.yizhan.task;

import com.azadkuu.yizhan.YizhanPlugin;
import com.azadkuu.yizhan.gui.GuiManager;
import com.azadkuu.yizhan.model.Shipment;
import com.azadkuu.yizhan.model.Station;
import com.azadkuu.yizhan.storage.Storage;
import org.bukkit.Bukkit;

import java.util.List;

public class DeliveryTask implements Runnable {

    private final YizhanPlugin plugin;
    private final Storage storage;
    private final GuiManager guiManager;
    private final int batchSize;

    public DeliveryTask(YizhanPlugin plugin, Storage storage, GuiManager guiManager, int batchSize) {
        this.plugin = plugin;
        this.storage = storage;
        this.guiManager = guiManager;
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
            plugin.getLogger().info("shipment #" + shipment.getId() + " from " + shipment.getFromStation()
                    + " delivered to " + shipment.getToStation() + " (owner=" + shipment.getOwner() + ")");
            Station target;
            try {
                target = storage.getStation(shipment.getToStation());
            } catch (RuntimeException ex) {
                target = null;
            }
            if (target != null) {
                Station station = target;
                Bukkit.getScheduler().runTask(plugin, () -> guiManager.refreshStation(station));
            }
        }
    }
}
