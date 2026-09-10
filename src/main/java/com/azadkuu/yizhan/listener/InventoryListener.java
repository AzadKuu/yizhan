package com.azadkuu.yizhan.listener;

import com.azadkuu.yizhan.YizhanPlugin;
import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.gui.GuiManager;
import com.azadkuu.yizhan.gui.StationHolder;
import com.azadkuu.yizhan.model.Route;
import com.azadkuu.yizhan.service.ItemFilter;
import com.azadkuu.yizhan.service.NotificationService;
import com.azadkuu.yizhan.service.TransportService;
import com.azadkuu.yizhan.storage.Storage;
import com.azadkuu.yizhan.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class InventoryListener implements Listener {

    private final YizhanPlugin plugin;
    private final PluginConfig config;
    private final Storage storage;
    private final ItemFilter filter;
    private final TransportService transport;
    private final NotificationService notificationService;
    private final GuiManager guiManager;

    public InventoryListener(YizhanPlugin plugin, PluginConfig config, Storage storage, ItemFilter filter,
                             TransportService transport, NotificationService notificationService, GuiManager guiManager) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.filter = filter;
        this.transport = transport;
        this.notificationService = notificationService;
        this.guiManager = guiManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof StationHolder holder)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        int raw = event.getRawSlot();
        int guiSize = top.getSize();
        if (raw < 0 || raw >= guiSize) {
            return;
        }

        if (holder.getView() == StationHolder.View.CHOOSER) {
            event.setCancelled(true);
            handleChooser(player, holder, raw);
            return;
        }

        if (isControlSlot(raw, holder)) {
            event.setCancelled(true);
            handleControl(player, holder, raw);
            return;
        }

        if (raw >= holder.getStation().getSize()) {
            event.setCancelled(true);
            return;
        }

        if (holder.getView() == StationHolder.View.RECEIVE) {
            if (isInsert(event, guiSize)) {
                event.setCancelled(true);
                Msg.send(player, config.getPrefix(), "&c收件箱只能取出，不能放入");
            } else {
                holder.setDirty(true);
            }
            return;
        }

        ItemStack incoming = resolveIncoming(event, guiSize);
        if (incoming != null && filter.isBlocked(incoming)) {
            event.setCancelled(true);
            Msg.send(player, config.getPrefix(), "&c物品 &f" + filter.describe(incoming) + " &c被识别为自定义物品，禁止运输");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof StationHolder holder)) {
            return;
        }
        int guiSize = top.getSize();
        boolean touchesGui = false;
        for (int slot : event.getRawSlots()) {
            if (slot < guiSize) {
                touchesGui = true;
                break;
            }
        }
        if (!touchesGui) {
            return;
        }
        if (holder.getView() == StationHolder.View.CHOOSER) {
            event.setCancelled(true);
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < guiSize && (isControlSlot(slot, holder) || slot >= holder.getStation().getSize())) {
                event.setCancelled(true);
                return;
            }
        }
        if (holder.getView() == StationHolder.View.RECEIVE) {
            event.setCancelled(true);
            return;
        }
        if (filter.isBlocked(event.getOldCursor())) {
            event.setCancelled(true);
            Msg.send((Player) event.getWhoClicked(), config.getPrefix(), "&c物品被识别为自定义物品，禁止运输");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof StationHolder holder)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        if (guiManager.getOpenHolder(player.getUniqueId()) != holder) {
            return;
        }
        guiManager.unregister(player.getUniqueId());
        if (holder.getView() == StationHolder.View.CHOOSER) {
            return;
        }
        if (holder.getView() == StationHolder.View.SEND) {
            if (!holder.isShipped()) {
                returnItems(player, holder);
            }
        } else {
            saveReceive(player, holder);
        }
    }

    private void handleChooser(Player player, StationHolder holder, int raw) {
        if (raw == GuiManager.CHOOSER_SEND) {
            guiManager.open(player, holder.getStation(), StationHolder.View.SEND);
        } else if (raw == GuiManager.CHOOSER_RECEIVE) {
            guiManager.open(player, holder.getStation(), StationHolder.View.RECEIVE);
        } else if (raw == 22) {
            player.closeInventory();
        }
    }

    private void handleControl(Player player, StationHolder holder, int raw) {
        if (raw == GuiManager.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (raw == GuiManager.SLOT_SWITCH) {
            switchView(player, holder);
            return;
        }
        if (raw == GuiManager.SLOT_ROUTE && holder.getView() == StationHolder.View.SEND) {
            cycleRoute(player, holder);
            return;
        }
        if (raw == GuiManager.SLOT_ACTION) {
            if (holder.getView() == StationHolder.View.SEND) {
                handleShip(player, holder);
            } else {
                claimAll(player, holder);
            }
        }
    }

    private void cycleRoute(Player player, StationHolder holder) {
        if (holder.getRoutes().size() <= 1) {
            Msg.send(player, config.getPrefix(), "&7当前只有一个可用路由");
            return;
        }
        holder.setSelectedRoute(holder.getSelectedRoute() + 1);
        guiManager.render(holder);
        player.updateInventory();
        Route route = holder.getRoutes().get(Math.floorMod(holder.getSelectedRoute(), holder.getRoutes().size()));
        Msg.send(player, config.getPrefix(), "&7当前目的地已切换为 &f" + route.getToStation());
    }

    private void switchView(Player player, StationHolder holder) {
        if (holder.getView() == StationHolder.View.SEND) {
            returnItems(player, holder);
            guiManager.open(player, holder.getStation(), StationHolder.View.RECEIVE);
        } else {
            saveReceive(player, holder);
            guiManager.open(player, holder.getStation(), StationHolder.View.SEND);
        }
    }

    private void handleShip(Player player, StationHolder holder) {
        List<Route> routes = holder.getRoutes();
        if (routes.isEmpty()) {
            Msg.send(player, config.getPrefix(), "&c本站未配置路由，无法发货");
            return;
        }
        Route route = routes.get(Math.floorMod(holder.getSelectedRoute(), routes.size()));
        Inventory inventory = holder.getInventory();
        int size = holder.getStation().getSize();
        Map<Integer, ItemStack> items = new TreeMap<>();
        for (int i = 0; i < size; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (filter.isBlocked(item)) {
                Msg.send(player, config.getPrefix(), "&c发货区存在自定义物品 &f" + filter.describe(item) + "&c，已阻止发货");
                return;
            }
            items.put(i, item.clone());
        }
        if (items.isEmpty()) {
            Msg.send(player, config.getPrefix(), "&7发货区是空的");
            return;
        }
        long shipmentId;
        try {
            shipmentId = transport.ship(holder.getStation(), route, player.getUniqueId(), items);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("ship failed: " + ex.getMessage());
            Msg.send(player, config.getPrefix(), "&c发货失败，请稍后重试");
            return;
        }
        holder.setShipped(true);
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, null);
        }
        player.closeInventory();
        int buffer = transport.resolveBufferSeconds(route, holder.getStation());
        Msg.send(player, config.getPrefix(), notificationService.shipStart(shipmentId, route.getToStation(), buffer));
    }

    private void claimAll(Player player, StationHolder holder) {
        Inventory inventory = holder.getInventory();
        int size = holder.getStation().getSize();
        int moved = 0;
        for (int i = 0; i < size; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            inventory.setItem(i, null);
            Map<Integer, ItemStack> left = player.getInventory().addItem(item);
            for (ItemStack drop : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            moved++;
        }
        if (moved > 0) {
            holder.setDirty(true);
            Msg.send(player, config.getPrefix(), "&a已领取 &f" + moved + " &a组物品");
        }
    }

    private void returnItems(Player player, StationHolder holder) {
        Inventory inventory = holder.getInventory();
        int size = holder.getStation().getSize();
        for (int i = 0; i < size; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            inventory.setItem(i, null);
            Map<Integer, ItemStack> left = player.getInventory().addItem(item);
            for (ItemStack drop : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    private void saveReceive(Player player, StationHolder holder) {
        if (!holder.isDirty()) {
            return;
        }
        Inventory inventory = holder.getInventory();
        int size = holder.getStation().getSize();
        Map<Integer, ItemStack> items = new TreeMap<>();
        for (int i = 0; i < size; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            items.put(i, item.clone());
        }
        try {
            int newVersion = storage.saveStationItems(holder.getStation().getId(), items, holder.getBaseVersion());
            if (newVersion < 0) {
                Msg.send(player, config.getPrefix(), "&c收件箱内容已被其他操作更新，本次更改未保存");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        guiManager.open(player, storage.getStation(holder.getStation().getId()), StationHolder.View.RECEIVE);
                    }
                });
            } else {
                holder.setBaseVersion(newVersion);
                holder.getStation().setVersion(newVersion);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("saveReceive failed: " + ex.getMessage());
            Msg.send(player, config.getPrefix(), "&c保存收件箱失败");
        }
    }

    private boolean isControlSlot(int raw, StationHolder holder) {
        if (holder.getView() == StationHolder.View.CHOOSER) {
            return raw == GuiManager.CHOOSER_SEND || raw == GuiManager.CHOOSER_RECEIVE || raw == 22;
        }
        return raw == GuiManager.SLOT_CLOSE || raw == GuiManager.SLOT_SWITCH
                || raw == GuiManager.SLOT_ROUTE || raw == GuiManager.SLOT_ACTION || raw == GuiManager.SLOT_INFO;
    }

    private boolean isInsert(InventoryClickEvent event, int guiSize) {
        return switch (event.getAction()) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> true;
            case MOVE_TO_OTHER_INVENTORY -> event.getRawSlot() >= guiSize;
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> true;
            default -> false;
        };
    }

    private ItemStack resolveIncoming(InventoryClickEvent event, int guiSize) {
        return switch (event.getAction()) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> event.getCursor();
            case MOVE_TO_OTHER_INVENTORY -> event.getRawSlot() >= guiSize ? event.getCurrentItem() : null;
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                int button = event.getHotbarButton();
                if (button >= 0) {
                    yield event.getWhoClicked().getInventory().getItem(button);
                }
                yield event.getCursor();
            }
            default -> null;
        };
    }
}
