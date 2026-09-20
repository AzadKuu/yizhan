package com.azadkuu.yizhan.gui;

import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.model.MailboxBlock;
import com.azadkuu.yizhan.model.Route;
import com.azadkuu.yizhan.model.Shipment;
import com.azadkuu.yizhan.model.Station;
import com.azadkuu.yizhan.model.StationMode;
import com.azadkuu.yizhan.service.ItemFilter;
import com.azadkuu.yizhan.service.TransportService;
import com.azadkuu.yizhan.storage.Storage;
import com.azadkuu.yizhan.util.Msg;
import com.azadkuu.yizhan.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class GuiManager {

    public static final int CHOOSER_SEND = 11;
    public static final int CHOOSER_RECEIVE = 15;

    public static int controlBase(int guiSize) { return guiSize - 9; }
    public static int slotClose(int guiSize) { return guiSize - 9; }
    public static int slotFee(int guiSize) { return guiSize - 8; }
    public static int slotSwitch(int guiSize) { return guiSize - 7; }
    public static int slotRoute(int guiSize) { return guiSize - 6; }
    public static int slotAction(int guiSize) { return guiSize - 5; }
    public static int slotInfo(int guiSize) { return guiSize - 1; }

    private final PluginConfig config;
    private final Storage storage;
    private final TransportService transport;
    private final ItemFilter filter;
    private final Map<UUID, StationHolder> open = new HashMap<>();
    private final Map<UUID, MailboxHolder> openMailboxes = new HashMap<>();
    private final Map<UUID, DiscardedHolder> openDiscarded = new HashMap<>();

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
        int size = view == StationHolder.View.CHOOSER ? 27 : station.getSize() + 9;
        Inventory inventory = Bukkit.createInventory(holder, size, Msg.component("&8驿站 &7· &6" + station.getTitle()));
        holder.setInventory(inventory);
        render(holder);
        open.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
    }

    public void render(StationHolder holder) {
        Map<Integer, ItemStack> buffer = null;
        int size = holder.getStation().getSize();
        if (holder.getView() == StationHolder.View.SEND) {
            buffer = new TreeMap<>();
            for (int i = 0; i < size; i++) {
                ItemStack item = holder.getInventory().getItem(i);
                if (item != null && !item.getType().isAir()) {
                    buffer.put(i, item);
                }
            }
        }
        holder.getInventory().clear();
        switch (holder.getView()) {
            case CHOOSER -> renderChooser(holder);
            case SEND -> renderSend(holder);
            case RECEIVE -> renderReceive(holder);
        }
        if (buffer != null) {
            for (Map.Entry<Integer, ItemStack> entry : buffer.entrySet()) {
                holder.getInventory().setItem(entry.getKey(), entry.getValue());
            }
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
        int gui = inventory.getSize();
        Station station = holder.getStation();
        List<Route> routes = holder.getRoutes();
        if (routes.isEmpty()) {
            inventory.setItem(slotInfo(gui), button(Material.BARRIER, "&c未配置路由",
                    "&7请管理员执行 &f/yz route " + station.getId() + " <目标站>"));
            inventory.setItem(slotClose(gui), button(Material.OAK_DOOR, "&e关闭"));
            return;
        }
        Route route = routes.get(Math.floorMod(holder.getSelectedRoute(), routes.size()));
        int buffer = transport.resolveBufferSeconds(route, station);
        int inTransit = storage.listInTransitFrom(station.getId()).size();
        int fee = route.getFee();
        if (holder.getFeeItem() != null && !holder.getFeeItem().getType().isAir()) {
            inventory.setItem(slotFee(gui), holder.getFeeItem());
        } else if (fee > 0) {
            inventory.setItem(slotFee(gui), button(Material.GOLD_NUGGET, "&e快递费槽",
                    "&7本线路需要 &f" + fee + " &7" + config.getCurrencyName(),
                    "&7把带 &f" + config.getCurrencyKey() + " &7键的物品放入此格"));
        }
        inventory.setItem(slotRoute(gui), button(Material.COMPASS, "&b切换目的地",
                "&7当前目的地: &f" + route.getToStation(),
                "&7点击切换到下一个路由"));
        inventory.setItem(slotAction(gui), button(Material.MINECART, "&a点击发货",
                "&7目的地: &f" + route.getToStation(),
                "&7缓冲时间: &f" + formatSeconds(buffer),
                fee > 0 ? "&7快递费: &f" + fee + " &7" + config.getCurrencyName() : "&7快递费: &f无",
                "&7放入发货区的物品将进入在途状态"));
        if (station.getMode() == StationMode.BOTH) {
            inventory.setItem(slotSwitch(gui), button(Material.CHEST, "&e切换到收件箱"));
        }
        inventory.setItem(slotInfo(gui), button(Material.PAPER, "&f发货区",
                "&7在途发货单: &f" + inTransit,
                fee > 0 ? "&7快递费槽: &f底部左起第 2 格 &7(需 &f" + fee + " " + config.getCurrencyName() + "&7)" : "&7本线路免快递费",
                "&7默认缓冲: &f" + formatSeconds(config.getDefaultBufferSeconds())));
        inventory.setItem(slotClose(gui), button(Material.OAK_DOOR, "&e关闭"));
    }

    private void renderReceive(StationHolder holder) {
        Inventory inventory = holder.getInventory();
        int gui = inventory.getSize();
        Station station = holder.getStation();
        Map<Integer, ItemStack> items = storage.loadStationItems(station.getId());
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            int slot = entry.getKey();
            if (slot >= 0 && slot < station.getSize()) {
                inventory.setItem(slot, entry.getValue());
            }
        }
        holder.setBaseVersion(station.getVersion());
        List<Shipment> incoming = storage.listInTransitTo(station.getId());
        inventory.setItem(slotAction(gui), button(Material.HOPPER, "&a全部领取",
                "&7把收件箱内所有物品放入背包"));
        if (station.getMode() == StationMode.BOTH) {
            inventory.setItem(slotSwitch(gui), button(Material.MINECART, "&e切换到发货区"));
        }
        inventory.setItem(slotInfo(gui), button(Material.PAPER, "&f收件箱",
                "&7已用槽位: &f" + items.size() + "/" + station.getSize(),
                "&7在途到达本站: &f" + incoming.size()));
        inventory.setItem(slotClose(gui), button(Material.OAK_DOOR, "&e关闭"));
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

    public MailboxHolder getOpenMailbox(UUID uuid) {
        return openMailboxes.get(uuid);
    }

    public void unregisterMailbox(UUID uuid) {
        openMailboxes.remove(uuid);
    }

    public void openMailbox(Player player) {
        UUID uuid = player.getUniqueId();
        int items = config.getMailboxSize();
        MailboxHolder holder = new MailboxHolder(uuid);
        Inventory inventory = Bukkit.createInventory(holder, items + 9,
                Msg.component("&8邮箱 &7· &6" + player.getName()));
        holder.setInventory(inventory);
        Map<Integer, ItemStack> stored = storage.loadMailboxItems(uuid);
        for (Map.Entry<Integer, ItemStack> entry : stored.entrySet()) {
            int slot = entry.getKey();
            if (slot >= 0 && slot < items) {
                inventory.setItem(slot, entry.getValue());
            }
        }
        holder.setOriginalSlots(stored.keySet());
        holder.setPendingCount(storage.countMailboxOverflow(uuid));
        renderMailbox(holder);
        openMailboxes.put(uuid, holder);
        player.openInventory(inventory);
    }

    public void renderMailbox(MailboxHolder holder) {
        Inventory inventory = holder.getInventory();
        int items = config.getMailboxSize();
        int used = 0;
        for (int i = 0; i < items; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                used++;
            }
        }
        inventory.setItem(mailSlotClose(items), button(Material.OAK_DOOR, "&e关闭"));
        inventory.setItem(mailSlotTakeAll(items), button(Material.HOPPER, "&a全部领取",
                "&7把邮箱内所有物品放入背包"));
        inventory.setItem(mailSlotInfo(items), button(Material.PAPER, "&f我的邮箱",
                "&7已用槽位: &f" + used + "/" + items,
                "&7这是你自己的邮箱，别人看不到"));
        int pending = holder.getPendingCount();
        if (pending > 0) {
            inventory.setItem(mailSlotReclaim(items), button(Material.ENDER_CHEST, "&6重新领取邮件",
                    "&7待领取: &f" + pending + " 件",
                    "&7邮箱满时收到的邮件会暂存在这里",
                    "&7清理出空位后点击补入邮箱"));
        } else {
            inventory.setItem(mailSlotReclaim(items), button(Material.GRAY_DYE, "&7暂无待领取邮件",
                    "&7邮箱满时收到的邮件会暂存在这里"));
        }
    }

    public static int mailSlotClose(int items) {
        return items;
    }

    public static int mailSlotTakeAll(int items) {
        return items + 4;
    }

    public static int mailSlotReclaim(int items) {
        return items + 6;
    }

    public static int mailSlotInfo(int items) {
        return items + 8;
    }

    public static final int DISCARDED_SLOT_CLOSE = 49;
    public static final int DISCARDED_SLOT_INFO = 53;

    public DiscardedHolder getOpenDiscarded(UUID uuid) {
        return openDiscarded.get(uuid);
    }

    public void unregisterDiscarded(UUID uuid) {
        openDiscarded.remove(uuid);
    }

    public void openDiscarded(Player player) {
        UUID uuid = player.getUniqueId();
        DiscardedHolder holder = new DiscardedHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, Msg.component("&8丢弃物品仓库 &7· &6管理员"));
        holder.setInventory(inventory);
        Map<Long, ItemStack> items = storage.listDiscardedItems();
        int slot = 0;
        for (Map.Entry<Long, ItemStack> entry : items.entrySet()) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot, entry.getValue());
            holder.getSlotToId().put(slot, entry.getKey());
            slot++;
        }
        renderDiscarded(holder);
        openDiscarded.put(uuid, holder);
        player.openInventory(inventory);
    }

    public void renderDiscarded(DiscardedHolder holder) {
        Inventory inventory = holder.getInventory();
        int count = holder.getSlotToId().size();
        inventory.setItem(DISCARDED_SLOT_CLOSE, button(Material.OAK_DOOR, "&e关闭"));
        inventory.setItem(DISCARDED_SLOT_INFO, button(Material.PAPER, "&f丢弃物品仓库",
                "&7当前显示: &f" + count + " &7件",
                "&7点击物品取出到背包",
                "&7超过 45 件仅显示前 45 件"));
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
        return TimeUtil.formatSeconds(totalSeconds);
    }

    public ItemFilter getFilter() {
        return filter;
    }

    public TransportService getTransport() {
        return transport;
    }
}
