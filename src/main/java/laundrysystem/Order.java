package laundrysystem;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a laundry order placed by a customer.
 */
public class Order {

    public static final String[] STATUSES = {
        "Pending", "Washing", "Drying", "Folding", "Ready for Pickup", "Delivered"
    };

    public static final String[] SERVICE_TYPES = {
        "Wash & Fold", "Dry Clean", "Iron Only"
    };

    private final int id;
    private Customer customer;
    private String serviceType;
    private double quantity; // kilos for Wash & Fold, item count otherwise
    private final LocalDate dropOffDate;
    private final LocalDate claimByDate; // dropOffDate + PricingConfig.getClaimWindowDays(), fixed at creation
    private String status;
    private double price;
    private boolean includeSoap;
    private boolean includeDetergent;
    private String itemType = ""; // staff-defined catalog item, e.g. "Clothes", "Hat" -- optional

    // Set once this order has been saved to MySQL (see DatabaseManager.insertOrder).
    // -1 means "not yet saved to the database".
    //
    // This is deliberately a SEPARATE id from the in-memory `id` above: this
    // class's `id` comes from DataStore's own counter (used for the sample
    // seed data and as a fallback), while dbId is the row's real
    // AUTO_INCREMENT id in MySQL. They are two different counters and are
    // not guaranteed to match -- mixing them up is what causes "No order
    // found" when looking an order up in the database by its DataStore id.
    // getDisplayId() below is the one ID that should be shown in the UI and
    // used for database lookups, once an order has been synced.
    private int dbId = -1;

    public Order(int id, Customer customer, String serviceType, double quantity, LocalDate dropOffDate) {
        this(id, customer, serviceType, quantity, dropOffDate, false, false);
    }

    public Order(int id, Customer customer, String serviceType, double quantity, LocalDate dropOffDate,
                 boolean includeSoap) {
        this(id, customer, serviceType, quantity, dropOffDate, includeSoap, false);
    }

    public Order(int id, Customer customer, String serviceType, double quantity, LocalDate dropOffDate,
                 boolean includeSoap, boolean includeDetergent) {
        this.id = id;
        this.customer = customer;
        this.serviceType = serviceType;
        this.quantity = quantity;
        this.dropOffDate = dropOffDate;
        this.claimByDate = dropOffDate.plusDays(PricingConfig.getClaimWindowDays());
        this.status = STATUSES[0];
        this.includeSoap = includeSoap;
        this.includeDetergent = includeDetergent;
        this.price = calculatePrice();
    }

    /**
     * Computes price from PricingConfig's current rates (editable in the
     * Settings tab) instead of hardcoded numbers -- a rate change there
     * takes effect on the next order created, or after recalculatePrice().
     */
    private double calculatePrice() {
        double total = quantity * PricingConfig.getRate(serviceType);
        if (includeSoap) total += PricingConfig.getSoapFee();
        if (includeDetergent) total += PricingConfig.getDetergentFee();
        return total;
    }

    /** Recomputes price from serviceType/quantity/includeSoap/includeDetergent -- call after changing any of those. */
    public void recalculatePrice() {
        this.price = calculatePrice();
    }

    /**
     * The itemized breakdown shown on the receipt (desktop dialog and the
     * web status page): main service line, plus Soap/Detergent as their
     * own priced rows when included.
     */
    public List<LineItem> getLineItems() {
        List<LineItem> lines = new ArrayList<>();
        String unit = serviceType.equals("Wash & Fold") ? "kg" : "qty";
        lines.add(new LineItem(serviceType, quantity + " " + unit, quantity * PricingConfig.getRate(serviceType)));
        if (includeSoap) {
            lines.add(new LineItem("Soap", "1 qty", PricingConfig.getSoapFee()));
        }
        if (includeDetergent) {
            lines.add(new LineItem("Detergent", "1 qty", PricingConfig.getDetergentFee()));
        }
        return lines;
    }

    /** One row of the itemized receipt: name, quantity display text, price. */
    public static class LineItem {
        public final String name;
        public final String quantityDisplay;
        public final double price;

        public LineItem(String name, String quantityDisplay, double price) {
            this.name = name;
            this.quantityDisplay = quantityDisplay;
            this.price = price;
        }
    }

    public int getId() { return id; }
    public int getDbId() { return dbId; }
    public void setDbId(int dbId) { this.dbId = dbId; }

    /**
     * The ID to show in tables/dialogs and to use for MySQL lookups. Once an
     * order has been saved to the database, this is the real MySQL row id;
     * until then it falls back to the local DataStore id.
     */
    public int getDisplayId() {
        return dbId != -1 ? dbId : id;
    }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    public LocalDate getDropOffDate() { return dropOffDate; }
    public LocalDate getClaimByDate() { return claimByDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public boolean isIncludeSoap() { return includeSoap; }
    public void setIncludeSoap(boolean includeSoap) { this.includeSoap = includeSoap; }
    public boolean isIncludeDetergent() { return includeDetergent; }
    public void setIncludeDetergent(boolean includeDetergent) { this.includeDetergent = includeDetergent; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType == null ? "" : itemType; }

    public boolean isCompleted() {
        return status.equals("Delivered");
    }

    public boolean isInProgress() {
        return status.equals("Washing") || status.equals("Drying")
                || status.equals("Folding") || status.equals("Ready for Pickup");
    }
}
