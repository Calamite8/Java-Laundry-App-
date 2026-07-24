package laundrysystem;

import java.io.*;
import java.util.*;

/**
 * Holds editable pricing (per-service rates + soap fee) and a staff-managed
 * catalog of item types (e.g. "Clothes", "Hat", "Undergarments"). Persisted
 * to a plain properties file next to the app, same pattern as
 * laundry_settings.properties -- safe to delete to reset to defaults.
 *
 * Order.java reads rates from here instead of hardcoding them, so changes
 * made in the Settings tab take effect on the next order created.
 */
public class PricingConfig {

    private static final String FILE = "laundry_pricing.properties";

    private static double washFoldRate = 50.0;   // per kilo
    private static double dryCleanRate = 150.0;  // per item
    private static double ironOnlyRate = 30.0;   // per item
    private static double soapFee = 20.0;         // flat add-on fee

    private static final List<String> items = new ArrayList<>(
            Arrays.asList("Clothes", "Bedsheets", "Curtains"));

    static {
        load();
    }

    public static double getRate(String serviceType) {
        switch (serviceType) {
            case "Wash & Fold": return washFoldRate;
            case "Dry Clean": return dryCleanRate;
            case "Iron Only": return ironOnlyRate;
            default: return 0.0;
        }
    }

    public static void setRate(String serviceType, double rate) {
        switch (serviceType) {
            case "Wash & Fold": washFoldRate = rate; break;
            case "Dry Clean": dryCleanRate = rate; break;
            case "Iron Only": ironOnlyRate = rate; break;
        }
        save();
    }

    public static double getSoapFee() {
        return soapFee;
    }

    public static void setSoapFee(double fee) {
        soapFee = fee;
        save();
    }

    /** Returns a copy -- callers can't mutate the internal list directly. */
    public static List<String> getItems() {
        return new ArrayList<>(items);
    }

    public static void addItem(String name) {
        name = name.trim();
        if (!name.isEmpty() && !items.contains(name)) {
            items.add(name);
            save();
        }
    }

    public static void removeItem(String name) {
        if (items.remove(name)) {
            save();
        }
    }

    private static void load() {
        File file = new File(FILE);
        if (!file.exists()) return;

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("Could not load pricing config, using defaults: " + e.getMessage());
            return;
        }

        washFoldRate = parseOrDefault(props.getProperty("washFoldRate"), washFoldRate);
        dryCleanRate = parseOrDefault(props.getProperty("dryCleanRate"), dryCleanRate);
        ironOnlyRate = parseOrDefault(props.getProperty("ironOnlyRate"), ironOnlyRate);
        soapFee = parseOrDefault(props.getProperty("soapFee"), soapFee);

        String itemsCsv = props.getProperty("items");
        if (itemsCsv != null && !itemsCsv.isBlank()) {
            items.clear();
            for (String item : itemsCsv.split("\\|")) {
                if (!item.isBlank()) items.add(item.trim());
            }
        }
    }

    private static void save() {
        Properties props = new Properties();
        props.setProperty("washFoldRate", String.valueOf(washFoldRate));
        props.setProperty("dryCleanRate", String.valueOf(dryCleanRate));
        props.setProperty("ironOnlyRate", String.valueOf(ironOnlyRate));
        props.setProperty("soapFee", String.valueOf(soapFee));
        props.setProperty("items", String.join("|", items)); // "|" -- item names won't contain it

        try (FileOutputStream out = new FileOutputStream(FILE)) {
            props.store(out, "Laundry System pricing & items -- auto-generated, safe to delete to reset");
        } catch (IOException e) {
            System.err.println("Could not save pricing config: " + e.getMessage());
        }
    }

    private static double parseOrDefault(String value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
