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
    /** 补领（重新领取溢出邮件）进行中：此期间所有点击都不处理，避免旧快照回灌复制。 */
    private boolean reclaiming;

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

    public boolean isReclaiming() {
        return reclaiming;
    }

    public void setReclaiming(boolean reclaiming) {
        this.reclaiming = reclaiming;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
