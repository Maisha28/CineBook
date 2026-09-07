import com.sun.net.httpserver.HttpExchange;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

/**
 * Handles /api/exp4/* — Bully Leader Election (Experiment 4).
 *
 * The handler manages a single in-process ProcessNode cluster (5 nodes, P1–P5)
 * launched on demand. It mirrors exactly what ElectionDemo.java does but
 * exposes the state over HTTP so the frontend can observe and drive it.
 *
 * Port mapping (portOffset=0, same as ElectionDemo Scenario 1):
 *   P1 → 1101, P2 → 1102, P3 → 1103, P4 → 1104, P5 → 1105
 */
public class Exp4Handler {

    // Only one cluster at a time in the bridge (portOffset=0)
    private static volatile Map<Integer, ProcessNode> cluster = null;
    // Captured log lines from ProcessNode.log() — we redirect System.out
    private static final List<String> LOG = new CopyOnWriteArrayList<>();
    private static volatile PrintStreamTee tee = null;

    /** POST /api/exp4/start — start the 5-node cluster */
    static void startCluster(HttpExchange ex) throws java.io.IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        if (cluster != null) { BridgeServer.json(ex, 200, "{\"status\":\"already running\"}"); return; }
        try {
            LOG.clear();
            installTee();
            cluster = new LinkedHashMap<>();
            for (int id : ProcessNode.PEER_IDS) {
                ProcessNode node = new ProcessNode(id, 5, 0);
                node.start();
                cluster.put(id, node);
            }
            BridgeServer.json(ex, 200, "{\"status\":\"started\",\"nodes\":[1,2,3,4,5]}");
        } catch (Exception e) {
            cluster = null;
            BridgeServer.json(ex, 500, "{\"error\":" + BridgeServer.jsonStr(e.getMessage()) + "}");
        }
    }

    /** POST /api/exp4/crash?node=<id> — crash a specific node */
    static void crash(HttpExchange ex) throws java.io.IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        if (cluster == null) { BridgeServer.json(ex, 400, "{\"error\":\"cluster not running\"}"); return; }
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        String nodeStr = q.get("node");
        if (nodeStr == null) { BridgeServer.json(ex, 400, "{\"error\":\"node required\"}"); return; }
        try {
            int nodeId = Integer.parseInt(nodeStr);
            ProcessNode node = cluster.get(nodeId);
            if (node == null) { BridgeServer.json(ex, 404, "{\"error\":\"node not found\"}"); return; }
            node.crash();
            BridgeServer.json(ex, 200, "{\"status\":\"crashed\",\"node\":" + nodeId + "}");
        } catch (NumberFormatException e) {
            BridgeServer.json(ex, 400, "{\"error\":\"node must be integer 1-5\"}");
        }
    }

    /**
     * POST /api/exp4/elect?initiator=<id>
     * Triggers startElection() on the given node (mimics a booking request
     * failing to reach the leader).
     */
    static void elect(HttpExchange ex) throws java.io.IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        if (cluster == null) { BridgeServer.json(ex, 400, "{\"error\":\"cluster not running\"}"); return; }
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        String initStr = q.get("initiator");
        if (initStr == null) { BridgeServer.json(ex, 400, "{\"error\":\"initiator required\"}"); return; }
        try {
            int initiatorId = Integer.parseInt(initStr);
            ProcessNode initiator = cluster.get(initiatorId);
            if (initiator == null) { BridgeServer.json(ex, 404, "{\"error\":\"initiator node not found\"}"); return; }
            // Run election in background so HTTP can return immediately
            new Thread(() -> initiator.startElection("UI-triggered election")).start();
            Thread.sleep(1500); // wait for election to settle
            BridgeServer.json(ex, 200, statusJson());
        } catch (Exception e) {
            BridgeServer.json(ex, 500, "{\"error\":" + BridgeServer.jsonStr(e.getMessage()) + "}");
        }
    }

    /** GET /api/exp4/status — current leader + alive/crashed state of each node */
    static void status(HttpExchange ex) throws java.io.IOException {
        if (cluster == null) {
            BridgeServer.json(ex, 200, "{\"running\":false}");
            return;
        }
        BridgeServer.json(ex, 200, statusJson());
    }

    /** POST /api/exp4/stop — tear down the cluster */
    static void stopCluster(HttpExchange ex) throws java.io.IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        if (cluster != null) {
            for (ProcessNode n : cluster.values()) {
                try { n.crash(); } catch (Exception ignored) {}
            }
            cluster = null;
        }
        LOG.clear();
        if (tee != null) { System.setOut(tee.original); tee = null; }
        BridgeServer.json(ex, 200, "{\"status\":\"stopped\"}");
    }

    /** GET /api/exp4/logs — return captured console output */
    static void logs(HttpExchange ex) throws java.io.IOException {
        StringBuilder sb = new StringBuilder("{\"logs\":[");
        List<String> snapshot = new ArrayList<>(LOG);
        for (int i = 0; i < snapshot.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(BridgeServer.jsonStr(snapshot.get(i)));
        }
        sb.append("]}");
        BridgeServer.json(ex, 200, sb.toString());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String statusJson() {
        if (cluster == null) return "{\"running\":false}";
        StringBuilder sb = new StringBuilder("{\"running\":true,\"nodes\":[");
        boolean first = true;
        for (Map.Entry<Integer, ProcessNode> e : cluster.entrySet()) {
            if (!first) sb.append(",");
            ProcessNode n = e.getValue();
            boolean alive = isAlive(n);
            sb.append("{\"id\":").append(e.getKey())
              .append(",\"alive\":").append(alive)
              .append(",\"leader\":").append(alive ? n.getLeaderId() : -1)
              .append(",\"electionInProgress\":").append(alive && n.isElectionInProgress())
              .append("}");
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }

    private static boolean isAlive(ProcessNode n) {
        try {
            Registry reg = LocateRegistry.getRegistry("localhost", 1100 + n.id);
            reg.lookup("Process" + n.id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Intercept System.out so ProcessNode.log() output goes to LOG[] */
    private static void installTee() {
        if (tee != null) return;
        tee = new PrintStreamTee(System.out, LOG);
        System.setOut(tee);
    }

    /** PrintStream that writes to original AND appends lines to a list */
    static class PrintStreamTee extends java.io.PrintStream {
        final java.io.PrintStream original;
        final List<String> sink;
        PrintStreamTee(java.io.PrintStream orig, List<String> sink) {
            super(orig, true);
            this.original = orig;
            this.sink = sink;
        }
        @Override public void println(String x) {
            super.println(x);
            if (x != null) sink.add(x);
        }
        @Override public void println(Object x) { println(String.valueOf(x)); }
    }
}
