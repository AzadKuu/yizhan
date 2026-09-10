package com.azadkuu.yizhan.model;

import java.util.UUID;

public class Notification {

    private final long id;
    private final UUID player;
    private final String message;
    private final long createdAt;

    public Notification(long id, UUID player, String message, long createdAt) {
        this.id = id;
        this.player = player;
        this.message = message;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public UUID getPlayer() {
        return player;
    }

    public String getMessage() {
        return message;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
