import java.io.Serializable;

// One entry in a server's replica log. The operationId is chosen by the
// CLIENT and reused on every retry, which is what makes a retried booking
// idempotent: a server that already holds a COMMITTED record for that id
// answers with the recorded result instead of booking again.
public record OperationRecord(String operationId, String seatId, String userName,
                              State state, String result, String executedBy)
        implements Serializable {

    public enum State {
        // Primary has asked the replica to log the operation; the booking may
        // or may not have reached the database yet.
        PREPARED,
        // The booking outcome is final (either SUCCESS or FAILED).
        COMMITTED
    }

    public OperationRecord committed(String finalResult) {
        return new OperationRecord(operationId, seatId, userName, State.COMMITTED, finalResult, executedBy);
    }
}
