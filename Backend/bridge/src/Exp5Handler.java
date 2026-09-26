import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.PrintStream;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles /api/exp5/* — Fault Tolerance with Primary-Backup Replication (Experiment 5).
 *
 * Exposes full cluster lifecycle management, synchronous replication observation,
 * client-driven failover, fault-injection (crash after DB commit), state recovery,
 * replica log inspection, and the 4-phase failover demo to the CineBook frontend.
 */
public class Exp5Handler {

    private static volatile ReplicatedBookingServer primaryServer = null;
    private static volatile ReplicatedBookingServer backupServer = null;
    private static volatile BookingStore store = null;
    private static volatile String storeType = "Unknown";

    // Captured logs from System.out (includes Log.log from ReplicatedBookingServer and Client)
    private static final List<String> LOG = new CopyOnWriteArrayList<>();
    private static volatile PrintStreamTee tee = null;

    // Client heartbeat & failover state
    private static volatile Client client = null;
    private static volatile String clientTarget = Cluster.PRIMARY;
    private static volatile boolean clientRunning = false;
    private static volatile int missedHeartbeats = 0;
    private static volatile String lastHeartbeatTime = "";
    private static Thread heartbeatThread = null;

    // Automated 4-phase demo state
    private static final AtomicBoolean demoRunning = new AtomicBoolean(false);
    private static final AtomicInteger demoStep = new AtomicInteger(0);
    private static volatile String savedBobOpId = null;
    private static volatile List<String> demoSeats = new ArrayList<>();

    // ── Store Initialization ──────────────────────────────────────────────────
    private static synchronized BookingStore getStore() {
        if (store == null) {
            try {
                JdbcBookingStore jdbc = new JdbcBookingStore();
                // Test connectivity with a dummy read
                jdbc.availableSeats("3fa85f64-5717-4562-b3fc-2c963f66afa6");
                store = jdbc;
                storeType = "PostgreSQL (Supabase)";
            } catch (Exception e) {
                store = new InMemoryBookingStore(24);
                storeType = "In-Memory Replicated Store";
            }
        }
        return store;
    }

    // ── HTTP Endpoints ────────────────────────────────────────────────────────

    /** GET /api/exp5/status — node roles, ports, liveness, and client status */
    static void status(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { BridgeServer.json(ex, 405, "{\"error\":\"GET only\"}"); return; }
        BridgeServer.json(ex, 200, buildStatusJson());
    }

    /** POST /api/exp5/start — starts Primary (:1201), Backup (:1202), and Client monitor */
    static void startCluster(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        try {
            installTee();
            BookingStore s = getStore();

            if (primaryServer == null) {
                primaryServer = new ReplicatedBookingServer(Cluster.PRIMARY, s, false);
                primaryServer.start();
            }
            if (backupServer == null) {
                backupServer = new ReplicatedBookingServer(Cluster.BACKUP, s, false);
                backupServer.start();
            }

            startClientMonitor();

            Log.log("Bridge", "Experiment 5 cluster online (Primary :1201, Backup :1202, Store=" + storeType + ")");
            BridgeServer.json(ex, 200, "{\"status\":\"started\",\"storeType\":" + BridgeServer.jsonStr(storeType) + "}");
        } catch (Exception e) {
            Log.log("Bridge", "Failed to start cluster: " + e.getMessage());
            BridgeServer.json(ex, 500, "{\"error\":" + BridgeServer.jsonStr(e.getMessage()) + "}");
        }
    }

    /** POST /api/exp5/stop — stops both servers and client monitor */
    static void stopCluster(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        stopClientMonitor();

        if (primaryServer != null) {
            try { primaryServer.crash(); } catch (Exception ignored) {}
            primaryServer = null;
        }
        if (backupServer != null) {
            try { backupServer.crash(); } catch (Exception ignored) {}
            backupServer = null;
        }

        demoRunning.set(false);
        demoStep.set(0);
        Log.log("Bridge", "Experiment 5 cluster stopped");
        BridgeServer.json(ex, 200, "{\"status\":\"stopped\"}");
    }

