package com.azadkuu.yizhan.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class MailSendHolder implements InventoryHolder {

    private final UUID target;
    private final String targetName;
    private final int inputSlots;
    private Inventory inventory;
    private boolean confirmed;

    public MailSendHolder(UUID target, String targetName, int inputSlots) {
        this.target = target;
        this.targetName = targetName;
        this.inputSlots = inputSlots;
    }

    public UUID getTarget() {
        return target;
    }

    public String getTargetName() {
        return targetName;
    }

    public int getInputSlots() {
        return inputSlots;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
