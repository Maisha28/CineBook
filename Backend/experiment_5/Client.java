import java.util.List;
import java.util.Scanner;
import java.util.UUID;

// Fault-tolerant client. It talks to whichever server is the ACTIVE PRIMARY,
// watches it with heartbeats, and when it stops answering it activates the
// other server and carries on -- retrying an interrupted booking under the
// SAME operationId so it cannot be booked twice.
public class Client {

    private static final long HEARTBEAT_INTERVAL_MS = 1000;
    private static final int MISSED_HEARTBEATS_BEFORE_FAILOVER = 2;
    private static final int MAX_ATTEMPTS = 5;

    private final Object failoverLock = new Object();
    private volatile String current = Cluster.PRIMARY;   // node requests are sent to
    private volatile boolean running = true;

    public void start() {
        if (failover() == null) {
            log("no ACTIVE PRIMARY reachable yet -- heartbeats will keep looking");
        }
        Thread t = new Thread(this::heartbeatLoop, "heartbeat");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
    }

    public static String newOperationId() {
        return "op-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String book(String seatId, String userName) {
        return book(newOperationId(), seatId, userName);
    }

    // Retries until some server gives a definite answer. Every attempt
    // carries the same operationId: if the first attempt actually committed
    // before the server died, the server that takes over recognises the id
    // and returns the original result instead of booking a second time.
    public String book(String operationId, String seatId, String userName) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String target = current;
            log("BOOK opId=" + operationId + " seat=" + seatId + " user=" + userName
                + " -> " + target + " (attempt " + attempt + ")");
            try {
                String reply = Cluster.lookup(target).bookSeat(operationId, seatId, userName);
                if (!reply.startsWith("NOT_PRIMARY")) return reply;
                log(target + " is no longer the primary -- looking for the current one");
            } catch (Exception e) {
                log("no reply from " + target + " (" + e.getClass().getSimpleName()
                    + ") -- the booking may or may not have committed; will retry with the SAME operationId");
            }
            failover();
        }
        return "ERROR: no server could complete the booking after " + MAX_ATTEMPTS + " attempts";
    }

    public List<String> availableSeats(String showId) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return Cluster.lookup(current).getAvailableSeats(showId);
            } catch (Exception e) {
                last = e;
                failover();
            }
        }
        throw last;
    }

    public void printStatus() {
        for (String node : new String[] {Cluster.PRIMARY, Cluster.BACKUP}) {
            NodeStatus s = statusOf(node);
            log(node + ": " + (s == null ? "DOWN (unreachable)" : s.role() + ", " + s.committedOps() + " committed op(s)")
                + (node.equals(current) ? "   <-- requests go here" : ""));
        }
    }

    // ------------------------------------------------------------------
    // Failure detection and failover
    // ------------------------------------------------------------------

    private void heartbeatLoop() {
        int missed = 0;
        String lastHealthy = null;
        while (running) {
            String target = current;
            try {
                NodeStatus s = Cluster.lookup(target).heartbeat();
                if (s.role() == Role.ACTIVE_PRIMARY) {
                    if (missed > 0 || !target.equals(lastHealthy)) {
                        log("heartbeat OK from " + target + " (" + s.role() + ", " + s.committedOps() + " committed op(s))");
                    }
                    lastHealthy = target;
                    missed = 0;
                } else {
                    log("heartbeat: " + target + " now reports " + s.role() + " -- it is not the ACTIVE PRIMARY any more");
                    missed = 0;
                    failover();
                }
            } catch (Exception e) {
                missed++;
                log("heartbeat MISSED from " + target + " (" + missed + "/" + MISSED_HEARTBEATS_BEFORE_FAILOVER + ")");
                if (missed >= MISSED_HEARTBEATS_BEFORE_FAILOVER) {
                    log(target + " declared FAILED");
                    missed = 0;
                    failover();
                }
            }
            sleep(HEARTBEAT_INTERVAL_MS);
        }
    }

    // Finds (or creates) an ACTIVE PRIMARY and points the client at it.
    // Shared by the heartbeat thread and the request path; the lock keeps
    // the two from racing each other into activating anything twice.
    private String failover() {
        synchronized (failoverLock) {
            String previous = current;
            for (int i = 0; i < 6; i++) {
                String found = locatePrimary();
                if (found != null) {
                    current = found;
                    if (!found.equals(previous)) {
                        log("FAILOVER complete: requests now go to " + found + " (ACTIVE PRIMARY)");
                    }
                    return found;
                }
                sleep(500);
            }
            return null;
        }
    }

    private String locatePrimary() {
        NodeStatus primary = statusOf(Cluster.PRIMARY);
        NodeStatus backup = statusOf(Cluster.BACKUP);

        if (primary != null && primary.role() == Role.ACTIVE_PRIMARY) return Cluster.PRIMARY;
        if (backup != null && backup.role() == Role.ACTIVE_PRIMARY) return Cluster.BACKUP;

        // Nobody is primary. Only promote a standby when the other node is
        // truly unreachable. A node that is merely RECOVERING will take the
        // role back by itself, and promoting the standby at that moment would
        // leave two primaries.
        if (primary == null && backup != null && backup.role() == Role.STANDBY) return activate(Cluster.BACKUP);
        if (backup == null && primary != null && primary.role() == Role.STANDBY) return activate(Cluster.PRIMARY);
        return null;
    }

    private String activate(String node) {
        log("connecting to " + node + " and ACTIVATING it as the new PRIMARY");
        try {
            NodeStatus s = Cluster.lookup(node).activate();
            return s.role() == Role.ACTIVE_PRIMARY ? node : null;
        } catch (Exception e) {
            log("could not activate " + node + " (" + e.getClass().getSimpleName() + ")");
            return null;
        }
    }

    private NodeStatus statusOf(String node) {
        try {
            return Cluster.lookup(node).heartbeat();
        } catch (Exception e) {
            return null;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private static void log(String msg) {
        Log.log("Client", msg);
    }

    // ------------------------------------------------------------------
    // Interactive console (one client per terminal)
    // ------------------------------------------------------------------

    public static void main(String[] args) {
        Client client = new Client();
        client.start();

        Scanner sc = new Scanner(System.in);
        String lastOp = null;
        String lastSeat = null;
        String lastUser = null;

        while (true) {
            System.out.println();
            System.out.println("1) list available seats  2) book a seat  3) retry the last booking with the SAME operationId"
                + "  4) status  5) quit");
            System.out.print("> ");
            if (!sc.hasNextLine()) return;
            String choice = sc.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> {
                        System.out.print("Show ID: ");
                        client.availableSeats(sc.nextLine().trim()).forEach(System.out::println);
                    }
                    case "2" -> {
                        System.out.print("Seat id: ");
                        lastSeat = sc.nextLine().trim();
                        System.out.print("Your name: ");
                        lastUser = sc.nextLine().trim();
                        lastOp = newOperationId();
                        System.out.println(client.book(lastOp, lastSeat, lastUser));
                    }
                    case "3" -> {
                        if (lastOp == null) {
                            System.out.println("nothing to retry yet");
                        } else {
                            System.out.println(client.book(lastOp, lastSeat, lastUser));
                        }
                    }
                    case "4" -> client.printStatus();
                    case "5" -> {
                        client.stop();
                        return;
                    }
                    default -> System.out.println("unknown option");
                }
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        }
    }
}
