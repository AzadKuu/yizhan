package com.azadkuu.yizhan.util;

public final class TimeUtil {

    private TimeUtil() {
    }

    public static String formatSeconds(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("时");
        }
        if (minutes > 0) {
            sb.append(minutes).append("分");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append("秒");
        }
        return sb.toString();
    }
}
