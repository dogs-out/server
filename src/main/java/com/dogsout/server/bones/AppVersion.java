package com.dogsout.server.bones;

/**
 * Reads the X-App-Version header ("1.4.0 (30)") so a rule can start with the app
 * version that knows about it, instead of breaking older installs with errors they
 * cannot explain to the user.
 */
public final class AppVersion {

    private AppVersion() {}

    /**
     * True when the header names a version older than major.minor. A missing header
     * counts as older: apps before 1.3 never sent one, and a rule they cannot know
     * about must not break them. (An unreadable one counts as current.)
     */
    public static boolean isOlderThan(String header, int major, int minor) {
        if (header == null || header.isBlank()) return true;
        String[] parts = header.trim().split("[ .]");
        try {
            int ma = Integer.parseInt(parts[0]);
            int mi = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return ma < major || (ma == major && mi < minor);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
