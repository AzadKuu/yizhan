package com.azadkuu.yizhan.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class DiscardedHolder implements InventoryHolder {

    private final Map<Integer, Long> slotToId = new HashMap<>();
    private Inventory inventory;

    public Map<Integer, Long> getSlotToId() {
        return slotToId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
