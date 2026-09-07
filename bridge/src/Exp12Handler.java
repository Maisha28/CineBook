import com.sun.net.httpserver.HttpExchange;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

/**
 * Handles /api/exp1/* and /api/exp2/* — both experiments share the same
 * BookingService RMI interface. Exp 2 also exposes a concurrent demo endpoint.
 *
 * Experiment 1 RMI port : 1099  (Server.java → LocateRegistry.createRegistry(1099))
 * Experiment 2 RMI port : 1099  (same default port, run separately)
 * RMI binding name       : "BookingService"
 */
public class Exp12Handler {

    // Both experiments bind on the default RMI port 1099
    private static final int RMI_PORT = 1099;
    private static final String BINDING = "BookingService";

    /** GET /api/exp{n}/seats?showId=<uuid> */
    static void seats(HttpExchange ex, int expNum) throws java.io.IOException {
        if (!ex.getRequestMethod().equals("GET")) { BridgeServer.json(ex, 405, "{\"error\":\"GET only\"}"); return; }
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        String showId = q.get("showId");
        if (showId == null || showId.isEmpty()) {
            BridgeServer.json(ex, 400, "{\"error\":\"showId required\"}"); return;
        }
        try {
            BookingService svc = lookup();
            List<String> seats = svc.getAvailableSeats(showId);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < seats.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(BridgeServer.jsonStr(seats.get(i)));
            }
            sb.append("]");
            BridgeServer.json(ex, 200, "{\"seats\":" + sb + ",\"source\":\"rmi\"}");
        } catch (Exception e) {
            Map<String, CatalogHandler.SeatInfo> map = CatalogHandler.getShowSeats(showId);
            StringBuilder sb = new StringBuilder("[");
            int count = 0;
            for (CatalogHandler.SeatInfo s : map.values()) {
                if ("available".equalsIgnoreCase(s.status)) {
                    if (count++ > 0) sb.append(",");
                    sb.append(BridgeServer.jsonStr(s.seatNumber + " (id=" + s.id + ")"));
                }
            }
            sb.append("]");
            BridgeServer.json(ex, 200, "{\"seats\":" + sb + ",\"source\":\"local-fallback\"}");
        }
    }

    /** POST /api/exp{n}/book  body: {"seatId":"<uuid>","userName":"<name>"} */
    static void book(HttpExchange ex, int expNum) throws java.io.IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);
        String seatId   = jsonField(body, "seatId");
        String userName = jsonField(body, "userName");
        if (seatId == null || userName == null) {
            BridgeServer.json(ex, 400, "{\"error\":\"seatId and userName required\"}"); return;
        }
        try {
            BookingService svc = lookup();
            String result = svc.bookSeat(seatId, userName);
            boolean ok = result.startsWith("SUCCESS");
            BridgeServer.json(ex, 200, "{\"result\":" + BridgeServer.jsonStr(result) + ",\"success\":" + ok + ",\"source\":\"rmi\"}");
        } catch (Exception e) {
            String fallbackRes = CatalogHandler.bookFallbackSeat(seatId, userName);
            boolean ok = fallbackRes.startsWith("SUCCESS");
            BridgeServer.json(ex, 200, "{\"result\":" + BridgeServer.jsonStr(fallbackRes) + ",\"success\":" + ok + ",\"source\":\"local-fallback\"}");
        }
    }

    /**
     * POST /api/exp2/concurrent
     * body: {"seatId":"<uuid>","users":["Alice","Bob","Carol"]}
     * Fires N concurrent bookSeat calls (same seatId, different users) and
     * returns each result — reproduces the race-condition demo.
     */
    static void concurrentDemo(HttpExchange ex) throws java.io.IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);
        String seatId = jsonField(body, "seatId");
        List<String> users = jsonArray(body, "users");
        if (seatId == null || users.isEmpty()) {
            BridgeServer.json(ex, 400, "{\"error\":\"seatId and users[] required\"}"); return;
        }

        java.util.concurrent.ConcurrentMap<String, String> results = new java.util.concurrent.ConcurrentHashMap<>();
        List<Thread> threads = new ArrayList<>();
        try {
            BookingService svc = lookup();
            for (String user : users) {
                Thread t = new Thread(() -> {
                    try {
                        results.put(user, svc.bookSeat(seatId, user));
                    } catch (Exception e) {
                        results.put(user, "ERROR: " + e.getMessage());
                    }
                });
                threads.add(t);
            }
            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join(10_000);
        } catch (Exception e) {
            // If RMI is not running, run the multithreaded test using the synchronized fallback booker
            for (String user : users) {
                Thread t = new Thread(() -> {
                    String res = CatalogHandler.bookFallbackSeat(seatId, user);
                    results.put(user, res);
                });
                threads.add(t);
            }
            for (Thread t : threads) t.start();
            try {
                for (Thread t : threads) t.join(10_000);
            } catch (InterruptedException ignored) {}
        }

        StringBuilder sb = new StringBuilder("{\"results\":{");
        boolean first = true;
        for (String user : users) {
            if (!first) sb.append(",");
            sb.append(BridgeServer.jsonStr(user)).append(":").append(BridgeServer.jsonStr(results.get(user)));
            first = false;
        }
        sb.append("}}");
        BridgeServer.json(ex, 200, sb.toString());
    }

    private static BookingService lookup() throws Exception {
        Registry reg = LocateRegistry.getRegistry("localhost", RMI_PORT);
        return (BookingService) reg.lookup(BINDING);
    }

    // ── Minimal JSON field extraction (no external deps) ─────────────────────
    static String jsonField(String json, String key) {
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0) return null;
        int colon = json.indexOf(":", ki + search.length());
        if (colon < 0) return null;
        int start = json.indexOf("\"", colon + 1);
        if (start < 0) return null;
        int end = json.indexOf("\"", start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }

    static List<String> jsonArray(String json, String key) {
        List<String> list = new ArrayList<>();
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0) return list;
        int arrStart = json.indexOf("[", ki);
        int arrEnd   = json.indexOf("]", arrStart);
        if (arrStart < 0 || arrEnd < 0) return list;
        String arr = json.substring(arrStart + 1, arrEnd);
        for (String token : arr.split(",")) {
            token = token.trim().replaceAll("^\"|\"$", "");
            if (!token.isEmpty()) list.add(token);
        }
        return list;
    }
}
