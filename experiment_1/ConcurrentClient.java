import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConcurrentClient {
    public static void main(String[] args) throws Exception {
        Registry registry = LocateRegistry.getRegistry("localhost", 1099);
        BookingService service = (BookingService) registry.lookup("BookingService");

        Scanner sc = new Scanner(System.in);
        System.out.print("Enter seat id to attack concurrently: ");
        String seatId = sc.nextLine().trim();
        System.out.print("How many simultaneous users? ");
        int n = Integer.parseInt(sc.nextLine().trim());

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch startGate = new CountDownLatch(1);

        for (int i = 1; i <= n; i++) {
            final String userName = "User" + i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    String result = service.bookSeat(seatId, userName);
                    System.out.println(userName + " -> " + result);
                } catch (Exception e) {
                    System.out.println(userName + " -> ERROR: " + e.getMessage());
                }
            });
        }

        System.out.println("Releasing " + n + " threads at once...");
        startGate.countDown();
        pool.shutdown();
        pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
        System.out.println("Done.");
    }
}