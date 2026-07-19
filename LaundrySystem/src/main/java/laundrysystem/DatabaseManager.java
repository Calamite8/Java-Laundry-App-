package laundrysystem;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Handles all MySQL access for the laundry system.
 *
 * Requires the MySQL Connector/J JAR on the classpath (see the QR claim
 * system guide, Part 2). Fill in DB_URL / DB_USER / DB_PASSWORD for your
 * environment -- better yet, load these from a config file or environment
 * variables instead of hardcoding them.
 */
public class DatabaseManager {

    // NOTE on allowPublicKeyRetrieval=true + useSSL=false: MySQL 8+ defaults
    // to the caching_sha2_password auth plugin, which needs to fetch the
    // server's RSA public key to authenticate. The driver won't do that
    // silently over an unencrypted connection unless you opt in with
    // allowPublicKeyRetrieval=true -- that's what "Public Key Retrieval is
    // not allowed" means. This combination is fine for local development
    // (localhost, trusted machine). For anything beyond that -- a shared
    // network, a remote DB server -- switch to useSSL=true (with a proper
    // cert) instead of allowPublicKeyRetrieval, since retrieving the key
    // unencrypted is vulnerable to a man-in-the-middle attack on untrusted
    // networks.
    // NOTE on "localhost" here: this only works when the app AND the MySQL
    // server are on the SAME machine. If you're running this app on
    // multiple devices (other staff PCs) that all need to save to ONE
    // shared database, "localhost" on each of those other devices points
    // at a MySQL server that isn't there -- that's why saving silently
    // fails everywhere except the machine MySQL is actually installed on.
    //
    // Fix: put the DB SERVER's actual LAN IP here (e.g. "192.168.1.50"),
    // the same way StatusServer.BASE_URL needed the real LAN IP instead of
    // localhost. Find it on the DB server machine with `ipconfig` /
    // `ifconfig`. Every device running this app should use that same IP.
    private static final String DB_HOST = "localhost";

    // connectTimeout=15000: gives up to 15 seconds for the connection to
    // establish, instead of the OS's shorter default. Matters specifically
    // over Tailscale -- a "cold" connection between two devices that
    // haven't talked recently needs a moment to negotiate a direct
    // peer-to-peer path (or fall back to a relay), and the default OS
    // timeout can give up before that finishes, even though the
    // connection would have succeeded with a few more seconds' patience.
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":3306/laundry_system"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=15000";
    private static final String DB_USER = "laundry_app";
    private static final String DB_PASSWORD = "change_this_password";

    private static Connection connection;

