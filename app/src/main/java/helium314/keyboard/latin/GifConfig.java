package helium314.keyboard.latin;

import android.content.Context;

/**
 * Configuration for Klipy GIF support.
 */
public class GifConfig {
    /**
     * Returns the Klipy API key to use, preferring the user-provided key if present.
     */
    public static String getKlipyApiKey(Context ctx) {
        String userKey = GifPrefs.getStoredApiKey(ctx);
        if (userKey != null && !userKey.trim().isEmpty()) {
            return userKey.trim();
        }
        return BuildConfig.KLIPY_API_KEY;
    }

    /**
     * Deprecated: use getKlipyApiKey(Context) instead.
     */
    @Deprecated
    public static String getTenorApiKey(Context ctx) {
        return getKlipyApiKey(ctx);
    }

    /**
     * Deprecated: use getKlipyApiKey(Context) instead.
     */
    @Deprecated
    public static String getKlipyApiKey() {
        return BuildConfig.KLIPY_API_KEY;
    }

    /**
     * Deprecated: use getKlipyApiKey(Context) instead.
     */
    @Deprecated
    public static String getTenorApiKey() {
        return BuildConfig.KLIPY_API_KEY;
    }
}
