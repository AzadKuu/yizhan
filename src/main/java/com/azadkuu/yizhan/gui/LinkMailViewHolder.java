package com.azadkuu.yizhan.gui;

import com.azadkuu.yizhan.model.LinkMail;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class LinkMailViewHolder implements InventoryHolder {

    private final UUID owner;
    private final LinkMail mail;
    private Inventory inventory;

    public LinkMailViewHolder(UUID owner, LinkMail mail) {
        this.owner = owner;
        this.mail = mail;
    }

    public UUID getOwner() {
        return owner;
    }

    public LinkMail getMail() {
        return mail;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
