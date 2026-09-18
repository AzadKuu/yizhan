package com.azadkuu.yizhan.command;

import com.azadkuu.yizhan.YizhanPlugin;
import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.gui.GuiManager;
import com.azadkuu.yizhan.model.MailboxBlock;
import com.azadkuu.yizhan.model.Route;
import com.azadkuu.yizhan.model.Station;
import com.azadkuu.yizhan.model.StationMode;
import com.azadkuu.yizhan.service.ItemFilter;
import com.azadkuu.yizhan.service.MailboxService;
import com.azadkuu.yizhan.service.TransportService;
import com.azadkuu.yizhan.storage.Storage;
import com.azadkuu.yizhan.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class YizhanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "bind", "unbind", "route", "fee", "buffer", "open", "list", "info", "mail", "mailbox", "dailyreward",
            "reload", "debugitem", "debugpdc", "help");

    private final YizhanPlugin plugin;
    private final PluginConfig config;
    private final Storage storage;
    private final TransportService transport;
    private final GuiManager guiManager;
    private final ItemFilter itemFilter;
    private final MailboxService mailboxService;

    public YizhanCommand(YizhanPlugin plugin, PluginConfig config, Storage storage, TransportService transport,
                         GuiManager guiManager, ItemFilter itemFilter, MailboxService mailboxService) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.transport = transport;
        this.guiManager = guiManager;
        this.itemFilter = itemFilter;
        this.mailboxService = mailboxService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "bind" -> bind(sender, args);
            case "unbind" -> unbind(sender, args);
            case "route" -> route(sender, args);
            case "fee" -> fee(sender, args);
            case "buffer" -> buffer(sender, args);
            case "open" -> open(sender, args);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "mail" -> mail(sender, args);
            case "mailbox" -> mailbox(sender, args);
            case "dailyreward" -> dailyReward(sender, args);
            case "discarded" -> discarded(sender);
            case "debugitem" -> debugItem(sender);
            case "debugpdc" -> debugPdc(sender);
            case "reload" -> reload(sender);
            default -> help(sender);
        }
        return true;
    }

    private void bind(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, config.getPrefix(), "&c该命令只能由玩家执行");
            return;
        }
        if (!player.hasPermission("yizhan.bind")) {
            Msg.send(sender, config.getPrefix(), "&c没有权限");
            return;
        }
        if (args.length < 3) {
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz bind <名称> <send|receive|both>");
            return;
        }
        String name = args[1];
        if (!isValidName(name)) {
            Msg.send(sender, config.getPrefix(), "&c名称只能包含字母、数字、下划线和短横线，长度 1-32");
            return;
        }
        StationMode mode = StationMode.parse(args[2]);
        if (mode == null) {
            Msg.send(sender, config.getPrefix(), "&c模式只能是 send、receive 或 both");
            return;
        }
        if (storage.getStation(name) != null) {
            Msg.send(sender, config.getPrefix(), "&c驿站 &f" + name + " &c已存在");
            return;
        }
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            Msg.send(sender, config.getPrefix(), "&c请把准星对准一个方块（6 格以内）");
            return;
        }
        Station occupied = storage.getStationAt(config.getServerId(), block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ());
        if (occupied != null) {
            Msg.send(sender, config.getPrefix(), "&c该位置已被驿站 &f" + occupied.getId() + " &c绑定");
            return;
        }
        Station station = new Station(name);
        station.setServerId(config.getServerId());
        station.setWorld(block.getWorld().getName());
        station.setX(block.getX());
        station.setY(block.getY());
        station.setZ(block.getZ());
        station.setMode(mode);
        station.setTitle(name);
        station.setSize(config.getDefaultStationSize());
        station.setBufferSeconds(null);
        storage.saveStation(station);
        Msg.send(sender, config.getPrefix(), "&a已绑定驿站 &f" + name + " &a模式 &f" + mode.name().toLowerCase(Locale.ROOT)
                + " &a位置 &f" + block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ());
    }

    private void unbind(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yizhan.bind")) {
            Msg.send(sender, config.getPrefix(), "&c没有权限");
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz unbind <名称>");
            return;
        }
        Station station = storage.getStation(args[1]);
        if (station == null) {
            Msg.send(sender, config.getPrefix(), "&c驿站 &f" + args[1] + " &c不存在");
            return;
        }
        storage.deleteStation(args[1]);
        Msg.send(sender, config.getPrefix(), "&a已解绑驿站 &f" + args[1]);
    }

    private void route(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yizhan.route")) {
            Msg.send(sender, config.getPrefix(), "&c没有权限");
            return;
        }
        if (args.length < 3) {
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz route <起点> <终点> [缓冲秒] [快递费]");
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz route remove <起点> <终点>");
            return;
        }
        if (args[1].equalsIgnoreCase("remove")) {
            if (args.length < 4) {
                Msg.send(sender, config.getPrefix(), "&7用法: &f/yz route remove <起点> <终点>");
                return;
            }
            storage.deleteRoute(args[2], args[3]);
            Msg.send(sender, config.getPrefix(), "&a已删除路由 &f" + args[2] + " &7-> &f" + args[3]);
            return;
        }
        String from = args[1];
        String to = args[2];
        Station fromStation = storage.getStation(from);
        Station toStation = storage.getStation(to);
        if (fromStation == null) {
            Msg.send(sender, config.getPrefix(), "&c起点驿站 &f" + from + " &c不存在");
            return;
        }
        if (toStation == null) {
            Msg.send(sender, config.getPrefix(), "&c终点驿站 &f" + to + " &c不存在");
            return;
        }
        if (!fromStation.getMode().canSend()) {
            Msg.send(sender, config.getPrefix(), "&c起点驿站 &f" + from + " &c不支持发货");
            return;
        }
        if (!toStation.getMode().canReceive()) {
            Msg.send(sender, config.getPrefix(), "&c终点驿站 &f" + to + " &c不支持收货");
            return;
        }
        Route existing = storage.getRoute(from, to);
        Integer seconds = existing == null ? null : existing.getBufferSeconds();
        if (args.length >= 4) {
            try {
                seconds = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                Msg.send(sender, config.getPrefix(), "&c缓冲时间必须是整数秒");
                return;
            }
            if (seconds < 0) {
                Msg.send(sender, config.getPrefix(), "&c缓冲时间不能为负数");
                return;
            }
        }
        int fee = existing == null ? 0 : existing.getFee();
        if (args.length >= 5) {
            try {
                fee = Integer.parseInt(args[4]);
            } catch (NumberFormatException ex) {
                Msg.send(sender, config.getPrefix(), "&c快递费必须是整数");
                return;
            }
            if (fee < 0) {
                Msg.send(sender, config.getPrefix(), "&c快递费不能为负数");
                return;
            }
        }
        Route route = new Route();
        route.setFromStation(from);
        route.setToStation(to);
        route.setBufferSeconds(seconds);
        route.setEnabled(true);
        route.setFee(fee);
        storage.saveRoute(route);
        int effective = transport.resolveBufferSeconds(route, fromStation);
        Msg.send(sender, config.getPrefix(), "&a已保存路由 &f" + from + " &7-> &f" + to
                + " &a缓冲 &f" + guiManager.formatSeconds(effective)
                + " &a快递费 &f" + (fee > 0 ? fee + " 个货币物品" : "无"));
    }

    private void fee(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yizhan.route")) {
            Msg.send(sender, config.getPrefix(), "&c没有权限");
            return;
        }
        if (args.length < 4) {
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz fee <起点> <终点> <数量>");
            return;
        }
        Route route = storage.getRoute(args[1], args[2]);
        if (route == null) {
            Msg.send(sender, config.getPrefix(), "&c路由 &f" + args[1] + " &7-> &f" + args[2] + " &c不存在");
            return;
        }
        int fee;
        try {
            fee = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            Msg.send(sender, config.getPrefix(), "&c快递费必须是整数");
            return;
        }
        if (fee < 0) {
            Msg.send(sender, config.getPrefix(), "&c快递费不能为负数");
            return;
        }
        route.setFee(fee);
        storage.saveRoute(route);
        Msg.send(sender, config.getPrefix(), "&a路由 &f" + args[1] + " &7-> &f" + args[2]
                + " &a的快递费已设为 &f" + (fee > 0 ? fee + " 个货币物品" : "无"));
    }

    private void mailbox(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yizhan.admin")) {
            Msg.send(sender, config.getPrefix(), "&c没有权限");
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz mailbox bind &7绑定准星方块为全服邮箱");
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz mailbox unbind &7解除绑定");
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz mailbox info &7查看当前绑定");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "bind" -> {
                if (!(sender instanceof Player player)) {
                    Msg.send(sender, config.getPrefix(), "&c该命令只能由玩家执行");
                    return;
                }
                Block block = player.getTargetBlockExact(6);
                if (block == null) {
                    Msg.send(sender, config.getPrefix(), "&c请把准星对准一个方块（6 格以内）");
                    return;
                }
                guiManager.bindMailboxBlock(block);
                Msg.send(sender, config.getPrefix(), "&a已把 &f" + block.getWorld().getName() + " "
                        + block.getX() + "," + block.getY() + "," + block.getZ()
                        + " &a设为全服邮箱方块，玩家右键即可打开自己的邮箱");
            }
            case "unbind" -> {
                if (guiManager.unbindMailboxBlock()) {
                    Msg.send(sender, config.getPrefix(), "&a已解除本服邮箱方块绑定");
                } else {
                    Msg.send(sender, config.getPrefix(), "&7本服尚未绑定邮箱方块");
                }
            }
            case "info" -> {
                MailboxBlock bound = guiManager.getMailboxBlock();
                if (bound == null) {
                    Msg.send(sender, config.getPrefix(), "&7本服尚未绑定邮箱方块，请用 &f/yz mailbox bind");
                } else {
                    Msg.send(sender, config.getPrefix(), "&a本服邮箱方块: &f" + bound.world() + " "
                            + bound.x() + "," + bound.y() + "," + bound.z());
                }
            }
            default -> Msg.send(sender, config.getPrefix(), "&7用法: &f/yz mailbox <bind|unbind|info>");
        }
    }

    private void dailyReward(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yizhan.admin")) {
            Msg.send(sender, config.getPrefix(), "&c没有权限");
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz dailyreward add &7把主手物品登记为每日奖励");
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz dailyreward list &7查看已登记的每日奖励");
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz dailyreward remove <槽位> &7移除某项奖励");
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz dailyreward clear &7清空全部登记");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> dailyRewardAdd(sender);
            case "list" -> dailyRewardList(sender);
            case "remove" -> dailyRewardRemove(sender, args);
            case "clear" -> dailyRewardClear(sender);
            default -> {
                Msg.send(sender, config.getPrefix(), "&7用法: &f/yz dailyreward <add|list|remove|clear>");
            }
        }
    }

    private void dailyRewardAdd(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, config.getPrefix(), "&c该命令只能由玩家执行");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            Msg.send(sender, config.getPrefix(), "&c主手没有物品，请手持要每天发放的物品再执行");
            return;
        }
        ItemStack template = hand.clone();
        String label = describeItem(template);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Map<Integer, ItemStack> items = storage.loadDailyRewardItems();
                int slot = 0;
                while (items.containsKey(slot)) {
                    slot++;
                }
                items.put(slot, template);
                storage.saveDailyRewardItems(items);
                int total = items.size();
                int assignedSlot = slot;
                Bukkit.getScheduler().runTask(plugin, () -> Msg.send(sender, config.getPrefix(),
                        "&a已登记每日奖励 &f" + label + " &7(槽位 &f" + assignedSlot + "&7)，共 &f" + total + " &7项"));
            } catch (RuntimeException ex) {
                String message = ex.getMessage();
                Bukkit.getScheduler().runTask(plugin, () -> Msg.send(sender, config.getPrefix(),
                        "&c登记失败: " + message));
            }
        });
    }

    private void dailyRewardList(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<Integer, ItemStack> items;
            try {
                items = storage.loadDailyRewardItems();
            } catch (RuntimeException ex) {
                String message = ex.getMessage();
                Bukkit.getScheduler().runTask(plugin, () -> Msg.send(sender, config.getPrefix(),
                        "&c读取失败: " + message));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Msg.send(sender, config.getPrefix(), "&6每日奖励登记物品 &7(共 &f" + items.size() + " &7项)");
                if (items.isEmpty()) {
                    Msg.send(sender, config.getPrefix(), "&7暂无登记，手持物品执行 &f/yz dailyreward add");
                } else {
                    for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                        Msg.send(sender, config.getPrefix(), "&f#" + entry.getKey() + " &7"
                                + describeItem(entry.getValue()));
                    }
                }
                Msg.send(sender, config.getPrefix(), "&7配置原版材质 &f" + config.getDailyRewardItems().size()
                        + " &7项 &8| &7功能开关: " + (config.isDailyRewardEnabled() ? "&a开启" : "&c关闭"));
            });
        });
    }

    private void dailyRewardRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz dailyreward remove <槽位>");
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            Msg.send(sender, config.getPrefix(), "&c槽位必须是整数");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Map<Integer, ItemStack> items = storage.loadDailyRewardItems();
                ItemStack removed = items.remove(slot);
                if (removed == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> Msg.send(sender, config.getPrefix(),
                            "&c槽位 &f" + slot + " &c没有登记物品"));
                    return;
                }
                storage.saveDailyRewardItems(items);
                String label = describeItem(removed);
                int total = items.size();
                Bukkit.getScheduler().runTask(plugin, () -> Msg.send(sender, config.getPrefix(),
                        "&a已移除 &f" + label + " &7(槽位 &f" + slot + "&7)，剩余 &f" + total + " &7项"));
            } catch (RuntimeException ex) {
                String message = ex.getMessage();
                Bukkit.getScheduler().runTask(plugin, () -> Msg.send(sender, config.getPrefix(),
                        "&c移除失败: " + message));
            }
        });
    }

    private void dailyRewardClear(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                storage.saveDailyRewardItems(new TreeMap<>());
                Bukkit.getScheduler().runTask(plugin, () -> Msg.send(sender, config.getPrefix(),
                        "&a已清空全部登记的每日奖励物品"));
            } catch (RuntimeException ex) {
                String message = ex.getMessage();
                Bukkit.getScheduler().runTask(plugin, () -> Msg.send(sender, config.getPrefix(),
                        "&c清空失败: " + message));
            }
        });
    }

    private String describeItem(ItemStack item) {
        StringBuilder sb = new StringBuilder(item.getType().name().toLowerCase(Locale.ROOT));
        sb.append(" x").append(item.getAmount());
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component display = meta.displayName();
            if (display != null) {
                sb.append(" (").append(PlainTextComponentSerializer.plainText().serialize(display)).append(")");
            }
        }
        return sb.toString();
    }

    private void mail(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz mail send <玩家> [数量] &7把主手物品发到对方邮箱");
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz mail give <玩家> <物品ID> <数量>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "send" -> mailSend(sender, args);
            case "give" -> mailGive(sender, args);
            default -> {
                Msg.send(sender, config.getPrefix(), "&7用法: &f/yz mail send <玩家> [数量]");
                Msg.send(sender, config.getPrefix(), "&7用法: &f/yz mail give <玩家> <物品ID> <数量>");
            }
        }
    }

    private void mailSend(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yizhan.mail.send")) {
            Msg.send(sender, config.getPrefix(), "&c没有权限");
            return;
        }
        if (!(sender instanceof Player player)) {
            Msg.send(sender, config.getPrefix(), "&c该命令只能由玩家执行");
            return;
        }
        if (args.length < 3) {
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz mail send <玩家> [数量]");
            return;
        }
        UUID target = resolveUuid(args[2]);
        if (target == null) {
            Msg.send(sender, config.getPrefix(), "&c找不到玩家 &f" + args[2]
                    + " &c（可用玩家名或 UUID；从未进服的玩家请用 UUID）");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            Msg.send(sender, config.getPrefix(), "&c主手没有物品");
            return;
        }
        int amount = hand.getAmount();
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                Msg.send(sender, config.getPrefix(), "&c数量必须是整数");
                return;
            }
            if (amount < 1) {
                Msg.send(sender, config.getPrefix(), "&c数量必须大于 0");
                return;
            }
            amount = Math.min(amount, hand.getAmount());
        }
        ItemStack send = hand.clone();
        send.setAmount(amount);
        int remain = hand.getAmount() - amount;
        if (remain <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            ItemStack back = hand.clone();
            back.setAmount(remain);
            player.getInventory().setItemInMainHand(back);
        }
        mailboxService.deliver(target, List.of(send),
                "&a你收到了邮件: " + amount + " 个物品，来自 " + player.getName());
        Msg.send(sender, config.getPrefix(), "&a已发送 &f" + amount + " &a个物品到 &f" + args[2] + " &a的邮箱");
    }

    private void mailGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yizhan.mail.send")) {
            Msg.send(sender, config.getPrefix(), "&c没有权限");
            return;
        }
        if (args.length < 5) {
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz mail give <玩家> <物品ID> <数量>");
            return;
        }
        UUID target = resolveUuid(args[2]);
        if (target == null) {
            Msg.send(sender, config.getPrefix(), "&c找不到玩家 &f" + args[2]
                    + " &c（可用玩家名或 UUID；从未进服的玩家请用 UUID）");
            return;
        }
        Material material = Material.matchMaterial(args[3]);
        if (material == null || material.isAir()) {
            Msg.send(sender, config.getPrefix(), "&c无效的物品ID: &f" + args[3]);
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[4]);
        } catch (NumberFormatException ex) {
            Msg.send(sender, config.getPrefix(), "&c数量必须是整数");
            return;
        }
        if (amount < 1) {
            Msg.send(sender, config.getPrefix(), "&c数量必须大于 0");
            return;
        }
        List<ItemStack> items = buildStacks(material, amount);
        mailboxService.deliver(target, items,
                "&a你收到了邮件: " + amount + " 个 " + material.name().toLowerCase(Locale.ROOT));
        Msg.send(sender, config.getPrefix(), "&a已发送 &f" + amount + " &a个 &f"
                + material.name().toLowerCase(Locale.ROOT) + " &a到 &f" + args[2] + " &a的邮箱");
    }

    @SuppressWarnings("deprecation")
    private UUID resolveUuid(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
        }
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline.getUniqueId();
        }
        return null;
    }

    private List<ItemStack> buildStacks(Material material, int amount) {
        List<ItemStack> out = new ArrayList<>();
        int max = Math.max(1, material.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            int take = Math.min(remaining, max);
            out.add(new ItemStack(material, take));
            remaining -= take;
        }
        return out;
    }

    private void buffer(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yizhan.route")) {
            Msg.send(sender, config.getPrefix(), "&c没有权限");
            return;
        }
        if (args.length < 3) {
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz buffer <驿站> <秒>");
            return;
        }
        Station station = storage.getStation(args[1]);
        if (station == null) {
            Msg.send(sender, config.getPrefix(), "&c驿站 &f" + args[1] + " &c不存在");
            return;
        }
        int seconds;
        try {
            seconds = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            Msg.send(sender, config.getPrefix(), "&c缓冲时间必须是整数秒");
            return;
        }
        if (seconds < 0) {
            Msg.send(sender, config.getPrefix(), "&c缓冲时间不能为负数");
            return;
        }
        station.setBufferSeconds(seconds);
        storage.saveStation(station);
        Msg.send(sender, config.getPrefix(), "&a驿站 &f" + station.getId() + " &a的缓冲时间已设为 &f" + guiManager.formatSeconds(seconds));
    }

    private void open(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, config.getPrefix(), "&c该命令只能由玩家执行");
            return;
        }
        if (!player.hasPermission("yizhan.open")) {
            Msg.send(sender, config.getPrefix(), "&c没有权限");
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz open <名称>");
            return;
        }
        Station station = storage.getStation(args[1]);
        if (station == null) {
            Msg.send(sender, config.getPrefix(), "&c驿站 &f" + args[1] + " &c不存在");
            return;
        }
        guiManager.open(player, station);
    }

    private void list(CommandSender sender) {
        List<Station> stations = storage.listStations();
        Msg.send(sender, config.getPrefix(), "&7共有 &f" + stations.size() + " &7个驿站");
        for (Station station : stations) {
            Msg.send(sender, config.getPrefix(), "&f" + station.getId() + " &7[" + station.getMode().name().toLowerCase(Locale.ROOT)
                    + "] &7" + station.getServerId() + " " + station.getWorld() + " "
                    + station.getX() + "," + station.getY() + "," + station.getZ());
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz info <名称>");
            return;
        }
        Station station = storage.getStation(args[1]);
        if (station == null) {
            Msg.send(sender, config.getPrefix(), "&c驿站 &f" + args[1] + " &c不存在");
            return;
        }
        int routes = storage.listRoutesFrom(station.getId()).size();
        Msg.send(sender, config.getPrefix(), "&6驿站 &f" + station.getId());
        Msg.send(sender, config.getPrefix(), "&7类型: &f" + station.getMode().name().toLowerCase(Locale.ROOT)
                + " &7位置: &f" + station.getServerId() + " " + station.getWorld() + " "
                + station.getX() + "," + station.getY() + "," + station.getZ());
        Msg.send(sender, config.getPrefix(), "&7容量: &f" + station.getSize()
                + " &7缓冲: &f" + (station.getBufferSeconds() == null ? "默认" : guiManager.formatSeconds(station.getBufferSeconds()))
                + " &7发送路由: &f" + routes + " &7版本: &f" + station.getVersion());
    }

    @SuppressWarnings("deprecation")
    private void debugItem(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, config.getPrefix(), "&c该命令只能由玩家执行");
            return;
        }
        if (!player.hasPermission("yizhan.admin")) {
            Msg.send(sender, config.getPrefix(), "&c没有权限");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            Msg.send(sender, config.getPrefix(), "&7主手没有物品");
            return;
        }
        ItemMeta meta = item.getItemMeta();
        Msg.send(sender, config.getPrefix(), "&6物品诊断 &8[&fdebug=" + config.isDebug() + "&8]");
        Msg.send(sender, config.getPrefix(), "&7类型: &f" + item.getType().name());
        String name = "(无)";
        if (meta != null && meta.hasDisplayName()) {
            Component display = meta.displayName();
            if (display != null) {
                name = PlainTextComponentSerializer.plainText().serialize(display);
            }
        }
        Msg.send(sender, config.getPrefix(), "&7显示名: &f" + name);
        Msg.send(sender, config.getPrefix(), "&7自定义模型数据: &f"
                + (meta != null && meta.hasCustomModelData() ? String.valueOf(meta.getCustomModelData()) : "(无)"));
        int pdcCount = 0;
        if (meta != null) {
            List<NamespacedKey> pdcKeys = new ArrayList<>(meta.getPersistentDataContainer().getKeys());
            pdcCount = pdcKeys.size();
            if (pdcKeys.isEmpty()) {
                Msg.send(sender, config.getPrefix(), "&7PDC 键值: &8(无，原版物品或未使用 PDC)");
            } else {
                Msg.send(sender, config.getPrefix(), "&7PDC 键值 (&f" + pdcKeys.size() + "&7):");
                for (NamespacedKey key : pdcKeys) {
                    String value = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                    Msg.send(sender, config.getPrefix(), "  &8- &f" + key + " &7= &f"
                            + (value == null ? "(非字符串类型)" : "\"" + value + "\""));
                }
            }
        }
        Map<String, String> customData = itemFilter.parseCustomData(meta);
        if (customData.isEmpty()) {
            Msg.send(sender, config.getPrefix(), "&7custom_data 键值: &8(无)");
        } else {
            Msg.send(sender, config.getPrefix(), "&7custom_data 键值 (&f" + customData.size() + "&7):");
            for (Map.Entry<String, String> entry : customData.entrySet()) {
                Msg.send(sender, config.getPrefix(), "  &8- &f" + entry.getKey() + " &7= &f\""
                        + entry.getValue() + "\"");
            }
        }
        boolean blocked = itemFilter.isBlocked(item);
        Msg.send(sender, config.getPrefix(), blocked
                ? "&7判定结果: &c会被拦截，禁止运输"
                : "&7判定结果: &a允许运输");
        if (!blocked && pdcCount > 0) {
            Msg.send(sender, config.getPrefix(), "&7提示: 该物品含 PDC 但未被拦截，"
                    + "请把上方命名空间加入 &fitem-filter.blocked-namespaces");
        }
        Msg.send(sender, config.getPrefix(), "&7SNBT: &f" + (meta == null ? "(null)" : meta.getAsString()));
    }

    @SuppressWarnings("deprecation")
    private void debugPdc(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, config.getPrefix(), "&c该命令只能由玩家执行");
            return;
        }
        if (!player.hasPermission("yizhan.admin")) {
            Msg.send(sender, config.getPrefix(), "&c没有权限");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            Msg.send(sender, config.getPrefix(), "&7主手没有物品");
            return;
        }
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            Msg.send(sender, config.getPrefix(), "&c该物品没有 ItemMeta");
            return;
        }
        NamespacedKey key = new NamespacedKey("nexo", "debug_key");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "test_value");
        copy.setItemMeta(meta);
        player.getInventory().setItemInMainHand(copy);
        Msg.send(sender, config.getPrefix(), "&a已用 Bukkit API 写入 PDC 键 &f" + key);
        Msg.send(sender, config.getPrefix(), "&7请执行 &f/data get entity @s SelectedItem &7查看 Paper 实际写出的 NBT 格式");
    }

    private void discarded(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, config.getPrefix(), "&c该命令只能由玩家执行");
            return;
        }
        if (!player.hasPermission("yizhan.admin")) {
            Msg.send(sender, config.getPrefix(), "&c没有权限");
            return;
        }
        guiManager.openDiscarded(player);
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("yizhan.admin")) {
            Msg.send(sender, config.getPrefix(), "&c没有权限");
            return;
        }
        plugin.reload();
        Msg.send(sender, config.getPrefix(), "&a配置已重载");
    }

    private void help(CommandSender sender) {
        Msg.send(sender, config.getPrefix(), "&6驿站系统命令");
        Msg.send(sender, config.getPrefix(), "&f/yz bind <名称> <send|receive|both> &7绑定准星方块");
        Msg.send(sender, config.getPrefix(), "&f/yz unbind <名称> &7解绑驿站");
        Msg.send(sender, config.getPrefix(), "&f/yz route <起点> <终点> [秒] [快递费] &7建立单向路由");
        Msg.send(sender, config.getPrefix(), "&f/yz route remove <起点> <终点> &7删除路由");
        Msg.send(sender, config.getPrefix(), "&f/yz fee <起点> <终点> <数量> &7设置路由快递费");
        Msg.send(sender, config.getPrefix(), "&f/yz buffer <驿站> <秒> &7设置缓冲时间");
        Msg.send(sender, config.getPrefix(), "&f/yz open <名称> &7远程打开驿站");
        Msg.send(sender, config.getPrefix(), "&f/yz list &7列出所有驿站");
        Msg.send(sender, config.getPrefix(), "&f/yz info <名称> &7查看驿站详情");
        Msg.send(sender, config.getPrefix(), "&f/yz mailbox <bind|unbind|info> &7设置全服邮箱方块");
        Msg.send(sender, config.getPrefix(), "&f/yz mail send <玩家> [数量] &7把主手物品发到对方邮箱");
        Msg.send(sender, config.getPrefix(), "&f/yz mail give <玩家> <物品ID> <数量> &7发送指定物品");
        Msg.send(sender, config.getPrefix(), "&f/yz dailyreward add &7把主手物品登记为每日奖励（支持自定义物品）");
        Msg.send(sender, config.getPrefix(), "&f/yz dailyreward <list|remove|clear> &7管理登记的每日奖励");
        Msg.send(sender, config.getPrefix(), "&f/yz discarded &7打开丢弃物品仓库（管理员领取）");
        Msg.send(sender, config.getPrefix(), "&f/yz debugitem &7诊断主手物品（PDC 键、SNBT 与拦截判定）");
        Msg.send(sender, config.getPrefix(), "&f/yz debugpdc &7用 Bukkit API 给主手物品写测试 PDC 键");
        Msg.send(sender, config.getPrefix(), "&f/yz reload &7重载配置");
    }

    private boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,32}");
    }

    private List<String> stationNames(String input) {
        List<String> out = new ArrayList<>();
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        for (Station station : storage.listStations()) {
            if (station.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(station.getId());
            }
        }
        return out;
    }

    private List<String> materialNames(String input) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isAir() || !material.isItem()) {
                continue;
            }
            String name = material.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(prefix)) {
                out.add(name);
                if (out.size() >= 50) {
                    break;
                }
            }
        }
        return out;
    }

    private List<String> onlineNames(String input) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(player.getName());
            }
        }
        return out;
    }

    private List<String> filtered(List<String> source, String input) {
        List<String> out = new ArrayList<>();
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        for (String value : source) {
            if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(value);
            }
        }
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filtered(SUB_COMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (sub.equals("bind")) {
                return new ArrayList<>();
            }
            if (sub.equals("route")) {
                List<String> options = new ArrayList<>();
                options.add("remove");
                options.addAll(stationNames(args[1]));
                return filtered(options, args[1]);
            }
            if (sub.equals("unbind") || sub.equals("open") || sub.equals("info") || sub.equals("buffer")) {
                return stationNames(args[1]);
            }
            if (sub.equals("fee")) {
                return stationNames(args[1]);
            }
            if (sub.equals("mailbox")) {
                return filtered(Arrays.asList("bind", "unbind", "info"), args[1]);
            }
            if (sub.equals("mail")) {
                return filtered(Arrays.asList("send", "give"), args[1]);
            }
            if (sub.equals("dailyreward")) {
                return filtered(Arrays.asList("add", "list", "remove", "clear"), args[1]);
            }
        }
        if (args.length == 3) {
            if (sub.equals("bind")) {
                return filtered(Arrays.asList("send", "receive", "both"), args[2]);
            }
            if (sub.equals("route")) {
                return stationNames(args[2]);
            }
            if (sub.equals("fee")) {
                return stationNames(args[2]);
            }
            if (sub.equals("mail")) {
                return onlineNames(args[2]);
            }
            if (sub.equals("dailyreward") && args[1].equalsIgnoreCase("remove")) {
                return filtered(Arrays.asList("0", "1", "2", "3", "4", "5"), args[2]);
            }
        }
        if (args.length == 4) {
            if (sub.equals("route") && !args[1].equalsIgnoreCase("remove")) {
                return filtered(Arrays.asList("60", "300", "600"), args[3]);
            }
            if (sub.equals("route")) {
                return stationNames(args[3]);
            }
            if (sub.equals("fee")) {
                return filtered(Arrays.asList("0", "1", "5", "10"), args[3]);
            }
            if (sub.equals("mail") && args[1].equalsIgnoreCase("send")) {
                return filtered(Arrays.asList("1", "8", "16", "32", "64"), args[3]);
            }
            if (sub.equals("mail")) {
                return materialNames(args[3]);
            }
        }
        if (args.length == 5) {
            if (sub.equals("route")) {
                return filtered(Arrays.asList("0", "1", "5", "10"), args[4]);
            }
            if (sub.equals("mail") && args[1].equalsIgnoreCase("give")) {
                return filtered(Arrays.asList("1", "8", "16", "32", "64"), args[4]);
            }
        }
        return new ArrayList<>();
    }
}
