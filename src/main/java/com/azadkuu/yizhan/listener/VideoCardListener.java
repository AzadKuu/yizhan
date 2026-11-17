package com.azadkuu.yizhan.listener;

import com.azadkuu.yizhan.YizhanPlugin;
import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.storage.Storage;
import com.azadkuu.yizhan.util.LinkGuard;
import com.azadkuu.yizhan.util.Msg;
import com.azadkuu.yizhan.util.VideoCards;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** 一次性视频卡：右键即消耗并私发可点链接，禁止被拿去当装饰。 */
public final class VideoCardListener implements Listener {

    private final YizhanPlugin plugin;
    private final PluginConfig config;
    private final Storage storage;

    public VideoCardListener(YizhanPlugin plugin, PluginConfig config, Storage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Long id = VideoCards.idOf(plugin, event.getItem());
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        consumeOne(player);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String url;
            try {
                url = storage.takeVideoCard(id, player.getUniqueId());
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("视频卡读取失败: " + ex.getMessage());
                url = null;
            }
            String result = url;
            plugin.getServer().getScheduler().runTask(plugin, () -> finish(player, result));
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ItemFrame) && !(entity instanceof ArmorStand)) {
            return;
        }
        Player player = event.getPlayer();
        if (VideoCards.idOf(plugin, player.getInventory().getItemInMainHand()) == null) {
            return;
        }
        event.setCancelled(true);
        Msg.send(player, config.getPrefix(), "&7视频卡是一次性的，不能拿来当装饰哦");
    }

    private void finish(Player player, String url) {
        if (!player.isOnline()) {
            return;
        }
        if (url == null) {
            Msg.send(player, config.getPrefix(), "&c这张卡已经用过了（或者被复制过），已经销毁");
            return;
        }
        Msg.send(player, config.getPrefix(), "&b▶ 视频卡 &7· &f点下面的链接打开");
        Msg.send(player, LinkGuard.linkText(url, config.getLinkWhitelist()));
        Msg.send(player, Msg.component("&8点不开就手动复制：&7" + url));
    }

    private static void consumeOne(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand);
    }
}
