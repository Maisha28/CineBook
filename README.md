# CineBook

A distributed movie-ticket-booking system used as the running example across
a series of distributed computing lab experiments.

## Experiments
- [experiment_1](experiment_1) -- RMI / RPC basics
- [experiment_2](experiment_2) -- multithreading: race conditions and `synchronized`
- [experiment_3](experiment_3) -- clock synchronization: Lamport logical clocks and Berkeley's algorithm
- [experiment_4](experiment_4) -- Bully algorithm: leader election among peer client processes (no server node)

Each experiment folder is self-contained and runnable on its own; see its
README for build/run instructions. All three share the JDBC driver in
[lib](lib).
