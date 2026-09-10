package com.azadkuu.yizhan.model;

public class Station {

    private final String id;
    private String serverId;
    private String world;
    private int x;
    private int y;
    private int z;
    private StationMode mode;
    private String title;
    private int size;
    private Integer bufferSeconds;
    private int version;

    public Station(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public StationMode getMode() {
        return mode;
    }

    public void setMode(StationMode mode) {
        this.mode = mode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public Integer getBufferSeconds() {
        return bufferSeconds;
    }

    public void setBufferSeconds(Integer bufferSeconds) {
        this.bufferSeconds = bufferSeconds;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String locationKey() {
        return serverId + ":" + world + ":" + x + ":" + y + ":" + z;
    }
}
