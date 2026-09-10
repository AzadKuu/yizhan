package com.azadkuu.yizhan.gui;

import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.model.Route;
import com.azadkuu.yizhan.model.Shipment;
import com.azadkuu.yizhan.model.Station;
import com.azadkuu.yizhan.model.StationMode;
import com.azadkuu.yizhan.service.ItemFilter;
import com.azadkuu.yizhan.service.TransportService;
import com.azadkuu.yizhan.storage.Storage;
import com.azadkuu.yizhan.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuiManager {

    public static final int SLOT_CLOSE = 45;
    public static final int SLOT_SWITCH = 47;
    public static final int SLOT_ROUTE = 48;
    public static final int SLOT_ACTION = 49;
    public static final int SLOT_INFO = 53;

    public static final int CHOOSER_SEND = 11;
    public static final int CHOOSER_RECEIVE = 15;

    private final PluginConfig config;
    private final Storage storage;
    private final TransportService transport;
    private final ItemFilter filter;
    private final Map<UUID, StationHolder> open = new HashMap<>();

    public GuiManager(PluginConfig config, Storage storage, TransportService transport, ItemFilter filter) {
        this.config = config;
        this.storage = storage;
        this.transport = transport;
        this.filter = filter;
    }

    public StationHolder getOpenHolder(UUID uuid) {
        return open.get(uuid);
    }

    public void unregister(UUID uuid) {
        open.remove(uuid);
    }

    public void open(Player player, Station station) {
        open(player, station, null);
    }

    public void open(Player player, Station station, StationHolder.View forcedView) {
        StationHolder.View view = forcedView;
        if (view == null) {
            if (station.getMode() == StationMode.BOTH) {
                view = StationHolder.View.CHOOSER;
            } else if (station.getMode() == StationMode.RECEIVE) {
                view = StationHolder.View.RECEIVE;
            } else {
                view = StationHolder.View.SEND;
            }
        }
        StationHolder holder = new StationHolder(station, view);
        if (view == StationHolder.View.SEND) {
            holder.setRoutes(storage.listRoutesFrom(station.getId()));
        }
        int size = view == StationHolder.View.CHOOSER ? 27 : 54;
        Inventory inventory = Bukkit.createInventory(holder, size, Msg.component("&8驿站 &7· &6" + station.getTitle()));
        holder.setInventory(inventory);
        render(holder);
        open.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
    }

    public void render(StationHolder holder) {
        holder.getInventory().clear();
        switch (holder.getView()) {
            case CHOOSER -> renderChooser(holder);
            case SEND -> renderSend(holder);
            case RECEIVE -> renderReceive(holder);
        }
    }

    private void renderChooser(StationHolder holder) {
        Inventory inventory = holder.getInventory();
        inventory.setItem(CHOOSER_SEND, button(Material.MINECART, "&a发货", "&7打开发货区，放入物品后点击发货"));
        inventory.setItem(CHOOSER_RECEIVE, button(Material.CHEST, "&b收件箱", "&7查看并领取已到达的物品"));
        inventory.setItem(22, button(Material.OAK_DOOR, "&e关闭"));
    }

    private void renderSend(StationHolder holder) {
        Inventory inventory = holder.getInventory();
        Station station = holder.getStation();
        List<Route> routes = holder.getRoutes();
        if (routes.isEmpty()) {
            inventory.setItem(SLOT_INFO, button(Material.BARRIER, "&c未配置路由",
                    "&7请管理员执行 &f/yz route " + station.getId() + " <目标站>"));
            inventory.setItem(SLOT_CLOSE, button(Material.OAK_DOOR, "&e关闭"));
            return;
        }
        Route route = routes.get(Math.floorMod(holder.getSelectedRoute(), routes.size()));
        int buffer = transport.resolveBufferSeconds(route, station);
        int inTransit = storage.listInTransitFrom(station.getId()).size();
        inventory.setItem(SLOT_ROUTE, button(Material.COMPASS, "&b切换目的地",
                "&7当前目的地: &f" + route.getToStation(),
                "&7点击切换到下一个路由"));
        inventory.setItem(SLOT_ACTION, button(Material.MINECART, "&a点击发货",
                "&7目的地: &f" + route.getToStation(),
                "&7缓冲时间: &f" + formatSeconds(buffer),
                "&7放入发货区的物品将进入在途状态"));
        if (station.getMode() == StationMode.BOTH) {
            inventory.setItem(SLOT_SWITCH, button(Material.CHEST, "&e切换到收件箱"));
        }
        inventory.setItem(SLOT_INFO, button(Material.PAPER, "&f发货区",
                "&7在途发货单: &f" + inTransit,
                "&7默认缓冲: &f" + formatSeconds(config.getDefaultBufferSeconds())));
        inventory.setItem(SLOT_CLOSE, button(Material.OAK_DOOR, "&e关闭"));
    }

    private void renderReceive(StationHolder holder) {
        Inventory inventory = holder.getInventory();
        Station station = holder.getStation();
        Map<Integer, ItemStack> items = storage.loadStationItems(station.getId());
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            int slot = entry.getKey();
            if (slot >= 0 && slot < station.getSize() && slot < SLOT_CLOSE) {
                inventory.setItem(slot, entry.getValue());
            }
        }
        holder.setBaseVersion(station.getVersion());
        List<Shipment> incoming = storage.listInTransitTo(station.getId());
        inventory.setItem(SLOT_ACTION, button(Material.HOPPER, "&a全部领取",
                "&7把收件箱内所有物品放入背包"));
        if (station.getMode() == StationMode.BOTH) {
            inventory.setItem(SLOT_SWITCH, button(Material.MINECART, "&e切换到发货区"));
        }
        inventory.setItem(SLOT_INFO, button(Material.PAPER, "&f收件箱",
                "&7已用槽位: &f" + items.size() + "/" + station.getSize(),
                "&7在途到达本站: &f" + incoming.size()));
        inventory.setItem(SLOT_CLOSE, button(Material.OAK_DOOR, "&e关闭"));
    }

    public void refreshStation(Station station) {
        for (Map.Entry<UUID, StationHolder> entry : open.entrySet()) {
            StationHolder holder = entry.getValue();
            if (!holder.getStation().getId().equals(station.getId())) {
                continue;
            }
            if (holder.getView() != StationHolder.View.RECEIVE) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            renderReceive(holder);
            player.updateInventory();
        }
    }

    private ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.component(name));
            if (lore.length > 0) {
                List<Component> lines = new ArrayList<>();
                for (String line : lore) {
                    lines.add(Msg.component(line));
                }
                meta.lore(lines);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public String formatSeconds(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("时");
        }
        if (minutes > 0) {
            sb.append(minutes).append("分");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append("秒");
        }
        return sb.toString();
    }

    public ItemFilter getFilter() {
        return filter;
    }

    public TransportService getTransport() {
        return transport;
    }
}
