import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TwoPhaseCommitCoordinator {

    public static class TransactionResult {
        public final String transactionId;
        public final boolean success;
        public final String globalDecision; // "GLOBAL_COMMIT" or "GLOBAL_ABORT"
        public final Map<String, String> participantVotes; // nodeId -> vote
        public final String summary;

        public TransactionResult(String transactionId, boolean success, String globalDecision, Map<String, String> participantVotes, String summary) {
            this.transactionId = transactionId;
            this.success = success;
            this.globalDecision = globalDecision;
            this.participantVotes = participantVotes;
            this.summary = summary;
        }
    }

    private final List<ParticipantNode> participants = new ArrayList<>();
    private final Map<String, TransactionResult> transactionHistory = new ConcurrentHashMap<>();

    public TwoPhaseCommitCoordinator() {
        // Default 3 distributed participants
        participants.add(new ParticipantNode("P1-Booking", "Seat Reservation Service"));
        participants.add(new ParticipantNode("P2-Payment", "Payment Gateway Service"));
        participants.add(new ParticipantNode("P3-Inventory", "Cinema Inventory Service"));
    }

    public List<ParticipantNode> participants() { return participants; }

    public ParticipantNode findParticipant(String nodeId) {
        for (ParticipantNode p : participants) {
            if (p.nodeId().equalsIgnoreCase(nodeId)) return p;
        }
        return null;
    }

    /** Executes a 2PC Distributed Transaction */
    public synchronized TransactionResult executeTransaction(String txId, String seatId, String userName, double amount) {
        Exp6Log.log("2PC-Coord", ">>> STARTING 2PC TRANSACTION " + txId + " (User: " + userName + ", Seat: " + seatId + ", Amount: ₹" + amount + ") <<<");
        Exp6Log.log("2PC-Coord", "Phase 1: Sending PREPARE (VOTE_REQUEST) to " + participants.size() + " participant nodes...");

        Map<String, String> votes = new LinkedHashMap<>();
        boolean allVotedCommit = true;
        String abortReason = "";

        // ── Phase 1: Voting Phase ──
        for (ParticipantNode p : participants) {
            String vote = p.prepare(txId, seatId, userName, amount);
            votes.put(p.nodeId(), vote);

            if (!vote.equals("VOTE_COMMIT")) {
                allVotedCommit = false;
                abortReason = p.nodeId() + " returned " + vote;
            }
        }

        // ── Phase 2: Decision Phase ──
        String decision;
        boolean success;
        String summary;

        if (allVotedCommit) {
            decision = "GLOBAL_COMMIT";
            success = true;
            summary = "All " + participants.size() + " participants voted COMMIT. Transaction " + txId + " successfully committed globally!";
            Exp6Log.log("2PC-Coord", "Phase 2 DECISION: " + decision + " -- All votes positive!");

            for (ParticipantNode p : participants) {
                p.commit(txId);
            }
        } else {
            decision = "GLOBAL_ABORT";
            success = false;
            summary = "Transaction " + txId + " ABORTED globally! Reason: " + abortReason;
            Exp6Log.log("2PC-Coord", "Phase 2 DECISION: " + decision + " -- " + abortReason);

            for (ParticipantNode p : participants) {
                p.abort(txId);
            }
        }

        TransactionResult result = new TransactionResult(txId, success, decision, votes, summary);
        transactionHistory.put(txId, result);
        return result;
    }

    public List<TransactionResult> getHistory() {
        return new ArrayList<>(transactionHistory.values());
    }

    public void reset() {
        transactionHistory.clear();
        for (ParticipantNode p : participants) {
            p.clear();
        }
    }

    public static void main(String[] args) {
        Exp6Log.log("Demo", "==========================================================");
        Exp6Log.log("Demo", " CineBook Experiment 6: Standalone 2PC Terminal Execution ");
        Exp6Log.log("Demo", "==========================================================");

        TwoPhaseCommitCoordinator coord = new TwoPhaseCommitCoordinator();

        // 1. Normal successful 2PC transaction
        Exp6Log.log("Demo", "--- Transaction 1: All Nodes Healthy ---");
        coord.executeTransaction("tx-101", "seat-A1", "Alice", 450.0);

        // 2. Fault Injection: Force Payment Service Abort
        Exp6Log.log("Demo", "--- Transaction 2: Injected Fault (Payment Service Abort) ---");
        ParticipantNode payment = coord.findParticipant("P2-Payment");
        if (payment != null) payment.setForceAbort(true);
        coord.executeTransaction("tx-102", "seat-A2", "Bob", 320.0);

        // 3. Quorum Consensus Demonstration
        Exp6Log.log("Demo", "--- Quorum Consensus Demonstration (N=5, W=3, R=3) ---");
        QuorumManager qm = new QuorumManager();
        qm.executeWrite("seat-A1", 2);
        qm.executeRead();
    }
}
