package com.azadkuu.yizhan.model;

public class Route {

    private int id;
    private String fromStation;
    private String toStation;
    private Integer bufferSeconds;
    private boolean enabled = true;
    private int fee;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFromStation() {
        return fromStation;
    }

    public void setFromStation(String fromStation) {
        this.fromStation = fromStation;
    }

    public String getToStation() {
        return toStation;
    }

    public void setToStation(String toStation) {
        this.toStation = toStation;
    }

    public Integer getBufferSeconds() {
        return bufferSeconds;
    }

    public void setBufferSeconds(Integer bufferSeconds) {
        this.bufferSeconds = bufferSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getFee() {
        return fee;
    }

    public void setFee(int fee) {
        this.fee = fee;
    }
}
