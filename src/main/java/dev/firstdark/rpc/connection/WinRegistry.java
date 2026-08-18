package dev.firstdark.rpc.connection;

import java.util.prefs.Preferences;

class WinRegistry {
    static final int HKEY_CURRENT_USER = -2147483647;
    static final Preferences userRoot = null;

    static void createKey(String key) {
        // Windowsレジストリへの書き込みを行わずダミーとして何もしない
    }

    static void writeStringValue(String key, String name, String value) {
        // Windowsレジストリへの書き込みを行わずダミーとして何もしない
    }

    static String readString() {
        return "";
    }
}
