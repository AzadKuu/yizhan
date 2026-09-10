package com.azadkuu.yizhan.util;

import org.bukkit.inventory.ItemStack;

public final class ItemSerializer {

    private ItemSerializer() {
    }

    public static byte[] serialize(ItemStack item) {
        return item.serializeAsBytes();
    }

    public static ItemStack deserialize(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        return ItemStack.deserializeBytes(data);
    }
}
