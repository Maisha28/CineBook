/**
 * Supported load balancing algorithms for CineBook Experiment 7.
 */
public enum LoadBalancingAlgorithm {
    ROUND_ROBIN("Round Robin", "Sequentially routes incoming requests across all healthy servers in rotation."),
    WEIGHTED_ROUND_ROBIN("Weighted Round Robin", "Distributes requests proportionally according to the processing capacity (weight) of each server.");

    private final String displayName;
    private final String description;

    LoadBalancingAlgorithm(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
