import java.io.Serializable;

public class TransactionRecord implements Serializable {
    public enum State { PREPARING, PREPARED, COMMITTED, ABORTED }

    private final String transactionId;
    private final String seatId;
    private final String userName;
    private final double amount;
    private State state;
    private String detail;
    private final long timestamp;

    public TransactionRecord(String transactionId, String seatId, String userName, double amount, State state, String detail) {
        this.transactionId = transactionId;
        this.seatId = seatId;
        this.userName = userName;
        this.amount = amount;
        this.state = state;
        this.detail = detail;
        this.timestamp = System.currentTimeMillis();
    }

    public String transactionId() { return transactionId; }
    public String seatId() { return seatId; }
    public String userName() { return userName; }
    public double amount() { return amount; }
    public State state() { return state; }
    public String detail() { return detail; }
    public long timestamp() { return timestamp; }

    public void setState(State state) { this.state = state; }
    public void setDetail(String detail) { this.detail = detail; }
}
