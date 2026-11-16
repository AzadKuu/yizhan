package com.azadkuu.yizhan.listener;

import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.gui.GuiManager;
import com.azadkuu.yizhan.model.MailboxBlock;
import com.azadkuu.yizhan.model.Station;
import com.azadkuu.yizhan.storage.Storage;
import com.azadkuu.yizhan.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InteractListener implements Listener {

    private final PluginConfig config;
    private final Storage storage;
    private final GuiManager guiManager;
    /** 本服受保护方块坐标键 -> 名称（邮箱 / 驿站），避免每次挖掘都查库。 */
    private final Map<String, String> protectedKeys = new HashMap<>();
    private long protectedKeysAt = 0L;
    private static final long PROTECTED_KEYS_TTL_MS = 5000L;

    public InteractListener(PluginConfig config, Storage storage, GuiManager guiManager) {
        this.config = config;
        this.storage = storage;
        this.guiManager = guiManager;
    }

    private boolean isMailboxBlock(MailboxBlock mailboxBlock, Block block) {
        return mailboxBlock != null
                && mailboxBlock.world().equals(block.getWorld().getName())
                && mailboxBlock.x() == block.getX()
                && mailboxBlock.y() == block.getY()
                && mailboxBlock.z() == block.getZ();
    }

    private static String key(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    /** 刷新受保护方块坐标（本服邮箱 + 本服驿站），5 秒缓存，避免每次挖掘都查库。 */
    private void refreshProtectedKeys() {
        long now = System.currentTimeMillis();
        if (now - protectedKeysAt <= PROTECTED_KEYS_TTL_MS) {
            return;
        }
        protectedKeysAt = now;
        try {
            Map<String, String> fresh = new HashMap<>();
            for (String mailboxKey : storage.listMailboxKeys(config.getServerId())) {
                fresh.put(mailboxKey, "邮箱方块");
            }
            for (Station station : storage.listStations()) {
                if (!config.getServerId().equals(station.getServerId())) {
                    continue;
                }
                fresh.put(station.getWorld() + ":" + station.getX() + ":" + station.getY() + ":" + station.getZ(), "驿站方块");
            }
            protectedKeys.clear();
            protectedKeys.putAll(fresh);
        } catch (RuntimeException ex) {
            protectedKeysAt = 0L;
        }
    }

    /** 邮箱 / 驿站方块不允许被挖掘（OP 例外，方便运维）。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() || player.hasPermission("yizhan.admin")) {
            return;
        }
        Block block = event.getBlock();
        refreshProtectedKeys();
        String label = protectedKeys.get(key(block));
        if (label == null) {
            return;
        }
        event.setCancelled(true);
        if ("邮箱方块".equals(label)) {
            Msg.send(player, config.getPrefix(),
                    "&c这是邮箱方块，不能挖哦！&7（自己的邮箱可以用 &f/yz mailbox unbind &7解绑）");
        } else {
            Msg.send(player, config.getPrefix(), "&c这是驿站方块，不能挖哦！&7找管理员处理吧");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();
        if (isMailboxBlock(storage.getPlayerMailboxBlock(player.getUniqueId(), config.getServerId()), block)
                || isMailboxBlock(storage.getPublicMailboxBlock(config.getServerId()), block)) {
            event.setCancelled(true);
            if (!player.hasPermission("yizhan.mail")) {
                Msg.send(player, config.getPrefix(), "&c你没有开邮箱的权限哦！");
                return;
            }
            guiManager.openMailbox(player);
            return;
        }
        List<UUID> mailboxOwners = storage.findMailboxOwners(config.getServerId(), block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ());
        if (!mailboxOwners.isEmpty()) {
            event.setCancelled(true);
            if (!player.hasPermission("yizhan.mail")) {
                Msg.send(player, config.getPrefix(), "&c你没有使用邮箱的权限哦！");
                return;
            }
            List<String> added = new ArrayList<>();
            List<String> known = new ArrayList<>();
            for (UUID owner : mailboxOwners) {
                if (owner.equals(player.getUniqueId())) {
                    continue;
                }
                String name = Bukkit.getOfflinePlayer(owner).getName();
                if (name == null || name.isBlank()) {
                    name = owner.toString().substring(0, 8);
                }
                if (storage.addMailContact(player.getUniqueId(), owner, name, config.getServerId())) {
                    added.add(name);
                } else {
                    known.add(name);
                }
            }
            if (!added.isEmpty()) {
                Msg.send(player, config.getPrefix(),
                        "&a已把 &f" + String.join("、", added) + " &a的邮箱地址加入通讯录（本服）");
            }
            if (!known.isEmpty()) {
                Msg.send(player, config.getPrefix(),
                        "&7" + String.join("、", known) + " 已经在你的通讯录里了");
            }
            // 方块是共用的，但打开的是「你自己」的邮箱：同一个方块，每人看到的是各自的邮件
            if (storage.getPlayerMailboxBlock(player.getUniqueId(), config.getServerId()) == null) {
                Msg.send(player, config.getPrefix(),
                        "&7你还没绑定自己的邮箱方块，可以对着自己的方块用 &f/yz mailbox bind");
            }
            guiManager.openMailbox(player);
            return;
        }

        Station station = storage.getStationAt(config.getServerId(), block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ());
        if (station == null) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("yizhan.open")) {
            Msg.send(player, config.getPrefix(), "&c你没有开驿站的权限哦！");
            return;
        }
        guiManager.open(player, station);
    }
}
