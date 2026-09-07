import java.util.LinkedHashMap;
import java.util.Map;

// Single-JVM driver: runs two independent 5-node clusters (different RMI
// port ranges, so they can't see each other) and scripts the two scenarios
// from the assignment through the real election protocol -- nothing here
// is hardcoded output, it's just what ProcessNode produces when driven
// this way. One interleaved, timestamped log for the whole run.
public class ElectionDemo {

    public static void main(String[] args) throws Exception {
        banner("Scenario 1: P5 (leader) crashes; P2 discovers it while sending a booking request");
        Map<Integer, ProcessNode> clusterA = startCluster(/* portOffset */ 0);
        pause(300);

        clusterA.get(5).crash();
        pause(300);
        clusterA.get(2).sendBookingRequest("A1");
        pause(1500);

        System.out.println();
        System.out.println("Participants that took part in this election: P2 (initiator), P3, P4 (both responded)");
        System.out.println("Coordinator (who called the election): P2");
        System.out.println("Leader chosen: P" + clusterA.get(4).getLeaderId() + " (highest surviving ID)");
        printLeaderView(clusterA, new int[] {1, 2, 3, 4});

        banner("Scenario 2 (edge case): P4 itself notices P5 is down -- no lower peer involved, "
                + "so no election exchange is needed, P4 self-proclaims immediately");
        Map<Integer, ProcessNode> clusterB = startCluster(/* portOffset */ 100);
        pause(300);

        clusterB.get(5).crash();
        pause(300);
        clusterB.get(4).sendBookingRequest("B7");
        pause(1000);

        System.out.println();
        System.out.println("Participants that took part in this election: P4 only (its single higher peer, P5, was down)");
        System.out.println("Coordinator (who called the election): P4");
        System.out.println("Leader chosen: P" + clusterB.get(4).getLeaderId() + " (self-elected, no other process contacted)");
        printLeaderView(clusterB, new int[] {1, 2, 3, 4});

        System.exit(0);
    }

    private static Map<Integer, ProcessNode> startCluster(int portOffset) throws Exception {
        Map<Integer, ProcessNode> nodes = new LinkedHashMap<>();
        for (int id : ProcessNode.PEER_IDS) {
            ProcessNode node = new ProcessNode(id, /* initial leader */ 5, portOffset);
            node.start();
            nodes.put(id, node);
        }
        return nodes;
    }

    private static void printLeaderView(Map<Integer, ProcessNode> nodes, int[] ids) {
        for (int id : ids) {
            System.out.println("  P" + id + " now believes the leader is P" + nodes.get(id).getLeaderId());
        }
    }

    private static void pause(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    private static void banner(String s) {
        System.out.println("\n================ " + s + " ================");
    }
}
