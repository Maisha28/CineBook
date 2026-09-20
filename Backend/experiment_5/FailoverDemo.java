import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Single-JVM driver: runs the Primary, the Backup and a client (each server
// on its own real RMI port) and walks through the whole story, printing one
// interleaved, timestamped log. Nothing here is canned output -- it is what
// the servers and client print when driven this way.
//
//   java -cp ".;../lib/postgresql-42.7.4.jar" FailoverDemo <showId>   (Supabase)
//   java FailoverDemo --memory                                        (no database)
public class FailoverDemo {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java FailoverDemo <showId>     (uses Supabase via db.properties)");
            System.out.println("       java FailoverDemo --memory     (in-memory stand-in for the database)");
            return;
        }
        boolean memory = args[0].equals("--memory");
        BookingStore store = memory ? new InMemoryBookingStore(6) : new JdbcBookingStore();

        banner("Setup: start Primary and Backup, then the client");
        ReplicatedBookingServer primary = startNode(Cluster.PRIMARY, store);
        ReplicatedBookingServer backup = startNode(Cluster.BACKUP, store);
        Client client = new Client();
        client.start();
        pause(500);

        List<String> seats = pickSeats(client, memory ? "any" : args[0], 4);
        client.printStatus();

        // ------------------------------------------------------------------
        banner("Phase 1: normal operation -- Primary replicates to Backup before committing");
        result(client.book(seats.get(0), "Alice"));
        pause(300);
        primary.printReplicaLog();
        backup.printReplicaLog();

        // ------------------------------------------------------------------
        banner("Phase 2: Primary crashes AFTER the booking is in the database but BEFORE replying");
        primary.armCrashAfterDbCommit();
        String bobOp = Client.newOperationId();
        result(client.book(bobOp, seats.get(1), "Bob"));
        System.out.println("DATABASE CHECK: " + store.countBookings(seats.get(1))
            + " booking row(s) for Bob's seat  (1 = the retry did NOT create a duplicate)");
        client.printStatus();

        // ------------------------------------------------------------------
        banner("Phase 3: old Primary restarts -- RECOVERING, syncs from the Backup, takes over again");
        primary = startNode(Cluster.PRIMARY, store);   // start() runs the whole recovery
        pause(1500);                                   // let the client's heartbeat notice
        client.printStatus();
        result(client.book(seats.get(2), "Carol"));
        pause(300);
        System.out.println("Re-sending Bob's ORIGINAL operationId to the recovered Primary:");
        result(client.book(bobOp, seats.get(1), "Bob"));
        primary.printReplicaLog();
        backup.printReplicaLog();

        // ------------------------------------------------------------------
        banner("Phase 4: Primary crashes while idle -- the client's HEARTBEAT detects it");
        primary.crash();
        pause(4500);                                   // 2 missed heartbeats + activation
        client.printStatus();
        result(client.book(seats.get(3), "Dave"));
        backup.printReplicaLog();

        banner("Done");
        if (!memory) printCleanupHint(seats);
        System.exit(0);
    }

    // The demo really books seats in Supabase; this is how to look at the
    // result and how to put the seats back afterwards.
    private static void printCleanupHint(List<String> seats) {
        String ids = "'" + String.join("', '", seats) + "'";
        System.out.println("\nSee the result in Supabase (SQL editor):");
        System.out.println("  SELECT b.user_name, s.seat_number, b.server_node FROM bookings b"
            + " JOIN seats s ON s.id = b.seat_id WHERE b.seat_id IN (" + ids + ");");
        System.out.println("Put the demo seats back afterwards:");
        System.out.println("  DELETE FROM bookings WHERE seat_id IN (" + ids + ");");
        System.out.println("  UPDATE seats SET status = 'available' WHERE id IN (" + ids + ");");
    }

    private static ReplicatedBookingServer startNode(String node, BookingStore store) throws Exception {
        ReplicatedBookingServer server = new ReplicatedBookingServer(node, store, false);
        server.start();
        return server;
    }

    // Seat ids come out of "A1 (id=<uuid>)" lines.
    private static List<String> pickSeats(Client client, String showId, int count) throws Exception {
        Pattern idPattern = Pattern.compile("id=([^)]+)");
        List<String> ids = new ArrayList<>();
        for (String line : client.availableSeats(showId)) {
            Matcher m = idPattern.matcher(line);
            if (m.find()) ids.add(m.group(1));
            if (ids.size() == count) break;
        }
        if (ids.size() < count) {
            throw new IllegalStateException("the demo needs " + count + " available seats, found " + ids.size());
        }
        System.out.println("Demo will use seats: " + ids);
        return ids;
    }

    private static void result(String reply) {
        System.out.println(">>> client received: " + reply);
    }

    private static void pause(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    private static void banner(String s) {
        System.out.println("\n================ " + s + " ================");
    }
}
