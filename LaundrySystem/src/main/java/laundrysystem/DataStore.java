package laundrysystem;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
/**
 * Simple in-memory data store shared across the whole application.
 * Replace this with a real database (e.g. JDBC + MySQL/SQLite) if you
 * need the data to persist between runs.
 */
public class DataStore {

    private static final List<Customer> customers = new ArrayList<>();
    private static final List<Order> orders = new ArrayList<>();
    private static int customerIdCounter = 1;
    private static int orderIdCounter = 1;

    static {
        /* Seed a bit of sample data so the dashboard isn't empty on first run.
        Customer c1 = addCustomer("Maria Santos", "0917-123-4567", "123 Rizal St, Quezon City");
        Customer c2 = addCustomer("Juan Dela Cruz", "0918-234-5678", "45 Bonifacio Ave, Manila");
        Customer c3 = addCustomer("Ana Reyes", "0919-345-6789", "78 Mabini St, Pasig City");

        addOrder(c1, "Wash & Fold", 5, LocalDate.now().minusDays(2));
        addOrder(c2, "Dry Clean", 3, LocalDate.now().minusDays(1));
        addOrder(c3, "Iron Only", 8, LocalDate.now());
        addOrder(c1, "Wash & Fold", 4, LocalDate.now());
        
        */
    }

    public static Customer addCustomer(String name, String phone, String address) {
        Customer c = new Customer(customerIdCounter++, name, phone, address);
        customers.add(c);
        return c;
    }

    public static Order addOrder(Customer customer, String serviceType, double quantity, LocalDate date) {
        return addOrder(customer, serviceType, quantity, date, false);
    }

    public static Order addOrder(Customer customer, String serviceType, double quantity, LocalDate date,
                                  boolean includeSoap) {
        Order o = new Order(orderIdCounter++, customer, serviceType, quantity, date, includeSoap);
        orders.add(o);
        return o;
    }

    public static List<Customer> getCustomers() { return customers; }
    public static List<Order> getOrders() { return orders; }

    public static void removeCustomer(Customer c) { customers.remove(c); }
    public static void removeOrder(Order o) { orders.remove(o); }

    public static Order findOrderById(int id) {
        for (Order o : orders) {
            if (o.getDisplayId() == id) return o;
        }
        return null;
    }
    
    
}
