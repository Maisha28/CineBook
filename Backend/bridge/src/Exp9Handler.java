import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.*;

/**
 * Handles /api/exp9/* — MPI-style Collective Communication: Broadcast,
 * Scatter, Gather (+ Reduce) across the CineBook Booking Cluster (Experiment 9).
 */
public class Exp9Handler {

    private static volatile MpiCommunicator communicator = null;

    private static final String DEFAULT_MESSAGE =
        "PROMO50 | Today's Lineup: Dune Part Two, Interstellar, Inception";

    public static synchronized MpiCommunicator getCommunicator() {
        if (communicator == null) {
            communicator = new MpiCommunicator(4);
        }
        return communicator;
    }

    /** GET /api/exp9/status */
    static void status(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { BridgeServer.json(ex, 405, "{\"error\":\"GET only\"}"); return; }
        BridgeServer.json(ex, 200, statusJson(getCommunicator()));
    }

    /** POST /api/exp9/start  body: {"size": 4} — rebuilds COMM_WORLD with N ranks */
    static void start(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);
        String sizeStr = jsonField(body, "size");
        int size;
        try {
            size = (sizeStr != null && !sizeStr.isBlank()) ? Integer.parseInt(sizeStr.trim()) : 4;
        } catch (NumberFormatException e) {
            size = 4;
        }
        size = Math.max(2, Math.min(size, MpiCommunicator.NODE_NAMES.length));

