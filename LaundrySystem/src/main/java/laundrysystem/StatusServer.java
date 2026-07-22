package laundrysystem;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * A tiny embedded web server, built entirely from the JDK's own
 * com.sun.net.httpserver package -- no extra Maven dependency needed.
 * It serves two pages, both read/written straight from MySQL:
 *
 *   http://<BASE_URL>/status?token=<claim token>
 *     Read-only: shows the order's current status. Nothing changes.
 *
 *   http://<BASE_URL>/claim?token=<claim token>
 *     Performs the claim (if not already claimed) the moment this page is
 *     opened, then shows a confirmation. This is the one to put in a QR
 *     code meant for "customer scans with their own phone to claim their
 *     order" -- unlike /status, just opening this link marks the order
 *     picked up. Don't hand this QR out before the order is actually ready.
 *
 * IMPORTANT -- BASE_URL: "localhost" only works if you scan the QR code
 * from the SAME machine running this app. For customers to actually open
 * it on their phones, change BASE_URL to an address their phone can
 * reach:
 *   - Same building, same Wi-Fi: this machine's LAN IP, e.g.
 *     "http://192.168.1.50:8080" (find it with `ipconfig` / `ifconfig`).
 *     Also make sure your OS firewall allows inbound connections on PORT.
 *   - Anywhere on the internet: you'd need a real domain/public IP
 *     pointed at this machine (port forwarding) or to deploy this on a
 *     proper server -- a desktop app on someone's PC generally isn't
 *     meant to be reachable from the open internet.
 */
public class StatusServer {

    private static final int PORT = 8080;

    // CHANGE THIS to your shop's actual reachable address before printing
    // QR codes for real customers -- see the class comment above.
    //
    // Quick cross-network testing tip: run `ngrok http 8080` (see
    // https://ngrok.com) and paste the https://...ngrok-free.app URL it
    // gives you here. That makes this server reachable from ANY phone on
    // ANY network (Wi-Fi or mobile data), not just your local LAN --
    // useful for testing today, but the free ngrok URL changes every time
    // you restart the tunnel, so it's not something to print on QR codes
    // for real, ongoing customer use. See the permanent setup notes for that.
    private static String BASE_URL = "https://ratably-untreed-isaias.ngrok-free.dev";

    private static HttpServer server;

    // Lets MainDashboard find out when a claim happens through /claim (a
    // customer scanning with their own phone), so it can refresh the
    // Orders tab / Dashboard stats immediately instead of only updating
    // when staff next click Refresh. Always invoked on the Swing EDT.
    private static java.util.function.Consumer<DatabaseManager.OrderRecord> onClaimCallback;

    /** Starts the server if it isn't already running. Safe to call more than once. */
    public static synchronized void start() {
        if (server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/status", new StatusHandler());
            server.createContext("/claim", new ClaimHandler());
            server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
            server.start();
            System.out.println("Status server running at " + BASE_URL + "/status?token=...");
            System.out.println("Claim server running at " + BASE_URL + "/claim?token=...");
        } catch (IOException e) {
            System.err.println("Could not start status server on port " + PORT + ": " + e.getMessage());
            server = null;
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /**
     * Lets MainDashboard register a callback for phone-initiated claims
     * (via /claim) -- it receives the claimed order's info so the UI can
     * both refresh the Orders/Dashboard tabs AND add a row to the Claims
     * table, same as a staff-initiated claim does.
     */
    public static void setOnClaimCallback(java.util.function.Consumer<DatabaseManager.OrderRecord> callback) {
        onClaimCallback = callback;
    }

    /**
     * Builds the read-only status-page URL for a given claim token, to
     * embed in a QR code.
     *
     * The extra ngrok-skip-browser-warning param is harmless when BASE_URL
     * isn't an ngrok tunnel (the handlers below only look at the "token"
     * param and ignore the rest) -- but when you ARE testing through
     * `ngrok http 8080`, it skips the ngrok free-tier interstitial "Visit
     * Site" warning page that would otherwise show up before your actual
     * page loads. ngrok normally expects this as a request header, but a
     * plain link click/QR scan can't attach custom headers, so the
     * query-param form is the practical way to get the same effect.
     */
    public static String buildStatusUrl(String claimToken) {
        return BASE_URL + "/status?token=" + claimToken + "&ngrok-skip-browser-warning=true";
    }

    /**
     * Builds the claim URL for a given token -- opening this link claims
     * the order. Put this one in a QR code meant for "scan with your phone
     * to claim", not buildStatusUrl.
     */
    public static String buildClaimUrl(String claimToken) {
        return BASE_URL + "/claim?token=" + claimToken + "&ngrok-skip-browser-warning=true";
    }

    /** Lets MainDashboard override the default localhost address at startup, e.g. to the LAN IP. */
    public static void setBaseUrl(String baseUrl) {
        BASE_URL = baseUrl;
    }

    // -----------------------------------------------------------------
    // /status -- read-only
    // -----------------------------------------------------------------
    private static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("[StatusServer] Received: " + exchange.getRequestMethod()
                    + " " + exchange.getRequestURI() + " from " + exchange.getRemoteAddress());
            String token = getQueryParam(exchange.getRequestURI().getQuery(), "token");

            String html;
            if (token == null || token.isEmpty()) {
                html = renderMessage("Missing order token.");
            } else {
                DatabaseManager.OrderRecord order = DatabaseManager.findByToken(token);
                html = (order == null) ? renderMessage("We couldn't find that order.") : renderStatus(order);
            }
            respond(exchange, html);
        }

        private static String renderStatus(DatabaseManager.OrderRecord order) {
            String claimedNote = order.claimed
                    ? "<p style=\"color:#27ae60;\">This order has been picked up.</p>"
                    : "";
            return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                    + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                    + "<title>Order Status</title>"
                    + "<style>body{font-family:sans-serif;max-width:420px;margin:40px auto;padding:0 20px;color:#333;}"
                    + "h1{font-size:20px;} .status{font-size:28px;font-weight:bold;color:#2980b9;margin:10px 0;}"
                    + "table{width:100%;border-collapse:collapse;margin-top:15px;}"
                    + "td{padding:8px 0;border-bottom:1px solid #eee;} td:first-child{color:#888;}</style>"
                    + "</head><body>"
                    + "<h1>Order #" + order.id + "</h1>"
                    + "<div class=\"status\">" + escape(order.status) + "</div>"
                    + claimedNote
                    + "<table>"
                    + "<tr><td>Customer</td><td>" + escape(order.customerName) + "</td></tr>"
                    + "<tr><td>Service</td><td>" + escape(order.serviceType) + "</td></tr>"
                    + "<tr><td>Total</td><td>\u20B1" + String.format("%.2f", order.price) + "</td></tr>"
                    + "</table>"
                    + "</body></html>";
        }
    }

