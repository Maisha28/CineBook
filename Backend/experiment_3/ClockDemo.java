import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

/**
 * Standalone Berkeley algorithm demo. Run this AFTER Server.java is up.
 *
 * NOTE on design: real Berkeley has the coordinator actively poll each
 * client over the network. Here, the "clients" are represented locally as
 * simulated participants with a configurable offset (we do NOT touch the
 * real OS clock, and we do NOT spin up N separate client processes just for
 * this). This program plays the role of "collect participant times, hand
 * them to the server coordinator, get corrections back, show the result" --
 * which is the part of Berkeley that actually matters for this demo. The
 * RMI server (BookingServiceImpl.synchronizeClocks) does the real averaging.
 */
public class ClockDemo {

    static class SimulatedClient {
        String id;
        long offsetMillis; // how far this client's clock is from real time

        SimulatedClient(String id, long offsetMillis) {
            this.id = id;
            this.offsetMillis = offsetMillis;
        }

        long currentTime() {
            return System.currentTimeMillis() + offsetMillis;
        }
    }

    public static void main(String[] args) throws Exception {
        Registry registry = LocateRegistry.getRegistry("localhost", 1099);
        BookingService service = (BookingService) registry.lookup("BookingService");

        Scanner sc = new Scanner(System.in);

        List<SimulatedClient> clients = new ArrayList<>();
        System.out.println("=== Berkeley Clock Synchronization Demo ===");
        System.out.print("How many simulated clients? ");
        int n = Integer.parseInt(sc.nextLine().trim());

        for (int i = 1; i <= n; i++) {
            System.out.print("Simulated offset for Client " + i + " in seconds (e.g. -5, 8, 4): ");
            long offsetSeconds = Long.parseLong(sc.nextLine().trim());
            clients.add(new SimulatedClient("Client" + i, offsetSeconds * 1000));
        }

        long serverTimeBefore = service.getServerTime();

        System.out.println("\nBefore Berkeley Synchronization");
        System.out.println("Server   : " + serverTimeBefore);
        Map<String, Long> clientTimesNow = new LinkedHashMap<>();
        for (SimulatedClient c : clients) {
            long t = c.currentTime();
            clientTimesNow.put(c.id, t);
            System.out.println(c.id + " : " + t + "  (offset " + (c.offsetMillis / 1000) + "s)");
        }

        Map<String, Long> corrections = service.synchronizeClocks(clientTimesNow);

        System.out.println("\nCalculated corrections (ms to add to each participant's clock)");
        for (Map.Entry<String, Long> e : corrections.entrySet()) {
            System.out.println(e.getKey() + " : " + e.getValue());
        }

        System.out.println("\nAfter Berkeley Synchronization");
        long serverCorrection = corrections.getOrDefault("SERVER", 0L);
        System.out.println("Server   : " + (serverTimeBefore + serverCorrection));
        for (SimulatedClient c : clients) {
            long corrected = c.currentTime() + corrections.getOrDefault(c.id, 0L);
            System.out.println(c.id + " : " + corrected);
        }

        System.out.println("\n(Every participant's corrected time should now be very close together.)");
    }
}