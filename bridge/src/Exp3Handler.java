import com.sun.net.httpserver.HttpExchange;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

/**
 * Handles /api/exp3/* — Experiment 3 adds Lamport clocks and Berkeley sync
 * on top of the basic booking service.
 *
 * RMI port    : 1099
 * Binding     : "BookingService"
 *
 * The Exp3 BookingService interface has:
 *   List<String>          getAvailableSeats(String showId)
 *   String                bookSeat(String seatId, String userName, long lamportTimestamp)
 *   Map<String,Long>      synchronizeClocks(Map<String,Long> clientTimes)
 *   long                  getServerTime()
 */
public class Exp3Handler {

    private static final int    RMI_PORT = 1099;
    private static final String BINDING  = "BookingService";

    // Lamport clock maintained on the bridge side (simulates a "client" clock)
    private static long lamportClock = 0;

    /** GET /api/exp3/seats?showId=<uuid> */
    static void seats(HttpExchange ex) throws java.io.IOException {
        if (!ex.getRequestMethod().equals("GET")) { BridgeServer.json(ex, 405, "{\"error\":\"GET only\"}"); return; }
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        String showId = q.get("showId");
        if (showId == null) { BridgeServer.json(ex, 400, "{\"error\":\"showId required\"}"); return; }
        try {
            BookingService3 svc = lookup();
            List<String> seats = svc.getAvailableSeats(showId);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < seats.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(BridgeServer.jsonStr(seats.get(i)));
            }
            sb.append("]");
            BridgeServer.json(ex, 200, "{\"seats\":" + sb + "}");
        } catch (Exception e) {
            BridgeServer.json(ex, 503, "{\"error\":" + BridgeServer.jsonStr(e.getMessage()) + "}");
        }
    }

    /**
     * POST /api/exp3/book
     * body: {"seatId":"<uuid>","userName":"<name>"}
     * The bridge increments its own Lamport clock before calling bookSeat.
     */
    static void book(HttpExchange ex) throws java.io.IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);
        String seatId   = Exp12Handler.jsonField(body, "seatId");
        String userName = Exp12Handler.jsonField(body, "userName");
        if (seatId == null || userName == null) {
            BridgeServer.json(ex, 400, "{\"error\":\"seatId and userName required\"}"); return;
        }
        long clientTs = ++lamportClock;   // Lamport send rule
        try {
            BookingService3 svc = lookup();
            String result = svc.bookSeat(seatId, userName, clientTs);
            boolean ok = result.startsWith("SUCCESS");
            BridgeServer.json(ex, 200,
                "{\"result\":" + BridgeServer.jsonStr(result) +
                ",\"success\":" + ok +
                ",\"clientLamport\":" + clientTs + "}");
        } catch (Exception e) {
            BridgeServer.json(ex, 503, "{\"error\":" + BridgeServer.jsonStr(e.getMessage()) + "}");
        }
    }

    /**
     * POST /api/exp3/lamport
     * body: {"clients":["C1","C2","C3"]}
     * Simulates multiple clients each with their own Lamport clock sending
     * concurrent booking-style events and returns the event log.
     */
    static void lamportDemo(HttpExchange ex) throws java.io.IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);
        List<String> clients = Exp12Handler.jsonArray(body, "clients");
        if (clients.isEmpty()) clients = Arrays.asList("C1", "C2", "C3");

        // Simulate each client with an independent Lamport clock sending a
        // request and the server applying the receive rule.
        List<String> log = new ArrayList<>();
        long[] clocks = new long[clients.size()];
        long serverClock = 0;

        for (int i = 0; i < clients.size(); i++) {
            clocks[i]++;                                     // send event on client i
            long sent = clocks[i];
            serverClock = Math.max(serverClock, sent) + 1;  // server receive rule
            log.add("[" + clients.get(i) + "] SEND  ts=" + sent);
            log.add("[SERVER]   RECV from " + clients.get(i) + " ts=" + sent + " → serverTs=" + serverClock);
        }

        StringBuilder sb = new StringBuilder("{\"log\":[");
        for (int i = 0; i < log.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(BridgeServer.jsonStr(log.get(i)));
        }
        sb.append("]}");
        BridgeServer.json(ex, 200, sb.toString());
    }

    /**
     * POST /api/exp3/berkeley
     * body: {"clients":{"C1":offset1,"C2":offset2,...}}
     * Calls the real synchronizeClocks() RMI method on the Exp3 server.
     * Each client supplies its current wall-clock time (ms since epoch).
     */
    static void berkeleySync(HttpExchange ex) throws java.io.IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);

        // Parse "clients" object: {"C1":12345,"C2":67890}
        Map<String, Long> clientTimes = new LinkedHashMap<>();
        int objStart = body.indexOf("{", body.indexOf("\"clients\"") + 9);
        int objEnd   = body.indexOf("}", objStart);
        if (objStart >= 0 && objEnd > objStart) {
            String inner = body.substring(objStart + 1, objEnd);
            for (String pair : inner.split(",")) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    String k = kv[0].trim().replaceAll("\"","");
                    try { clientTimes.put(k, Long.parseLong(kv[1].trim())); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        // Fall back: generate plausible simulated values
        if (clientTimes.isEmpty()) {
            long now = System.currentTimeMillis();
            clientTimes.put("C1", now - 2500);
            clientTimes.put("C2", now + 1800);
            clientTimes.put("C3", now - 750);
        }

        try {
            BookingService3 svc = lookup();
            Map<String, Long> corrections = svc.synchronizeClocks(clientTimes);
            StringBuilder sb = new StringBuilder("{\"corrections\":{");
            boolean first = true;
            for (Map.Entry<String, Long> e : corrections.entrySet()) {
                if (!first) sb.append(",");
                sb.append(BridgeServer.jsonStr(e.getKey())).append(":").append(e.getValue());
                first = false;
            }
            sb.append("}}");
            BridgeServer.json(ex, 200, sb.toString());
        } catch (Exception e) {
            BridgeServer.json(ex, 503, "{\"error\":" + BridgeServer.jsonStr(e.getMessage()) + "}");
        }
    }

    private static BookingService3 lookup() throws Exception {
        Registry reg = LocateRegistry.getRegistry("localhost", RMI_PORT);
        return (BookingService3) reg.lookup(BINDING);
    }
}
