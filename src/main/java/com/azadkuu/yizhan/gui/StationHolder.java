package com.azadkuu.yizhan.gui;

import com.azadkuu.yizhan.model.Route;
import com.azadkuu.yizhan.model.Station;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class StationHolder implements InventoryHolder {

    public enum View {
        CHOOSER,
        SEND,
        RECEIVE
    }

    private final Station station;
    private View view;
    private Inventory inventory;
    private final List<Route> routes = new ArrayList<>();
    private int selectedRoute;
    private boolean shipped;
    private boolean dirty;
    private int baseVersion;
    private ItemStack feeItem;

    public StationHolder(Station station, View view) {
        this.station = station;
        this.view = view;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Station getStation() {
        return station;
    }

    public View getView() {
        return view;
    }

    public void setView(View view) {
        this.view = view;
    }

    public List<Route> getRoutes() {
        return routes;
    }

    public void setRoutes(List<Route> routes) {
        this.routes.clear();
        this.routes.addAll(routes);
    }

    public int getSelectedRoute() {
        return selectedRoute;
    }

    public void setSelectedRoute(int selectedRoute) {
        this.selectedRoute = selectedRoute;
    }

    public boolean isShipped() {
        return shipped;
    }

    public void setShipped(boolean shipped) {
        this.shipped = shipped;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public int getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(int baseVersion) {
        this.baseVersion = baseVersion;
    }

    public ItemStack getFeeItem() {
        return feeItem;
    }

    public void setFeeItem(ItemStack feeItem) {
        this.feeItem = feeItem;
    }
}
