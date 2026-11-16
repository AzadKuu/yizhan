package com.azadkuu.yizhan.gui;

import com.azadkuu.yizhan.model.MailContact;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.UUID;

public class ContactsHolder implements InventoryHolder {

    private final UUID owner;
    private final int page;
    private final List<MailContact> contacts;
    private Inventory inventory;

    public ContactsHolder(UUID owner, int page, List<MailContact> contacts) {
        this.owner = owner;
        this.page = page;
        this.contacts = contacts;
    }

    public UUID getOwner() {
        return owner;
    }

    public int getPage() {
        return page;
    }

    public List<MailContact> getContacts() {
        return contacts;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
