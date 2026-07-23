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

    public static final double SOAP_FEE = 20.0; // flat add-on fee, in pesos

    private final int id;
    private Customer customer;
    private String serviceType;
    private double quantity; // kilos for Wash & Fold, item count otherwise
    private final LocalDate dropOffDate;
    private String status;
    private double price;
    private boolean includeSoap;

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
        this(id, customer, serviceType, quantity, dropOffDate, false);
    }

    public Order(int id, Customer customer, String serviceType, double quantity, LocalDate dropOffDate,
                 boolean includeSoap) {
        this.id = id;
        this.customer = customer;
        this.serviceType = serviceType;
        this.quantity = quantity;
        this.dropOffDate = dropOffDate;
        this.status = STATUSES[0];
        this.includeSoap = includeSoap;
        this.price = calculatePrice();
    }

    private double calculatePrice() {
        double base;
        switch (serviceType) {
            case "Wash & Fold":
                base = quantity * 50.0;   // per kilo
                break;
            case "Dry Clean":
                base = quantity * 150.0;  // per item
                break;
            case "Iron Only":
                base = quantity * 30.0;   // per item
                break;
            default:
                base = 0.0;
        }
        return base + (includeSoap ? SOAP_FEE : 0.0);
    }

    /** Recomputes price from serviceType/quantity/includeSoap -- call after changing any of those. */
    public void recalculatePrice() {
        this.price = calculatePrice();
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public boolean isIncludeSoap() { return includeSoap; }
    public void setIncludeSoap(boolean includeSoap) { this.includeSoap = includeSoap; }

    public boolean isCompleted() {
        return status.equals("Delivered");
    }

    public boolean isInProgress() {
        return status.equals("Washing") || status.equals("Drying")
                || status.equals("Folding") || status.equals("Ready for Pickup");
    }
}
