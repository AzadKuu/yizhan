package com.azadkuu.yizhan.storage;

import com.azadkuu.yizhan.config.PluginConfig;
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
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        };
        try (Connection c = conn(); Statement st = c.createStatement()) {
            for (String sql : ddl) {
                st.executeUpdate(sql);
            }
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
        String sql = "INSERT INTO " + prefix + "routes (from_station, to_station, buffer_seconds, enabled) VALUES (?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE buffer_seconds=VALUES(buffer_seconds), enabled=VALUES(enabled)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, route.getFromStation());
            ps.setString(2, route.getToStation());
            if (route.getBufferSeconds() == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, route.getBufferSeconds());
            }
            ps.setBoolean(4, route.isEnabled());
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
    public boolean deliverShipment(long shipmentId) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                String toStation;
                try (PreparedStatement ps = c.prepareStatement("SELECT to_station FROM " + prefix + "shipments WHERE id=? AND status='IN_TRANSIT'")) {
                    ps.setLong(1, shipmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return false;
                        }
                        toStation = rs.getString(1);
                    }
                }
                Station station = getStation(c, toStation);
                if (station == null) {
                    c.rollback();
                    return false;
                }
                Map<Integer, byte[]> items = loadShipmentItemBytes(c, shipmentId);
                if (items.isEmpty()) {
                    c.rollback();
                    return false;
                }
                List<Integer> free = freeSlots(station.getSize(), loadOccupiedSlots(c, toStation));
                if (free.size() < items.size()) {
                    c.rollback();
                    return false;
                }
                insertStationItems(c, toStation, free, items);
                int affected;
                try (PreparedStatement ps = c.prepareStatement("UPDATE " + prefix + "shipments SET status='DELIVERED', version=version+1 WHERE id=? AND status='IN_TRANSIT'")) {
                    ps.setLong(1, shipmentId);
                    affected = ps.executeUpdate();
                }
                if (affected == 0) {
                    c.rollback();
                    return false;
                }
                touchStationVersion(c, toStation);
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
            throw new StorageException("deliverShipment failed", ex);
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
}
