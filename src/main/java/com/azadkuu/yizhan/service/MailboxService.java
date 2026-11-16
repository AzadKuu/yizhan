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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        deliver(target, items, notification, null);
    }

    /**
     * 投递到邮箱。异步任务失败时把物品交回 onFailure（在主线程调用），
     * 避免「GUI 已经清空 + 异步写库失败」导致物品凭空消失。
     */
    public void deliver(UUID target, List<ItemStack> items, String notification,
                        java.util.function.Consumer<List<ItemStack>> onFailure) {
        // 投递前净化：只保留白名单 open_url，剥掉 run_command 之类可执行点击
        List<ItemStack> safe = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            safe.add(plugin.getLinkGuard().sanitize(item));
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int stashed = storage.depositToMailbox(target, safe, config.getMailboxSize());
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
                if (onFailure != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> onFailure.accept(safe));
                }
                return;
            }
            notifyOnline(target);
        });
    }

    /** 投递一封链接邮件（只存链接，不占邮箱物品格）。 */
    public void deliverLink(UUID target, UUID sender, String senderName, String url, String notification) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                storage.addLinkMail(target, sender, senderName, config.getServerId(), url);
                if (notification != null && !notification.isBlank()) {
                    storage.pushNotification(target, notification);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("投递链接邮件失败: " + ex.getMessage());
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
                String claimDate = LocalDate.now().format(DATE_FORMAT);
                if (!storage.markDailyClaim(uuid, claimDate)) {
                    return;
                }
                int stashed;
                try {
                    stashed = storage.depositToMailbox(uuid, items, config.getMailboxSize());
                } catch (RuntimeException ex) {
                    // 发奖失败就把领取标记撤回，别让玩家白丢一天
                    storage.rollbackDailyClaim(uuid, claimDate);
                    throw ex;
                }
                if (stashed > 0) {
                    plugin.getLogger().warning("邮箱已满，每日奖励 " + stashed + " 件暂存待领取: " + uuid);
                }
                String message = config.getDailyRewardMessage() + " &f(" + itemSummary(items) + ")";
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

    private String itemSummary(List<ItemStack> items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            String name;
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                name = item.getItemMeta().getDisplayName();
            } else {
                name = item.getType().name().toLowerCase(Locale.ROOT);
            }
            counts.merge(name, item.getAmount(), Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!first) {
                sb.append("&a, &f");
            }
            first = false;
            sb.append("&f").append(entry.getValue()).append(" &a个 &f").append(entry.getKey());
        }
        return sb.toString();
    }
}
