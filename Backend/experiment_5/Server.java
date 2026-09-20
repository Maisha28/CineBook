import java.util.Scanner;

// Run one server per terminal:
//   java -cp ".;../lib/postgresql-42.7.4.jar" Server primary
//   java -cp ".;../lib/postgresql-42.7.4.jar" Server backup
// Crashing here is real process death (Runtime.halt), so the client sees
// exactly what it would see if the machine dropped off the network.
public class Server {

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || !(args[0].equalsIgnoreCase("primary") || args[0].equalsIgnoreCase("backup"))) {
            System.out.println("Usage: java Server <primary|backup>");
            return;
        }
        String node = args[0].equalsIgnoreCase("primary") ? Cluster.PRIMARY : Cluster.BACKUP;

        ReplicatedBookingServer server = new ReplicatedBookingServer(node, new JdbcBookingStore(), true);
        server.start();

        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println();
            System.out.println("[" + node + "] role = " + server.getRole());
            System.out.println("1) status  2) show replica log  3) crash now  4) crash right after the next database commit");
            System.out.print("> ");
            // No console attached: keep serving (RMI threads keep the JVM alive).
            if (!sc.hasNextLine()) return;
            switch (sc.nextLine().trim()) {
                case "1" -> Log.log(node, server.status().toString());
                case "2" -> server.printReplicaLog();
                case "3" -> server.crash();
                case "4" -> server.armCrashAfterDbCommit();
                default -> System.out.println("unknown option");
            }
        }
    }
}
