import java.util.Scanner;

// Run one process as its own OS process / terminal:
//   java ProcessMain <id 1-5> [initialLeaderId]
// With all five running in separate terminals this is the real distributed
// version of the demo -- each Pn only talks to the others over RMI.
public class ProcessMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java ProcessMain <id 1-5> [initialLeaderId, default 5]");
            return;
        }
        int id = Integer.parseInt(args[0]);
        int initialLeader = args.length > 1 ? Integer.parseInt(args[1]) : 5;

        ProcessNode node = new ProcessNode(id, initialLeader, 0);
        node.start();

        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n[P" + id + "] known leader = P" + node.getLeaderId());
            System.out.println("1) send booking request  2) start election manually  3) status  4) crash");
            System.out.print("> ");
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1":
                    System.out.print("Seat id: ");
                    node.sendBookingRequest(sc.nextLine().trim());
                    break;
                case "2":
                    node.startElection("manual trigger from P" + id + "'s console");
                    break;
                case "3":
                    node.log("leader=P" + node.getLeaderId() + ", electionInProgress=" + node.isElectionInProgress());
                    break;
                case "4":
                    node.crash();
                    return;
                default:
                    System.out.println("unknown option");
            }
        }
    }
}
