package com.azadkuu.yizhan.model;

import java.util.Locale;

public enum StationMode {
    SEND,
    RECEIVE,
    BOTH;

    public static StationMode parse(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean canSend() {
        return this == SEND || this == BOTH;
    }

    public boolean canReceive() {
        return this == RECEIVE || this == BOTH;
    }
}