    /**
     * Returns a shared connection, opening one if needed.
     *
     * isValid(3) (not just isClosed()) matters over an unreliable path like
     * Tailscale: if the underlying route drops or changes (direct <-> relay
     * failover, Wi-Fi switch, etc.), the Connection object doesn't
     * automatically know it's dead -- isClosed() only reflects whether
     * close() was explicitly called. isValid() actually pings the server
     * to confirm the connection still works, and forces a fresh reconnect
     * if not, instead of silently failing on every query afterward.
     */
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(3)) {
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        }
        return connection;
    }

    /**
     * Inserts a new order row and returns the generated order id, or -1 on failure.
     */
    public static int insertOrder(String customerName, String serviceType,
                                   double quantity, double price, String claimToken) {
        String sql = "INSERT INTO orders (customer_name, service_type, quantity, price, claim_token) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, customerName);
            ps.setString(2, serviceType);
            ps.setDouble(3, quantity);
            ps.setDouble(4, price);
            ps.setString(5, claimToken);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("insertOrder failed: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Pulls every order already sitting in MySQL back into the in-memory
     * DataStore, so orders survive an app restart instead of only living
     * in MySQL invisibly. Call this once at startup, before the tabs are
     * built. Safe to call more than once -- orders already present in
     * DataStore (matched by their real MySQL id via getDisplayId()) are
     * skipped rather than duplicated.
     *
     * Customers aren't stored in MySQL (only the customer_name string is,
     * denormalized onto each order row), so this reuses an existing
     * DataStore customer with a matching name, or creates a bare-bones one
     * (name only, no phone/address) if none exists yet.
     */
    public static void loadOrdersToDataStore() {
        String sql = "SELECT id, customer_name, service_type, quantity, status, created_at FROM orders ORDER BY id";
        try (PreparedStatement ps = getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int dbOrderId = rs.getInt("id");
                if (DataStore.findOrderById(dbOrderId) != null) {
                    continue; // already loaded -- don't duplicate
                }

                Customer customer = findOrCreateCustomerByName(rs.getString("customer_name"));

                Timestamp createdTs = rs.getTimestamp("created_at");
                LocalDate dropOffDate = (createdTs != null)
                        ? createdTs.toLocalDateTime().toLocalDate()
                        : LocalDate.now();

                Order order = DataStore.addOrder(customer, rs.getString("service_type"),
                        rs.getDouble("quantity"), dropOffDate);
                order.setDbId(dbOrderId);
                order.setStatus(rs.getString("status"));
            }
        } catch (SQLException e) {
            System.err.println("loadOrdersToDataStore failed (orders will still work, "
                    + "just won't show previously-created ones this session)");
            System.err.println("  Exception type: " + e.getClass().getName());
            System.err.println("  Message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Customer findOrCreateCustomerByName(String name) {
        for (Customer c : DataStore.getCustomers()) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return DataStore.addCustomer(name, "", "");
    }

    /**
     * Looks up an order by its numeric id (used by the Dashboard's
     * "Generate / Reprint QR" tool). Returns null if not found.
     */
    public static OrderRecord findById(int orderId) {
        String sql = "SELECT id, customer_name, service_type, quantity, price, status, claimed, claim_token "
                + "FROM orders WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new OrderRecord(
                            rs.getInt("id"),
                            rs.getString("customer_name"),
                            rs.getString("service_type"),
                            rs.getDouble("quantity"),
                            rs.getDouble("price"),
                            rs.getString("status"),
                            rs.getBoolean("claimed"),
                            rs.getString("claim_token")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("findById failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Looks up an order by its claim token. Returns null if not found.
     */
    public static OrderRecord findByToken(String token) {
        String sql = "SELECT id, customer_name, service_type, quantity, price, status, claimed, claim_token "
                + "FROM orders WHERE claim_token = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new OrderRecord(
                            rs.getInt("id"),
                            rs.getString("customer_name"),
                            rs.getString("service_type"),
                            rs.getDouble("quantity"),
                            rs.getDouble("price"),
                            rs.getString("status"),
                            rs.getBoolean("claimed"),
                            rs.getString("claim_token")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("findByToken failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Updates just the status text column (used when staff change an
     * order's status via the Orders tab dropdown -- Washing, Drying, etc.
     * -- so the public status page reflects it, not just the in-memory
     * table). Does not touch claimed/claimed_at; those are only set by
     * markClaimed() when an order is actually scanned and claimed.
     */
    public static boolean updateStatus(int orderId, String status) {
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, orderId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("updateStatus failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Marks an order as claimed (idempotent-safe: only updates if not already claimed).
     * Returns true if this call is the one that performed the claim.
     */
    public static boolean markClaimed(int orderId) {
        String sql = "UPDATE orders SET claimed = TRUE, claimed_at = ?, status = 'Delivered' "
                + "WHERE id = ? AND claimed = FALSE";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(2, orderId);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("markClaimed failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Simple read-only snapshot of an orders row, used by ClaimPanel.
     */
    public static class OrderRecord {
        public final int id;
        public final String customerName;
        public final String serviceType;
        public final double quantity;
        public final double price;
        public final String status;
        public final boolean claimed;
        public final String claimToken;

        public OrderRecord(int id, String customerName, String serviceType,
                            double quantity, double price, String status, boolean claimed,
                            String claimToken) {
            this.id = id;
            this.customerName = customerName;
            this.serviceType = serviceType;
            this.quantity = quantity;
            this.price = price;
            this.status = status;
            this.claimed = claimed;
            this.claimToken = claimToken;
        }
    }
}
