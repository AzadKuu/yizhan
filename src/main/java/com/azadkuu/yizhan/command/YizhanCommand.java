package com.azadkuu.yizhan.command;

import com.azadkuu.yizhan.YizhanPlugin;
import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.gui.GuiManager;
import com.azadkuu.yizhan.model.Route;
import com.azadkuu.yizhan.model.Station;
import com.azadkuu.yizhan.model.StationMode;
import com.azadkuu.yizhan.service.TransportService;
import com.azadkuu.yizhan.storage.Storage;
import com.azadkuu.yizhan.util.Msg;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class YizhanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "bind", "unbind", "route", "buffer", "open", "list", "info", "reload", "help");

    private final YizhanPlugin plugin;
    private final PluginConfig config;
    private final Storage storage;
    private final TransportService transport;
    private final GuiManager guiManager;

    public YizhanCommand(YizhanPlugin plugin, PluginConfig config, Storage storage, TransportService transport,
                         GuiManager guiManager) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.transport = transport;
        this.guiManager = guiManager;
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
            case "buffer" -> buffer(sender, args);
            case "open" -> open(sender, args);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
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
            Msg.send(sender, config.getPrefix(), "&7用法: &f/yz route <起点> <终点> [缓冲秒]");
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
        Integer seconds = null;
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
        Route route = new Route();
        route.setFromStation(from);
        route.setToStation(to);
        route.setBufferSeconds(seconds);
        route.setEnabled(true);
        storage.saveRoute(route);
        int effective = transport.resolveBufferSeconds(route, fromStation);
        Msg.send(sender, config.getPrefix(), "&a已保存路由 &f" + from + " &7-> &f" + to
                + " &a缓冲 &f" + guiManager.formatSeconds(effective));
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
        Msg.send(sender, config.getPrefix(), "&f/yz route <起点> <终点> [秒] &7建立单向路由");
        Msg.send(sender, config.getPrefix(), "&f/yz route remove <起点> <终点> &7删除路由");
        Msg.send(sender, config.getPrefix(), "&f/yz buffer <驿站> <秒> &7设置缓冲时间");
        Msg.send(sender, config.getPrefix(), "&f/yz open <名称> &7远程打开驿站");
        Msg.send(sender, config.getPrefix(), "&f/yz list &7列出所有驿站");
        Msg.send(sender, config.getPrefix(), "&f/yz info <名称> &7查看驿站详情");
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
        }
        if (args.length == 3) {
            if (sub.equals("bind")) {
                return filtered(Arrays.asList("send", "receive", "both"), args[2]);
            }
            if (sub.equals("route")) {
                return stationNames(args[2]);
            }
        }
        if (args.length == 4) {
            if (sub.equals("route") && !args[1].equalsIgnoreCase("remove")) {
                return filtered(Arrays.asList("60", "300", "600"), args[3]);
            }
            if (sub.equals("route")) {
                return stationNames(args[3]);
            }
        }
        return new ArrayList<>();
    }
}
