package helium314.keyboard.latin;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Preferences for Klipy GIF support.
 */
public final class GifPrefs {
    public static final String PREFS_NAME = "heli_keyboard_prefs";
    public static final String KEY_KLIPY_ENABLED = "klipy_enabled";
    public static final String KEY_KLIPY_API_KEY = "klipy_api_key";
    public static final String KEY_TENOR_ENABLED = "tenor_enabled";
    public static final String KEY_TENOR_API_KEY = "tenor_api_key";
    /**
     * Preference key for GIF share size.
     * Values: "tinygif", "mediumgif", "gif" (original).
     */
    public static final String KEY_GIF_SHARE_SIZE = "gif_share_size";
    /**
     * Default GIF share size: original GIF.
     */
    public static final String DEFAULT_GIF_SHARE_SIZE = "auto";

    private GifPrefs() { /* no instance */ }

    public static boolean isKlipyEnabled(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_KLIPY_ENABLED, sp.getBoolean(KEY_TENOR_ENABLED, false));
    }

    public static void setKlipyEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_KLIPY_ENABLED, enabled)
            .putBoolean(KEY_TENOR_ENABLED, enabled)
            .apply();
    }

    public static String getStoredApiKey(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String klipyKey = sp.getString(KEY_KLIPY_API_KEY, "");
        if (klipyKey != null && !klipyKey.trim().isEmpty()) {
            return klipyKey;
        }
        return sp.getString(KEY_TENOR_API_KEY, "");
    }

    public static void setApiKey(Context ctx, String key) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_KLIPY_API_KEY, key)
            .putString(KEY_TENOR_API_KEY, key)
            .apply();
    }

    /**
     * Deprecated: use isKlipyEnabled(Context) instead.
     */
    @Deprecated
    public static boolean isTenorEnabled(Context ctx) {
        return isKlipyEnabled(ctx);
    }

    /**
     * Deprecated: use setKlipyEnabled(Context, boolean) instead.
     */
    @Deprecated
    public static void setTenorEnabled(Context ctx, boolean enabled) {
        setKlipyEnabled(ctx, enabled);
    }
    
    /**
     * Get the preferred GIF share size.
     * @param ctx context
     * @return one of "tinygif", "mediumgif", or "gif"
     */
    public static String getShareSize(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_GIF_SHARE_SIZE, DEFAULT_GIF_SHARE_SIZE);
    }
    
    /**
     * Set the preferred GIF share size.
     * @param ctx context
     * @param size one of "tinygif", "mediumgif", or "gif"
     */
    public static void setShareSize(Context ctx, String size) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_GIF_SHARE_SIZE, size).apply();
    }
}
