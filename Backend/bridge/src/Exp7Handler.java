import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.*;

/**
 * Handles /api/exp7/* — Load Balancing & Fault Tolerance in Distributed Systems (Experiment 7).
 */
public class Exp7Handler {

    private static volatile LoadBalancer loadBalancer = null;

    public static synchronized LoadBalancer getLoadBalancer() {
        if (loadBalancer == null) {
            loadBalancer = new LoadBalancer();
        }
        return loadBalancer;
    }

    /** GET /api/exp7/status */
    static void status(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { BridgeServer.json(ex, 405, "{\"error\":\"GET only\"}"); return; }

        LoadBalancer lb = getLoadBalancer();
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"algorithm\":").append(BridgeServer.jsonStr(lb.getAlgorithm().name())).append(",");
        sb.append("\"algorithmName\":").append(BridgeServer.jsonStr(lb.getAlgorithm().getDisplayName())).append(",");
        sb.append("\"algorithmDescription\":").append(BridgeServer.jsonStr(lb.getAlgorithm().getDescription())).append(",");
        sb.append("\"totalRequests\":").append(lb.getTotalRequestsReceived()).append(",");
        sb.append("\"totalFailovers\":").append(lb.getTotalFailoversOccurred()).append(",");

        sb.append("\"servers\":[");
        List<BackendServerNode> servers = lb.getServers();
        for (int i = 0; i < servers.size(); i++) {
            BackendServerNode s = servers.get(i);
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"nodeId\":").append(BridgeServer.jsonStr(s.getNodeId())).append(",");
            sb.append("\"name\":").append(BridgeServer.jsonStr(s.getName())).append(",");
            sb.append("\"weight\":").append(s.getWeight()).append(",");
            sb.append("\"currentWeight\":").append(s.getCurrentWeight()).append(",");
            sb.append("\"alive\":").append(s.isAlive()).append(",");
            sb.append("\"requestsHandled\":").append(s.getTotalRequestsHandled()).append(",");
            sb.append("\"activeConnections\":").append(s.getActiveConnections());
            sb.append("}");
        }
        sb.append("],");

        // Include last 15 dispatch history items
        List<LoadBalancer.DispatchResult> history = lb.getRoutingHistory();
        int start = Math.max(0, history.size() - 15);
        sb.append("\"recentHistory\":[");
        for (int i = history.size() - 1, count = 0; i >= start; i--, count++) {
            LoadBalancer.DispatchResult r = history.get(i);
            if (count > 0) sb.append(",");
            sb.append("{");
            sb.append("\"requestId\":").append(BridgeServer.jsonStr(r.requestId)).append(",");
            sb.append("\"success\":").append(r.success).append(",");
            sb.append("\"serverId\":").append(BridgeServer.jsonStr(r.serverId)).append(",");
            sb.append("\"serverName\":").append(BridgeServer.jsonStr(r.serverName)).append(",");
            sb.append("\"algorithm\":").append(BridgeServer.jsonStr(r.algorithm.name())).append(",");
            sb.append("\"wasFailover\":").append(r.wasFailover).append(",");
            sb.append("\"failedServerId\":").append(BridgeServer.jsonStr(r.failedServerId)).append(",");
            sb.append("\"confirmationCode\":").append(BridgeServer.jsonStr(r.confirmationCode)).append(",");
            sb.append("\"message\":").append(BridgeServer.jsonStr(r.message));
            sb.append("}");
        }
        sb.append("]}");

        BridgeServer.json(ex, 200, sb.toString());
    }

    /** POST /api/exp7/config */
    static void config(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);
        LoadBalancer lb = getLoadBalancer();

        String algoStr = jsonField(body, "algorithm");
        if (algoStr != null) {
            try {
                LoadBalancingAlgorithm algo = LoadBalancingAlgorithm.valueOf(algoStr.trim().toUpperCase());
                lb.setAlgorithm(algo);
            } catch (IllegalArgumentException e) {
                BridgeServer.json(ex, 400, "{\"error\":\"Invalid algorithm. Use ROUND_ROBIN or WEIGHTED_ROUND_ROBIN\"}");
                return;
            }
        }

        // Optional weight updates
        String s1WeightStr = jsonField(body, "Server-1");
        String s2WeightStr = jsonField(body, "Server-2");
        String s3WeightStr = jsonField(body, "Server-3");

        if (s1WeightStr != null) lb.updateServerWeight("Server-1", Integer.parseInt(s1WeightStr));
        if (s2WeightStr != null) lb.updateServerWeight("Server-2", Integer.parseInt(s2WeightStr));
        if (s3WeightStr != null) lb.updateServerWeight("Server-3", Integer.parseInt(s3WeightStr));

        BridgeServer.json(ex, 200, "{\"status\":\"configured\",\"algorithm\":" + BridgeServer.jsonStr(lb.getAlgorithm().name()) + "}");
    }

    /** POST /api/exp7/dispatch */
    static void dispatch(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);
        String seatId = jsonField(body, "seatId");
        String userName = jsonField(body, "userName");

        if (seatId == null || seatId.isBlank()) seatId = "A-" + (int)(Math.random() * 20 + 1);
        if (userName == null || userName.isBlank()) userName = "Client-" + (int)(Math.random() * 100 + 1);

        LoadBalancer lb = getLoadBalancer();
        LoadBalancer.DispatchResult res = lb.routeRequest(seatId, userName);

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"requestId\":").append(BridgeServer.jsonStr(res.requestId)).append(",");
        sb.append("\"success\":").append(res.success).append(",");
        sb.append("\"serverId\":").append(BridgeServer.jsonStr(res.serverId)).append(",");
        sb.append("\"serverName\":").append(BridgeServer.jsonStr(res.serverName)).append(",");
        sb.append("\"algorithm\":").append(BridgeServer.jsonStr(res.algorithm.name())).append(",");
        sb.append("\"wasFailover\":").append(res.wasFailover).append(",");
        sb.append("\"failedServerId\":").append(BridgeServer.jsonStr(res.failedServerId)).append(",");
        sb.append("\"confirmationCode\":").append(BridgeServer.jsonStr(res.confirmationCode)).append(",");
        sb.append("\"message\":").append(BridgeServer.jsonStr(res.message));
        sb.append("}");

        BridgeServer.json(ex, res.success ? 200 : 503, sb.toString());
    }

    /** POST /api/exp7/batch */
    static void batch(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);
        String countStr = jsonField(body, "count");
        int count = (countStr != null) ? Integer.parseInt(countStr) : 12;
        count = Math.min(Math.max(1, count), 100);

        LoadBalancer lb = getLoadBalancer();
        LoadBalancer.BatchSummary summary = lb.routeBatch(count, "Seat", "User");

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"totalRequests\":").append(summary.totalRequests).append(",");
        sb.append("\"algorithm\":").append(BridgeServer.jsonStr(summary.algorithm.name())).append(",");
        sb.append("\"failoversCount\":").append(summary.failoversCount).append(",");

        sb.append("\"counts\":{");
        int i = 0;
        for (Map.Entry<String, Integer> e : summary.serverCounts.entrySet()) {
            if (i > 0) sb.append(",");
            sb.append(BridgeServer.jsonStr(e.getKey())).append(":").append(e.getValue());
            i++;
        }
        sb.append("},");

        sb.append("\"percentages\":{");
        i = 0;
        for (Map.Entry<String, Double> e : summary.serverPercentages.entrySet()) {
            if (i > 0) sb.append(",");
            sb.append(BridgeServer.jsonStr(e.getKey())).append(":").append(e.getValue());
            i++;
        }
        sb.append("},");

        sb.append("\"sequence\":[");
        for (int j = 0; j < summary.sequence.size(); j++) {
            if (j > 0) sb.append(",");
            sb.append(BridgeServer.jsonStr(summary.sequence.get(j)));
        }
        sb.append("]}");

        BridgeServer.json(ex, 200, sb.toString());
    }

    /** POST /api/exp7/health?nodeId=Server-2&action=crash|recover|ping */
    static void health(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        String nodeId = q.get("nodeId");
        String action = q.get("action");

        LoadBalancer lb = getLoadBalancer();

        if ("ping_all".equalsIgnoreCase(action)) {
            Map<String, Boolean> check = lb.checkHealth();
            StringBuilder sb = new StringBuilder("{\"status\":\"checked\",\"nodes\":{");
            int i = 0;
            for (Map.Entry<String, Boolean> e : check.entrySet()) {
                if (i > 0) sb.append(",");
                sb.append(BridgeServer.jsonStr(e.getKey())).append(":").append(e.getValue());
                i++;
            }
            sb.append("}}");
            BridgeServer.json(ex, 200, sb.toString());
            return;
        }

        if (nodeId == null) {
            BridgeServer.json(ex, 400, "{\"error\":\"nodeId required\"}");
            return;
        }

        BackendServerNode node = lb.findServer(nodeId);
        if (node == null) {
            BridgeServer.json(ex, 404, "{\"error\":\"Server node not found: " + nodeId + "\"}");
            return;
        }

        if ("crash".equalsIgnoreCase(action)) {
            node.crash();
        } else if ("recover".equalsIgnoreCase(action)) {
            node.recover();
        } else if ("toggle".equalsIgnoreCase(action)) {
            if (node.isAlive()) node.crash();
            else node.recover();
        } else {
            BridgeServer.json(ex, 400, "{\"error\":\"Unknown action: " + action + "\"}");
            return;
        }

        BridgeServer.json(ex, 200, "{\"nodeId\":" + BridgeServer.jsonStr(nodeId) + ",\"alive\":" + node.isAlive() + "}");
    }

    /** POST /api/exp7/reset */
    static void reset(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        getLoadBalancer().reset();
        BridgeServer.json(ex, 200, "{\"status\":\"reset\"}");
    }

    /** GET /api/exp7/logs */
    static void logs(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { BridgeServer.json(ex, 405, "{\"error\":\"GET only\"}"); return; }
        List<String> list = getLoadBalancer().getMemoryLogs();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(BridgeServer.jsonStr(list.get(i)));
        }
        sb.append("]");
        BridgeServer.json(ex, 200, sb.toString());
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
