# Experiment 7: Load Balancing and Fault Tolerance in Distributed Systems

## Objective
To implement a Load Balancer for the CineBook distributed movie-ticket booking system that distributes client requests across multiple backend servers using **Round Robin** and **Weighted Round Robin** algorithms, while incorporating **active health monitoring and fault handling** to detect server failures and automatically redirect requests to healthy servers.

---

## Architectural Overview

```
                      [ Incoming Client Booking Requests ]
                                       │
                                       ▼
                        ┌──────────────────────────────┐
                        │   CineBook Load Balancer     │
                        │ ──────────────────────────── │
                        │  Algorithms:                 │
                        │   • Round Robin              │
                        │   • Weighted Round Robin     │
                        │  Health Monitor & Failover   │
                        └──────────────┬───────────────┘
                                       │
                ┌──────────────────────┼──────────────────────┐
                ▼                      ▼                      ▼
      ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
      │     Server 1     │   │     Server 2     │   │     Server 3     │
      │  Primary Cluster │   │   Compute Node   │   │    Edge Node     │
      │   (Weight: 3)    │   │   (Weight: 2)    │   │   (Weight: 1)    │
      └──────────────────┘   └──────────────────┘   └──────────────────┘
```

---

## Key Components & Algorithms

### 1. Round Robin (RR)
- Requests are dispatched sequentially across the pool of available healthy servers in rotation ($S_1 \to S_2 \to S_3 \to S_1 \dots$).
- Provides uniform distribution when backend servers have identical processing capacities.

### 2. Weighted Round Robin (WRR)
- Allocates incoming traffic proportionally based on assigned server capacity weights ($w_1 : w_2 : w_3 = 3 : 2 : 1$).
- Implemented using the **Smooth Weighted Round Robin** (Nginx) algorithm:
  - Avoids burst clustering ($S_1, S_1, S_1, S_2, S_2, S_3$).
  - Produces evenly interleaved distributions ($S_1, S_2, S_1, S_3, S_1, S_2$).
  - Strictly guarantees that over each scheduling cycle of $W = \sum w_i$ requests, each server $S_i$ receives exactly $w_i$ requests.

### 3. Health Monitoring & Dynamic Fault Tolerance
- **Health Checks (`ping`)**: Continually checks node responsiveness. Unhealthy nodes are excluded from request distribution.
- **Automatic Failover Redirection**: If a server crashes mid-flight or right before request execution, the load balancer intercepts the error, flags the node as unhealthy, and redirects the request to a healthy backup server without dropping client requests.
- **Dynamic Node Recovery**: When an offline or crashed server becomes healthy again, the health monitor re-integrates it back into the active scheduling rotation.

---

## Source Files

- `BackendServerNode.java` — Server entity tracking health, weight, active connections, and request handling.
- `LoadBalancingAlgorithm.java` — Enum defining `ROUND_ROBIN` and `WEIGHTED_ROUND_ROBIN`.
- `LoadBalancer.java` — The core load balancing engine with health check, selection algorithms, failover redirection, and batch simulation.
- `LoadBalancerDemo.java` — Standalone Java test suite illustrating the 5 key scenarios.
- `Exp7Log.java` — Timestamped logger for distributed load balancing events.

---

## How to Run Standalone Terminal Demo

From this folder:
```bash
javac *.java
java LoadBalancerDemo
```

---

## Web UI & REST API Integration

Experiment 7 is integrated into the CineBook Java Bridge (`:8080`) and accessible via the **CineBook Systems Lab** workbench at:
- **UI Workbench**: `http://localhost:3000/lab?tab=loadbalancer` (or `http://localhost:8080/lab?tab=loadbalancer`)
- **Direct Route**: `http://localhost:3000/exp7`
- **REST Endpoints**:
  - `GET  /api/exp7/status`
  - `POST /api/exp7/config`
  - `POST /api/exp7/dispatch`
  - `POST /api/exp7/batch`
  - `POST /api/exp7/health`
  - `POST /api/exp7/reset`
  - `GET  /api/exp7/logs`
