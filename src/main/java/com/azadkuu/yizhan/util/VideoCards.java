package com.azadkuu.yizhan.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次性视频卡：物品里只存数据库 id，链接与“是否已用过”都在数据库里，
 * 所以复制物品栏的卡也没用 —— 用过的 id 数据库里已经作废。
 */
public final class VideoCards {

    private VideoCards() {
    }

    public static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "video_card");
    }

    public static ItemStack create(Plugin plugin, long id, String title) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String name = title == null || title.isBlank() ? "视频链接" : title.trim();
        if (name.length() > 40) {
            name = name.substring(0, 39) + "…";
        }
        meta.displayName(Msg.component("&d▶ 视频卡 &7· &f" + name));
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.component("&7一次性：右键打开后消失"));
        lore.add(Msg.component("&7不能放进展示框、盔甲架"));
        lore.add(Msg.component("&8编号 #" + id));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.LONG, id);
        item.setItemMeta(meta);
        return item;
    }

    public static Long idOf(Plugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.get(key(plugin), PersistentDataType.LONG);
    }
}
