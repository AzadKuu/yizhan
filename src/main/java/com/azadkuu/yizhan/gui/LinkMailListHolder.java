package com.azadkuu.yizhan.gui;

import com.azadkuu.yizhan.model.LinkMail;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.UUID;

public class LinkMailListHolder implements InventoryHolder {

    private final UUID owner;
    private final int page;
    private final List<LinkMail> mails;
    private Inventory inventory;

    public LinkMailListHolder(UUID owner, int page, List<LinkMail> mails) {
        this.owner = owner;
        this.page = page;
        this.mails = mails;
    }

    public UUID getOwner() {
        return owner;
    }

    public int getPage() {
        return page;
    }

    public List<LinkMail> getMails() {
        return mails;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
