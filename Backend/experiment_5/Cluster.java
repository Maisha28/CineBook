import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

// Where the two servers live. "Primary" and "Backup" are fixed node
// identities (which process, which port) -- they are NOT the same thing as
// the Role a node currently plays. After a failover the node named "Backup"
// is the one acting as ACTIVE_PRIMARY.
public final class Cluster {

    public static final String PRIMARY = "Primary";
    public static final String BACKUP = "Backup";
    public static final String BINDING = "BookingServer";

    static {
        // Make every stub point at "localhost" instead of whatever address
        // InetAddress.getLocalHost() happens to resolve to (VPN / Wi-Fi off /
        // virtual adapters can make that wrong or slow).
        System.setProperty("java.rmi.server.hostname", "localhost");
    }

    private Cluster() {}

    public static int portOf(String node) {
        return PRIMARY.equals(node) ? 1201 : 1202;
    }

    public static String peerOf(String node) {
        return PRIMARY.equals(node) ? BACKUP : PRIMARY;
    }

    // Each server runs its own RMI registry, so "is this node up?" is
    // simply "can I reach its registry and its object?".
    public static BookingServer lookup(String node) throws Exception {
        Registry registry = LocateRegistry.getRegistry("localhost", portOf(node));
        return (BookingServer) registry.lookup(BINDING);
    }
}