        MpiCommunicator comm = getCommunicator();
        comm.resize(size);
        BridgeServer.json(ex, 200, statusJson(comm));
    }

    /** POST /api/exp9/broadcast  body: {"message": "..."} */
    static void broadcast(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);
        String message = jsonField(body, "message");
        if (message == null || message.isBlank()) message = DEFAULT_MESSAGE;

        MpiCommunicator comm = getCommunicator();
        comm.broadcast(message);
        BridgeServer.json(ex, 200, statusJson(comm));
    }

    /** POST /api/exp9/scatter  body: {"values":[0,10,20,30]} — one value per rank */
    static void scatter(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);
        MpiCommunicator comm = getCommunicator();
        List<Integer> values = jsonIntArray(body, "values");

        if (values == null || values.isEmpty()) {
            values = defaultWorkload(comm.size());
        }

        try {
            comm.scatter(values);
        } catch (IllegalArgumentException e) {
            BridgeServer.json(ex, 400, "{\"error\":" + BridgeServer.jsonStr(e.getMessage()) + "}");
            return;
        }
        BridgeServer.json(ex, 200, statusJson(comm));
    }

    /** POST /api/exp9/gather */
    static void gather(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        MpiCommunicator comm = getCommunicator();
        comm.gather();
        BridgeServer.json(ex, 200, statusJson(comm));
    }

    /** POST /api/exp9/reduce */
    static void reduce(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        MpiCommunicator comm = getCommunicator();
        comm.reduceSum();
        BridgeServer.json(ex, 200, statusJson(comm));
    }

    /**
     * POST /api/exp9/runAll  body: {"message":"...", "values":[...]}
     * Runs the full Bcast -> Scatter -> Gather -> Reduce pipeline in one call,
     * mirroring the Expected Output walkthrough from the lab reference.
     */
    static void runAll(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);
        MpiCommunicator comm = getCommunicator();

        String message = jsonField(body, "message");
        if (message == null || message.isBlank()) message = DEFAULT_MESSAGE;

        List<Integer> values = jsonIntArray(body, "values");
        if (values == null || values.isEmpty()) {
            values = defaultWorkload(comm.size());
        }

        comm.broadcast(message);
        try {
            comm.scatter(values);
        } catch (IllegalArgumentException e) {
            BridgeServer.json(ex, 400, "{\"error\":" + BridgeServer.jsonStr(e.getMessage()) + "}");
            return;
        }
        comm.gather();
        comm.reduceSum();

        BridgeServer.json(ex, 200, statusJson(comm));
    }

    /** POST /api/exp9/fault?rank=3&action=crash|recover|toggle */
    static void fault(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        String rankStr = q.get("rank");
        String action = q.get("action");
        if (rankStr == null) { BridgeServer.json(ex, 400, "{\"error\":\"rank required\"}"); return; }

        MpiCommunicator comm = getCommunicator();
        int rank;
        try {
            rank = Integer.parseInt(rankStr);
        } catch (NumberFormatException e) {
            BridgeServer.json(ex, 400, "{\"error\":\"rank must be an integer\"}");
            return;
        }

        MpiProcess p = comm.findRank(rank);
        if (p == null) { BridgeServer.json(ex, 404, "{\"error\":\"rank not found\"}"); return; }
        if (rank == 0) { BridgeServer.json(ex, 400, "{\"error\":\"cannot crash the root rank (0)\"}"); return; }

        if ("crash".equalsIgnoreCase(action)) {
            comm.crash(rank);
        } else if ("recover".equalsIgnoreCase(action)) {
            comm.recover(rank);
        } else if ("toggle".equalsIgnoreCase(action)) {
            if (p.isAlive()) comm.crash(rank); else comm.recover(rank);
        } else {
            BridgeServer.json(ex, 400, "{\"error\":\"Unknown action: " + action + "\"}");
            return;
        }
        BridgeServer.json(ex, 200, statusJson(comm));
    }

    /** POST /api/exp9/reset */
    static void reset(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        getCommunicator().reset();
        BridgeServer.json(ex, 200, statusJson(getCommunicator()));
    }

    /** GET /api/exp9/logs */
    static void logs(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { BridgeServer.json(ex, 405, "{\"error\":\"GET only\"}"); return; }
        List<String> list = getCommunicator().getMemoryLogs();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(BridgeServer.jsonStr(list.get(i)));
        }
        sb.append("]");
        BridgeServer.json(ex, 200, sb.toString());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static List<Integer> defaultWorkload(int size) {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < size; i++) values.add(i * 10);
        return values;
    }

    // ── JSON builders ────────────────────────────────────────────────────────

    private static String statusJson(MpiCommunicator comm) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"size\":").append(comm.size()).append(",");

        sb.append("\"processes\":[");
        List<MpiProcess> procs = comm.getProcesses();
        for (int i = 0; i < procs.size(); i++) {
            MpiProcess p = procs.get(i);
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"rank\":").append(p.getRank()).append(",");
            sb.append("\"name\":").append(BridgeServer.jsonStr(p.getName())).append(",");
            sb.append("\"isRoot\":").append(p.isRoot()).append(",");
            sb.append("\"alive\":").append(p.isAlive()).append(",");
            sb.append("\"receivedBroadcast\":").append(BridgeServer.jsonStr(p.getReceivedBroadcast())).append(",");
            sb.append("\"scatterValue\":").append(p.getScatterValue() == null ? "null" : p.getScatterValue()).append(",");
            sb.append("\"computedValue\":").append(p.getComputedValue() == null ? "null" : p.getComputedValue());
            sb.append("}");
        }
        sb.append("],");

        sb.append("\"lastBroadcastMessage\":").append(BridgeServer.jsonStr(comm.getLastBroadcastMessage())).append(",");

        sb.append("\"lastScatterInput\":");
        appendIntArray(sb, comm.getLastScatterInput());
        sb.append(",");

        sb.append("\"lastGatherResult\":");
        appendIntArray(sb, comm.getLastGatherResult());
        sb.append(",");

        sb.append("\"lastReduceSum\":").append(comm.getLastReduceSum() == null ? "null" : comm.getLastReduceSum()).append(",");

        sb.append("\"counts\":{");
        sb.append("\"broadcast\":").append(comm.getBroadcastCount()).append(",");
        sb.append("\"scatter\":").append(comm.getScatterCount()).append(",");
        sb.append("\"gather\":").append(comm.getGatherCount()).append(",");
        sb.append("\"reduce\":").append(comm.getReduceCount());
        sb.append("}");

        sb.append("}");
        return sb.toString();
    }

    private static void appendIntArray(StringBuilder sb, List<Integer> list) {
        if (list == null) { sb.append("null"); return; }
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Integer v = list.get(i);
            sb.append(v == null ? "null" : v);
        }
        sb.append("]");
    }

    /**
     * Extracts a JSON field value. Quoted string values are scanned to their
     * matching closing quote (so commas inside the string, e.g. a promo
     * message, don't truncate the value); unquoted values stop at the next
     * delimiter.
     */
    private static String jsonField(String json, String key) {
        if (json == null) return null;
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return null;
        int colon = json.indexOf(":", idx);
        if (colon == -1) return null;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;

        if (start < json.length() && json.charAt(start) == '"') {
            StringBuilder val = new StringBuilder();
            int i = start + 1;
            while (i < json.length() && json.charAt(i) != '"') {
                if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                    val.append(json.charAt(i + 1));
                    i += 2;
                } else {
                    val.append(json.charAt(i));
                    i++;
                }
            }
            return val.toString();
        }

        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ']') end++;
        return json.substring(start, end).trim();
    }

    private static List<Integer> jsonIntArray(String json, String key) {
        if (json == null) return null;
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return null;
        int colon = json.indexOf(":", idx);
        if (colon == -1) return null;
        int start = json.indexOf("[", colon);
        if (start == -1) return null;
        int end = json.indexOf("]", start);
        if (end == -1) return null;
        String inner = json.substring(start + 1, end).trim();
        List<Integer> result = new ArrayList<>();
        if (inner.isEmpty()) return result;
        for (String part : inner.split(",")) {
            try { result.add(Integer.parseInt(part.trim())); } catch (NumberFormatException ignored) {}
        }
        return result;
    }
}
