# Experiment 4: Bully Algorithm (Leader Election)

Five peer client processes, P1..P5, elect a coordinator among themselves --
there is no separate server node. Every process runs its own RMI registry
and can call any other process directly; whichever process ends up
"leader" is just the one the others agreed to route requests to.

## Files
- `Process.java` -- RMI interface every peer exposes to the others:
  `election`, `announceCoordinator`, `ping`
- `ProcessNode.java` -- the Bully algorithm itself, shared by both entry
  points below
- `ProcessMain.java` -- run one process per terminal (true distributed demo)
- `ElectionDemo.java` -- runs all 5 as threads in one JVM (still real,
  separate RMI ports) and scripts the assignment's scenario end to end,
  printing one combined log

## The algorithm (as implemented)
Membership is the fixed set {1,2,3,4,5}; the system starts with P5 (the
highest ID) as the assumed leader.

- `startElection()`: the initiator calls `election(myId)` on every peer
  with a **higher** ID only.
  - If **none respond**, it declares itself coordinator and calls
    `announceCoordinator()` on everyone else. No election exchange with
    lower peers happens -- this is the edge case below.
  - If **any respond**, the initiator steps back and waits for a
    `announceCoordinator()` message.
- `election(candidateId)` (received from a lower peer): reply `true`
  ("I outrank you"), then start its own election in the background --
  this is what makes the highest surviving process win even if it wasn't
  the one that first noticed the crash.
- `announceCoordinator(id)`: adopt `id` as the new leader.

## Run: separate processes (multi-terminal)
This experiment has no database dependency, so no classpath jar is needed --
just compile and run directly. From this folder, one terminal per process:
```
javac *.java
java ProcessMain 1
java ProcessMain 2
java ProcessMain 3
java ProcessMain 4
java ProcessMain 5
```
Each console has a small menu: send a booking request to the current
leader, start an election manually, check status, or crash (terminate)
that process. To reproduce the assignment's example: crash P5 from its
terminal, then from P2's terminal send a booking request -- P2 will find
P5 unreachable and start the election.

## Run: single-JVM demo (one combined log)
```
javac *.java
java ElectionDemo
```
This runs two independent 5-node clusters back to back:

**Scenario 1** -- P5 crashes; P2 sends a booking request, finds the leader
unreachable, and starts an election. P3 and P4 (the higher IDs) both
reply OK, so P2 steps back. P3, having been challenged, starts its own
election against P4 and also steps back. P4, finding its own higher peer
(P5) unreachable, becomes coordinator and broadcasts to everyone --
matching the assignment's example (P2 initiates, P3 and P4 both respond,
P4 wins).

**Scenario 2 (edge case)** -- P4 itself is the one to notice P5 is down
(e.g. it sends a request first). Since P4's only higher peer is P5, it
gets zero responses and becomes coordinator immediately -- no election
exchange with P1/P2/P3 happens at all.

Sample output (see the demo's own printed summary for the authoritative
run): P2/initiator, P3+P4/responded, P4/leader for scenario 1; P4 only,
self-elected, for scenario 2.
