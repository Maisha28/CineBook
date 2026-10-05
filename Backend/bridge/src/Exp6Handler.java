import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Handles /api/exp6/* — Distributed Two-Phase Commit (2PC) & Quorum Consensus (Experiment 6).
 */
public class Exp6Handler {

    private static volatile TwoPhaseCommitCoordinator coordinator = null;
    private static volatile QuorumManager quorumManager = null;
    private static final List<String> LOG = new CopyOnWriteArrayList<>();

    private static synchronized TwoPhaseCommitCoordinator getCoordinator() {
        if (coordinator == null) {
            coordinator = new TwoPhaseCommitCoordinator();
        }
        return coordinator;
    }

    private static synchronized QuorumManager getQuorumManager() {
        if (quorumManager == null) {
            quorumManager = new QuorumManager();
        }
        return quorumManager;
    }

    /** GET /api/exp6/status */
    static void status(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { BridgeServer.json(ex, 405, "{\"error\":\"GET only\"}"); return; }

        TwoPhaseCommitCoordinator coord = getCoordinator();
        QuorumManager qm = getQuorumManager();

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"running\":true,");
        sb.append("\"participants\":[");

        List<ParticipantNode> list = coord.participants();
        for (int i = 0; i < list.size(); i++) {
            ParticipantNode p = list.get(i);
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"nodeId\":").append(BridgeServer.jsonStr(p.nodeId())).append(",");
            sb.append("\"serviceName\":").append(BridgeServer.jsonStr(p.serviceName())).append(",");
            sb.append("\"alive\":").append(p.alive()).append(",");
            sb.append("\"forceAbort\":").append(p.forceAbort()).append(",");
            sb.append("\"simulateTimeout\":").append(p.simulateTimeout());
            sb.append("}");
        }
        sb.append("],");

        // Quorum Info
        sb.append("\"quorum\":{");
        sb.append("\"N\":").append(qm.N()).append(",");
        sb.append("\"W\":").append(qm.W()).append(",");
        sb.append("\"R\":").append(qm.R()).append(",");
        sb.append("\"strongConsistency\":").append(qm.isStrongConsistency());
        sb.append("},");

        sb.append("\"transactionCount\":").append(coord.getHistory().size());
        sb.append("}");

        BridgeServer.json(ex, 200, sb.toString());
    }

    /** POST /api/exp6/start */
    static void startCluster(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        getCoordinator();
        getQuorumManager();
        Exp6Log.log("Bridge", "Experiment 6: 2PC & Quorum Consensus Cluster online");
        BridgeServer.json(ex, 200, "{\"status\":\"started\"}");
    }

    /** POST /api/exp6/stop */
    static void stopCluster(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        if (coordinator != null) coordinator.reset();
        Exp6Log.log("Bridge", "Experiment 6 cluster stopped");
        BridgeServer.json(ex, 200, "{\"status\":\"stopped\"}");
    }

    /** POST /api/exp6/transaction */
    static void transaction(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);
        String txId = jsonField(body, "txId");
        String seatId = jsonField(body, "seatId");
        String userName = jsonField(body, "userName");
        String amountStr = jsonField(body, "amount");

        if (txId == null || txId.isBlank()) txId = "tx-" + UUID.randomUUID().toString().substring(0, 8);
        if (seatId == null) seatId = "seat-A1";
        if (userName == null) userName = "Alice";
        double amount = (amountStr != null) ? Double.parseDouble(amountStr) : 450.0;

        TwoPhaseCommitCoordinator.TransactionResult res = getCoordinator().executeTransaction(txId, seatId, userName, amount);

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"transactionId\":").append(BridgeServer.jsonStr(res.transactionId)).append(",");
        sb.append("\"success\":").append(res.success).append(",");
        sb.append("\"globalDecision\":").append(BridgeServer.jsonStr(res.globalDecision)).append(",");
        sb.append("\"summary\":").append(BridgeServer.jsonStr(res.summary)).append(",");
        sb.append("\"votes\":{");

        int i = 0;
        for (Map.Entry<String, String> entry : res.participantVotes.entrySet()) {
            if (i > 0) sb.append(",");
            sb.append(BridgeServer.jsonStr(entry.getKey())).append(":").append(BridgeServer.jsonStr(entry.getValue()));
            i++;
        }
        sb.append("}}");

        BridgeServer.json(ex, 200, sb.toString());
    }

    /** POST /api/exp6/fault?nodeId=P2-Payment&action=abort|timeout|crash|recover */
    static void fault(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        String nodeId = q.get("nodeId");
        String action = q.get("action");

        ParticipantNode p = getCoordinator().findParticipant(nodeId);
        if (p == null) {
            BridgeServer.json(ex, 400, "{\"error\":\"Participant not found\"}");
            return;
        }

        if ("abort".equalsIgnoreCase(action)) {
            p.setForceAbort(!p.forceAbort());
            Exp6Log.log("Bridge", "Toggled forceAbort on " + nodeId + " -> " + p.forceAbort());
        } else if ("timeout".equalsIgnoreCase(action)) {
            p.setSimulateTimeout(!p.simulateTimeout());
            Exp6Log.log("Bridge", "Toggled simulateTimeout on " + nodeId + " -> " + p.simulateTimeout());
        } else if ("crash".equalsIgnoreCase(action)) {
            p.setAlive(false);
            Exp6Log.log("Bridge", nodeId + " set to DOWN");
        } else if ("recover".equalsIgnoreCase(action)) {
            p.setAlive(true);
            p.setForceAbort(false);
            p.setSimulateTimeout(false);
            Exp6Log.log("Bridge", nodeId + " RECOVERED and reset to healthy state");
        }

        BridgeServer.json(ex, 200, "{\"status\":\"updated\",\"nodeId\":" + BridgeServer.jsonStr(nodeId) + "}");
    }

    /** POST /api/exp6/quorum/config?N=5&W=3&R=3 */
    static void quorumConfig(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        int n = Integer.parseInt(q.getOrDefault("N", "5"));
        int w = Integer.parseInt(q.getOrDefault("W", "3"));
        int r = Integer.parseInt(q.getOrDefault("R", "3"));

        getQuorumManager().setParameters(n, w, r);
        BridgeServer.json(ex, 200, "{\"status\":\"configured\",\"N\":" + n + ",\"W\":" + w + ",\"R\":" + r + "}");
    }

    /** POST /api/exp6/quorum/write */
    static void quorumWrite(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);
        String verStr = jsonField(body, "version");
        int version = (verStr != null) ? Integer.parseInt(verStr) : (int)(System.currentTimeMillis() / 1000 % 1000);

        QuorumManager.QuorumResult res = getQuorumManager().executeWrite("booking-record", version);
        BridgeServer.json(ex, 200, quorumResultJson(res));
    }

    /** POST /api/exp6/quorum/read */
    static void quorumRead(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        QuorumManager.QuorumResult res = getQuorumManager().executeRead();
        BridgeServer.json(ex, 200, quorumResultJson(res));
    }

    /** GET /api/exp6/logs */
    static void logs(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { BridgeServer.json(ex, 405, "{\"error\":\"GET only\"}"); return; }
        BridgeServer.json(ex, 200, "{\"logs\":[]}");
    }

    /** POST /api/exp6/reset */
    static void reset(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        getCoordinator().reset();
        BridgeServer.json(ex, 200, "{\"status\":\"reset\"}");
    }

    private static String quorumResultJson(QuorumManager.QuorumResult res) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"operation\":").append(BridgeServer.jsonStr(res.operation)).append(",");
        sb.append("\"totalNodes\":").append(res.totalNodes).append(",");
        sb.append("\"writeQuorum\":").append(res.writeQuorum).append(",");
        sb.append("\"readQuorum\":").append(res.readQuorum).append(",");
        sb.append("\"strongConsistency\":").append(res.strongConsistency).append(",");
        sb.append("\"success\":").append(res.success).append(",");
        sb.append("\"acksReceived\":").append(res.acksReceived).append(",");
        sb.append("\"details\":").append(BridgeServer.jsonStr(res.details)).append(",");
        sb.append("\"nodeVersions\":{");

        int i = 0;
        for (Map.Entry<String, Integer> entry : res.nodeVersions.entrySet()) {
            if (i > 0) sb.append(",");
            sb.append(BridgeServer.jsonStr(entry.getKey())).append(":").append(entry.getValue());
            i++;
        }
        sb.append("}}");
        return sb.toString();
    }

    private static String jsonField(String json, String key) {
        if (json == null) return null;
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return null;
        int colon = json.indexOf(":", idx);
        if (colon == -1) return null;
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != '"' && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        return json.substring(start, end).trim();
    }
}
