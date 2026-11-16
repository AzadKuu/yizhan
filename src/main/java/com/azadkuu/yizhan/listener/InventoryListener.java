package com.azadkuu.yizhan.listener;

import com.azadkuu.yizhan.YizhanPlugin;
import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.gui.GuiManager;
import com.azadkuu.yizhan.gui.LinkMailListHolder;
import com.azadkuu.yizhan.gui.LinkMailViewHolder;
import com.azadkuu.yizhan.gui.ContactsHolder;
import com.azadkuu.yizhan.gui.DiscardedHolder;
import com.azadkuu.yizhan.gui.MailSendHolder;
import com.azadkuu.yizhan.gui.MailboxHolder;
import com.azadkuu.yizhan.model.LinkMail;
import com.azadkuu.yizhan.model.MailContact;
import com.azadkuu.yizhan.gui.StationHolder;
import com.azadkuu.yizhan.model.Route;
import com.azadkuu.yizhan.model.Station;
import com.azadkuu.yizhan.service.ItemFilter;
import com.azadkuu.yizhan.service.NotificationService;
import com.azadkuu.yizhan.service.TransportService;
import com.azadkuu.yizhan.storage.Storage;
import com.azadkuu.yizhan.util.LinkGuard;
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
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.WritableBookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
        if (top.getHolder() instanceof DiscardedHolder discarded) {
            handleDiscardedClick(event, player, top, discarded);
            return;
        }
        if (top.getHolder() instanceof ContactsHolder contacts) {
            handleContactsClick(event, player, top, contacts);
            return;
        }
        if (top.getHolder() instanceof MailSendHolder send) {
            handleMailSendClick(event, player, top, send);
            return;
        }
        if (top.getHolder() instanceof LinkMailListHolder linkList) {
            handleLinkMailListClick(event, player, top, linkList);
            return;
        }
        if (top.getHolder() instanceof LinkMailViewHolder linkView) {
            handleLinkMailViewClick(event, player, top, linkView);
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

        if (holder.getView() == StationHolder.View.SEND && raw == GuiManager.slotFee(holder.getInventory().getSize())) {
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
                Msg.send(player, config.getPrefix(), config.getReceiveOnlyMessage());
            } else {
                holder.setDirty(true);
            }
            return;
        }

        ItemStack incoming = resolveIncoming(event, guiSize);
        if (incoming != null && filter.isBlocked(incoming)) {
            event.setCancelled(true);
            sendItemBlocked(player, incoming, config.getItemBlockedMessage());
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
            Msg.send(player, config.getPrefix(), config.getReceiveOnlyMessage());
            return;
        }
        if (filter.isBlocked(current)) {
            sendItemBlocked(player, current, config.getItemBlockedMessage());
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
            } else if (raw == GuiManager.mailSlotReclaim(items)) {
                reclaimMailbox(player, mailbox);
            } else if (raw == GuiManager.mailSlotContacts(items)) {
                guiManager.openContacts(player, 0);
            } else if (raw == GuiManager.mailSlotLinks(items)) {
                guiManager.openLinkMails(player, 0);
            }
        }
    }

    private void handleDiscardedClick(InventoryClickEvent event, Player player, Inventory top, DiscardedHolder holder) {
        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }
        if (raw >= 54) {
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (raw == GuiManager.DISCARDED_SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (raw >= 45) {
            return;
        }
        Long id = holder.getSlotToId().get(raw);
        if (id == null) {
            return;
        }
        ItemStack item = top.getItem(raw);
        if (item == null || item.getType().isAir()) {
            return;
        }
        try {
            storage.claimDiscardedItem(id);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("claim discarded item " + id + " failed: " + ex.getMessage());
            Msg.send(player, config.getPrefix(), config.getClaimFailedMessage());
            return;
        }
        giveOrDrop(player, item.clone());
        top.setItem(raw, null);
        holder.getSlotToId().remove(raw);
        guiManager.renderDiscarded(holder);
        player.updateInventory();
    }

    private void handleContactsClick(InventoryClickEvent event, Player player, Inventory top, ContactsHolder holder) {
        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }
        if (raw >= top.getSize()) {
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (raw == GuiManager.contactSlotClose()) {
            player.closeInventory();
            return;
        }
        if (raw == GuiManager.contactSlotPrev()) {
            guiManager.openContacts(player, holder.getPage() - 1);
            return;
        }
        if (raw == GuiManager.contactSlotNext()) {
            guiManager.openContacts(player, holder.getPage() + 1);
            return;
        }
        int index = holder.getPage() * GuiManager.CONTACT_LIST_SIZE + raw;
        if (index < 0 || index >= holder.getContacts().size()) {
            return;
        }
        MailContact contact = holder.getContacts().get(index);
        guiManager.openSendTo(player, contact.uuid(), contact.name());
    }

    private void handleMailSendClick(InventoryClickEvent event, Player player, Inventory top, MailSendHolder holder) {
        int raw = event.getRawSlot();
        int guiSize = top.getSize();
        int inputSlots = holder.getInputSlots();
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
                if (filter.isBlocked(current)) {
                    sendItemBlocked(player, current, config.getItemBlockedMessage());
                    return;
                }
                ItemStack leftover = moveInto(top, 0, inputSlots, current);
                event.setCurrentItem(leftover);
                player.updateInventory();
            }
            return;
        }
        if (raw < inputSlots) {
            ItemStack incoming = resolveIncoming(event, guiSize);
            if (incoming != null && filter.isBlocked(incoming)) {
                event.setCancelled(true);
                sendItemBlocked(player, incoming, config.getItemBlockedMessage());
            }
            return;
        }
        event.setCancelled(true);
        if (raw == 45) {
            returnMailSendItems(player, holder);
            guiManager.openContacts(player, 0);
        } else if (raw == 49) {
            confirmMailSend(player, top, holder);
        } else if (raw == 51) {
            giveLinkBook(player);
        } else if (raw == 53) {
            player.closeInventory();
        }
    }

    private void confirmMailSend(Player player, Inventory top, MailSendHolder holder) {
        int inputSlots = holder.getInputSlots();
        int linkSlot = -1;
        for (int i = 0; i < inputSlots; i++) {
            if (isLinkBook(top.getItem(i))) {
                linkSlot = i;
                break;
            }
        }
        if (linkSlot >= 0) {
            sendLinkMail(player, top, holder, linkSlot);
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < inputSlots; i++) {
            ItemStack item = top.getItem(i);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        if (items.isEmpty()) {
            Msg.send(player, config.getPrefix(), "&7邮寄区还是空的，先放点要寄的东西吧");
            return;
        }
        for (int i = 0; i < inputSlots; i++) {
            top.setItem(i, null);
        }
        holder.setConfirmed(true);
        String itemName = items.get(0).getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String receive = config.getMailReceivedFromMessage()
                .replace("%amount%", String.valueOf(items.size()))
                .replace("%item%", itemName)
                .replace("%sender%", player.getName());
        try {
            plugin.getMailboxService().deliver(holder.getTarget(), items, receive);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("send mail to " + holder.getTarget() + " failed: " + ex.getMessage());
            for (ItemStack item : items) {
                giveOrDrop(player, item);
            }
            Msg.send(player, config.getPrefix(), config.getClaimFailedMessage());
            player.closeInventory();
            return;
        }
        Msg.send(player, config.getPrefix(), config.getMailSentMessage()
                .replace("%amount%", String.valueOf(items.size()))
                .replace("%item%", itemName)
                .replace("%target%", holder.getTargetName()));
        player.closeInventory();
    }

    private NamespacedKey linkBookKey() {
        return new NamespacedKey(plugin, "link_mail");
    }

    private boolean isLinkBook(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte flag = meta.getPersistentDataContainer().get(linkBookKey(), PersistentDataType.BYTE);
        if (flag != null && flag == 1) {
            return true;
        }
        // 兜底：客户端编辑成书后个别情况可能不留 PDC 标记，只要页里有 http 链接就按链接邮件处理
        return item.getType() == org.bukkit.Material.WRITABLE_BOOK
                && readLinkBook(item).contains("http");
    }

    private String readLinkBook(ItemStack item) {
        if (item == null) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof WritableBookMeta book) {
            List<String> pages = book.getPages();
            if (pages == null || pages.isEmpty()) {
                return "";
            }
            return String.join(" ", pages);
        }
        return "";
    }

    private void giveLinkBook(Player player) {
        if (!config.isLinkMailEnabled()) {
            Msg.send(player, config.getPrefix(), "&7本服没有开放寄链接的功能");
            return;
        }
        ItemStack book = new ItemStack(org.bukkit.Material.WRITABLE_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.component("&b链接邮件"));
            meta.lore(List.of(
                    Msg.component("&7把视频链接写在书里"),
                    Msg.component("&7写完放回上面的邮寄区"),
                    Msg.component("&7再点「确认发送」")));
            if (meta instanceof WritableBookMeta writable) {
                writable.setPages("把视频链接写在这一页（支持 bilibili.com / b23.tv），"
                        + "然后把书放回邮寄区，再点「确认发送」。");
            }
            meta.getPersistentDataContainer().set(linkBookKey(), PersistentDataType.BYTE, (byte) 1);
            book.setItemMeta(meta);
        }
        if (!player.getInventory().addItem(book).isEmpty()) {
            giveOrDrop(player, book);
        }
        Msg.send(player, config.getPrefix(), "&a拿到一本「链接邮件」书：&7写上链接，放回邮寄区再确认");
    }

    private void sendLinkMail(Player player, Inventory top, MailSendHolder holder, int slot) {
        ItemStack book = top.getItem(slot);
        String raw = readLinkBook(book);
        top.setItem(slot, null);
        // 邮寄区里除这本书以外的物品先退回，confirmed=true 之后 onClose 不会再退
        for (int i = 0; i < holder.getInputSlots(); i++) {
            if (i == slot) {
                continue;
            }
            ItemStack other = top.getItem(i);
            if (other != null && !other.getType().isAir()) {
                top.setItem(i, null);
                giveOrDrop(player, other);
            }
        }
        String url = plugin.getLinkGuard().firstAllowedUrl(raw);
        if (url == null) {
            giveOrDrop(player, book);
            Msg.send(player, config.getPrefix(), "&c书里没有白名单链接：&7只支持 "
                    + String.join(" / ", config.getLinkWhitelist()));
            return;
        }
        if (url.length() > config.getLinkMailMaxLength()) {
            giveOrDrop(player, book);
            Msg.send(player, config.getPrefix(), "&c链接太长了（上限 " + config.getLinkMailMaxLength() + " 字符）");
            return;
        }
        int sent = storage.countLinkMailsSince(player.getUniqueId(), config.getServerId(),
                System.currentTimeMillis() - 86_400_000L);
        if (sent >= config.getLinkMailMaxPerDay()) {
            giveOrDrop(player, book);
            Msg.send(player, config.getPrefix(), "&c今天已经寄了 " + sent + " 条链接，明天再来吧");
            return;
        }
        holder.setConfirmed(true);
        giveOrDrop(player, book);
        plugin.getMailboxService().deliverLink(holder.getTarget(), player.getUniqueId(), player.getName(), url,
                config.getMailReceivedFromMessage()
                        .replace("%amount%", "1")
                        .replace("%item%", "视频链接")
                        .replace("%sender%", player.getName()));
        Msg.send(player, config.getPrefix(), "&a链接已寄给 &f" + holder.getTargetName());
        player.closeInventory();
    }

    private void handleLinkMailListClick(InventoryClickEvent event, Player player, Inventory top,
                                        LinkMailListHolder holder) {
        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }
        if (raw >= top.getSize()) {
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (raw == GuiManager.contactSlotClose()) {
            player.closeInventory();
            return;
        }
        if (raw == GuiManager.contactSlotPrev()) {
            guiManager.openLinkMails(player, holder.getPage() - 1);
            return;
        }
        if (raw == GuiManager.contactSlotNext()) {
            guiManager.openLinkMails(player, holder.getPage() + 1);
            return;
        }
        int index = holder.getPage() * GuiManager.LINK_LIST_SIZE + raw;
        if (index < 0 || index >= holder.getMails().size()) {
            return;
        }
        LinkMail mail = holder.getMails().get(index);
        markLinkMailRead(player, mail);
        guiManager.openLinkMailView(player, mail);
    }

    private void handleLinkMailViewClick(InventoryClickEvent event, Player player, Inventory top,
                                        LinkMailViewHolder holder) {
        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }
        if (raw >= top.getSize()) {
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (raw == 22) {
            player.closeInventory();
            return;
        }
        if (raw == 15) {
            LinkMail mail = holder.getMail();
            player.closeInventory();
            Msg.send(player, Msg.component("&7来自 &f" + mail.senderName() + " &7的链接："));
            Msg.send(player, Msg.component("&b→ ")
                    .append(LinkGuard.linkText(mail.url(), config.getLinkWhitelist())));
        }
    }

    private void markLinkMailRead(Player player, LinkMail mail) {
        if (mail.read()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> storage.markLinkMailRead(mail.id(), uuid));
    }

    private void returnMailSendItems(Player player, MailSendHolder holder) {
        if (holder.isConfirmed()) {
            return;
        }
        Inventory top = holder.getInventory();
        if (top == null) {
            return;
        }
        int inputSlots = holder.getInputSlots();
        for (int i = 0; i < inputSlots; i++) {
            ItemStack item = top.getItem(i);
            if (item != null && !item.getType().isAir()) {
                top.setItem(i, null);
                giveOrDrop(player, item);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ContactsHolder) {
            event.setCancelled(true);
            return;
        }
        if (top.getHolder() instanceof LinkMailListHolder || top.getHolder() instanceof LinkMailViewHolder) {
            event.setCancelled(true);
            return;
        }
        if (top.getHolder() instanceof MailSendHolder send) {
            for (int slot : event.getRawSlots()) {
                if (slot >= send.getInputSlots() && slot < top.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
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
        if (top.getHolder() instanceof DiscardedHolder) {
            for (int slot : event.getRawSlots()) {
                if (slot < 54) {
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
            if (slot < guiSize && (isControlSlot(slot, holder) || slot == GuiManager.slotFee(guiSize)
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
            sendItemBlocked((Player) event.getWhoClicked(), event.getOldCursor(), config.getItemBlockedMessage());
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
        if (top.getHolder() instanceof DiscardedHolder) {
            guiManager.unregisterDiscarded(player.getUniqueId());
            return;
        }
        if (top.getHolder() instanceof MailSendHolder send) {
            returnMailSendItems(player, send);
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
        int gui = holder.getInventory().getSize();
        if (raw == GuiManager.slotClose(gui)) {
            player.closeInventory();
            return;
        }
        if (raw == GuiManager.slotSwitch(gui)) {
            switchView(player, holder);
            return;
        }
        if (raw == GuiManager.slotRoute(gui) && holder.getView() == StationHolder.View.SEND) {
            cycleRoute(player, holder);
            return;
        }
        if (raw == GuiManager.slotAction(gui)) {
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
        String hint = "&c本站不收这个！";

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType().isAir()) {
                return;
            }
            if (!filter.isCurrency(current)) {
                Msg.send(player, config.getPrefix(), hint);
            } else if (stored != null) {
                Msg.send(player, config.getPrefix(), config.getFeeSlotOccupiedMessage());
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
                    + config.getCurrencyKey() + " &7键的" + config.getCurrencyName());
        } else {
            holder.setFeeItem(null);
            event.setCursor(stored);
        }
        guiManager.render(holder);
        player.updateInventory();
    }

    private void cycleRoute(Player player, StationHolder holder) {
        if (holder.getRoutes().size() <= 1) {
            Msg.send(player, config.getPrefix(), config.getRouteSingleMessage());
            return;
        }
        holder.setSelectedRoute(holder.getSelectedRoute() + 1);
        guiManager.render(holder);
        player.updateInventory();
        Route route = holder.getRoutes().get(Math.floorMod(holder.getSelectedRoute(), holder.getRoutes().size()));
        String toTitle = route.getToStation();
        Station toStation = storage.getStation(route.getToStation());
        if (toStation != null && toStation.getTitle() != null && !toStation.getTitle().isBlank()) {
            toTitle = toStation.getTitle();
        }
        Msg.send(player, config.getPrefix(), config.getRouteSwitchedMessage().replace("%station%", toTitle));
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
            Msg.send(player, config.getPrefix(), config.getNoRouteMessage());
            return;
        }
        Route route = routes.get(Math.floorMod(holder.getSelectedRoute(), routes.size()));
        int fee = route.getFee();
        ItemStack feeItem = holder.getFeeItem();
        if (fee > 0) {
            if (feeItem == null || feeItem.getType().isAir() || !filter.isCurrency(feeItem)) {
                Msg.send(player, config.getPrefix(), "&c本线路需要 &f" + fee
                        + " &c" + config.getCurrencyName() + "作为快递费，请放入快递费槽");
                return;
            }
            if (feeItem.getAmount() < fee) {
                Msg.send(player, config.getPrefix(), config.getFeeInsufficientMessage()
                        .replace("%need%", String.valueOf(fee))
                        .replace("%have%", String.valueOf(feeItem.getAmount())));
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
                String blockedMsg = config.getItemBlockedInSendMessage();
                int idx = blockedMsg.indexOf("%item%");
                if (idx >= 0) {
                    player.sendMessage(Msg.join(
                            Msg.component(config.getPrefix()),
                            Msg.component(blockedMsg.substring(0, idx)),
                            filter.nameComponent(item),
                            Msg.component(blockedMsg.substring(idx + 6))));
                } else {
                    Msg.send(player, config.getPrefix(), blockedMsg);
                }
                return;
            }
            items.put(i, item.clone());
        }
        if (items.isEmpty()) {
            Msg.send(player, config.getPrefix(), config.getShipEmptyMessage());
            return;
        }
        Station target = storage.getStation(route.getToStation());
        if (target == null) {
            Msg.send(player, config.getPrefix(), config.getStationNotFoundMessage().replace("%station%", route.getToStation()));
            return;
        }
        int capacity = target.getSize();
        int need = items.size();
        int occupied;
        int pending;
        try {
            occupied = storage.countStationStacks(route.getToStation());
            pending = storage.countInTransitStacks(route.getToStation());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("检查目标驿站容量失败: " + ex.getMessage());
            Msg.send(player, config.getPrefix(), config.getShipFailedMessage());
            return;
        }
        if (occupied + pending + need > capacity) {
            int freeLeft = Math.max(0, capacity - occupied - pending);
            Msg.send(player, config.getPrefix(), config.getStationCapacityExceededMessage()
                    .replace("%station%", route.getToStation())
                    .replace("%free%", String.valueOf(freeLeft))
                    .replace("%need%", String.valueOf(need))
                    .replace("%used%", String.valueOf(occupied))
                    .replace("%pending%", String.valueOf(pending)));
            return;
        }
        long shipmentId;
        try {
            shipmentId = transport.ship(holder.getStation(), route, player.getUniqueId(), items);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("ship failed: " + ex.getMessage());
            Msg.send(player, config.getPrefix(), config.getShipFailedMessage());
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
            inventory.setItem(GuiManager.slotFee(inventory.getSize()), null);
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
            Msg.send(player, config.getPrefix(), config.getClaimSuccessMessage().replace("%amount%", String.valueOf(moved)));
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
            Msg.send(player, config.getPrefix(), config.getClaimSuccessMessage().replace("%amount%", String.valueOf(moved)));
        } else {
            Msg.send(player, config.getPrefix(), config.getMailboxEmptyMessage());
        }
    }

    private void reclaimMailbox(Player player, MailboxHolder holder) {
        int items = config.getMailboxSize();
        Inventory inventory = holder.getInventory();
        Map<Integer, ItemStack> snapshot = new TreeMap<>();
        for (int i = 0; i < items; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                snapshot.put(i, item.clone());
            }
        }
        UUID owner = holder.getOwner();
        Set<Integer> clearSlots = new TreeSet<>(holder.getOriginalSlots());
        clearSlots.removeAll(snapshot.keySet());
        holder.setOriginalSlots(snapshot.keySet());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int moved;
            int pending;
            Map<Integer, ItemStack> reloaded;
            try {
                storage.saveMailboxItems(owner, snapshot, clearSlots);
                moved = storage.reclaimMailboxOverflow(owner, items);
                reloaded = storage.loadMailboxItems(owner);
                pending = storage.countMailboxOverflow(owner);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("重新领取邮件失败: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin,
                        () -> Msg.send(player, config.getPrefix(), config.getReclaimFailedMessage()));
                return;
            }
            final int movedFinal = moved;
            final int pendingFinal = pending;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (guiManager.getOpenMailbox(owner) != holder) {
                    return;
                }
                for (int i = 0; i < items; i++) {
                    inventory.setItem(i, null);
                }
                for (Map.Entry<Integer, ItemStack> entry : reloaded.entrySet()) {
                    int slot = entry.getKey();
                    if (slot >= 0 && slot < items) {
                        inventory.setItem(slot, entry.getValue());
                    }
                }
                holder.setPendingCount(pendingFinal);
                guiManager.renderMailbox(holder);
                player.updateInventory();
                if (movedFinal > 0) {
                    Msg.send(player, config.getPrefix(), config.getReclaimSuccessMessage()
                            .replace("%amount%", String.valueOf(movedFinal))
                            + (pendingFinal > 0 ? config.getReclaimPendingMessage()
                                    .replace("%pending%", String.valueOf(pendingFinal)) : ""));
                } else {
                    Msg.send(player, config.getPrefix(), config.getReclaimNoneMessage());
                }
            });
        });
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
            inventory.setItem(GuiManager.slotFee(inventory.getSize()), null);
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
        Set<Integer> clearSlots = new TreeSet<>(holder.getOriginalSlots());
        clearSlots.removeAll(snapshot.keySet());
        holder.setOriginalSlots(snapshot.keySet());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                storage.saveMailboxItems(owner, snapshot, clearSlots);
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
                Msg.send(player, config.getPrefix(), config.getSaveConflictMessage());
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
            Msg.send(player, config.getPrefix(), config.getSaveFailedMessage());
        }
    }

    private boolean isControlSlot(int raw, StationHolder holder) {
        if (holder.getView() == StationHolder.View.CHOOSER) {
            return raw == GuiManager.CHOOSER_SEND || raw == GuiManager.CHOOSER_RECEIVE || raw == 22;
        }
        int gui = holder.getInventory().getSize();
        return raw == GuiManager.slotClose(gui) || raw == GuiManager.slotSwitch(gui)
                || raw == GuiManager.slotRoute(gui) || raw == GuiManager.slotAction(gui) || raw == GuiManager.slotInfo(gui);
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
