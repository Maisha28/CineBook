// What a server is doing right now (as opposed to which server it is).
public enum Role {
    // Idle replica: applies PREPARE/COMMIT from the primary, serves no bookings.
    STANDBY,
    // Just restarted: catching up from the active peer, not yet serving.
    RECOVERING,
    // The one server that accepts booking requests.
    ACTIVE_PRIMARY
}
