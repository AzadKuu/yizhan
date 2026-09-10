package com.azadkuu.yizhan.service;

import com.azadkuu.yizhan.config.PluginConfig;
import com.azadkuu.yizhan.model.Route;
import com.azadkuu.yizhan.model.Station;
import com.azadkuu.yizhan.storage.Storage;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class TransportService {

    private final PluginConfig config;
    private final Storage storage;

    public TransportService(PluginConfig config, Storage storage) {
        this.config = config;
        this.storage = storage;
    }

    public int resolveBufferSeconds(Route route, Station station) {
        if (route != null && route.getBufferSeconds() != null) {
            return route.getBufferSeconds();
        }
        if (station != null && station.getBufferSeconds() != null) {
            return station.getBufferSeconds();
        }
        return config.getDefaultBufferSeconds();
    }

    public long ship(Station station, Route route, UUID owner, Map<Integer, ItemStack> items) {
        int buffer = resolveBufferSeconds(route, station);
        Instant depart = Instant.now();
        Instant arrive = depart.plusSeconds(buffer);
        return storage.createShipment(route, owner, depart, arrive, items);
    }

    public PluginConfig getConfig() {
        return config;
    }

    public Storage getStorage() {
        return storage;
    }
}
