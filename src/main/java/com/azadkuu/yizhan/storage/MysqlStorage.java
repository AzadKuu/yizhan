package com.azadkuu.yizhan.storage;

import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.model.DeliveryResult;
import com.azadkuu.yizhan.model.MailboxBlock;
import com.azadkuu.yizhan.model.Notification;
import com.azadkuu.yizhan.model.Route;
import com.azadkuu.yizhan.model.Shipment;
import com.azadkuu.yizhan.model.ShipmentStatus;
import com.azadkuu.yizhan.model.Station;
import com.azadkuu.yizhan.model.StationMode;
import com.azadkuu.yizhan.util.ItemSerializer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public class MysqlStorage implements Storage {

    private final PluginConfig config;
    private HikariDataSource dataSource;
    private String prefix = "yz_";

    public MysqlStorage(PluginConfig config) {
        this.config = config;
    }

    @Override
    public void init() throws Exception {
        this.prefix = config.getTablePrefix();
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:mysql://" + config.getDbHost() + ":" + config.getDbPort() + "/" + config.getDbName()
                + "?useSSL=" + config.isDbUseSsl()
                + "&allowPublicKeyRetrieval=true&characterEncoding=utf8&useUnicode=true&serverTimezone=UTC");
        hc.setUsername(config.getDbUser());
        hc.setPassword(config.getDbPassword());
        hc.setMaximumPoolSize(config.getDbPoolSize());
        hc.setPoolName("YizhanPool");
        hc.setConnectionTimeout(config.getDbConnectionTimeoutMs());
        hc.setInitializationFailTimeout(-1L);
        this.dataSource = new HikariDataSource(hc);
        createTables();
        try (Connection c = dataSource.getConnection()) {
            if (!c.isValid(5)) {
                throw new SQLException("database connection is not valid");
            }
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private Connection conn() throws SQLException {
        return dataSource.getConnection();
    }

    private void createTables() throws SQLException {
        String[] ddl = new String[]{
                "CREATE TABLE IF NOT EXISTS " + prefix + "stations ("
                        + "id VARCHAR(64) NOT NULL,"
                        + "server_id VARCHAR(64) NOT NULL,"
                        + "world VARCHAR(64) NOT NULL,"
                        + "x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,"
                        + "mode VARCHAR(16) NOT NULL,"
                        + "title VARCHAR(64) NOT NULL,"
                        + "size INT NOT NULL,"
                        + "buffer_seconds INT NULL,"
                        + "version INT NOT NULL DEFAULT 0,"
                        + "created_at BIGINT NOT NULL,"
                        + "updated_at BIGINT NOT NULL,"
                        + "PRIMARY KEY (id),"
                        + "UNIQUE KEY uk_yz_location (server_id, world, x, y, z)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",

                "CREATE TABLE IF NOT EXISTS " + prefix + "routes ("
                        + "id INT NOT NULL AUTO_INCREMENT,"
                        + "from_station VARCHAR(64) NOT NULL,"
                        + "to_station VARCHAR(64) NOT NULL,"
                        + "buffer_seconds INT NULL,"
                        + "enabled TINYINT NOT NULL DEFAULT 1,"
                        + "fee INT NOT NULL DEFAULT 0,"
                        + "PRIMARY KEY (id),"
                        + "UNIQUE KEY uk_yz_route (from_station, to_station)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",

                "CREATE TABLE IF NOT EXISTS " + prefix + "shipments ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT,"
                        + "route_id INT NULL,"
                        + "from_station VARCHAR(64) NOT NULL,"
                        + "to_station VARCHAR(64) NOT NULL,"
                        + "status VARCHAR(16) NOT NULL,"
                        + "depart_at BIGINT NOT NULL,"
                        + "arrive_at BIGINT NOT NULL,"
                        + "owner_uuid VARCHAR(36) NULL,"
                        + "version INT NOT NULL DEFAULT 0,"
                        + "PRIMARY KEY (id),"
                        + "KEY idx_yz_due (status, arrive_at)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",

                "CREATE TABLE IF NOT EXISTS " + prefix + "shipment_items ("
                        + "shipment_id BIGINT NOT NULL,"
                        + "slot INT NOT NULL,"
                        + "item_data LONGBLOB NOT NULL,"
                        + "PRIMARY KEY (shipment_id, slot)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",

                "CREATE TABLE IF NOT EXISTS " + prefix + "station_items ("
                        + "station_id VARCHAR(64) NOT NULL,"
                        + "slot INT NOT NULL,"
                        + "item_data LONGBLOB NOT NULL,"
                        + "PRIMARY KEY (station_id, slot)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",

                "CREATE TABLE IF NOT EXISTS " + prefix + "notifications ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT,"
                        + "player_uuid VARCHAR(36) NOT NULL,"
                        + "message TEXT NOT NULL,"
                        + "created_at BIGINT NOT NULL,"
                        + "delivered TINYINT NOT NULL DEFAULT 0,"
                        + "PRIMARY KEY (id),"
                        + "KEY idx_yz_notify (player_uuid, delivered)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",

                "CREATE TABLE IF NOT EXISTS " + prefix + "mailbox_items ("
                        + "player_uuid VARCHAR(36) NOT NULL,"
                        + "slot INT NOT NULL,"
                        + "item_data LONGBLOB NOT NULL,"
                        + "PRIMARY KEY (player_uuid, slot)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",

                "CREATE TABLE IF NOT EXISTS " + prefix + "daily_claims ("
                        + "player_uuid VARCHAR(36) NOT NULL,"
                        + "claim_date VARCHAR(10) NOT NULL,"
                        + "claimed_at BIGINT NOT NULL,"
                        + "PRIMARY KEY (player_uuid)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",

                "CREATE TABLE IF NOT EXISTS " + prefix + "mailbox_blocks ("
                        + "server_id VARCHAR(64) NOT NULL,"
                        + "world VARCHAR(64) NOT NULL,"
                        + "x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,"
                        + "created_at BIGINT NOT NULL,"
                        + "PRIMARY KEY (server_id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",

                "CREATE TABLE IF NOT EXISTS " + prefix + "daily_rewards ("
                        + "slot INT NOT NULL,"
                        + "item_data LONGBLOB NOT NULL,"
                        + "PRIMARY KEY (slot)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",

                "CREATE TABLE IF NOT EXISTS " + prefix + "mailbox_overflow ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT,"
                        + "player_uuid VARCHAR(36) NOT NULL,"
                        + "item_data LONGBLOB NOT NULL,"
                        + "created_at BIGINT NOT NULL,"
                        + "PRIMARY KEY (id),"
                        + "KEY idx_yz_overflow (player_uuid, id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",

                "CREATE TABLE IF NOT EXISTS " + prefix + "discarded_items ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT,"
                        + "shipment_id BIGINT NOT NULL,"
                        + "owner_uuid VARCHAR(36) NULL,"
                        + "from_station VARCHAR(64) NOT NULL,"
                        + "to_station VARCHAR(64) NOT NULL,"
                        + "item_data LONGBLOB NOT NULL,"
                        + "discarded_at BIGINT NOT NULL,"
                        + "PRIMARY KEY (id),"
                        + "KEY idx_yz_discard (owner_uuid, id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        };
        try (Connection c = conn(); Statement st = c.createStatement()) {
            for (String sql : ddl) {
                st.executeUpdate(sql);
            }
            ensureColumn(c, prefix + "routes", "fee", "fee INT NOT NULL DEFAULT 0");
            ensureColumn(c, prefix + "shipments", "full_since", "full_since BIGINT NULL");
        }
    }

    private void ensureColumn(Connection c, String table, String column, String ddl) throws SQLException {
        String check = "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?";
        try (PreparedStatement ps = c.prepareStatement(check)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + ddl);
        }
    }

    private Station mapStation(ResultSet rs) throws SQLException {
        Station station = new Station(rs.getString("id"));
        station.setServerId(rs.getString("server_id"));
        station.setWorld(rs.getString("world"));
        station.setX(rs.getInt("x"));
        station.setY(rs.getInt("y"));
        station.setZ(rs.getInt("z"));
        StationMode mode = StationMode.parse(rs.getString("mode"));
        station.setMode(mode == null ? StationMode.SEND : mode);
        station.setTitle(rs.getString("title"));
        station.setSize(rs.getInt("size"));
        int buffer = rs.getInt("buffer_seconds");
        station.setBufferSeconds(rs.wasNull() ? null : buffer);
        station.setVersion(rs.getInt("version"));
        return station;
    }

    private Shipment mapShipment(ResultSet rs) throws SQLException {
        Shipment shipment = new Shipment();
        shipment.setId(rs.getLong("id"));
        shipment.setRouteId(rs.getInt("route_id"));
        shipment.setFromStation(rs.getString("from_station"));
        shipment.setToStation(rs.getString("to_station"));
        try {
            shipment.setStatus(ShipmentStatus.valueOf(rs.getString("status")));
        } catch (IllegalArgumentException ex) {
            shipment.setStatus(ShipmentStatus.IN_TRANSIT);
        }
        shipment.setDepartAt(Instant.ofEpochMilli(rs.getLong("depart_at")));
        shipment.setArriveAt(Instant.ofEpochMilli(rs.getLong("arrive_at")));
        String owner = rs.getString("owner_uuid");
        shipment.setOwner(owner == null ? null : UUID.fromString(owner));
        shipment.setVersion(rs.getInt("version"));
        long fullSince = rs.getLong("full_since");
        if (!rs.wasNull()) {
            shipment.setFullSince(Instant.ofEpochMilli(fullSince));
        }
        return shipment;
    }

    private Route mapRoute(ResultSet rs) throws SQLException {
        Route route = new Route();
        route.setId(rs.getInt("id"));
        route.setFromStation(rs.getString("from_station"));
        route.setToStation(rs.getString("to_station"));
        int buffer = rs.getInt("buffer_seconds");
        route.setBufferSeconds(rs.wasNull() ? null : buffer);
        route.setEnabled(rs.getBoolean("enabled"));
        route.setFee(rs.getInt("fee"));
        return route;
    }

    private Station getStation(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM " + prefix + "stations WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapStation(rs);
                }
            }
        }
        return null;
    }

    @Override
    public Station getStation(String id) {
        try (Connection c = conn()) {
            return getStation(c, id);
        } catch (SQLException ex) {
            throw new StorageException("getStation failed", ex);
        }
    }

    @Override
    public Station getStationAt(String serverId, String world, int x, int y, int z) {
        String sql = "SELECT * FROM " + prefix + "stations WHERE server_id=? AND world=? AND x=? AND y=? AND z=?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.setString(2, world);
            ps.setInt(3, x);
            ps.setInt(4, y);
            ps.setInt(5, z);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapStation(rs);
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("getStationAt failed", ex);
        }
        return null;
    }

    @Override
    public List<Station> listStations() {
        List<Station> out = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM " + prefix + "stations ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(mapStation(rs));
            }
        } catch (SQLException ex) {
            throw new StorageException("listStations failed", ex);
        }
        return out;
    }

    @Override
    public void saveStation(Station station) {
        String sql = "INSERT INTO " + prefix + "stations "
                + "(id, server_id, world, x, y, z, mode, title, size, buffer_seconds, version, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,0,?,?) "
                + "ON DUPLICATE KEY UPDATE server_id=VALUES(server_id), world=VALUES(world), x=VALUES(x), y=VALUES(y), "
                + "z=VALUES(z), mode=VALUES(mode), title=VALUES(title), size=VALUES(size), "
                + "buffer_seconds=VALUES(buffer_seconds), updated_at=VALUES(updated_at)";
        long now = System.currentTimeMillis();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, station.getId());
            ps.setString(2, station.getServerId());
            ps.setString(3, station.getWorld());
            ps.setInt(4, station.getX());
            ps.setInt(5, station.getY());
            ps.setInt(6, station.getZ());
            ps.setString(7, station.getMode().name());
            ps.setString(8, station.getTitle());
            ps.setInt(9, station.getSize());
            if (station.getBufferSeconds() == null) {
                ps.setNull(10, Types.INTEGER);
            } else {
                ps.setInt(10, station.getBufferSeconds());
            }
            ps.setLong(11, now);
            ps.setLong(12, now);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("saveStation failed", ex);
        }
    }

    @Override
    public void deleteStation(String id) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + prefix + "stations WHERE id=?")) {
                ps.setString(1, id);
                ps.executeUpdate();
                try (PreparedStatement ps2 = c.prepareStatement("DELETE FROM " + prefix + "station_items WHERE station_id=?")) {
                    ps2.setString(1, id);
                    ps2.executeUpdate();
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                try {
                    c.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("deleteStation failed", ex);
        }
    }

    @Override
    public Route getRoute(String from, String to) {
        String sql = "SELECT * FROM " + prefix + "routes WHERE from_station=? AND to_station=?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from);
            ps.setString(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRoute(rs);
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("getRoute failed", ex);
        }
        return null;
    }

    @Override
    public Route getRouteById(int id) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM " + prefix + "routes WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRoute(rs);
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("getRouteById failed", ex);
        }
        return null;
    }

    @Override
    public List<Route> listRoutesFrom(String fromStation) {
        List<Route> out = new ArrayList<>();
        String sql = "SELECT * FROM " + prefix + "routes WHERE from_station=? AND enabled=1 ORDER BY id";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, fromStation);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRoute(rs));
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("listRoutesFrom failed", ex);
        }
        return out;
    }

    @Override
    public void saveRoute(Route route) {
        String sql = "INSERT INTO " + prefix + "routes (from_station, to_station, buffer_seconds, enabled, fee) VALUES (?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE buffer_seconds=VALUES(buffer_seconds), enabled=VALUES(enabled), fee=VALUES(fee)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, route.getFromStation());
            ps.setString(2, route.getToStation());
            if (route.getBufferSeconds() == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, route.getBufferSeconds());
            }
            ps.setBoolean(4, route.isEnabled());
            ps.setInt(5, route.getFee());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("saveRoute failed", ex);
        }
    }

    @Override
    public void deleteRoute(String from, String to) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("DELETE FROM " + prefix + "routes WHERE from_station=? AND to_station=?")) {
            ps.setString(1, from);
            ps.setString(2, to);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("deleteRoute failed", ex);
        }
    }

    @Override
    public long createShipment(Route route, UUID owner, Instant departAt, Instant arriveAt, Map<Integer, ItemStack> items) {
        String insertShipment = "INSERT INTO " + prefix + "shipments "
                + "(route_id, from_station, to_station, status, depart_at, arrive_at, owner_uuid, version) "
                + "VALUES (?,?,?,?,?,?,?,0)";
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                long shipmentId;
                try (PreparedStatement ps = c.prepareStatement(insertShipment, Statement.RETURN_GENERATED_KEYS)) {
                    if (route.getId() > 0) {
                        ps.setInt(1, route.getId());
                    } else {
                        ps.setNull(1, Types.INTEGER);
                    }
                    ps.setString(2, route.getFromStation());
                    ps.setString(3, route.getToStation());
                    ps.setString(4, ShipmentStatus.IN_TRANSIT.name());
                    ps.setLong(5, departAt.toEpochMilli());
                    ps.setLong(6, arriveAt.toEpochMilli());
                    if (owner == null) {
                        ps.setNull(7, Types.VARCHAR);
                    } else {
                        ps.setString(7, owner.toString());
                    }
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("no generated shipment id");
                        }
                        shipmentId = keys.getLong(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + prefix + "shipment_items (shipment_id, slot, item_data) VALUES (?,?,?)")) {
                    for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                        ItemStack item = entry.getValue();
                        if (item == null || item.getType().isAir()) {
                            continue;
                        }
                        ps.setLong(1, shipmentId);
                        ps.setInt(2, entry.getKey());
                        ps.setBytes(3, ItemSerializer.serialize(item));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
                return shipmentId;
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                try {
                    c.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("createShipment failed", ex);
        }
    }

    @Override
    public List<Shipment> listDueShipments(int limit) {
        List<Shipment> out = new ArrayList<>();
        String sql = "SELECT * FROM " + prefix + "shipments WHERE status='IN_TRANSIT' AND arrive_at<=? ORDER BY arrive_at ASC LIMIT ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapShipment(rs));
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("listDueShipments failed", ex);
        }
        return out;
    }

    @Override
    public List<Shipment> listInTransitTo(String toStation) {
        return listInTransit("to_station", toStation);
    }

    @Override
    public List<Shipment> listInTransitFrom(String fromStation) {
        return listInTransit("from_station", fromStation);
    }

    private List<Shipment> listInTransit(String column, String value) {
        List<Shipment> out = new ArrayList<>();
        String sql = "SELECT * FROM " + prefix + "shipments WHERE status='IN_TRANSIT' AND " + column + "=? ORDER BY arrive_at ASC";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapShipment(rs));
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("listInTransit failed", ex);
        }
        return out;
    }

    @Override
    public void loadShipmentItems(Shipment shipment) {
        Map<Integer, ItemStack> items = new TreeMap<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT slot, item_data FROM " + prefix + "shipment_items WHERE shipment_id=? ORDER BY slot")) {
            ps.setLong(1, shipment.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack item = ItemSerializer.deserialize(rs.getBytes(2));
                    if (item != null && !item.getType().isAir()) {
                        items.put(rs.getInt(1), item);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("loadShipmentItems failed", ex);
        }
        shipment.setItems(items);
    }

    private Map<Integer, byte[]> loadShipmentItemBytes(Connection c, long shipmentId) throws SQLException {
        Map<Integer, byte[]> items = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT slot, item_data FROM " + prefix + "shipment_items WHERE shipment_id=? ORDER BY slot")) {
            ps.setLong(1, shipmentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.put(rs.getInt(1), rs.getBytes(2));
                }
            }
        }
        return items;
    }

    private Set<Integer> loadOccupiedSlots(Connection c, String stationId) throws SQLException {
        Set<Integer> occupied = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT slot FROM " + prefix + "station_items WHERE station_id=?")) {
            ps.setString(1, stationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    occupied.add(rs.getInt(1));
                }
            }
        }
        return occupied;
    }

    private List<Integer> freeSlots(int size, Set<Integer> occupied) {
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (!occupied.contains(i)) {
                free.add(i);
            }
        }
        return free;
    }

    private void insertStationItems(Connection c, String stationId, List<Integer> slots, Map<Integer, byte[]> items) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + prefix + "station_items (station_id, slot, item_data) VALUES (?,?,?) "
                + "ON DUPLICATE KEY UPDATE item_data=VALUES(item_data)")) {
            int index = 0;
            for (byte[] data : items.values()) {
                ps.setString(1, stationId);
                ps.setInt(2, slots.get(index++));
                ps.setBytes(3, data);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void touchStationVersion(Connection c, String stationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE " + prefix + "stations SET version=version+1, updated_at=? WHERE id=?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, stationId);
            ps.executeUpdate();
        }
    }

    @Override
    public DeliveryResult deliverShipment(long shipmentId) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                String toStation;
                try (PreparedStatement ps = c.prepareStatement("SELECT to_station FROM " + prefix + "shipments WHERE id=? AND status='IN_TRANSIT'")) {
                    ps.setLong(1, shipmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return DeliveryResult.SKIPPED;
                        }
                        toStation = rs.getString(1);
                    }
                }
                Station station = getStation(c, toStation);
                if (station == null) {
                    c.rollback();
                    return DeliveryResult.SKIPPED;
                }
                Map<Integer, byte[]> items = loadShipmentItemBytes(c, shipmentId);
                if (items.isEmpty()) {
                    c.rollback();
                    return DeliveryResult.SKIPPED;
                }
                List<Integer> free = freeSlots(station.getSize(), loadOccupiedSlots(c, toStation));
                if (free.size() < items.size()) {
                    c.rollback();
                    return DeliveryResult.WAITING_FULL;
                }
                insertStationItems(c, toStation, free, items);
                int affected;
                try (PreparedStatement ps = c.prepareStatement("UPDATE " + prefix
                        + "shipments SET status='DELIVERED', full_since=NULL, version=version+1 WHERE id=? AND status='IN_TRANSIT'")) {
                    ps.setLong(1, shipmentId);
                    affected = ps.executeUpdate();
                }
                if (affected == 0) {
                    c.rollback();
                    return DeliveryResult.SKIPPED;
                }
                touchStationVersion(c, toStation);
                c.commit();
                return DeliveryResult.DELIVERED;
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                try {
                    c.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("deliverShipment failed", ex);
        }
    }

    @Override
    public boolean markShipmentFull(long shipmentId) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("UPDATE " + prefix
                     + "shipments SET full_since=? WHERE id=? AND status='IN_TRANSIT' AND full_since IS NULL")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setLong(2, shipmentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new StorageException("markShipmentFull failed", ex);
        }
    }

    @Override
    public List<Shipment> listShipmentsStuckFull(int limit, long fullBeforeMillis) {
        List<Shipment> out = new ArrayList<>();
        String sql = "SELECT * FROM " + prefix + "shipments WHERE status='IN_TRANSIT' "
                + "AND full_since IS NOT NULL AND full_since<=? ORDER BY full_since ASC LIMIT ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, fullBeforeMillis);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapShipment(rs));
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("listShipmentsStuckFull failed", ex);
        }
        return out;
    }

    @Override
    public boolean discardShipment(long shipmentId) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                long sid;
                UUID owner;
                String fromStation;
                String toStation;
                try (PreparedStatement ps = c.prepareStatement("SELECT id, owner_uuid, from_station, to_station FROM " + prefix
                        + "shipments WHERE id=? AND status='IN_TRANSIT' FOR UPDATE")) {
                    ps.setLong(1, shipmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return false;
                        }
                        sid = rs.getLong(1);
                        String raw = rs.getString(2);
                        owner = raw == null ? null : UUID.fromString(raw);
                        fromStation = rs.getString(3);
                        toStation = rs.getString(4);
                    }
                }
                Map<Integer, byte[]> items = loadShipmentItemBytes(c, shipmentId);
                if (!items.isEmpty()) {
                    long now = System.currentTimeMillis();
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + prefix
                            + "discarded_items (shipment_id, owner_uuid, from_station, to_station, item_data, discarded_at) VALUES (?,?,?,?,?,?)")) {
                        for (byte[] data : items.values()) {
                            ps.setLong(1, sid);
                            ps.setString(2, owner == null ? null : owner.toString());
                            ps.setString(3, fromStation);
                            ps.setString(4, toStation);
                            ps.setBytes(5, data);
                            ps.setLong(6, now);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE " + prefix
                        + "shipments SET status='DISCARDED', full_since=NULL, version=version+1 WHERE id=? AND status='IN_TRANSIT'")) {
                    ps.setLong(1, shipmentId);
                    ps.executeUpdate();
                }
                deleteShipmentItems(c, shipmentId);
                c.commit();
                return true;
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                try {
                    c.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("discardShipment failed", ex);
        }
    }

    @Override
    public Map<Long, ItemStack> listDiscardedItems() {
        Map<Long, ItemStack> out = new LinkedHashMap<>();
        String sql = "SELECT id, item_data FROM " + prefix + "discarded_items ORDER BY id ASC";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    byte[] data = rs.getBytes(2);
                    ItemStack item = ItemSerializer.deserialize(data);
                    if (item != null && !item.getType().isAir()) {
                        out.put(id, item);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("listDiscardedItems failed", ex);
        }
        return out;
    }

    @Override
    public boolean claimDiscardedItem(long id) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("DELETE FROM " + prefix + "discarded_items WHERE id=?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new StorageException("claimDiscardedItem failed", ex);
        }
    }

    @Override
    public int countDiscardedItems() {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + prefix + "discarded_items")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("countDiscardedItems failed", ex);
        }
        return 0;
    }

    private void deleteShipmentItems(Connection c, long shipmentId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + prefix + "shipment_items WHERE shipment_id=?")) {
            ps.setLong(1, shipmentId);
            ps.executeUpdate();
        }
    }

    @Override
    public int countStationStacks(String stationId) {
        return countStacks("SELECT COUNT(*) FROM " + prefix + "station_items WHERE station_id=?", stationId);
    }

    @Override
    public int countInTransitStacks(String toStation) {
        String sql = "SELECT COUNT(*) FROM " + prefix + "shipment_items i JOIN " + prefix
                + "shipments s ON s.id=i.shipment_id WHERE s.status='IN_TRANSIT' AND s.to_station=?";
        return countStacks(sql, toStation);
    }

    private int countStacks(String sql, String value) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new StorageException("countStacks failed", ex);
        }
    }

    @Override
    public boolean cancelShipment(long shipmentId) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                String fromStation;
                try (PreparedStatement ps = c.prepareStatement("SELECT from_station FROM " + prefix + "shipments WHERE id=? AND status='IN_TRANSIT'")) {
                    ps.setLong(1, shipmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return false;
                        }
                        fromStation = rs.getString(1);
                    }
                }
                Station station = getStation(c, fromStation);
                if (station == null) {
                    c.rollback();
                    return false;
                }
                Map<Integer, byte[]> items = loadShipmentItemBytes(c, shipmentId);
                if (items.isEmpty()) {
                    c.rollback();
                    return false;
                }
                List<Integer> free = freeSlots(station.getSize(), loadOccupiedSlots(c, fromStation));
                if (free.size() < items.size()) {
                    c.rollback();
                    return false;
                }
                insertStationItems(c, fromStation, free, items);
                int affected;
                try (PreparedStatement ps = c.prepareStatement("UPDATE " + prefix + "shipments SET status='CANCELLED', version=version+1 WHERE id=? AND status='IN_TRANSIT'")) {
                    ps.setLong(1, shipmentId);
                    affected = ps.executeUpdate();
                }
                if (affected == 0) {
                    c.rollback();
                    return false;
                }
                touchStationVersion(c, fromStation);
                c.commit();
                return true;
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                try {
                    c.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("cancelShipment failed", ex);
        }
    }

    @Override
    public Map<Integer, ItemStack> loadStationItems(String stationId) {
        Map<Integer, ItemStack> out = new TreeMap<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT slot, item_data FROM " + prefix + "station_items WHERE station_id=? ORDER BY slot")) {
            ps.setString(1, stationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack item = ItemSerializer.deserialize(rs.getBytes(2));
                    if (item != null && !item.getType().isAir()) {
                        out.put(rs.getInt(1), item);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("loadStationItems failed", ex);
        }
        return out;
    }

    @Override
    public int saveStationItems(String stationId, Map<Integer, ItemStack> items, int expectedVersion) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                int newVersion = expectedVersion + 1;
                if (expectedVersion < 0) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE " + prefix + "stations SET version=version+1, updated_at=? WHERE id=?")) {
                        ps.setLong(1, System.currentTimeMillis());
                        ps.setString(2, stationId);
                        ps.executeUpdate();
                    }
                } else {
                    int affected;
                    try (PreparedStatement ps = c.prepareStatement("UPDATE " + prefix + "stations SET version=version+1, updated_at=? WHERE id=? AND version=?")) {
                        ps.setLong(1, System.currentTimeMillis());
                        ps.setString(2, stationId);
                        ps.setInt(3, expectedVersion);
                        affected = ps.executeUpdate();
                    }
                    if (affected == 0) {
                        c.rollback();
                        return -1;
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + prefix + "station_items WHERE station_id=?")) {
                    ps.setString(1, stationId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + prefix + "station_items (station_id, slot, item_data) VALUES (?,?,?)")) {
                    for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                        ItemStack item = entry.getValue();
                        if (item == null || item.getType().isAir()) {
                            continue;
                        }
                        ps.setString(1, stationId);
                        ps.setInt(2, entry.getKey());
                        ps.setBytes(3, ItemSerializer.serialize(item));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
                return newVersion;
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                try {
                    c.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("saveStationItems failed", ex);
        }
    }

    @Override
    public void pushNotification(UUID player, String message) {
        String sql = "INSERT INTO " + prefix + "notifications (player_uuid, message, created_at, delivered) VALUES (?,?,?,0)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, message);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("pushNotification failed", ex);
        }
    }

    @Override
    public List<Notification> claimNotifications(UUID player) {
        List<Notification> out = new ArrayList<>();
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                List<Long> ids = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT id, message, created_at FROM " + prefix
                        + "notifications WHERE player_uuid=? AND delivered=0 ORDER BY id LIMIT 200 FOR UPDATE")) {
                    ps.setString(1, player.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            out.add(new Notification(rs.getLong(1), player, rs.getString(2), rs.getLong(3)));
                            ids.add(rs.getLong(1));
                        }
                    }
                }
                if (!ids.isEmpty()) {
                    StringBuilder sql = new StringBuilder("UPDATE " + prefix + "notifications SET delivered=1 WHERE id IN (");
                    for (int i = 0; i < ids.size(); i++) {
                        if (i > 0) {
                            sql.append(',');
                        }
                        sql.append('?');
                    }
                    sql.append(')');
                    try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                        for (int i = 0; i < ids.size(); i++) {
                            ps.setLong(i + 1, ids.get(i));
                        }
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return out;
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                try {
                    c.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("claimNotifications failed", ex);
        }
    }

    @Override
    public Map<Integer, ItemStack> loadMailboxItems(UUID player) {
        Map<Integer, ItemStack> out = new TreeMap<>();
        String sql = "SELECT slot, item_data FROM " + prefix + "mailbox_items WHERE player_uuid=? ORDER BY slot";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack item = ItemSerializer.deserialize(rs.getBytes(2));
                    if (item != null && !item.getType().isAir()) {
                        out.put(rs.getInt(1), item);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("loadMailboxItems failed", ex);
        }
        return out;
    }

    @Override
    public void saveMailboxItems(UUID player, Map<Integer, ItemStack> items, Collection<Integer> clearSlots) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                if (clearSlots != null && !clearSlots.isEmpty()) {
                    StringBuilder sql = new StringBuilder("DELETE FROM " + prefix
                            + "mailbox_items WHERE player_uuid=? AND slot IN (");
                    int index = 0;
                    for (Integer ignored : clearSlots) {
                        sql.append(index++ == 0 ? "?" : ",?");
                    }
                    sql.append(")");
                    try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                        ps.setString(1, player.toString());
                        int i = 2;
                        for (Integer slot : clearSlots) {
                            ps.setInt(i++, slot);
                        }
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + prefix
                        + "mailbox_items (player_uuid, slot, item_data) VALUES (?,?,?) "
                        + "ON DUPLICATE KEY UPDATE item_data=VALUES(item_data)")) {
                    for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                        ItemStack item = entry.getValue();
                        if (item == null || item.getType().isAir()) {
                            continue;
                        }
                        ps.setString(1, player.toString());
                        ps.setInt(2, entry.getKey());
                        ps.setBytes(3, ItemSerializer.serialize(item));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                try {
                    c.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("saveMailboxItems failed", ex);
        }
    }

    @Override
    public int depositToMailbox(UUID player, List<ItemStack> items, int size) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                int stashed = depositToMailboxInternal(c, player, items, size);
                c.commit();
                return stashed;
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                try {
                    c.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("depositToMailbox failed", ex);
        }
    }

    private int depositToMailboxInternal(Connection c, UUID player, List<ItemStack> items, int size) throws SQLException {
        Set<Integer> occupied = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT slot FROM " + prefix
                + "mailbox_items WHERE player_uuid=? FOR UPDATE")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    occupied.add(rs.getInt(1));
                }
            }
        }
        List<Integer> free = freeSlots(size, occupied);
        List<ItemStack> leftover = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + prefix
                + "mailbox_items (player_uuid, slot, item_data) VALUES (?,?,?) "
                + "ON DUPLICATE KEY UPDATE item_data=VALUES(item_data)")) {
            int index = 0;
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                if (index >= free.size()) {
                    leftover.add(item);
                    continue;
                }
                ps.setString(1, player.toString());
                ps.setInt(2, free.get(index++));
                ps.setBytes(3, ItemSerializer.serialize(item));
                ps.addBatch();
            }
            ps.executeBatch();
        }
        if (leftover.isEmpty()) {
            return 0;
        }
        stashOverflowInternal(c, player, leftover);
        return leftover.size();
    }

    private void stashOverflowInternal(Connection c, UUID player, List<ItemStack> items) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + prefix
                + "mailbox_overflow (player_uuid, item_data, created_at) VALUES (?,?,?)")) {
            long now = System.currentTimeMillis();
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                ps.setString(1, player.toString());
                ps.setBytes(2, ItemSerializer.serialize(item));
                ps.setLong(3, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public int countMailboxOverflow(UUID player) {
        return countStacks("SELECT COUNT(*) FROM " + prefix + "mailbox_overflow WHERE player_uuid=?", player.toString());
    }

    @Override
    public int reclaimMailboxOverflow(UUID player, int size) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                Set<Integer> occupied = new HashSet<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT slot FROM " + prefix
                        + "mailbox_items WHERE player_uuid=? FOR UPDATE")) {
                    ps.setString(1, player.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            occupied.add(rs.getInt(1));
                        }
                    }
                }
                List<Integer> free = freeSlots(size, occupied);
                if (free.isEmpty()) {
                    c.rollback();
                    return 0;
                }
                Map<Long, byte[]> pending = new LinkedHashMap<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT id, item_data FROM " + prefix
                        + "mailbox_overflow WHERE player_uuid=? ORDER BY id LIMIT 400")) {
                    ps.setString(1, player.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            pending.put(rs.getLong(1), rs.getBytes(2));
                        }
                    }
                }
                if (pending.isEmpty()) {
                    c.rollback();
                    return 0;
                }
                List<Long> moved = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + prefix
                        + "mailbox_items (player_uuid, slot, item_data) VALUES (?,?,?) "
                        + "ON DUPLICATE KEY UPDATE item_data=VALUES(item_data)")) {
                    int index = 0;
                    for (Map.Entry<Long, byte[]> entry : pending.entrySet()) {
                        if (index >= free.size()) {
                            break;
                        }
                        ps.setString(1, player.toString());
                        ps.setInt(2, free.get(index++));
                        ps.setBytes(3, entry.getValue());
                        ps.addBatch();
                        moved.add(entry.getKey());
                    }
                    ps.executeBatch();
                }
                if (!moved.isEmpty()) {
                    StringBuilder sql = new StringBuilder("DELETE FROM " + prefix + "mailbox_overflow WHERE id IN (");
                    for (int i = 0; i < moved.size(); i++) {
                        sql.append(i == 0 ? "?" : ",?");
                    }
                    sql.append(")");
                    try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                        for (int i = 0; i < moved.size(); i++) {
                            ps.setLong(i + 1, moved.get(i));
                        }
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return moved.size();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                try {
                    c.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("reclaimMailboxOverflow failed", ex);
        }
    }

    @Override
    public boolean markDailyClaim(UUID player, String date) {
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO " + prefix
                    + "daily_claims (player_uuid, claim_date, claimed_at) VALUES (?,?,?)")) {
                ps.setString(1, player.toString());
                ps.setString(2, date);
                ps.setLong(3, System.currentTimeMillis());
                if (ps.executeUpdate() > 0) {
                    return true;
                }
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + prefix
                    + "daily_claims SET claim_date=?, claimed_at=? WHERE player_uuid=? AND claim_date<>?")) {
                ps.setString(1, date);
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, player.toString());
                ps.setString(4, date);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException ex) {
            throw new StorageException("markDailyClaim failed", ex);
        }
    }

    @Override
    public Map<Integer, ItemStack> loadDailyRewardItems() {
        Map<Integer, ItemStack> out = new TreeMap<>();
        String sql = "SELECT slot, item_data FROM " + prefix + "daily_rewards ORDER BY slot";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack item = ItemSerializer.deserialize(rs.getBytes(2));
                    if (item != null && !item.getType().isAir()) {
                        out.put(rs.getInt(1), item);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("loadDailyRewardItems failed", ex);
        }
        return out;
    }

    @Override
    public void saveDailyRewardItems(Map<Integer, ItemStack> items) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + prefix + "daily_rewards")) {
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + prefix
                        + "daily_rewards (slot, item_data) VALUES (?,?)")) {
                    for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                        ItemStack item = entry.getValue();
                        if (item == null || item.getType().isAir()) {
                            continue;
                        }
                        ps.setInt(1, entry.getKey());
                        ps.setBytes(2, ItemSerializer.serialize(item));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                try {
                    c.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("saveDailyRewardItems failed", ex);
        }
    }

    @Override
    public MailboxBlock getMailboxBlock(String serverId) {
        String sql = "SELECT world, x, y, z FROM " + prefix + "mailbox_blocks WHERE server_id=?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new MailboxBlock(serverId, rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4));
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("getMailboxBlock failed", ex);
        }
        return null;
    }

    @Override
    public void saveMailboxBlock(MailboxBlock block) {
        String sql = "INSERT INTO " + prefix + "mailbox_blocks (server_id, world, x, y, z, created_at) "
                + "VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), "
                + "y=VALUES(y), z=VALUES(z)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, block.serverId());
            ps.setString(2, block.world());
            ps.setInt(3, block.x());
            ps.setInt(4, block.y());
            ps.setInt(5, block.z());
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("saveMailboxBlock failed", ex);
        }
    }

    @Override
    public void deleteMailboxBlock(String serverId) {
        String sql = "DELETE FROM " + prefix + "mailbox_blocks WHERE server_id=?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("deleteMailboxBlock failed", ex);
        }
    }
}
