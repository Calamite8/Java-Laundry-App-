package laundrysystem;

import java.util.prefs.Preferences;

/**
 * Tiny helper for "keep signed in": remembers, across app restarts, whether
 * the last session ended in a logged-in state.
 *
 * Uses java.util.prefs.Preferences (built into the JDK -- on Windows this
 * is stored in the registry under HKEY_CURRENT_USER, no separate file to
 * manage). MainDashboard's constructor marks the session as signed in
 * (it only runs after a successful login); "Log Out" in the Settings tab
 * clears it again.
 */
public class SessionManager {

    private static final Preferences PREFS = Preferences.userNodeForPackage(SessionManager.class);
    private static final String KEY_SIGNED_IN = "keepSignedIn";

    public static boolean isKeptSignedIn() {
        return PREFS.getBoolean(KEY_SIGNED_IN, false);
    }

    public static void markSignedIn() {
        PREFS.putBoolean(KEY_SIGNED_IN, true);
    }

    public static void clearSignedIn() {
        PREFS.putBoolean(KEY_SIGNED_IN, false);
    }
}
