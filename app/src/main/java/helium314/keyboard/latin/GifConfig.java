package helium314.keyboard.latin;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Configuration for Tenor GIF support.
 */
public class GifConfig {
    /**
     * Returns the Tenor API key to use, preferring the user-provided key if present.
     */
    public static String getTenorApiKey(Context ctx) {
        String userKey = GifPrefs.getStoredApiKey(ctx);
        if (userKey != null && !userKey.trim().isEmpty()) {
            return userKey.trim();
        }
        return BuildConfig.TENOR_API_KEY;
    }
    /**
     * Deprecated: use getTenorApiKey(Context) instead.
     */
    @Deprecated
    public static String getTenorApiKey() {
        return BuildConfig.TENOR_API_KEY;
    }
}