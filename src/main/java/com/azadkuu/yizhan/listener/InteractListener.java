package com.azadkuu.yizhan.listener;

import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.gui.GuiManager;
import com.azadkuu.yizhan.model.MailboxBlock;
import com.azadkuu.yizhan.model.Station;
import com.azadkuu.yizhan.storage.Storage;
import com.azadkuu.yizhan.util.Msg;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class InteractListener implements Listener {

    private final PluginConfig config;
    private final Storage storage;
    private final GuiManager guiManager;

    public InteractListener(PluginConfig config, Storage storage, GuiManager guiManager) {
        this.config = config;
        this.storage = storage;
        this.guiManager = guiManager;
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
        MailboxBlock mailboxBlock = storage.getPlayerMailboxBlock(player.getUniqueId(), config.getServerId());
        if (mailboxBlock != null
                && mailboxBlock.world().equals(block.getWorld().getName())
                && mailboxBlock.x() == block.getX()
                && mailboxBlock.y() == block.getY()
                && mailboxBlock.z() == block.getZ()) {
            event.setCancelled(true);
            if (!player.hasPermission("yizhan.mail")) {
                Msg.send(player, config.getPrefix(), "&c你没有权限打开邮箱");
                return;
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
            Msg.send(player, config.getPrefix(), "&c你没有权限打开驿站");
            return;
        }
        guiManager.open(player, station);
    }
}
