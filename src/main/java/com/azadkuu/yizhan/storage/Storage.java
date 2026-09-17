package com.azadkuu.yizhan.storage;

import com.azadkuu.yizhan.model.MailboxBlock;
import com.azadkuu.yizhan.model.Notification;
import com.azadkuu.yizhan.model.Route;
import com.azadkuu.yizhan.model.Shipment;
import com.azadkuu.yizhan.model.Station;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
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

    long createShipment(Route route, UUID owner, Instant departAt, Instant arriveAt, Map<Integer, ItemStack> items);

    List<Shipment> listDueShipments(int limit);

    List<Shipment> listInTransitTo(String toStation);

    List<Shipment> listInTransitFrom(String fromStation);

    void loadShipmentItems(Shipment shipment);

    boolean deliverShipment(long shipmentId);

    boolean cancelShipment(long shipmentId);

    Map<Integer, ItemStack> loadStationItems(String stationId);

    int saveStationItems(String stationId, Map<Integer, ItemStack> items, int expectedVersion);

    void pushNotification(UUID player, String message);

    List<Notification> claimNotifications(UUID player);

    Map<Integer, ItemStack> loadMailboxItems(UUID player);

    void saveMailboxItems(UUID player, Map<Integer, ItemStack> items);

    List<ItemStack> depositToMailbox(UUID player, List<ItemStack> items, int size);

    boolean markDailyClaim(UUID player, String date);

    MailboxBlock getMailboxBlock(String serverId);

    void saveMailboxBlock(MailboxBlock block);

    void deleteMailboxBlock(String serverId);
}
