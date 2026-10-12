# Experiment 9: MPI Collective Communication (Broadcast, Scatter, Gather)

## Objective
To implement and demonstrate **MPI-style collective communication operations** — **broadcast**, **scatter**, and **gather** (plus a **reduce** extension) — for efficient data sharing among processes in a distributed environment, modeled on the CineBook Booking Cluster.

---

## Architectural Overview

```
                         [ HQ Coordinator — Rank 0 ]
                                    │
            ┌───────────── MPI_Bcast (config / promo) ─────────────┐
            │                       │                               │
            ▼                       ▼                               ▼
    ┌───────────────┐      ┌───────────────┐              ┌───────────────┐
    │  Rank 1        │      │  Rank 2        │     ...      │  Rank N-1      │
    │  Mumbai Branch │      │  Delhi Branch  │              │  Branch Node   │
    └───────────────┘      └───────────────┘              └───────────────┘

            ┌───────── MPI_Scatter (booking workload[]) ───────────┐
            │  values[0]→Rank0   values[1]→Rank1   ...  values[N-1]→RankN-1 │
            └───────────────────────────────────────────────────────┘

            ┌───────── MPI_Gather / MPI_Reduce(SUM) ────────────────┐
            │  Rank r computes: computed = scatterValue[r] + r       │
            │  All ranks  ──────────────▶  Rank 0 (aggregated)       │
            └───────────────────────────────────────────────────────┘
```

CineBook's booking cluster is modeled as an MPI communicator (`COMM_WORLD`): **rank 0** is the **HQ Coordinator**, and **ranks 1..N-1** are regional **cinema branch nodes**. Each rank runs on its own Java thread, so every collective call genuinely executes concurrently across ranks rather than in a sequential loop — mirroring how real MPI processes operate as independent OS processes.

### Why MPI Collective Communication?
- Reduces communication overhead versus point-to-point messaging between every pair of nodes.
- Improves scalability and performance of distributed programs.
- Provides high-level APIs for common data exchange patterns (fan-out, split, fan-in).

---

## Key Components & Operations

| Operation | Description | CineBook Mapping |
|---|---|---|
| **Broadcast** (`MPI_Bcast`) | Sends the same data from the root process to all others. | HQ pushes today's promo code / movie lineup config to every branch. |
| **Scatter** (`MPI_Scatter`) | Distributes different parts of an array from root to all processes (one chunk per rank). | HQ splits a batch of ticket-booking workload across branches. |
| **Gather** (`MPI_Gather`) | Collects data from all processes back to the root. | Each branch reports its processed booking count back to HQ. |
| **Reduce(SUM)** (`MPI_Reduce`) | Aggregates values from all ranks directly at the root (extension, no intermediate array). | HQ computes the total bookings processed cluster-wide in one step. |

Each worker rank's local computation mirrors the mpi4py reference program exactly:
```python
computed_value = received_value + rank
gathered_data = comm.gather(computed_value, root=0)
```

---

## Source Files

- `MpiProcess.java` — A single rank's local state: received broadcast data, scattered chunk, computed value, alive/crashed status.
- `MpiCommunicator.java` — The `COMM_WORLD` engine: `broadcast()`, `scatter()`, `gather()`, `reduceSum()`, fault injection (`crash`/`recover`), and in-memory event log. Every collective spins up one thread per rank.
- `CollectiveCommDemo.java` — Standalone Java test program illustrating all 5 scenarios (Bcast, Scatter, Gather, Reduce, fault tolerance).
- `Exp9Log.java` — Timestamped logger for collective communication events.

---

## How to Run Standalone Terminal Demo

From this folder:
```bash
javac *.java
java CollectiveCommDemo
```

### Reference: Equivalent real MPI program (Python / mpi4py)
The classic lab reference this experiment is modeled on:
```bash
pip install mpi4py
mpirun -n 4 python mpi_collective.py
```
```python
from mpi4py import MPI
comm = MPI.COMM_WORLD
rank = comm.Get_rank()
size = comm.Get_size()

# ---------- BROADCAST ----------
data = "Hello MPI Processes!" if rank == 0 else None
data = comm.bcast(data, root=0)

# ---------- SCATTER ----------
scatter_data = [i * 10 for i in range(size)] if rank == 0 else None
received_value = comm.scatter(scatter_data, root=0)

# ---------- GATHER ----------
computed_value = received_value + rank
gathered_data = comm.gather(computed_value, root=0)
```
`MpiCommunicator.java` reproduces this exact broadcast → scatter → gather (+ reduce) pipeline in Java so it can run without an MPI runtime and be driven live from the CineBook web UI.

---

## Web UI & REST API Integration

Experiment 9 is integrated into the CineBook Java Bridge (`:8080`) and accessible via the **CineBook Systems Lab** workbench at:
- **UI Workbench**: `http://localhost:3000/lab?tab=mpi` (or `http://localhost:8080/lab?tab=mpi`)
- **Direct Route**: `http://localhost:3000/exp9`
- **REST Endpoints**:
  - `GET  /api/exp9/status`
  - `POST /api/exp9/start`      — rebuild COMM_WORLD with a chosen rank count
  - `POST /api/exp9/broadcast`  — MPI_Bcast a message from root to all ranks
  - `POST /api/exp9/scatter`    — MPI_Scatter an array (1 value per rank)
  - `POST /api/exp9/gather`     — MPI_Gather computed values back to root
  - `POST /api/exp9/reduce`     — MPI_Reduce(SUM) aggregate at root
  - `POST /api/exp9/runAll`     — runs the full Bcast → Scatter → Gather → Reduce pipeline
  - `POST /api/exp9/fault`      — crash/recover a worker rank (fault-tolerance demo)
  - `POST /api/exp9/reset`
  - `GET  /api/exp9/logs`

---

## Problem Statements (Extensions Implemented / Supported)

1. **Reduce instead of Gather** — `MpiCommunicator.reduceSum()` computes the cluster-wide total directly via `MPI_Reduce(SUM)` without materializing the full gathered array.
2. **Fault tolerance** — `crash(rank)` / `recover(rank)` let you take a branch node offline and observe how broadcast/scatter/gather skip it, then reintegrate it.
3. **Broadcast → per-rank compute → Gather** — the square/compute-then-gather pattern is directly exercised by the `compute()` step (`scatterValue + rank`) feeding into both `gather()` and `reduceSum()`.
4. **Point-to-point vs collective** — contrast this experiment's fan-out/fan-in collectives with Experiment 7's point-to-point load-balanced dispatch to compare overhead and code complexity.