    /** POST /api/exp5/crash?node=Primary|Backup — crashes the specified server */
    static void crash(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        String node = q.get("node");
        if (node == null) { BridgeServer.json(ex, 400, "{\"error\":\"node query param required (Primary|Backup)\"}"); return; }

        if (Cluster.PRIMARY.equalsIgnoreCase(node)) {
            if (primaryServer != null) {
                primaryServer.crash();
                primaryServer = null;
                Log.log("Bridge", "Primary server crashed on command");
                BridgeServer.json(ex, 200, "{\"status\":\"crashed\",\"node\":\"Primary\"}");
            } else {
                BridgeServer.json(ex, 400, "{\"error\":\"Primary is already down\"}");
            }
        } else if (Cluster.BACKUP.equalsIgnoreCase(node)) {
            if (backupServer != null) {
                backupServer.crash();
                backupServer = null;
                Log.log("Bridge", "Backup server crashed on command");
                BridgeServer.json(ex, 200, "{\"status\":\"crashed\",\"node\":\"Backup\"}");
            } else {
                BridgeServer.json(ex, 400, "{\"error\":\"Backup is already down\"}");
            }
        } else {
            BridgeServer.json(ex, 400, "{\"error\":\"Invalid node. Expected Primary or Backup\"}");
        }
    }

    /** POST /api/exp5/restart?node=Primary|Backup — restarts a server with recovery */
    static void restart(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        String node = q.get("node");
        if (node == null) { BridgeServer.json(ex, 400, "{\"error\":\"node query param required (Primary|Backup)\"}"); return; }

        try {
            installTee();
            BookingStore s = getStore();

            if (Cluster.PRIMARY.equalsIgnoreCase(node)) {
                if (primaryServer != null) {
                    try { primaryServer.crash(); } catch (Exception ignored) {}
                }
                primaryServer = new ReplicatedBookingServer(Cluster.PRIMARY, s, false);
                primaryServer.start();
                Log.log("Bridge", "Primary server restarted and recovered");
                BridgeServer.json(ex, 200, "{\"status\":\"restarted\",\"node\":\"Primary\"}");
            } else if (Cluster.BACKUP.equalsIgnoreCase(node)) {
                if (backupServer != null) {
                    try { backupServer.crash(); } catch (Exception ignored) {}
                }
                backupServer = new ReplicatedBookingServer(Cluster.BACKUP, s, false);
                backupServer.start();
                Log.log("Bridge", "Backup server restarted and resynced");
                BridgeServer.json(ex, 200, "{\"status\":\"restarted\",\"node\":\"Backup\"}");
            } else {
                BridgeServer.json(ex, 400, "{\"error\":\"Invalid node. Expected Primary or Backup\"}");
            }
        } catch (Exception e) {
            BridgeServer.json(ex, 500, "{\"error\":" + BridgeServer.jsonStr(e.getMessage()) + "}");
        }
    }

    /** POST /api/exp5/arm-crash?node=Primary|Backup — arms crash right after next DB commit */
    static void armCrash(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        String node = q.get("node");
        if (node == null || Cluster.PRIMARY.equalsIgnoreCase(node)) {
            if (primaryServer != null) {
                primaryServer.armCrashAfterDbCommit();
                Log.log("Bridge", "Fault injected: Primary will crash right after next DB commit");
                BridgeServer.json(ex, 200, "{\"status\":\"armed\",\"node\":\"Primary\"}");
            } else {
                BridgeServer.json(ex, 400, "{\"error\":\"Primary is not running\"}");
            }
        } else if (Cluster.BACKUP.equalsIgnoreCase(node)) {
            if (backupServer != null) {
                backupServer.armCrashAfterDbCommit();
                Log.log("Bridge", "Fault injected: Backup will crash right after next DB commit");
                BridgeServer.json(ex, 200, "{\"status\":\"armed\",\"node\":\"Backup\"}");
            } else {
                BridgeServer.json(ex, 400, "{\"error\":\"Backup is not running\"}");
            }
        } else {
            BridgeServer.json(ex, 400, "{\"error\":\"Invalid node\"}");
        }
    }

