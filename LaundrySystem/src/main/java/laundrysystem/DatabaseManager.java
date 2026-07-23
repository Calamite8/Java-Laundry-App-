package laundrysystem;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.io.*;
import java.util.Properties;

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
    private static final String DB_HOST = "100.101.23.68";

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

    // --- Testing mode: disable MySQL entirely ---------------------------
    // When disabled, none of the methods below touch MySQL at all -- they
    // read/write an in-memory map instead, so the app (order creation, QR
    // codes, claiming) still fully works for testing without a database
    // connection. MainDashboard's Settings tab toggles this.
    private static boolean mysqlEnabled = true;
    private static final java.util.Map<Integer, OrderRecord> fakeStore = new java.util.LinkedHashMap<>();
    private static int fakeIdCounter = 1;

    // --- Local file persistence: a sub-option under "Disable MySQL" -----
    // Plain in-memory testing mode (above) loses every order the moment the
    // app closes. When this is ALSO turned on (only meaningful while MySQL
    // is disabled), the same fakeStore map is additionally written to a
    // local file after every change, and reloaded from that file at
    // startup -- so orders, QR codes, and claims survive an app restart
    // without needing MySQL at all. This is what makes the QR status-check
    // and claim pages keep working correctly across restarts in a no-SQL
    // setup: StatusServer/DatabaseManager.findByToken/markClaimed already
    // read and write fakeStore regardless of this flag, since mysqlEnabled
    // is false either way -- this flag only decides whether that map is
    // also persisted to disk.
    private static final String SETTINGS_FILE = "laundry_settings.properties";
    private static final String LOCAL_DATA_FILE = "laundry_local_data.dat";
    private static boolean localPersistenceEnabled = false;
    private static boolean shutdownHookRegistered = false;

    static {
        loadSettings();
        if (!mysqlEnabled && localPersistenceEnabled) {
            loadLocalStore();
            registerShutdownHookIfNeeded();
        }
    }

    public static boolean isMysqlEnabled() {
        return mysqlEnabled;
    }

    public static void setMysqlEnabled(boolean enabled) {
        mysqlEnabled = enabled;
        persistSettings();
    }

    public static boolean isLocalPersistenceEnabled() {
        return localPersistenceEnabled;
    }

    /**
     * Turns local-file persistence on/off. Only meaningful while MySQL is
     * disabled (the Settings tab keeps this checkbox disabled otherwise).
     * Turning it ON loads whatever was previously saved to disk into
     * fakeStore (merging by id, so nothing already created this session is
     * lost). Turning it OFF just stops writing further changes to disk --
     * data already on disk is left alone, and what's currently in memory
     * keeps working for the rest of the session.
     */
    public static void setLocalPersistenceEnabled(boolean enabled) {
        localPersistenceEnabled = enabled;
        persistSettings();
        if (enabled) {
            loadLocalStore();
            registerShutdownHookIfNeeded();
        }
    }

    /** Saves the small mysqlEnabled/localPersistenceEnabled flags so the app remembers the chosen mode next launch. */
    private static void persistSettings() {
        Properties props = new Properties();
        props.setProperty("mysqlEnabled", String.valueOf(mysqlEnabled));
        props.setProperty("localPersistenceEnabled", String.valueOf(localPersistenceEnabled));
        try (OutputStream out = new FileOutputStream(SETTINGS_FILE)) {
            props.store(out, "Laundry System settings -- auto-generated, safe to delete");
        } catch (IOException e) {
            System.err.println("Could not save settings: " + e.getMessage());
        }
    }

    private static void loadSettings() {
        File f = new File(SETTINGS_FILE);
        if (!f.exists()) {
            return; // defaults: mysqlEnabled=true, localPersistenceEnabled=false
        }
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            props.load(in);
            mysqlEnabled = Boolean.parseBoolean(props.getProperty("mysqlEnabled", "true"));
            localPersistenceEnabled = Boolean.parseBoolean(props.getProperty("localPersistenceEnabled", "false"));
        } catch (IOException e) {
            System.err.println("Could not read settings, using defaults: " + e.getMessage());
        }
    }

    /**
     * Small serializable wrapper so the fakeStore map and the id counter
     * (needed so new orders don't reuse ids after a restart) are saved and
     * restored together in one file.
     */
    private static class LocalData implements Serializable {
        private static final long serialVersionUID = 1L;
        java.util.Map<Integer, OrderRecord> orders;
        int nextId;

        LocalData(java.util.Map<Integer, OrderRecord> orders, int nextId) {
            this.orders = orders;
            this.nextId = nextId;
        }
    }

    /** Writes the current in-memory fakeStore to disk. Call after any change while localPersistenceEnabled is on. */
    private static void saveLocalStore() {
        LocalData data = new LocalData(new java.util.LinkedHashMap<>(fakeStore), fakeIdCounter);
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(LOCAL_DATA_FILE))) {
            out.writeObject(data);
        } catch (IOException e) {
            System.err.println("Could not save local data file: " + e.getMessage());
        }
    }

    /** Reads previously-saved orders back into fakeStore, merging by id (skips ids already present in memory). */
    @SuppressWarnings("unchecked")
    private static void loadLocalStore() {
        File f = new File(LOCAL_DATA_FILE);
        if (!f.exists()) {
            return; // nothing saved yet -- fine, fakeStore just starts empty
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            LocalData data = (LocalData) in.readObject();
            for (java.util.Map.Entry<Integer, OrderRecord> entry : data.orders.entrySet()) {
                fakeStore.putIfAbsent(entry.getKey(), entry.getValue());
            }
            fakeIdCounter = Math.max(fakeIdCounter, data.nextId);
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Could not read local data file (starting empty): " + e.getMessage());
        }
    }

    /** Called after any fakeStore mutation; no-ops unless local persistence is actually turned on. */
    private static void saveLocalStoreIfEnabled() {
        if (localPersistenceEnabled) {
            saveLocalStore();
        }
    }

    /**
     * Belt-and-suspenders on top of the per-action saves above: registers a
     * JVM shutdown hook that writes fakeStore to disk one more time on the
     * way out, whatever the exit path is -- closing the window
     * (EXIT_ON_CLOSE calls System.exit, which runs shutdown hooks), the app
     * quitting normally, or the process being killed with Ctrl+C. It will
     * NOT run on a hard crash (JVM killed via `kill -9` / task manager "End
     * Process", power loss, etc.) -- that's exactly why every insert/update/
     * claim already saves immediately on its own, so nothing depends on a
     * clean exit in the first place. Registered at most once per run.
     */
    private static synchronized void registerShutdownHookIfNeeded() {
        if (shutdownHookRegistered) {
            return;
        }
        shutdownHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (localPersistenceEnabled) {
                saveLocalStore();
            }
        }, "laundry-local-store-shutdown-save"));
    }

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
        if (!mysqlEnabled) {
            int fakeId = fakeIdCounter++;
            fakeStore.put(fakeId, new OrderRecord(fakeId, customerName, serviceType,
                    quantity, price, "Pending", false, claimToken));
            saveLocalStoreIfEnabled();
            return fakeId;
        }

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
        if (!mysqlEnabled) {
            if (localPersistenceEnabled) {
                loadLocalStoreIntoDataStore();
            }
            return; // pure in-memory testing mode -- nothing on disk to load
        }

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

    /**
     * Same idea as the MySQL loop above, but for local-file mode: mirrors
     * every order already sitting in fakeStore (just loaded from
     * LOCAL_DATA_FILE by the static initializer) into the in-memory
     * DataStore that the UI actually reads from, so a restart doesn't show
     * an empty Orders tab. Skips ids already present, same as the MySQL path.
     */
    private static void loadLocalStoreIntoDataStore() {
        for (OrderRecord r : fakeStore.values()) {
            if (DataStore.findOrderById(r.id) != null) {
                continue; // already loaded -- don't duplicate
            }
            Customer customer = findOrCreateCustomerByName(r.customerName);
            Order order = DataStore.addOrder(customer, r.serviceType, r.quantity, LocalDate.now());
            order.setDbId(r.id);
            order.setStatus(r.status);
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
        if (!mysqlEnabled) {
            return fakeStore.get(orderId);
        }

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
        if (!mysqlEnabled) {
            for (OrderRecord r : fakeStore.values()) {
                if (r.claimToken.equals(token)) {
                    return r;
                }
            }
            return null;
        }

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
     * Updates customer name, service type, quantity, and price for an
     * existing order -- used when staff manually edit a row in the merged
     * Orders & Claims tab. Status/claimed are handled separately by
     * updateStatus() and markClaimed().
     */
    public static boolean updateOrder(int orderId, String customerName, String serviceType,
                                       double quantity, double price) {
        if (!mysqlEnabled) {
            OrderRecord r = fakeStore.get(orderId);
            if (r == null) return false;
            fakeStore.put(orderId, new OrderRecord(r.id, customerName, serviceType,
                    quantity, price, r.status, r.claimed, r.claimToken));
            saveLocalStoreIfEnabled();
            return true;
        }

        String sql = "UPDATE orders SET customer_name = ?, service_type = ?, quantity = ?, price = ? WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, customerName);
            ps.setString(2, serviceType);
            ps.setDouble(3, quantity);
            ps.setDouble(4, price);
            ps.setInt(5, orderId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("updateOrder failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Updates just the status text column (used when staff change an
     * order's status via the Orders tab dropdown -- Washing, Drying, etc.
     * -- so the public status page reflects it, not just the in-memory
     * table). Does not touch claimed/claimed_at; those are only set by
     * markClaimed() when an order is actually scanned and claimed.
     */
    public static boolean updateStatus(int orderId, String status) {
        if (!mysqlEnabled) {
            OrderRecord r = fakeStore.get(orderId);
            if (r == null) return false;
            fakeStore.put(orderId, new OrderRecord(r.id, r.customerName, r.serviceType,
                    r.quantity, r.price, status, r.claimed, r.claimToken));
            saveLocalStoreIfEnabled();
            return true;
        }

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
        if (!mysqlEnabled) {
            OrderRecord r = fakeStore.get(orderId);
            if (r == null || r.claimed) return false;
            fakeStore.put(orderId, new OrderRecord(r.id, r.customerName, r.serviceType,
                    r.quantity, r.price, "Delivered", true, r.claimToken));
            saveLocalStoreIfEnabled();
            return true;
        }

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
     * Deletes an order permanently -- used by the Orders & Claims tab's
     * "Delete Selected" (including multi-select). Handles both real MySQL
     * and the fakeStore/local-save testing mode, same pattern as every
     * other write method here.
     */
    public static boolean deleteOrder(int orderId) {
        if (!mysqlEnabled) {
            boolean removed = fakeStore.remove(orderId) != null;
            if (removed) saveLocalStoreIfEnabled();
            return removed;
        }

        String sql = "DELETE FROM orders WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, orderId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                System.err.println("deleteOrder: no row matched id=" + orderId
                        + " (already deleted, or this app instance's id doesn't match MySQL's)");
            }
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("deleteOrder failed for id=" + orderId
                    + " -- " + e.getClass().getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Simple read-only snapshot of an orders row, used by ClaimPanel.
     */
    public static class OrderRecord implements Serializable {
        private static final long serialVersionUID = 1L;
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
