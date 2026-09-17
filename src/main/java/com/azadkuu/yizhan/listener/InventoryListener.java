package com.azadkuu.yizhan.listener;

import com.azadkuu.yizhan.YizhanPlugin;
import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.gui.GuiManager;
import com.azadkuu.yizhan.gui.MailboxHolder;
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
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

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
        Player player = (Player) event.getWhoClicked();
        if (top.getHolder() instanceof MailboxHolder mailbox) {
            handleMailboxClick(event, player, top, mailbox);
            return;
        }
        if (!(top.getHolder() instanceof StationHolder holder)) {
            return;
        }
        int raw = event.getRawSlot();
        int guiSize = top.getSize();
        if (raw < 0) {
            return;
        }

        if (raw >= guiSize) {
            handlePlayerInventoryClick(event, player, top, holder, guiSize);
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

        if (holder.getView() == StationHolder.View.SEND && raw == GuiManager.SLOT_FEE) {
            event.setCancelled(true);
            handleFeeSlot(event, player, holder);
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
            sendItemBlocked(player, incoming, " &c被识别为自定义物品，禁止运输");
        }
    }

    /**
     * 处理点击落在玩家背包区域的情况。shift 点击（MOVE_TO_OTHER_INVENTORY）会把背包物品
     * 移动到 GUI，必须在这里显式接管，否则会绕过物品过滤直接进入发货区。
     */
    private void handlePlayerInventoryClick(InventoryClickEvent event, Player player, Inventory top,
                                            StationHolder holder, int guiSize) {
        if (event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return;
        }
        if (holder.getView() == StationHolder.View.CHOOSER) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        if (holder.getView() == StationHolder.View.RECEIVE) {
            Msg.send(player, config.getPrefix(), "&c收件箱只能取出，不能放入");
            return;
        }
        if (filter.isBlocked(current)) {
            sendItemBlocked(player, current, " &c被识别为自定义物品，禁止运输");
            return;
        }
        ItemStack leftover = moveInto(top, 0, holder.getStation().getSize(), current);
        event.setCurrentItem(leftover);
        player.updateInventory();
    }

    private void handleMailboxClick(InventoryClickEvent event, Player player, Inventory top, MailboxHolder mailbox) {
        int raw = event.getRawSlot();
        int guiSize = top.getSize();
        int items = config.getMailboxSize();
        if (raw < 0) {
            return;
        }
        if (raw >= guiSize) {
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
                ItemStack current = event.getCurrentItem();
                if (current == null || current.getType().isAir()) {
                    return;
                }
                ItemStack leftover = moveInto(top, 0, items, current);
                event.setCurrentItem(leftover);
                player.updateInventory();
            }
            return;
        }
        if (raw >= items) {
            event.setCancelled(true);
            if (raw == GuiManager.mailSlotClose(items)) {
                player.closeInventory();
            } else if (raw == GuiManager.mailSlotTakeAll(items)) {
                claimAllMailbox(player, mailbox);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof MailboxHolder) {
            int items = config.getMailboxSize();
            for (int slot : event.getRawSlots()) {
                if (slot >= items && slot < top.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
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
            if (slot < guiSize && (isControlSlot(slot, holder) || slot == GuiManager.SLOT_FEE
                    || slot >= holder.getStation().getSize())) {
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
            sendItemBlocked((Player) event.getWhoClicked(), event.getOldCursor(), " &c被识别为自定义物品，禁止运输");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        Player player = (Player) event.getPlayer();
        if (top.getHolder() instanceof MailboxHolder mailbox) {
            if (guiManager.getOpenMailbox(player.getUniqueId()) != mailbox) {
                return;
            }
            guiManager.unregisterMailbox(player.getUniqueId());
            saveMailbox(mailbox);
            return;
        }
        if (!(top.getHolder() instanceof StationHolder holder)) {
            return;
        }
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

    private void handleFeeSlot(InventoryClickEvent event, Player player, StationHolder holder) {
        ItemStack stored = holder.getFeeItem();
        if (stored != null && stored.getType().isAir()) {
            stored = null;
        }
        ItemStack cursor = event.getCursor();
        boolean hasCursor = cursor != null && !cursor.getType().isAir();
        String hint = "&c快递费槽只能放带 &f" + config.getCurrencyKey() + " &c键的货币物品";

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType().isAir()) {
                return;
            }
            if (!filter.isCurrency(current)) {
                Msg.send(player, config.getPrefix(), hint);
            } else if (stored != null) {
                Msg.send(player, config.getPrefix(), "&7快递费槽已有物品，请先取出");
            } else {
                holder.setFeeItem(current.clone());
                event.setCurrentItem(null);
            }
        } else if (hasCursor) {
            if (!filter.isCurrency(cursor)) {
                Msg.send(player, config.getPrefix(), hint);
            } else {
                holder.setFeeItem(cursor.clone());
                event.setCursor(stored);
            }
        } else if (stored == null) {
            Msg.send(player, config.getPrefix(), "&7快递费槽是空的，请放入带 &f"
                    + config.getCurrencyKey() + " &7键的货币物品");
        } else {
            holder.setFeeItem(null);
            event.setCursor(stored);
        }
        guiManager.render(holder);
        player.updateInventory();
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
        int fee = route.getFee();
        ItemStack feeItem = holder.getFeeItem();
        if (fee > 0) {
            if (feeItem == null || feeItem.getType().isAir() || !filter.isCurrency(feeItem)) {
                Msg.send(player, config.getPrefix(), "&c本线路需要 &f" + fee
                        + " &c个货币物品作为快递费，请放入快递费槽");
                return;
            }
            if (feeItem.getAmount() < fee) {
                Msg.send(player, config.getPrefix(), "&c快递费不足，需要 &f" + fee
                        + " &c个，当前只有 &f" + feeItem.getAmount() + " &c个");
                return;
            }
        }
        Inventory inventory = holder.getInventory();
        int size = holder.getStation().getSize();
        Map<Integer, ItemStack> items = new TreeMap<>();
        for (int i = 0; i < size; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (filter.isBlocked(item)) {
                player.sendMessage(Msg.join(
                        Msg.component(config.getPrefix()),
                        Msg.component("&c发货区存在自定义物品 "),
                        filter.nameComponent(item),
                        Msg.component(" &c，已阻止发货")));
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
        if (feeItem != null && !feeItem.getType().isAir()) {
            int remain = Math.max(0, feeItem.getAmount() - fee);
            if (remain > 0) {
                ItemStack back = feeItem.clone();
                back.setAmount(remain);
                giveOrDrop(player, back);
            }
            holder.setFeeItem(null);
            inventory.setItem(GuiManager.SLOT_FEE, null);
        }
        player.closeInventory();
        int buffer = transport.resolveBufferSeconds(route, holder.getStation());
        if (config.isDebug()) {
            plugin.getLogger().info("[debug] 发货成功 #" + shipmentId + " " + holder.getStation().getId()
                    + " -> " + route.getToStation() + " 物品组数=" + items.size()
                    + " 快递费=" + fee + " 缓冲=" + buffer + "s");
        }
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
            giveOrDrop(player, item);
            moved++;
        }
        if (moved > 0) {
            holder.setDirty(true);
            Msg.send(player, config.getPrefix(), "&a已领取 &f" + moved + " &a组物品");
        }
    }

    private void claimAllMailbox(Player player, MailboxHolder holder) {
        Inventory inventory = holder.getInventory();
        int items = config.getMailboxSize();
        int moved = 0;
        for (int i = 0; i < items; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            inventory.setItem(i, null);
            giveOrDrop(player, item);
            moved++;
        }
        guiManager.renderMailbox(holder);
        player.updateInventory();
        if (moved > 0) {
            Msg.send(player, config.getPrefix(), "&a已领取 &f" + moved + " &a组物品");
        } else {
            Msg.send(player, config.getPrefix(), "&7邮箱是空的");
        }
    }

    private void sendItemBlocked(Player player, ItemStack item, String suffix) {
        player.sendMessage(Msg.join(
                Msg.component(config.getPrefix()),
                Msg.component("&c物品 "),
                filter.nameComponent(item),
                Msg.component(suffix)));
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
            giveOrDrop(player, item);
        }
        ItemStack feeItem = holder.getFeeItem();
        if (feeItem != null && !feeItem.getType().isAir()) {
            holder.setFeeItem(null);
            inventory.setItem(GuiManager.SLOT_FEE, null);
            giveOrDrop(player, feeItem);
        }
    }

    private void saveMailbox(MailboxHolder holder) {
        Inventory inventory = holder.getInventory();
        int items = config.getMailboxSize();
        Map<Integer, ItemStack> snapshot = new TreeMap<>();
        for (int i = 0; i < items; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            snapshot.put(i, item.clone());
        }
        UUID owner = holder.getOwner();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                storage.saveMailboxItems(owner, snapshot);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("保存邮箱失败: " + ex.getMessage());
            }
        });
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

    private void giveOrDrop(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        Map<Integer, ItemStack> left = player.getInventory().addItem(item);
        for (ItemStack drop : left.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private ItemStack moveInto(Inventory top, int from, int to, ItemStack source) {
        ItemStack moving = source.clone();
        for (int i = from; i < to && !moving.getType().isAir(); i++) {
            ItemStack slotItem = top.getItem(i);
            if (slotItem == null || slotItem.getType().isAir()) {
                top.setItem(i, moving);
                return null;
            }
            if (slotItem.isSimilar(moving)) {
                int space = slotItem.getMaxStackSize() - slotItem.getAmount();
                if (space > 0) {
                    int move = Math.min(space, moving.getAmount());
                    slotItem.setAmount(slotItem.getAmount() + move);
                    top.setItem(i, slotItem);
                    moving.setAmount(moving.getAmount() - move);
                }
            }
        }
        return moving.getType().isAir() ? null : moving;
    }
}