    // -----------------------------------------------------------------
    // /claim -- performs the claim on GET, then shows a confirmation.
    // This is what makes "customer scans with their own phone" actually
    // claim the order, no staff action needed.
    // -----------------------------------------------------------------
    private static class ClaimHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("[StatusServer] Received: " + exchange.getRequestMethod()
                    + " " + exchange.getRequestURI() + " from " + exchange.getRemoteAddress());
            String token = getQueryParam(exchange.getRequestURI().getQuery(), "token");

            String html;
            if (token == null || token.isEmpty()) {
                html = renderMessage("Missing order token.");
            } else {
                DatabaseManager.OrderRecord order = DatabaseManager.findByToken(token);
                if (order == null) {
                    html = renderMessage("We couldn't find that order.");
                } else if (order.claimed) {
                    html = renderClaimResult(order, "Already claimed", "This order was already picked up.");
                } else {
                    boolean success = DatabaseManager.markClaimed(order.id);
                    if (success) {
                        // Mirror onto the in-memory order and let the UI know,
                        // same as a staff-side claim in MainDashboard.
                        Order localOrder = DataStore.findOrderById(order.id);
                        if (localOrder != null) {
                            localOrder.setStatus("Delivered");
                        }
                        GoogleSheetsSync.logClaim(order.id, order.customerName, order.serviceType, order.price);
                        if (onClaimCallback != null) {
                            SwingUtilities.invokeLater(() -> onClaimCallback.accept(order));
                        }
                        html = renderClaimResult(order, "Claimed!", "Thanks -- this order is now marked picked up.");
                    } else {
                        html = renderMessage("Couldn't claim this order -- please show this screen to staff.");
                    }
                }
            }
            respond(exchange, html);
        }

        private static String renderClaimResult(DatabaseManager.OrderRecord order, String heading, String message) {
            return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                    + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                    + "<title>Order Claimed</title>"
                    + "<style>body{font-family:sans-serif;max-width:420px;margin:40px auto;padding:0 20px;color:#333;text-align:center;}"
                    + "h1{font-size:24px;color:#27ae60;} p{color:#555;}</style>"
                    + "</head><body>"
                    + "<h1>" + escape(heading) + "</h1>"
                    + "<p>" + escape(message) + "</p>"
                    + "<p>Order #" + order.id + " -- " + escape(order.customerName) + "</p>"
                    + "</body></html>";
        }
    }

    // -----------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------
    private static void respond(HttpExchange exchange, String html) throws IOException {
        System.out.println("[StatusServer] " + exchange.getRequestMethod() + " "
                + exchange.getRequestURI() + " -> responding, " + html.length() + " chars");

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        // Some reverse proxies (ngrok included) can behave oddly with Java's
        // built-in HttpServer over a kept-alive connection -- forcing each
        // response to close its own connection avoids that class of issue.
        exchange.getResponseHeaders().set("Connection", "close");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String getQueryParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String renderMessage(String message) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                + "<title>Order Status</title></head><body style=\"font-family:sans-serif;\">"
                + "<p>" + escape(message) + "</p></body></html>";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
