package com.azadkuu.yizhan.service;

import com.azadkuu.yizhan.config.PluginConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.Locale;
import java.util.Set;

public class ItemFilter {

    private final PluginConfig config;

    public ItemFilter(PluginConfig config) {
        this.config = config;
    }

    @SuppressWarnings("deprecation")
    public boolean isBlocked(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Set<String> namespaces = config.getBlockedNamespaces();
        Set<String> keys = config.getBlockedKeys();
        if (!namespaces.isEmpty() || !keys.isEmpty()) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            for (NamespacedKey key : pdc.getKeys()) {
                if (namespaces.contains(key.getNamespace().toLowerCase(Locale.ROOT))) {
                    return true;
                }
                if (keys.contains(key.toString().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return config.isBlockCustomModelData() && meta.hasCustomModelData();
    }

    public String describe(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "empty";
        }
        return item.getType().name();
    }
}
