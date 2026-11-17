package com.azadkuu.yizhan.storage;

import com.azadkuu.yizhan.model.DeliveryResult;
import com.azadkuu.yizhan.model.LinkMail;
import com.azadkuu.yizhan.model.MailContact;
import com.azadkuu.yizhan.model.MailboxBlock;
import com.azadkuu.yizhan.model.Notification;
import com.azadkuu.yizhan.model.Route;
import com.azadkuu.yizhan.model.Shipment;
import com.azadkuu.yizhan.model.Station;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface Storage extends AutoCloseable {

    void init() throws Exception;

    @Override
    void close();

    Station getStation(String id);

    Station getStationAt(String serverId, String world, int x, int y, int z);

    List<Station> listStations();

    void saveStation(Station station);

    void deleteStation(String id);

    Route getRoute(String from, String to);

    Route getRouteById(int id);

    List<Route> listRoutesFrom(String fromStation);

    void saveRoute(Route route);

    void deleteRoute(String from, String to);

    /** 与本驿站相关的路由条数（作为起点或终点）。 */
    int countRoutesAt(String stationId);

    /** 删除与本驿站相关的全部路由（作为起点或终点），返回删除条数。 */
    int deleteRoutesAt(String stationId);

    long createShipment(Route route, UUID owner, Instant departAt, Instant arriveAt, Map<Integer, ItemStack> items);

    List<Shipment> listDueShipments(int limit);

    List<Shipment> listInTransitTo(String toStation);

    List<Shipment> listInTransitFrom(String fromStation);

    void loadShipmentItems(Shipment shipment);

    DeliveryResult deliverShipment(long shipmentId);

    boolean markShipmentFull(long shipmentId);

    List<Shipment> listShipmentsStuckFull(int limit, long fullBeforeMillis);

    boolean discardShipment(long shipmentId);

    boolean cancelShipment(long shipmentId);

    int countStationStacks(String stationId);

    int countInTransitStacks(String toStation);

    Map<Integer, ItemStack> loadStationItems(String stationId);

    int saveStationItems(String stationId, Map<Integer, ItemStack> items, int expectedVersion);

    Map<Long, ItemStack> listDiscardedItems();

    boolean claimDiscardedItem(long id);

    int countDiscardedItems();

    void pushNotification(UUID player, String message);

    List<Notification> claimNotifications(UUID player);

    Map<UUID, List<Notification>> claimNotificationsBatch(List<UUID> players);

    Map<Integer, ItemStack> loadMailboxItems(UUID player);

    void saveMailboxItems(UUID player, Map<Integer, ItemStack> items, Collection<Integer> clearSlots);

    int depositToMailbox(UUID player, List<ItemStack> items, int size);

    int countMailboxOverflow(UUID player);

    int reclaimMailboxOverflow(UUID player, int size);

    boolean markDailyClaim(UUID player, String date);

    /** 发奖失败时把当天的领取标记撤回，让玩家还能再领一次。 */
    boolean rollbackDailyClaim(UUID player, String date);

    Map<Integer, ItemStack> loadDailyRewardItems();

    void saveDailyRewardItems(Map<Integer, ItemStack> items);

    MailboxBlock getPlayerMailboxBlock(UUID player, String serverId);

    void savePlayerMailboxBlock(UUID player, String serverId, String world, int x, int y, int z);

    boolean deletePlayerMailboxBlock(UUID player, String serverId);

    MailboxBlock getPublicMailboxBlock(String serverId);

    void savePublicMailboxBlock(String serverId, String world, int x, int y, int z);

    boolean deletePublicMailboxBlock(String serverId);

    /** 本服全部邮箱方块坐标键（world:x:y:z，含个人绑定与公共邮箱），供快速判定用。 */
    java.util.Set<String> listMailboxKeys(String serverId);

    boolean addMailContact(UUID owner, UUID contact, String contactName, String serverId);

    List<MailContact> listMailContacts(UUID owner, String serverId);

    List<UUID> findMailboxOwners(String serverId, String world, int x, int y, int z);

    /** 投递一封链接邮件，返回自增 id。 */
    long addLinkMail(UUID recipient, UUID sender, String senderName, String serverId, String url);

    /** 某人在本服的链接邮件，最近的在前。 */
    List<LinkMail> listLinkMails(UUID recipient, String serverId);

    /** 标记已读（只能标自己的）。 */
    boolean markLinkMailRead(long id, UUID recipient);

    /** 该玩家本服在 since 之后发出去的链接邮件数量，用于限流。 */
    int countLinkMailsSince(UUID sender, String serverId, long since);

    /** 建一张一次性视频卡，返回自增 id（id 就是物品里存的凭据）。 */
    long createVideoCard(UUID owner, String serverId, String url, String title);

    /**
     * 原子地作废一张视频卡：成功返回它的 URL，失败（不存在 / 已被用过）返回 null。
     * 复制出来的同 id 卡片只有第一张能用。
     */
    String takeVideoCard(long id, UUID user);

    /** 该玩家在 since 之后做了多少张视频卡，用于限流。 */
    int countVideoCardsSince(UUID owner, long since);

    void saveItemTemplate(String code, ItemStack item);

    ItemStack getItemTemplate(String code);

    Map<String, ItemStack> listItemTemplates();

    boolean deleteItemTemplate(String code);
}
