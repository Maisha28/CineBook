import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// One client process (P1..P5) participating in Bully leader election.
// Every node runs its own RMI registry and can call every other node's
// registry directly -- there is no coordinator-hosting server process.
public class ProcessNode extends UnicastRemoteObject implements Process {

    static final int[] PEER_IDS = {1, 2, 3, 4, 5};

    final int id;
    private final int portOffset;
    private volatile int leaderId;
    private final AtomicBoolean electionInProgress = new AtomicBoolean(false);

    // portOffset lets the single-JVM demo run more than one independent
    // 5-node cluster (different port ranges) without them seeing each other.
    ProcessNode(int id, int initialLeaderId, int portOffset) throws RemoteException {
        super();
        this.id = id;
        this.leaderId = initialLeaderId;
        this.portOffset = portOffset;
    }

    private int portOf(int peerId) {
        return 1100 + portOffset + peerId;
    }

    private String bindingName() {
        return "Process" + id;
    }

    void start() throws Exception {
        Registry registry = LocateRegistry.createRegistry(portOf(id));
        registry.rebind(bindingName(), this);
        log("online, known leader = P" + leaderId);
    }

    // Simulates the process terminating: it stops answering lookups and
    // RMI calls. Any peer that tries to reach it afterwards sees a
    // NotBoundException/ConnectException, exactly like a real crash.
    void crash() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", portOf(id));
            registry.unbind(bindingName());
        } catch (Exception ignored) {
        }
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (Exception ignored) {
        }
        log("CRASHED (process terminated)");
    }

    private Process lookupPeer(int peerId) throws Exception {
        Registry registry = LocateRegistry.getRegistry("localhost", portOf(peerId));
        return (Process) registry.lookup("Process" + peerId);
    }

    int getLeaderId() {
        return leaderId;
    }

    boolean isElectionInProgress() {
        return electionInProgress.get();
    }

    void sendBookingRequest(String seatId) {
        if (leaderId == id) {
            log("I am the leader -- processing booking request for seat " + seatId + " locally");
            return;
        }
        log("sending booking request for seat " + seatId + " to leader P" + leaderId);
        try {
            Process leader = lookupPeer(leaderId);
            leader.ping();
            log("leader P" + leaderId + " accepted booking request for seat " + seatId);
        } catch (Exception e) {
            log("leader P" + leaderId + " is unreachable (" + e.getClass().getSimpleName()
                    + ") -- initiating election");
            startElection("leader P" + leaderId + " did not respond to a request");
        }
    }

    void startElection(String reason) {
        if (!electionInProgress.compareAndSet(false, true)) {
            log("election already in progress, ignoring trigger (" + reason + ")");
            return;
        }
        log("=== starting ELECTION (" + reason + ") ===");

        List<Integer> higherIds = new ArrayList<>();
        for (int peerId : PEER_IDS) {
            if (peerId > id) higherIds.add(peerId);
        }

        if (higherIds.isEmpty()) {
            log("no higher-ID process exists in the system -- becoming coordinator, no election needed");
            becomeCoordinator();
            return;
        }

        List<Integer> responded = new ArrayList<>();
        for (int peerId : higherIds) {
            try {
                Process peer = lookupPeer(peerId);
                if (peer.election(id)) {
                    responded.add(peerId);
                    log("P" + peerId + " responded OK -- stepping aside for it");
                }
            } catch (Exception e) {
                log("P" + peerId + " did not respond (presumed down)");
            }
        }

        if (responded.isEmpty()) {
            log("no higher-ID process responded -- becoming coordinator");
            becomeCoordinator();
        } else {
            log("participants that responded: " + responded
                    + " -- waiting for a COORDINATOR announcement");
            electionInProgress.set(false);
        }
    }

    private void becomeCoordinator() {
        leaderId = id;
        log(">>> I am the new COORDINATOR: P" + id + " <<<");
        for (int peerId : PEER_IDS) {
            if (peerId == id) continue;
            try {
                lookupPeer(peerId).announceCoordinator(id);
            } catch (Exception e) {
                log("could not notify P" + peerId + " (presumed down)");
            }
        }
        electionInProgress.set(false);
    }

    @Override
    public boolean election(int candidateId) throws RemoteException {
        log("received ELECTION from P" + candidateId + " -- I outrank it, replying OK");
        // Bully rule: a process that gets challenged by a lower-ID peer
        // must itself confirm whether it is the highest surviving process.
        new Thread(() -> startElection("responding to P" + candidateId + "'s election")).start();
        return true;
    }

    @Override
    public void announceCoordinator(int newLeaderId) throws RemoteException {
        leaderId = newLeaderId;
        electionInProgress.set(false);
        log("received COORDINATOR message -- new leader is P" + newLeaderId);
    }

    @Override
    public boolean ping() throws RemoteException {
        return true;
    }

    void log(String msg) {
        synchronized (System.out) {
            System.out.printf("[%s] [P%d] %s%n", java.time.LocalTime.now().withNano(0), id, msg);
        }
    }
}
