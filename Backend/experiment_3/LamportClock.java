/**
 * A Lamport logical clock.
 *
 * This does NOT track real/physical time. It only tracks logical ordering
 * of events: "did event A happen causally before event B, as far as this
 * distributed system can tell from the messages it exchanged."
 *
 * Rules (standard Lamport rules):
 *   - On a LOCAL event (e.g. "I am about to send a booking request"):
 *         L = L + 1
 *   - On RECEIVING a message carrying timestamp T (e.g. server gets a request):
 *         L = max(L, T) + 1
 *
 * Every Client/ConcurrentClient thread gets its OWN LamportClock (they are
 * separate logical processes). The RMI server also keeps exactly ONE
 * LamportClock, shared across all incoming requests, because from the
 * server's point of view all requests are events in a single logical
 * timeline it observes.
 */
public class LamportClock {

    private long time = 0;

    /**
     * Call this for a purely local event (e.g. "client is about to send
     * a request"). Returns the new logical timestamp to attach to that event.
     */
    public synchronized long tick() {
        time = time + 1;
        return time;
    }

    /**
     * Call this when a message/request carrying a remote logical timestamp
     * arrives (e.g. server received a booking request with the client's
     * Lamport value). Applies the standard receive rule and returns the
     * server's new logical timestamp for this event.
     */
    public synchronized long update(long receivedTimestamp) {
        time = Math.max(time, receivedTimestamp) + 1;
        return time;
    }

    public synchronized long current() {
        return time;
    }
}