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
import java.util.List;
import java.util.UUID;

public class InteractListener implements Listener {

    private final PluginConfig config;
    private final Storage storage;
    private final GuiManager guiManager;

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

    /** 这个方块是不是邮箱（个人绑定的或本服公共邮箱）。 */
    private boolean isMailbox(Block block) {
        if (isMailboxBlock(storage.getPublicMailboxBlock(config.getServerId()), block)) {
            return true;
        }
        return !storage.findMailboxOwners(config.getServerId(), block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ()).isEmpty();
    }

    /** 邮箱方块不允许被挖掘（OP 例外，方便运维）。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) {
            return;
        }
        Block block = event.getBlock();
        if (!isMailbox(block)) {
            return;
        }
        event.setCancelled(true);
        Msg.send(player, config.getPrefix(),
                "&c这是邮箱方块，不能挖哦！&7（自己的邮箱可以用 &f/yz mailbox unbind &7解绑）");
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
