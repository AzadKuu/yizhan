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
import org.bukkit.inventory.EquipmentSlot;
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
    /** 同一次快照里的附带数据，避免每次右键都查库。 */
    private final Map<String, List<UUID>> ownerCache = new HashMap<>();
    private final Map<String, Station> stationCache = new HashMap<>();
    private final Map<UUID, MailboxBlock> ownMailboxCache = new HashMap<>();
    private String publicMailboxKey = null;

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

    /** 每 5 秒刷一次快照（邮箱坐标、驿站、公共邮箱、各玩家自己的邮箱），命中都是内存查表。 */
    private void refreshSnapshot() {
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
            Map<String, Station> stations = new HashMap<>();
            for (Station station : storage.listStations()) {
                if (!config.getServerId().equals(station.getServerId())) {
                    continue;
                }
                String stationKey = station.getWorld() + ":" + station.getX() + ":" + station.getY() + ":" + station.getZ();
                fresh.put(stationKey, "驿站方块");
                stations.put(stationKey, station);
            }
            MailboxBlock publicBlock = storage.getPublicMailboxBlock(config.getServerId());
            String publicKey = publicBlock == null ? null
                    : publicBlock.world() + ":" + publicBlock.x() + ":" + publicBlock.y() + ":" + publicBlock.z();
            protectedKeys.clear();
            protectedKeys.putAll(fresh);
            stationCache.clear();
            stationCache.putAll(stations);
            publicMailboxKey = publicKey;
            ownerCache.clear();
            ownMailboxCache.clear();
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
        refreshSnapshot();
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
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            // 主手和副手会各触发一次，只处理一次，顺便把查库次数砍半
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();
        refreshSnapshot();
        String blockKey = key(block);
        MailboxBlock own = ownMailboxCache.get(player.getUniqueId());
        if (own == null && !ownMailboxCache.containsKey(player.getUniqueId())) {
            own = storage.getPlayerMailboxBlock(player.getUniqueId(), config.getServerId());
            ownMailboxCache.put(player.getUniqueId(), own);
        }
        if (isMailboxBlock(own, block) || blockKey.equals(publicMailboxKey)) {
            event.setCancelled(true);
            if (!player.hasPermission("yizhan.mail")) {
                Msg.send(player, config.getPrefix(), "&c你没有开邮箱的权限哦！");
                return;
            }
            guiManager.openMailbox(player);
            return;
        }
        List<UUID> mailboxOwners = ownerCache.get(blockKey);
        if (mailboxOwners == null) {
            if (!protectedKeys.containsKey(blockKey)) {
                // 不是任何人的邮箱/驿站：完全不用查库
                mailboxOwners = List.of();
            } else {
                mailboxOwners = storage.findMailboxOwners(config.getServerId(), block.getWorld().getName(),
                        block.getX(), block.getY(), block.getZ());
            }
            ownerCache.put(blockKey, mailboxOwners);
        }
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
            // 邮箱是各自独立的：别人的方块只用来加通讯录，不打开界面
            // （要开邮箱请右键自己绑定的方块，或本服公共邮箱方块）
            if (own == null) {
                Msg.send(player, config.getPrefix(),
                        "&7你还没绑定自己的邮箱方块，对准自己的方块用 &f/yz mailbox bind &7就能收邮件啦");
            }
            return;
        }
        Station station = stationCache.get(blockKey);
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
