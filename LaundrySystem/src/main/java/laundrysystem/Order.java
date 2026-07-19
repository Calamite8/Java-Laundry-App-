package laundrysystem;

import java.time.LocalDate;

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
    private final Customer customer;
    private final String serviceType;
    private final double quantity; // kilos for Wash & Fold, item count otherwise
    private final LocalDate dropOffDate;
    private String status;
    private final double price;

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
        this.id = id;
        this.customer = customer;
        this.serviceType = serviceType;
        this.quantity = quantity;
        this.dropOffDate = dropOffDate;
        this.status = STATUSES[0];
        this.price = calculatePrice();
    }

    private double calculatePrice() {
        switch (serviceType) {
            case "Wash & Fold":
                return quantity * 50.0;   // per kilo
            case "Dry Clean":
                return quantity * 150.0;  // per item
            case "Iron Only":
                return quantity * 30.0;   // per item
            default:
                return 0.0;
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
    public String getServiceType() { return serviceType; }
    public double getQuantity() { return quantity; }
    public LocalDate getDropOffDate() { return dropOffDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getPrice() { return price; }

    public boolean isCompleted() {
        return status.equals("Delivered");
    }

    public boolean isInProgress() {
        return status.equals("Washing") || status.equals("Drying")
                || status.equals("Folding") || status.equals("Ready for Pickup");
    }
}
