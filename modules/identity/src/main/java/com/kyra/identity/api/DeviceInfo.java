package com.kyra.identity.api;

/**
 * Where a login/refresh came from, for session tracking and new-device alerts.
 * Values are best-effort; never trusted for authorization.
 */
public record DeviceInfo(String ip, String userAgent) {

    public static DeviceInfo of(String ip, String userAgent) {
        return new DeviceInfo(
                ip == null ? "unknown" : ip,
                userAgent == null ? "unknown" : userAgent);
    }
}
