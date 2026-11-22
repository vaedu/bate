package com.beat.pay.util;

import java.util.UUID;

public final class IdUtils {
    private IdUtils() {}

    public static String uuid() { return UUID.randomUUID().toString(); }

    public static boolean isUuid(String s) {
        try {
            return s != null && UUID.fromString(s).toString().equalsIgnoreCase(s);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
