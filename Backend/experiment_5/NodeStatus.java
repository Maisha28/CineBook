import java.io.Serializable;

// Reply to a heartbeat: who answered, what role it is playing, and how many
// committed operations its replica log holds.
public record NodeStatus(String node, Role role, int committedOps) implements Serializable {
}
