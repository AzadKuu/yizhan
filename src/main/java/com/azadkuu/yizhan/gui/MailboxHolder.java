package com.azadkuu.yizhan.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MailboxHolder implements InventoryHolder {

    private final UUID owner;
    private final Set<Integer> originalSlots = new HashSet<>();
    private Inventory inventory;
    private int pendingCount;

    public MailboxHolder(UUID owner) {
        this.owner = owner;
    }

    public UUID getOwner() {
        return owner;
    }

    public Set<Integer> getOriginalSlots() {
        return originalSlots;
    }

    public void setOriginalSlots(Set<Integer> slots) {
        originalSlots.clear();
        if (slots != null) {
            originalSlots.addAll(slots);
        }
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount) {
        this.pendingCount = pendingCount;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
