package laundrysystem;

import java.io.*;
import java.util.*;

/**
 * Persists the custom domain/base URL used for the web status page (what
 * gets embedded in every QR code -- see StatusServer.BASE_URL), plus a
 * short history of previously used domains so staff can switch back
 * without retyping them.
 *
 * Same pattern as PricingConfig: a plain properties file next to the app,
 * safe to delete to reset to the localhost default.
 */
public class DomainConfig {

    private static final String FILE = "laundry_domain.properties";
    private static final int MAX_HISTORY = 10;

    private static String currentDomain = "http://localhost:8080";
    private static final List<String> history = new ArrayList<>();

    static {
        load();
    }

    public static String getCurrentDomain() {
        return currentDomain;
    }

    /**
     * Sets the active domain and records it in history (moved to the
     * front if it was already there, so "most recently used" stays on top).
     */
    public static void setCurrentDomain(String domain) {
        domain = domain.trim();
        if (domain.isEmpty()) return;

        currentDomain = domain;
        history.remove(domain);
        history.add(0, domain);
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
        save();
    }

    /** Returns a copy, most recently used first. */
    public static List<String> getHistory() {
        return new ArrayList<>(history);
    }

    private static void load() {
        File file = new File(FILE);
        if (!file.exists()) return;

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("Could not load domain config, using default: " + e.getMessage());
            return;
        }

        String saved = props.getProperty("currentDomain");
        if (saved != null && !saved.isBlank()) {
            currentDomain = saved;
        }

        String historyCsv = props.getProperty("history");
        if (historyCsv != null && !historyCsv.isBlank()) {
            history.clear();
            for (String h : historyCsv.split("\\|")) {
                if (!h.isBlank()) history.add(h.trim());
            }
        }
    }

    private static void save() {
        Properties props = new Properties();
        props.setProperty("currentDomain", currentDomain);
        props.setProperty("history", String.join("|", history)); // "|" -- domains won't contain it

        try (FileOutputStream out = new FileOutputStream(FILE)) {
            props.store(out, "Laundry System web status domain -- auto-generated, safe to delete to reset");
        } catch (IOException e) {
            System.err.println("Could not save domain config: " + e.getMessage());
        }
    }
}
