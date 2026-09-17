package com.azadkuu.yizhan.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class MailboxHolder implements InventoryHolder {

    private final UUID owner;
    private Inventory inventory;

    public MailboxHolder(UUID owner) {
        this.owner = owner;
    }

    public UUID getOwner() {
        return owner;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
