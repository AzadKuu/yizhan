package com.azadkuu.yizhan.service;

import com.azadkuu.yizhan.YizhanPlugin;
import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MailboxService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final YizhanPlugin plugin;
    private final PluginConfig config;
    private final Storage storage;
    private final NotificationService notificationService;

    public MailboxService(YizhanPlugin plugin, PluginConfig config, Storage storage,
                          NotificationService notificationService) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.notificationService = notificationService;
    }

    public void deliver(UUID target, List<ItemStack> items, String notification) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int stashed = storage.depositToMailbox(target, items, config.getMailboxSize());
                if (stashed > 0) {
                    plugin.getLogger().warning("邮箱已满，" + stashed + " 组物品暂存待领取: " + target);
                }
                String message = notification;
                if (stashed > 0) {
                    String suffix = "&e邮箱已满，" + stashed + " 件邮件已暂存，清理邮箱后点「重新领取」";
                    message = (message == null || message.isBlank()) ? suffix : message + " " + suffix;
                }
                if (message != null && !message.isBlank()) {
                    storage.pushNotification(target, message);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("投递邮箱失败: " + ex.getMessage());
                return;
            }
            notifyOnline(target);
        });
    }

    public void grantDailyReward(Player player) {
        if (!config.isDailyRewardEnabled()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<ItemStack> items = buildDailyItems();
                if (items.isEmpty()) {
                    return;
                }
                if (!storage.markDailyClaim(uuid, LocalDate.now().format(DATE_FORMAT))) {
                    return;
                }
                int stashed = storage.depositToMailbox(uuid, items, config.getMailboxSize());
                if (stashed > 0) {
                    plugin.getLogger().warning("邮箱已满，每日奖励 " + stashed + " 件暂存待领取: " + uuid);
                }
                String message = config.getDailyRewardMessage();
                if (stashed > 0) {
                    message = message + " &e（" + stashed + " 件已暂存，清理后点「重新领取」）";
                }
                storage.pushNotification(uuid, message);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("每日奖励发放失败: " + ex.getMessage());
                return;
            }
            notifyOnline(uuid);
        });
    }

    private void notifyOnline(UUID target) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(target);
            if (online == null || !online.isOnline()) {
                return;
            }
            notificationService.flush(online);
        });
    }

    private List<ItemStack> buildDailyItems() {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack template : storage.loadDailyRewardItems().values()) {
            if (template == null || template.getType().isAir()) {
                continue;
            }
            items.add(template.clone());
        }
        for (PluginConfig.DailyRewardItem entry : config.getDailyRewardItems()) {
            Material material = Material.matchMaterial(entry.material());
            if (material == null || material.isAir()) {
                plugin.getLogger().warning("每日奖励配置了无效材质: " + entry.material());
                continue;
            }
            int remaining = entry.amount();
            int maxStack = Math.max(1, material.getMaxStackSize());
            while (remaining > 0) {
                int amount = Math.min(remaining, maxStack);
                items.add(new ItemStack(material, amount));
                remaining -= amount;
            }
        }
        return items;
    }
}