    /** POST /api/exp5/activate?node=Primary|Backup — manually triggers client failover promotion */
    static void activate(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        String node = q.get("node");
        if (node == null) node = Cluster.BACKUP;

        try {
            BookingServer server = Cluster.lookup(node);
            NodeStatus status = server.activate();
            clientTarget = node;
            Log.log("Bridge", "Client activated " + node + " -> " + status.role());
            BridgeServer.json(ex, 200, "{\"status\":\"activated\",\"node\":" + BridgeServer.jsonStr(node)
                    + ",\"role\":" + BridgeServer.jsonStr(status.role().name()) + "}");
        } catch (Exception e) {
            BridgeServer.json(ex, 500, "{\"error\":" + BridgeServer.jsonStr(e.getMessage()) + "}");
        }
    }

    /** GET /api/exp5/seats?showId=<uuid> */
    static void seats(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { BridgeServer.json(ex, 405, "{\"error\":\"GET only\"}"); return; }
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        String showId = q.get("showId");
        if (showId == null || showId.isEmpty()) {
            showId = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
        }

        try {
            // Read from whichever server is active, or direct store
            List<String> seats = null;
            if (primaryServer != null && isNodeAlive(Cluster.PRIMARY)) {
                seats = primaryServer.getAvailableSeats(showId);
            } else if (backupServer != null && isNodeAlive(Cluster.BACKUP)) {
                seats = backupServer.getAvailableSeats(showId);
            } else {
                seats = getStore().availableSeats(showId);
            }

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < seats.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(BridgeServer.jsonStr(seats.get(i)));
            }
            sb.append("]");
            BridgeServer.json(ex, 200, "{\"seats\":" + sb + ",\"storeType\":" + BridgeServer.jsonStr(storeType) + "}");
        } catch (Exception e) {
            BridgeServer.json(ex, 500, "{\"error\":" + BridgeServer.jsonStr(e.getMessage()) + "}");
        }
    }

    /**
     * POST /api/exp5/book
     * Body: { "seatId": "...", "userName": "...", "operationId": "...", "targetNode": "..." }
     */
    static void book(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);
        String seatId = jsonField(body, "seatId");
        String userName = jsonField(body, "userName");
        String opId = jsonField(body, "operationId");
        String targetNode = jsonField(body, "targetNode");

        if (seatId == null || userName == null) {
            BridgeServer.json(ex, 400, "{\"error\":\"seatId and userName required\"}"); return;
        }
        if (opId == null || opId.isBlank()) {
            opId = Client.newOperationId();
        }

        installTee();

        // If targetNode is specified explicitly (to demonstrate NOT_PRIMARY or direct invocation)
        if (targetNode != null && !targetNode.isBlank()) {
            try {
                BookingServer server = Cluster.lookup(targetNode);
                String reply = server.bookSeat(opId, seatId, userName);
                boolean success = reply.startsWith("SUCCESS");
                BridgeServer.json(ex, 200, "{\"result\":" + BridgeServer.jsonStr(reply)
                        + ",\"success\":" + success
                        + ",\"operationId\":" + BridgeServer.jsonStr(opId)
                        + ",\"executedBy\":" + BridgeServer.jsonStr(targetNode)
                        + ",\"attempts\":1,\"failoverOccurred\":false}");
                return;
            } catch (Exception e) {
                BridgeServer.json(ex, 200, "{\"result\":" + BridgeServer.jsonStr("ERROR: " + e.getMessage())
                        + ",\"success\":false"
                        + ",\"operationId\":" + BridgeServer.jsonStr(opId)
                        + ",\"executedBy\":" + BridgeServer.jsonStr(targetNode)
                        + ",\"attempts\":1,\"failoverOccurred\":false}");
                return;
            }
        }

        // Standard client invocation with automated failover and idempotency
        try {
            if (client == null) {
                client = new Client();
            }
            String initialTarget = clientTarget;
            String reply = client.book(opId, seatId, userName);
            boolean success = reply.startsWith("SUCCESS");
            boolean failoverOccurred = !initialTarget.equals(clientTarget);

            BridgeServer.json(ex, 200, "{\"result\":" + BridgeServer.jsonStr(reply)
                    + ",\"success\":" + success
                    + ",\"operationId\":" + BridgeServer.jsonStr(opId)
                    + ",\"executedBy\":" + BridgeServer.jsonStr(clientTarget)
                    + ",\"attempts\":" + (failoverOccurred ? 2 : 1)
                    + ",\"failoverOccurred\":" + failoverOccurred
                    + ",\"currentPrimary\":" + BridgeServer.jsonStr(clientTarget) + "}");
        } catch (Exception e) {
            BridgeServer.json(ex, 500, "{\"error\":" + BridgeServer.jsonStr(e.getMessage()) + "}");
        }
    }

    /** GET /api/exp5/replicalogs — snapshot of replica logs from both servers */
    static void replicaLogs(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { BridgeServer.json(ex, 405, "{\"error\":\"GET only\"}"); return; }

        List<OperationRecord> pLogs = (primaryServer != null) ? primaryServer.getOpLogSnapshot() : Collections.emptyList();
        List<OperationRecord> bLogs = (backupServer != null) ? backupServer.getOpLogSnapshot() : Collections.emptyList();

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"primary\":").append(recordsToJson(pLogs)).append(",");
        sb.append("\"backup\":").append(recordsToJson(bLogs)).append(",");
        sb.append("\"inSync\":").append(pLogs.size() == bLogs.size() && pLogs.size() > 0);
        sb.append("}");

        BridgeServer.json(ex, 200, sb.toString());
    }

    /** GET /api/exp5/logs — captured real-time protocol log lines */
    static void logs(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { BridgeServer.json(ex, 405, "{\"error\":\"GET only\"}"); return; }
        StringBuilder sb = new StringBuilder("{\"logs\":[");
        List<String> snapshot = new ArrayList<>(LOG);
        for (int i = 0; i < snapshot.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(BridgeServer.jsonStr(snapshot.get(i)));
        }
        sb.append("]}");
        BridgeServer.json(ex, 200, sb.toString());
    }

    /**
     * POST /api/exp5/demo/step?step=1|2|3|4|auto
     * Executes the scripted 4-phase failover demo steps matching FailoverDemo.java
     */
    static void demoStep(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        String stepStr = q.get("step");
        if (stepStr == null) stepStr = "1";

        installTee();
        BookingStore s = getStore();

        try {
            if ("auto".equalsIgnoreCase(stepStr)) {
                if (demoRunning.compareAndSet(false, true)) {
                    new Thread(Exp5Handler::runAutomatedDemoStory, "demo-story").start();
                    BridgeServer.json(ex, 200, "{\"status\":\"started_auto_story\"}");
                } else {
                    BridgeServer.json(ex, 200, "{\"status\":\"already_running\"}");
                }
                return;
            }

            int step = Integer.parseInt(stepStr);
            String message = executeSingleDemoStep(step, s);
            BridgeServer.json(ex, 200, "{\"step\":" + step + ",\"message\":" + BridgeServer.jsonStr(message) + "}");
        } catch (Exception e) {
            BridgeServer.json(ex, 500, "{\"error\":" + BridgeServer.jsonStr(e.getMessage()) + "}");
        }
    }

    /** POST /api/exp5/reset — clears demo seats, store, and logs */
    static void reset(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        stopClientMonitor();

        if (primaryServer != null) {
            try { primaryServer.crash(); } catch (Exception ignored) {}
            primaryServer = null;
        }
        if (backupServer != null) {
            try { backupServer.crash(); } catch (Exception ignored) {}
            backupServer = null;
        }

        store = null; // will reinitialize fresh store
        LOG.clear();
        demoStep.set(0);
        demoRunning.set(false);
        savedBobOpId = null;
        demoSeats.clear();

        Log.log("Bridge", "Experiment 5 workbench reset to initial state");
        BridgeServer.json(ex, 200, "{\"status\":\"reset\"}");
    }

    // ── Demo Execution Helpers ────────────────────────────────────────────────

    private static String executeSingleDemoStep(int step, BookingStore s) throws Exception {
        ensureClusterRunning(s);

        if (demoSeats.size() < 4) {
            demoSeats = pickSeatsForDemo("3fa85f64-5717-4562-b3fc-2c963f66afa6", 4);
        }

        switch (step) {
            case 1: {
                banner("Phase 1: Normal synchronous replication -- Primary replicates to Backup before commit");
                String reply = client.book(demoSeats.get(0), "Alice");
                demoStep.set(1);
                return "Phase 1 complete: " + reply + " (replicated to Backup before DB commit)";
            }
            case 2: {
                banner("Phase 2: Primary crashes AFTER database commit, before replying -- Idempotent retry on Backup");
                if (primaryServer != null) {
                    primaryServer.armCrashAfterDbCommit();
                }
                savedBobOpId = Client.newOperationId();
                Log.log("Client", "Sending booking for Bob with opId=" + savedBobOpId + " (fault armed)");
                String reply = client.book(savedBobOpId, demoSeats.get(1), "Bob");
                int bookingCount = s.countBookings(demoSeats.get(1));
                demoStep.set(2);
                return "Phase 2 complete: " + reply + " (DB has " + bookingCount + " row, retry did NOT duplicate)";
            }
            case 3: {
                banner("Phase 3: Old Primary restarts -- RECOVERING, syncs from Backup, takes over again");
                primaryServer = new ReplicatedBookingServer(Cluster.PRIMARY, s, false);
                primaryServer.start();
                Thread.sleep(1500); // let heartbeat observe

                String reply1 = client.book(demoSeats.get(2), "Carol");
                String reply2 = "N/A";
                if (savedBobOpId != null) {
                    Log.log("Client", "Re-submitting Bob's ORIGINAL operationId=" + savedBobOpId + " to recovered Primary:");
                    reply2 = client.book(savedBobOpId, demoSeats.get(1), "Bob");
                }
                demoStep.set(3);
                return "Phase 3 complete: Carol booked (" + reply1 + "); Bob original opId resend returned: " + reply2;
            }
            case 4: {
                banner("Phase 4: Primary crashes while idle -- client HEARTBEAT detects and promotes Backup");
                if (primaryServer != null) {
                    primaryServer.crash();
                    primaryServer = null;
                }
                Thread.sleep(3500); // wait for 2 missed heartbeats + activation
                String reply = client.book(demoSeats.get(3), "Dave");
                demoStep.set(4);
                return "Phase 4 complete: Primary died while idle, client heartbeat detected failure, activated Backup, booked Dave: " + reply;
            }
            default:
                throw new IllegalArgumentException("Unknown demo step " + step);
        }
    }

    private static void runAutomatedDemoStory() {
        try {
            BookingStore s = getStore();
            executeSingleDemoStep(1, s);
            Thread.sleep(1500);
            executeSingleDemoStep(2, s);
            Thread.sleep(2000);
            executeSingleDemoStep(3, s);
            Thread.sleep(2000);
            executeSingleDemoStep(4, s);
            banner("4-Phase Failover Story Completed Successfully");
        } catch (Exception e) {
            Log.log("Demo", "Error running automated demo: " + e.getMessage());
        } finally {
            demoRunning.set(false);
        }
    }

    private static void ensureClusterRunning(BookingStore s) throws Exception {
        if (primaryServer == null) {
            primaryServer = new ReplicatedBookingServer(Cluster.PRIMARY, s, false);
            primaryServer.start();
        }
        if (backupServer == null) {
            backupServer = new ReplicatedBookingServer(Cluster.BACKUP, s, false);
            backupServer.start();
        }
        startClientMonitor();
    }

    private static List<String> pickSeatsForDemo(String showId, int count) {
        List<String> result = new ArrayList<>();
        try {
            List<String> avail = getStore().availableSeats(showId);
            Pattern p = Pattern.compile("id=([^)]+)");
            for (String s : avail) {
                Matcher m = p.matcher(s);
                if (m.find()) result.add(m.group(1));
                if (result.size() >= count) break;
            }
        } catch (Exception ignored) {}

        while (result.size() < count) {
            result.add("demo-seat-" + (result.size() + 1));
        }
        return result;
    }

    // ── Client Heartbeat Monitor ──────────────────────────────────────────────

    private static synchronized void startClientMonitor() {
        if (clientRunning) return;
        clientRunning = true;
        if (client == null) {
            client = new Client();
        }

        heartbeatThread = new Thread(() -> {
            while (clientRunning) {
                try {
                    String target = clientTarget;
                    BookingServer s = Cluster.lookup(target);
                    NodeStatus status = s.heartbeat();
                    lastHeartbeatTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));

                    if (status.role() == Role.ACTIVE_PRIMARY) {
                        missedHeartbeats = 0;
                    } else {
                        // Not active primary, attempt failover to peer
                        missedHeartbeats = 0;
                        handleClientFailover();
                    }
                } catch (Exception e) {
                    missedHeartbeats++;
                    if (missedHeartbeats >= 2) {
                        handleClientFailover();
                        missedHeartbeats = 0;
                    }
                }

                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }, "Exp5-ClientHeartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private static synchronized void stopClientMonitor() {
        clientRunning = false;
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
            heartbeatThread = null;
        }
        missedHeartbeats = 0;
    }

    private static void handleClientFailover() {
        String peer = Cluster.peerOf(clientTarget);
        try {
            BookingServer peerServer = Cluster.lookup(peer);
            NodeStatus peerStatus = peerServer.heartbeat();
            if (peerStatus.role() == Role.ACTIVE_PRIMARY) {
                clientTarget = peer;
                Log.log("Client", "Switched active target to " + peer + " (already ACTIVE PRIMARY)");
            } else if (peerStatus.role() == Role.STANDBY) {
                Log.log("Client", "Primary unresponsive; promoting " + peer + " from STANDBY -> ACTIVE PRIMARY");
                NodeStatus activated = peerServer.activate();
                if (activated.role() == Role.ACTIVE_PRIMARY) {
                    clientTarget = peer;
                    Log.log("Client", "Failover complete: requests now go to " + peer);
                }
            }
        } catch (Exception ignored) {
            Log.log("Client", "Failover attempt failed: peer " + peer + " unreachable");
        }
    }

    // ── JSON Helpers ──────────────────────────────────────────────────────────

    private static String buildStatusJson() {
        boolean pAlive = isNodeAlive(Cluster.PRIMARY);
        boolean bAlive = isNodeAlive(Cluster.BACKUP);

        Role pRole = (primaryServer != null && pAlive) ? primaryServer.getRole() : Role.STANDBY;
        Role bRole = (backupServer != null && bAlive) ? backupServer.getRole() : Role.STANDBY;

        int pOps = (primaryServer != null && pAlive) ? primaryServer.status().committedOps() : 0;
        int bOps = (backupServer != null && bAlive) ? backupServer.status().committedOps() : 0;

        boolean pCrashArmed = (primaryServer != null && pAlive && primaryServer.isCrashAfterDbCommitArmed());
        boolean bCrashArmed = (backupServer != null && bAlive && backupServer.isCrashAfterDbCommitArmed());

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"running\":").append(pAlive || bAlive).append(",");
        sb.append("\"storeType\":").append(BridgeServer.jsonStr(storeType)).append(",");
        sb.append("\"demoStep\":").append(demoStep.get()).append(",");
        sb.append("\"demoRunning\":").append(demoRunning.get()).append(",");

        // Primary node status
        sb.append("\"primary\":{")
          .append("\"node\":\"Primary\",")
          .append("\"port\":1201,")
          .append("\"alive\":").append(pAlive).append(",")
          .append("\"role\":").append(BridgeServer.jsonStr(pAlive ? pRole.name() : "DOWN")).append(",")
          .append("\"committedOps\":").append(pOps).append(",")
          .append("\"crashArmed\":").append(pCrashArmed)
          .append("},");

        // Backup node status
        sb.append("\"backup\":{")
          .append("\"node\":\"Backup\",")
          .append("\"port\":1202,")
          .append("\"alive\":").append(bAlive).append(",")
          .append("\"role\":").append(BridgeServer.jsonStr(bAlive ? bRole.name() : "DOWN")).append(",")
          .append("\"committedOps\":").append(bOps).append(",")
          .append("\"crashArmed\":").append(bCrashArmed)
          .append("},");

        // Client heartbeat status
        sb.append("\"client\":{")
          .append("\"active\":").append(clientRunning).append(",")
          .append("\"currentPrimary\":").append(BridgeServer.jsonStr(clientTarget)).append(",")
          .append("\"missedHeartbeats\":").append(missedHeartbeats).append(",")
          .append("\"lastHeartbeat\":").append(BridgeServer.jsonStr(lastHeartbeatTime))
          .append("}");

        sb.append("}");
        return sb.toString();
    }

    private static String recordsToJson(List<OperationRecord> ops) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ops.size(); i++) {
            if (i > 0) sb.append(",");
            OperationRecord op = ops.get(i);
            sb.append("{")
              .append("\"operationId\":").append(BridgeServer.jsonStr(op.operationId())).append(",")
              .append("\"seatId\":").append(BridgeServer.jsonStr(op.seatId())).append(",")
              .append("\"userName\":").append(BridgeServer.jsonStr(op.userName())).append(",")
              .append("\"state\":").append(BridgeServer.jsonStr(op.state().name())).append(",")
              .append("\"result\":").append(BridgeServer.jsonStr(op.result())).append(",")
              .append("\"executedBy\":").append(BridgeServer.jsonStr(op.executedBy()))
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static boolean isNodeAlive(String node) {
        try {
            Registry reg = LocateRegistry.getRegistry("localhost", Cluster.portOf(node));
            reg.lookup(Cluster.BINDING);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String jsonField(String json, String key) {
        if (json == null) return null;
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki >= 0) {
            int colon = json.indexOf(":", ki + search.length());
            if (colon >= 0) {
                int start = json.indexOf("\"", colon + 1);
                if (start >= 0) {
                    int end = json.indexOf("\"", start + 1);
                    if (end > start) return json.substring(start + 1, end);
                }
            }
        }
        Pattern p = Pattern.compile("[\"']?" + Pattern.quote(key) + "[\"']?\\s*:\\s*[\"']?([^\"',}\\s]+|\"[^\"]*\"|'[^']*')[\"']?");
        Matcher m = p.matcher(json);
        if (m.find()) {
            String val = m.group(1).trim();
            if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1);
            } else if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1);
            }
            return val;
        }
        return null;
    }

    private static void banner(String s) {
        Log.log("Demo", "================ " + s + " ================");
    }

    // ── Log Interception ──────────────────────────────────────────────────────

    private static synchronized void installTee() {
        if (tee != null) return;
        tee = new PrintStreamTee(System.out, LOG);
        System.setOut(tee);
    }

    static class PrintStreamTee extends PrintStream {
        final PrintStream original;
        final List<String> sink;

        PrintStreamTee(PrintStream orig, List<String> sink) {
            super(orig, true);
            this.original = orig;
            this.sink = sink;
        }

        @Override
        public void println(String x) {
            super.println(x);
            record(x);
        }

        @Override
        public void println(Object x) {
            super.println(x);
            record(String.valueOf(x));
        }

        @Override
        public PrintStream format(String format, Object... args) {
            super.format(format, args);
            try {
                record(String.format(format, args));
            } catch (Exception ignored) {}
            return this;
        }

        private synchronized void record(String msg) {
            if (msg != null && !msg.isBlank()) {
                sink.add(msg.trim());
                if (sink.size() > 800) sink.remove(0);
            }
        }
    }
}
