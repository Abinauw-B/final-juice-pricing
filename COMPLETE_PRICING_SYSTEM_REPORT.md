# COMPLETE PRICING SYSTEM REPORT
**Project:** Mojito Exchange (Dynamic Beverage Stock Market System)  
**Workspace:** `d:\Juice Dynamic Price Project`  
**Generated At:** 2026-09-18T09:50:00+05:30  
**Status:** Production Codebase Audit & Architectural Deep-Dive  
**Target File:** `COMPLETE_PRICING_SYSTEM_REPORT.md` (Project Root)

---

## TABLE OF CONTENTS
1. [Project Pricing Overview](#1-project-pricing-overview)
2. [Complete Pricing Architecture](#2-complete-pricing-architecture)
3. [All Pricing-Related Files](#3-all-pricing-related-files)
4. [Product Pricing Configuration](#4-product-pricing-configuration)
5. [Current Price Source of Truth](#5-current-price-source-of-truth)
6. [Pricing Formula](#6-pricing-formula)
7. [Demand Calculation](#7-demand-calculation)
8. [Demand Normalization](#8-demand-normalization)
9. [Pricing Interval](#9-pricing-interval)
10. [Pricing Cycle](#10-pricing-cycle)
11. [Price Movement Rules](#11-price-movement-rules)
12. [Minimum / Base / Maximum](#12-minimum--base--maximum)
13. [No-Buy Behavior](#13-no-buy-behavior)
14. [Multi-Product Pricing](#14-multi-product-pricing)
15. [Demand Windows](#15-demand-windows)
16. [Database Tables](#16-database-tables)
17. [Price History](#17-price-history)
18. [Admin Pricing Changes](#18-admin-pricing-changes)
19. [Changing Pricing Interval](#19-changing-pricing-interval)
20. [Audit Trail](#20-audit-trail)
21. [Manual Price Override](#21-manual-price-override)
22. [Market Crash](#22-market-crash)
23. [Simulator](#23-simulator)
24. [POS Server-Side Price Authority](#24-pos-server-side-price-authority)
25. [Concurrent Purchases](#25-concurrent-purchases)
26. [Redis](#26-redis)
27. [WebSocket / STOMP](#27-websocket--stomp)
28. [Frontend Price Flow](#28-frontend-price-flow)
29. [Scheduler and Loop Audit](#29-scheduler-and-loop-audit)
30. [Configuration Versioning](#30-configuration-versioning)
31. [Cycle ID / Idempotency](#31-cycle-id--idempotency)
32. [Money Handling](#32-money-handling)
33. [Admin Validation](#33-admin-validation)
34. [Real-World Test Cases](#34-real-world-test-cases)
35. [10-Cycle Simulation Analysis](#35-10-cycle-simulation-analysis)
36. [Long-Run Analysis](#36-long-run-analysis)
37. [Hardcoded Values](#37-hardcoded-values)
38. [Intended vs Actual](#38-intended-vs-actual)
39. [Bugs and Risks](#39-bugs-and-risks)
40. [Complete Real-World Example](#40-complete-real-world-example)
41. [Final Simple Explanation](#41-final-simple-explanation)
42. [Final Pricing Flow Diagram](#42-final-pricing-flow-diagram)
43. [Final Status](#43-final-status)

---

## 1. PROJECT PRICING OVERVIEW

### Simple Explanation (Layman / Fresher Level)
The **Mojito Exchange** dynamic pricing system turns a juice bar into a live stock exchange. When customers buy lots of a specific juice (e.g., Fresh Mango Juice), its price ticks up by ₹1.00 because demand is surging. When nobody buys a juice for an extended period, its price ticks down by ₹1.00 to encourage sales. The price can never go higher than a set maximum ceiling (₹30.00) or lower than a set floor limit (₹20.00). During a "Market Crash" emergency event, all prices instantly plunge to the absolute minimum of ₹20.00 for exactly 3 minutes (180 seconds), siren sounds play in the venue, and prices freeze before restoring to their pre-crash levels. All live prices are broadcast across venue LED boards, POS registers, and the Admin Panel in real time.

### Technical Explanation
The pricing engine is a high-throughput, event-driven, time-series reactive system built on **Spring Boot 3.2.0 (Java 17)**, **PostgreSQL 16**, **Redis 7.2**, and **STOMP WebSockets over SockJS**. 
The system runs an autonomous settlement engine via `PricingSettlementCoordinator` driven by Spring's `ThreadPoolTaskScheduler`. Every settlement cycle (default: 60s, configurable down to 10s), the system queries committed sales across three backward-looking non-overlapping contiguous time slices ($W_0, W_1, W_2$). It computes a **Discrete Weighted Moving Average (DWMA)** of beverage cup sales, scales the beverage's configured target demand to the current cycle interval, applies **Bayesian Laplace smoothing** ($K=1.5$), evaluates the resulting demand ratio against asymmetric surge ($R_d \ge 1.10$) and decay ($R_d < 0.90$) thresholds, and calculates an incremental step movement ($\Delta P \in \{+1.00, 0.00, -1.00\}$). 

The transaction updates the authoritative PostgreSQL relational model under `SERIALIZABLE` or `READ_COMMITTED` with pessimistic row locking (`SELECT FOR UPDATE`), appends an immutable record to `price_history` (only if $\Delta P \ne 0$), creates an immutable audit row in `pricing_audit_log`, commits to PostgreSQL, refreshes read-through Redis caches, and dispatches JSON broadcast payloads to `/topic/price-updates` and `/topic/led-display`.

---

## 2. COMPLETE PRICING ARCHITECTURE

```text
=============================================================================================================
                                     MOJITO EXCHANGE PRICING ARCHITECTURE
=============================================================================================================

  +-------------------------------------------------------------------------------------------------------+
  |                                            CLIENT LAYER                                               |
  +-----------------------------------+-----------------------------------+-------------------------------+
  |        CUSTOMER POS (8000)        |         ADMIN PANEL (8001)        |       LED DISPLAY (8000)      |
  |  - Displays live catalog prices   |  - Real-time engine telemetry     |  - High-impact stadium board  |
  |  - Requests price lock token (10s)|  - Global & per-juice config      |  - 4x2 juice card matrix      |
  |  - Dispatches checkout orders     |  - Market crash trigger & audit   |  - Live audio siren & alerts  |
  +-----------------+-----------------+-----------------+-----------------+---------------+---------------+
                    |                                   |                                 ^
       HTTP Checkout| REST               HTTP Config &  | REST                 STOMP / WS | Live Broadcast
       & Lock Tokens|                    Manual Override|                      Subscribers|
                    v                                   v                                 |
  +---------------------------------------------------------------------------------------+---------------+
  |                                     SPRING BOOT BACKEND (PORT 8088)                                   |
  |                                                                                                       |
  |   [POSService / PriceLockService]               [PricingConfigurationService]                         |
  |    - Validates server price                      - Dynamic runtime parameters                         |
  |    - Deducts batch inventory                     - Monotonic version increment                        |
  |    - Writes orders & order_items                 - Thread-safe volatile state                         |
  |                                                                                                       |
  |   [LiveMarketSimulatorService]                  [MarketCrashService]                                  |
  |    - Automated virtual purchasing                - 180s absolute floor override                       |
  |    - Disabled by default                         - In-memory pre-crash price snapshot                 |
  |                                                                                                       |
  |                       +-----------------------------------------------+                               |
  |                       |         PRICING SETTLEMENT ENGINE             |                               |
  |                       |        (PricingSettlementCoordinator)         |                               |
  |                       +-----------------------+-----------------------+                               |
  |                                               |                                                       |
  |                                               v                                                       |
  |                                 [PriceAdjustmentService]                                              |
  |                                  - Time-slice aggregation (W0, W1, W2)|                               |
  |                                  - DWMA Demand: Sw = (1.0*W0+0.5*W1+0.25*W2)/1.75                     |
  |                                  - Time normalization: Tnorm = T1min * (Dt / 60)                     |
  |                                  - Bayesian Ratio: Rd = (Sw + 1.5*Tnorm) / (2.5*Tnorm)                |
  |                                  - Clamped Step Movement: +1.00 / 0.00 / -1.00                        |
  |                                  - Boundary enforcement [minCupPrice, maxCupPrice]                    |
  |                                               |                                                       |
  |                         +---------------------+---------------------+                                 |
  |                         |                                           |                                 |
  |                         v Transaction Commit                        v Cache Evict/Put                 |
  |               +--------------------+                      +-------------------+                       |
  |               |   POSTGRESQL 16    |                      |     REDIS 7.2     |                       |
  |               | (AUTHORITATIVE DB) |                      |   (READ CACHE)    |                       |
  |               |  - products        |                      |  - juice:prices   |                       |
  |               |  - orders          |                      |  - market:crash   |                       |
  |               |  - price_history   |                      +-------------------+                       |
  |               |  - audit_log       |                                                                  |
  |               +---------+----------+                                                                  |
  |                         |                                                                             |
  |                         v Post-Commit Event Dispatched                                                |
  |               +-------------------------------------------------------------+                         |
  |               |                  SimpMessagingTemplate                      |                         |
  |               |   Topics: /topic/price-updates, /topic/led-display,         |                         |
  |               |           /topic/market-crash, /topic/settlement-heartbeat  |                         |
  +---------------+-------------------------------------------------------------+-------------------------+
```

---

## 3. ALL PRICING-RELATED FILES

| File Path | Primary Class / Component | Key Methods | Architectural Purpose |
| :--- | :--- | :--- | :--- |
| `backend/.../service/PricingSettlementCoordinator.java` | `PricingSettlementCoordinator` | `runSettlementCycle()`, `recalculateAllPrices()`, `publishPriceUpdates()` | Central pricing orchestrator; triggers DWMA cycles and schedules ticks. |
| `backend/.../service/PriceAdjustmentService.java` | `PriceAdjustmentService` | `calculateAdjustedPrice()`, `calculateDemandRatio()`, `calculateDWMA()` | Core mathematical calculation engine; implements Bayesian smoothing and clamping. |
| `backend/.../service/PricingConfigurationService.java` | `PricingConfigurationService` | `getEffectiveGlobalConfig()`, `updateGlobalConfig()`, `updateProductConfig()` | In-memory and DB configuration manager; manages versioning and thresholds. |
| `backend/.../service/MarketCrashService.java` | `MarketCrashService` | `triggerMarketCrash()`, `stopMarketCrash()`, `getCrashStatus()` | Manages 180s market crash override, snapshotting, and restoration. |
| `backend/.../service/POSService.java` | `POSService` | `processCheckout()`, `validateServerPrice()`, `recordSale()` | Point-of-sale checkout processor; server-side price authority enforcer. |
| `backend/.../service/PriceLockService.java` | `PriceLockService` | `acquireLockToken()`, `validateLockToken()`, `releaseLockToken()` | 10-second price freeze token issuer for frontend cart stability. |
| `backend/.../config/DynamicPricingSchedulerConfig.java` | `DynamicPricingSchedulerConfig` | `reschedulePricingTask()`, `scheduleNextSettlement()` | Dynamic cron/fixed-delay scheduler using `ThreadPoolTaskScheduler`. |
| `backend/.../service/LiveMarketSimulatorService.java` | `LiveMarketSimulatorService` | `simulateRandomSale()`, `simulateSurge()`, `runSimulationTick()` | Synthetic load generator simulating customer checkout volume. |
| `backend/.../repository/PricingRedisRepository.java` | `PricingRedisRepository` | `cacheMarketPrices()`, `getCachedPrices()`, `setCrashState()` | Low-latency Redis cache abstraction for price lookups. |
| `backend/.../repository/PriceHistoryRepository.java` | `PriceHistoryRepository` | `save()`, `findTop10ByBeverageIdOrderByRecordedAtDesc()` | PostgreSQL repository storing append-only historical price movements. |
| `backend/.../repository/PricingAuditLogRepository.java` | `PricingAuditLogRepository` | `save()`, `findByEventTypeOrderByTimestampDesc()` | Immutable audit trail store for configuration changes and overrides. |
| `backend/.../repository/OrderItemRepository.java` | `OrderItemRepository` | `countCupsSoldInWindow()`, `countCompletedSalesExcludingCrash()` | Windowed demand query aggregator reading from committed customer sales. |
| `customer-web/src/index.html` | Client POS Application | `checkout()`, `fetchMarketPrices()`, `subscribeWebSocket()` | Customer ordering interface with STOMP client and server token handling. |
| `customer-web/src/led-display.html` | Stadium LED Display | `renderDashboard()`, `renderCrashJuiceMatrix()`, `triggerMarketCrash()` | Venue ticker and emergency billboard with unclipped matrix and siren. |
| `admin-panel/src/index.html` | Administrative Cockpit | `savePricingConfig()`, `triggerCrash()`, `loadAuditTrail()` | Management console for setting intervals, targets, and manual overrides. |

---

## 4. PRODUCT PRICING CONFIGURATION

The database contains **8 canonical commercial beverages**. All 8 beverages share a uniform price band (Base: ₹25.00, Floor: ₹20.00, Ceiling: ₹30.00) with tailored 1-minute target demand figures:

| Beverage ID | Product Name | Canonical Flavour Key | Current DB Price | Base Price | Floor Price (Min) | Ceiling Price (Max) | 1-Min Demand Target | Active Interval |
| :---: | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **1** | Fresh Mango Juice | `FRESH_MANGO_JUICE` | **₹25.00** | ₹25.00 | ₹20.00 | ₹30.00 | 0.50 cups/min | 60 sec (global) |
| **2** | Zesty Lemon Juice | `ZESTY_LEMON_JUICE` | **₹25.00** | ₹25.00 | ₹20.00 | ₹30.00 | 0.55 cups/min | 60 sec (global) |
| **3** | Cool Mint Cooler | `COOL_MINT_COOLER` | **₹25.00** | ₹25.00 | ₹20.00 | ₹30.00 | 0.40 cups/min | 60 sec (global) |
| **4** | Valencia Orange Juice | `VALENCIA_ORANGE_JUICE` | **₹25.00** | ₹25.00 | ₹20.00 | ₹30.00 | 0.55 cups/min | 60 sec (global) |
| **5** | Strawberry Delight | `STRAWBERRY_DELIGHT` | **₹25.00** | ₹25.00 | ₹20.00 | ₹30.00 | 0.55 cups/min | 60 sec (global) |
| **6** | Royal Grape Juice | `ROYAL_GRAPE_JUICE` | **₹25.00** | ₹25.00 | ₹20.00 | ₹30.00 | 0.55 cups/min | 60 sec (global) |
| **7** | Lychee Mist | `LYCHEE_MIST` | **₹25.00** | ₹25.00 | ₹20.00 | ₹30.00 | 0.55 cups/min | 60 sec (global) |
| **23** | Thunder Power | `THUNDER_POWER` | **₹25.00** | ₹25.00 | ₹20.00 | ₹30.00 | 0.45 cups/min | 60 sec (global) |

*Data source: Verified live from PostgreSQL 16 `products` table, Flyway baseline migrations `V1__init_schema.sql` through `V41__add_price_version_column.sql`, and confirmed via backend `/api/pricing/config`.*

---

## 5. CURRENT PRICE SOURCE OF TRUTH

```text
===================================================================================
CURRENT PRICE SOURCE OF TRUTH = PostgreSQL 16 (products.current_cup_price)
===================================================================================
```

### Detailed Verification
1. **Is PostgreSQL Authoritative?**  
   **YES.** The `products` table in PostgreSQL is the sole system-of-record. Every transactional checkout in `POSService.java` reads `products.current_cup_price` directly with row-level locks.
2. **Is Redis Authoritative?**  
   **NO.** Redis is strictly a non-authoritative read-through caching layer with short TTLs (default 30–60s) or event-invalidated keys (`juice:prices`). If Redis contains stale or corrupt data, the engine ignores it upon database commit.
3. **Can Frontend State Modify the Price?**  
   **NO.** Client requests sending a `lockedPrice` or custom `unitPrice` are completely discarded during checkout validation.
4. **Does Backend Ignore Client-Supplied `lockedPrice`?**  
   **YES.** In `POSService.validateAndResolvePrice()`, if the client supplies a `lockedPrice`, the backend checks for an active, unexpired `priceLockToken` in the `price_locks` table. If the token is invalid, missing, or expired (10s TTL), the backend silently overrides the price with PostgreSQL's `products.current_cup_price`.
5. **What Happens After Backend Restart?**  
   Upon reboot, Spring Boot reads the persistent state directly from PostgreSQL. The in-memory DWMA ring buffers re-initialize by running backward SQL window queries against `order_items`. Prices remain exactly as committed before shutdown.
6. **What Happens After Redis Restart?**  
   Redis cache misses immediately trigger a fallback query to PostgreSQL, repopulating the Redis cache seamlessly.
7. **What Happens After PostgreSQL Restart?**  
   Transactions fail fast with connection pool exceptions (`HikariCP`); no pricing updates or sales can commit until PostgreSQL is restored. Stored prices undergo zero data loss.

---

## 6. PRICING FORMULA

The dynamic pricing engine uses a **Bayesian-smoothed Discrete Weighted Moving Average (DWMA)** model.

### Mathematical Formulation

#### 1. Discrete Time-Window Sales Extraction
For a settlement cycle executing at timestamp $t$ with interval duration $\Delta t$:
$$w_0 = \sum \text{cups sold in } [t - \Delta t, t)$$
$$w_1 = \sum \text{cups sold in } [t - 2\Delta t, t - \Delta t)$$
$$w_2 = \sum \text{cups sold in } [t - 3\Delta t, t - 2\Delta t)$$
*(Sales marked with `is_crash_sale = true` are excluded from all windows).*

#### 2. Weighted Moving Demand ($S_w$)
$$S_w = \frac{W_0 \cdot w_0 + W_1 \cdot w_1 + W_2 \cdot w_2}{W_0 + W_1 + W_2}$$
With standard configured weights $W_0 = 1.00, W_1 = 0.50, W_2 = 0.25$:
$$S_w = \frac{1.00 \cdot w_0 + 0.50 \cdot w_1 + 0.25 \cdot w_2}{1.75}$$

#### 3. Normalized Interval Target Demand ($T_{\text{norm}}$)
$$T_{\text{norm}} = T_{\text{1min}} \times \left(\frac{\Delta t}{60}\right)$$

#### 4. Bayesian Smoothed Demand Ratio ($R_d$)
To prevent division by zero or extreme wild swings on fractional counts, Bayesian Laplace smoothing is applied with prior confidence factor $K = 1.5$:
$$R_d = \frac{S_w + K \cdot T_{\text{norm}}}{T_{\text{norm}} + K \cdot T_{\text{norm}}} = \frac{S_w + 1.5 \cdot T_{\text{norm}}}{2.5 \cdot T_{\text{norm}}}$$
*When zero sales occur ($S_w = 0$):*
$$R_d = \frac{1.5 \cdot T_{\text{norm}}}{2.5 \cdot T_{\text{norm}}} = \frac{1.5}{2.5} \equiv 0.6000$$

#### 5. Step Price Adjustment ($\Delta P$)
$$\Delta P = \begin{cases} 
+1.00 & \text{if } R_d \ge 1.10 \text{ (High Demand / Surge)} \\
0.00 & \text{if } 0.90 \le R_d < 1.10 \text{ (Balanced / Stable)} \\
-1.00 & \text{if } R_d < 0.90 \text{ (Low Demand / Decay, subject to cooldown)}
\end{cases}$$

#### 6. Boundary Clamping
$$P_{\text{new}} = \min\Big(\max\big(P_{\text{current}} + \Delta P,\; P_{\text{min}}\big),\; P_{\text{max}}\Big)$$

---

### Concrete Real-World Numerical Example (Fresh Mango Juice)

```text
Current Price:       P_current = ₹25.00
Floor / Ceiling:     P_min = ₹20.00, P_max = ₹30.00
Settlement Interval: Dt = 60 seconds
Target Demand:       T_1min = 0.50 cups/min => T_norm = 0.50 * (60/60) = 0.50 cups

Window Sales:
  Current window (w0):   2 cups
  Previous window (w1):  1 cup
  Older window (w2):     0 cups

1. Calculate DWMA Demand (Sw):
   Sw = (1.00 * 2 + 0.50 * 1 + 0.25 * 0) / 1.75
   Sw = (2.00 + 0.50 + 0.00) / 1.75 = 2.50 / 1.75 = 1.4286 cups

2. Calculate Bayesian Demand Ratio (Rd):
   Rd = (1.4286 + 1.5 * 0.50) / (2.5 * 0.50)
   Rd = (1.4286 + 0.75) / 1.25 = 2.1786 / 1.25 = 1.7429

3. Decision Logic:
   Rd = 1.7429 >= 1.10 (High Demand Surge Threshold)
   Decision: SURGE (+₹1.00)

4. Clamped New Price:
   P_new = min(max(25.00 + 1.00, 20.00), 30.00) = ₹26.00

Movement: ₹25.00 → ₹26.00
```

---

## 7. DEMAND CALCULATION

### Flow from Purchase to Windowed Demand
1. Customer buys beverage at POS.
2. `POSService.processCheckout()` executes inside a Spring `@Transactional` boundary.
3. Inserts into `orders` (`status = 'COMPLETED'`, `order_timestamp = NOW()`).
4. Inserts into `order_items` (`beverage_id`, `quantity`, `price_at_order`, `is_crash_sale`).
5. Inventory deducted from `batches` table based on standard 250 ml recipe volume.
6. When `PricingSettlementCoordinator` triggers, `OrderItemRepository` aggregates sales:
   ```sql
   SELECT COALESCE(SUM(oi.quantity), 0)
   FROM order_items oi
   JOIN orders o ON oi.order_id = o.id
   WHERE oi.beverage_id = :beverageId
     AND o.status = 'COMPLETED'
     AND o.order_timestamp >= :startTime
     AND o.order_timestamp < :endTime
     AND (oi.is_crash_sale IS NULL OR oi.is_crash_sale = FALSE);
   ```
7. **Exclusions**: Cancelled (`CANCELLED`), refunded, failed, and crash-period orders (`is_crash_sale = TRUE`) are strictly excluded from demand counting.
8. **Double Counting Protection**: Boundaries use half-open intervals $[t_{\text{start}}, t_{\text{end}})$. Because the end of $W_0$ matches the start of $W_1$ in subsequent cycles, zero sales are counted twice or omitted.

---

## 8. DEMAND NORMALIZATION

### Verification Across Time Intervals
The normalization equation scales the 1-minute configured target to the cycle duration $\Delta t$:
$$T_{\text{norm}} = T_{\text{1min}} \times \left(\frac{\Delta t}{60}\right)$$

| Interval ($\Delta t$) | Time Factor ($\Delta t / 60$) | Base 1-Min Target | Normalized Target ($T_{\text{norm}}$) | $S_w = 0$ Smooth Ratio ($R_d$) | Expected Zero-Sales Movement |
| :---: | :---: | :---: | :---: | :---: | :---: |
| **10 sec** | $0.1667$ | $0.50$ | $0.0833$ | $0.6000$ | $-₹1.00$ (subject to 60s decay cooldown) |
| **15 sec** | $0.2500$ | $0.50$ | $0.1250$ | $0.6000$ | $-₹1.00$ (subject to 60s decay cooldown) |
| **30 sec** | $0.5000$ | $0.50$ | $0.2500$ | $0.6000$ | $-₹1.00$ (subject to 60s decay cooldown) |
| **1 min** | $1.0000$ | $0.50$ | $0.5000$ | $0.6000$ | $-₹1.00$ (decay after 60s) |
| **2 min** | $2.0000$ | $0.50$ | $1.0000$ | $0.6000$ | $-₹1.00$ (decay every cycle) |
| **5 min** | $5.0000$ | $0.50$ | $2.5000$ | $0.6000$ | $-₹1.00$ (decay every cycle) |
| **10 min** | $10.0000$ | $0.50$ | $5.0000$ | $0.6000$ | $-₹1.00$ (decay every cycle) |
| **15 min** | $15.0000$ | $0.50$ | $7.5000$ | $0.6000$ | $-₹1.00$ (decay every cycle) |
| **30 min** | $30.0000$ | $0.50$ | $15.0000$ | $0.6000$ | $-₹1.00$ (decay every cycle) |
| **60 min** | $60.0000$ | $0.50$ | $30.0000$ | $0.6000$ | $-₹1.00$ (decay every cycle) |

### Implementation Behavior & Discrepancies
* **Proportional Scaling**: Real-world demand produces mathematically consistent demand ratios across different intervals because Bayesian smoothing scales with $T_{\text{norm}}$.
* **Micro-Interval Cooldown Quirk**: In `PriceAdjustmentService.java`, downward price decay enforces `minDecayCooldownSeconds = Math.max(60, intervalSec)`. When the interval is set to 10 seconds, the price will **NOT** drop every 10 seconds; it will only drop once every 60 seconds.

---

## 9. PRICING INTERVAL

1. **Configuration Location**: Configured globally in database table `pricing_engine_config` (`settlement_interval_seconds = 60`) and managed via `PricingConfigurationService.java`.
2. **Dynamic Adaptation**: The scheduler does not require an application restart. When changed in Admin Panel, `PricingConfigurationService` updates `settlementIntervalSeconds` and invokes `DynamicPricingSchedulerConfig.reschedule()`.
3. **Running Cycle Impact**: The currently running cycle completes its sleep or timer tick normally. The new interval takes effect immediately for the next scheduled tick.
4. **Historical Windows**: Demand aggregation queries compute timestamps dynamically based on the current interval: $[t - \Delta t, t)$, $[t - 2\Delta t, t - \Delta t)$, $[t - 3\Delta t, t - 2\Delta t)$. No historical sales data is lost or altered.

---

## 10. PRICING CYCLE

```text
Cycle Start (Triggered by ThreadPoolTaskScheduler)
      │
      ▼
Check Market Crash Status (If crash active, skip DWMA recalculation)
      │
      ▼
Read Configuration (settlementIntervalSeconds, weights, thresholds)
      │
      ▼
Determine Time Windows: W0 [t-Dt, t), W1 [t-2Dt, t-Dt), W2 [t-3Dt, t-2Dt)
      │
      ▼
Query PostgreSQL: Aggregate completed cups per product across W0, W1, W2
      │
      ▼
Calculate Weighted Moving Demand: Sw = (1.0*W0 + 0.5*W1 + 0.25*W2) / 1.75
      │
      ▼
Calculate Normalized Target: Tnorm = T_1min * (Dt / 60)
      │
      ▼
Calculate Bayesian Demand Ratio: Rd = (Sw + 1.5*Tnorm) / (2.5*Tnorm)
      │
      ▼
Determine Step Direction: +1.00 (Rd >= 1.10), 0.00 (0.90 <= Rd < 1.10), -1.00 (Rd < 0.90)
      │
      ▼
Enforce Cooldown & Minimum/Maximum Clamp: [₹20.00, ₹30.00]
      │
      ▼
Begin Database Transaction (@Transactional)
      │
      ├─► Update `products.current_cup_price`
      ├─► Increment `products.price_version`
      ├─► If (OldPrice != NewPrice): Insert row into `price_history`
      └─► Insert audit entry into `pricing_audit_log`
      │
      ▼
Commit Transaction to PostgreSQL
      │
      ▼
Invalidate / Update Redis Cache (`juice:prices`)
      │
      ▼
Publish STOMP Payloads to `/topic/price-updates` and `/topic/led-display`
      │
      ▼
Frontend DOM Updates (POS, Admin, LED Board reflect new prices)
      │
      ▼
Cycle End (Schedule next execution at t + Dt)
```

---

## 11. PRICE MOVEMENT RULES

1. **Normal Settlement Movement**: Strictly constrained to step increments:
   $$\Delta P \in \{+₹1.00,\; ₹0.00,\; -₹1.00\}$$
2. **Verification of $\pm ₹2$ or $\pm ₹3$**:
   * Under standard automated pricing, no calculation path can jump by more than ₹1.00.
   * `increaseStep` is locked to `1.00`, and `decreaseStep1` is locked to `1.00`.
3. **Exceptions Leading to Multi-Rupee Changes**:
   * **Market Crash Trigger**: Prices jump directly from current price to floor (e.g., ₹28.00 $\to$ ₹20.00, a drop of -₹8.00).
   * **Market Crash Restore**: Prices jump directly from floor back to pre-crash snapshot (e.g., ₹20.00 $\to$ ₹28.00, an increase of +₹8.00).
   * **Manual Admin Override**: An admin explicitly entering a new price in Admin Panel can set any value between ₹20.00 and ₹30.00.

---

## 12. MINIMUM / BASE / MAXIMUM

* **Ceiling Saturation**: When a beverage reaches ₹30.00 (Maximum Limit), any further high demand calculates $\Delta P = +1.00$, but clamping forces:
  $$\min(30.00 + 1.00, 30.00) = ₹30.00$$
  The price stays pinned at ₹30.00. No history record is written because the price did not change.
* **Floor Saturation**: When a beverage reaches ₹20.00 (Minimum Limit), any continued lack of demand calculates $\Delta P = -1.00$, but clamping forces:
  $$\max(20.00 - 1.00, 20.00) = ₹20.00$$
  The price stays pinned at ₹20.00.
* **Prolonged Zero Demand at Floor**: The price does **NOT** reset to base price (₹25.00) automatically. It remains at ₹20.00 until renewed customer purchases pull the demand ratio above 1.10.

---

## 13. NO-BUY BEHAVIOR

| Inactivity Duration | Cycles Elapsed ($\Delta t = 60\text{s}$) | DWMA Sales ($S_w$) | Bayesian Ratio ($R_d$) | Cooldown Status | Effective Movement | Price (Starting at ₹25.00) |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **10 seconds** | $0.16$ | — | — | Active | ₹0.00 | ₹25.00 |
| **20 seconds** | $0.33$ | — | — | Active | ₹0.00 | ₹25.00 |
| **1 minute** | $1.0$ | $0.00$ | $0.6000$ | Cooldown Elapsed | $-₹1.00$ | ₹24.00 |
| **2 minutes** | $2.0$ | $0.00$ | $0.6000$ | Cooldown Elapsed | $-₹1.00$ | ₹23.00 |
| **3 minutes** | $3.0$ | $0.00$ | $0.6000$ | Cooldown Elapsed | $-₹1.00$ | ₹22.00 |
| **4 minutes** | $4.0$ | $0.00$ | $0.6000$ | Cooldown Elapsed | $-₹1.00$ | ₹21.00 |
| **5 minutes** | $5.0$ | $0.00$ | $0.6000$ | Cooldown Elapsed | $-₹1.00$ | ₹20.00 (Floor Reached) |
| **6+ minutes** | $6.0+$ | $0.00$ | $0.6000$ | Clamped | ₹0.00 | ₹20.00 (Pinned at Floor) |

---

## 14. MULTI-PRODUCT PRICING

The system operates on **Product-Specific Independent Pricing**:
1. **Scenario A (Only Mango Bought)**:
   * **Mango Price**: Surges up to ceiling (₹25 $\to$ ₹26 $\to$ ₹27 $\dots \to$ ₹30).
   * **All Other Products**: Experience zero demand decay, dropping by ₹1.00 per cooldown period until reaching the floor of ₹20.00.
2. **Scenario B (All Bought Except Mint Cooler)**:
   * **Bought Juices**: Prices rise or remain stable based on their individual sales vs targets.
   * **Mint Cooler**: Drops by ₹1.00 per minute until hitting ₹20.00.
3. **Scenario C (All Products Bought Heavily)**:
   * Every product independently calculates $R_d \ge 1.10$ and all increase by $+₹1.00$ up to the ₹30.00 maximum ceiling. There is no market-wide quota or zero-sum price cannibalization.

---

## 15. DEMAND WINDOWS

* **Window 0 ($W_0$)**: $[t - \Delta t, t)$ (Weight: 1.00)
* **Window 1 ($W_1$)**: $[t - 2\Delta t, t - \Delta t)$ (Weight: 0.50)
* **Window 2 ($W_2$)**: $[t - 3\Delta t, t - 2\Delta t)$ (Weight: 0.25)
* **Overlap**: None. Windows are mutually exclusive and collectively exhaustive over $[t - 3\Delta t, t)$.
* **Boundary Conditions**: Timestamp queries use `>= start AND < end`, preventing any sale from being counted twice across consecutive time slices.

---

## 16. DATABASE TABLES

```sql
-- 1. PRODUCTS TABLE (Source of Truth)
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    flavour VARCHAR(100) NOT NULL UNIQUE,
    current_cup_price NUMERIC(10,2) NOT NULL DEFAULT 25.00,
    default_cup_price NUMERIC(10,2) NOT NULL DEFAULT 25.00,
    min_cup_price NUMERIC(10,2) NOT NULL DEFAULT 20.00,
    max_cup_price NUMERIC(10,2) NOT NULL DEFAULT 30.00,
    target_sales_per_1minute NUMERIC(10,2) NOT NULL DEFAULT 0.50,
    price_version BIGINT DEFAULT 1
);

-- 2. ORDERS TABLE
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(64) NOT NULL UNIQUE,
    order_timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    total_amount NUMERIC(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED'
);

-- 3. ORDER_ITEMS TABLE
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT REFERENCES orders(id),
    beverage_id BIGINT REFERENCES products(id),
    quantity INTEGER NOT NULL,
    price_at_order NUMERIC(10,2) NOT NULL,
    is_crash_sale BOOLEAN DEFAULT FALSE
);

-- 4. PRICE_HISTORY TABLE (Append-Only)
CREATE TABLE price_history (
    id BIGSERIAL PRIMARY KEY,
    beverage_id BIGINT REFERENCES products(id),
    old_price NUMERIC(10,2) NOT NULL,
    new_price NUMERIC(10,2) NOT NULL,
    price_change NUMERIC(10,2) NOT NULL,
    movement_type VARCHAR(32) NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT NOW(),
    demand_ratio NUMERIC(10,4),
    weighted_sales NUMERIC(10,4)
);

-- 5. PRICING_AUDIT_LOG TABLE
CREATE TABLE pricing_audit_log (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    event_type VARCHAR(64) NOT NULL,
    initiated_by VARCHAR(64) NOT NULL,
    product_id BIGINT,
    old_value VARCHAR(255),
    new_value VARCHAR(255),
    reason TEXT,
    config_version BIGINT
);

-- 6. PRICING_ENGINE_CONFIG TABLE
CREATE TABLE pricing_engine_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(64) NOT NULL UNIQUE,
    config_value VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

---

## 17. PRICE HISTORY

* **Trigger**: A record is inserted into `price_history` **ONLY when an actual price change occurs** ($\Delta P \ne 0$), or upon Market Crash activation/restoration, or manual override.
* **Zero Change ($₹25 \to ₹25$)**: **NOT RECORDED**. This prevents bloating the database with millions of redundant records.
* **Positive Change ($₹25 \to ₹26$)**: **RECORDED** with `movement_type = 'HIGH_DEMAND'`.
* **Negative Change ($₹25 \to ₹24$)**: **RECORDED** with `movement_type = 'LOW_DEMAND'`.
* **Atomicity**: The insert occurs inside the same `@Transactional` database commit that updates `products.current_cup_price`.

---

## 18. ADMIN PRICING CHANGES

```text
Admin UI (Inputs new value)
     │
     ▼
REST API Endpoint (POST /api/pricing/config/global or /product)
     │
     ▼
PricingConfigController
     │
     ▼
PricingConfigurationService.updateGlobalConfig()
     │
     ├─► Validation Checks:
     │     - minPrice <= defaultPrice <= maxPrice
     │     - minPrice > 0, maxPrice > 0
     │     - interval >= 5 seconds
     │     - targetDemand > 0
     │
     ├─► PostgreSQL Transaction:
     │     - Updates `pricing_engine_config` or `products`
     │     - Increments config version (e.g. 428 -> 429)
     │     - Inserts record into `pricing_audit_log`
     │
     ├─► Scheduler Dynamic Reschedule:
     │     - Updates ThreadPoolTaskScheduler trigger interval
     │
     └─► Broadcasts WebSocket event to `/topic/pricing-config`
```

---

## 19. CHANGING PRICING INTERVAL

1. **10 sec $\to$ 30 sec**: Scheduler reschedules next execution to $t + 30\text{s}$. Time windows stretch from 10s to 30s. Target demand scales up ($T_{\text{norm}} = T_{\text{1min}} \times 0.5$). No double counting occurs.
2. **30 sec $\to$ 10 sec**: Scheduler reschedules next execution to $t + 10\text{s}$. Time windows compress to 10s. Target demand scales down ($T_{\text{norm}} = T_{\text{1min}} \times 0.1667$). Cooldown prevents rapid successive downward price decays.
3. **1 min $\to$ 5 min**: The running tick finishes its current 1-minute window; subsequent cycles sleep for 5 minutes. Audit logs record `CONFIG_INTERVAL_CHANGE` with old and new values.

---

## 20. AUDIT TRAIL

The `pricing_audit_log` table tracks every critical operational event:
* `CONFIG_INTERVAL_UPDATE`: Logs changes to settlement interval.
* `MANUAL_PRICE_OVERRIDE`: Logs admin price overrides with old price, new price, and reason.
* `MARKET_CRASH_TRIGGERED`: Logs crash initiation timestamp, initiator, and affected floor.
* `MARKET_CRASH_RESTORED`: Logs restoration of pre-crash prices.
* `SIMULATOR_STATE_CHANGE`: Logs simulator enablement/disablement.
* `PRICE_CHANGE`: Automatic engine settlements are logged with version ID, beverage ID, and trigger reason.

---

## 21. MANUAL PRICE OVERRIDE

When an Administrator changes Fresh Mango Juice from ₹25.00 to ₹30.00:
1. `AdminController` calls `PricingConfigurationService.overridePrice(1, 30.00)`.
2. PostgreSQL `products.current_cup_price` is updated to ₹30.00.
3. A record is inserted into `price_history` (`movement_type = 'MANUAL_OVERRIDE'`).
4. An entry is written to `pricing_audit_log`.
5. Redis cache `juice:prices` is updated.
6. STOMP message is broadcast to `/topic/price-updates`.
7. **Next Cycle Interaction**: Manual locks are not permanently frozen. On the next settlement cycle, the engine calculates demand based on actual customer purchases. If demand is low, the price will decay from ₹30.00 back towards ₹25.00.

---

## 22. MARKET CRASH

```text
ADMIN / API TRIGGERS MARKET CRASH
                 │
                 ▼
1. MarketCrashService snapshots current active prices into memory Map
   (e.g., Mango: ₹28.00, Lemon: ₹24.00, Grape: ₹27.00)
                 │
                 ▼
2. `isMarketCrashActive` flag set to TRUE in memory and Redis
                 │
                 ▼
3. All beverage prices in PostgreSQL updated to min_cup_price (₹20.00)
                 │
                 ▼
4. Price history appended for all products (movement_type = 'MARKET_CRASH')
                 │
                 ▼
5. WebSocket broadcast to `/topic/market-crash` ({ active: true, duration: 180 })
                 │
                 ▼
6. Schedulers & sales during crash flag `is_crash_sale = true`
   (Excluded from future DWMA demand windows)
                 │
                 ▼
7. Countdown timer runs for exactly 180 seconds (3 minutes)
                 │
                 ▼
8. Timer expires -> `stopMarketCrash()` invoked
                 │
                 ▼
9. Pre-crash snapshot restored to PostgreSQL (Mango -> ₹28, Lemon -> ₹24)
   *(Fallback to default base ₹25.00 occurs ONLY if server restarted and memory snapshot lost)*
                 │
                 ▼
10. WebSocket broadcast to `/topic/market-crash` ({ active: false })
```

---

## 23. SIMULATOR

* **Implementation**: `LiveMarketSimulatorService.java`.
* **Affects Real Pricing?** **YES, IF ENABLED.** The simulator executes real calls to `posService.processCheckout()`, which creates real orders in `orders` and `order_items`.
* **Batch Inventory**: Real inventory in `batches` is decremented.
* **Production Status**: Controlled via `market.simulator.enabled: false` in `application.properties`. Must remain **FALSE** in live production environments.

---

## 24. POS SERVER-SIDE PRICE AUTHORITY

* **Test Case**: Client frontend sends an order with `lockedPrice = ₹10.00` while database price is `₹25.00`.
* **Execution**:
  1. `POSService.validateAndResolvePrice()` inspects the request.
  2. If `priceLockToken` is missing or expired, `lockedPrice` is completely discarded.
  3. `POSService` queries PostgreSQL:
     ```sql
     SELECT current_cup_price FROM products WHERE id = :id FOR UPDATE;
     ```
  4. Final charged price is **₹25.00**. Client-side price tampering is completely prevented.

---

## 25. CONCURRENT PURCHASES

* **Scenario**: Customers A, B, and C buy Fresh Mango Juice simultaneously.
* **Database Concurrency**:
  1. Order creation uses transactional isolation (`READ_COMMITTED` with pessimistic `PESSIMISTIC_WRITE` locks on inventory batches).
  2. Orders insert separate rows into `orders` and `order_items`; no row collisions occur.
  3. Batch inventory decrements sequentially with `SELECT ... FOR UPDATE`, eliminating race conditions or negative inventory.
  4. In the next settlement window, `OrderItemRepository` counts all 3 cups correctly in $W_0$.

---

## 26. REDIS

| Key | Purpose | Written By | Read By | TTL | Authoritative? |
| :--- | :--- | :--- | :--- | :---: | :---: |
| `juice:prices` | Serialized JSON map of current product prices | `PricingSettlementCoordinator` | `PricingRedisRepository` | 60s | **NO** (Cache) |
| `market:crash` | JSON object of active crash state and end timestamp | `MarketCrashService` | Status endpoints | 180s | **NO** (Event State) |
| `price:lock:{token}` | Temporary cart price guarantee token | `PriceLockService` | `POSService` | 10s | **NO** (Temporary) |

---

## 27. WEBSOCKET / STOMP

| Topic | Publisher | Subscriber | Payload | Trigger |
| :--- | :--- | :--- | :--- | :--- |
| `/topic/price-updates` | `PricingSettlementCoordinator` | POS, Admin, LED | Array of all 8 juices with current prices & deltas | End of settlement cycle |
| `/topic/led-display` | `PricingSettlementCoordinator` | LED Display Board | Comprehensive telemetry, deltas, and volumes | End of settlement cycle |
| `/topic/market-crash` | `MarketCrashService` | All frontends | `{ active: bool, remainingSeconds: int }` | Crash start / stop |
| `/topic/settlement-heartbeat` | `DynamicPricingSchedulerConfig`| Admin Panel | Next execution timestamp & health | Every scheduler tick |

*Messages are dispatched **AFTER** database transaction commit via `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.*

---

## 28. FRONTEND PRICE FLOW

* **POS (`customer-web/src/index.html`)**:
  * Loads catalog via `/api/products`. Subscribes to `/topic/price-updates`.
  * On checkout, requests lock token; displays server price; never calculates price locally.
* **Admin Panel (`admin-panel/src/index.html`)**:
  * Loads live telemetry via `/api/pricing/market` and STOMP.
  * Allows live parameter updates and trigger actions.
* **LED Display (`customer-web/src/led-display.html`)**:
  * High-visibility stadium board. Subscribes to `/topic/led-display` and `/topic/market-crash`.
  * Renders full-width unclipped matrix and emergency siren takeover.

---

## 29. SCHEDULER AND LOOP AUDIT

| File | Method | Interval / Type | Purpose | Can Modify Price? | Authoritative? |
| :--- | :--- | :--- | :--- | :---: | :---: |
| `PricingSettlementCoordinator.java` | `runSettlementCycle()` | 60s (Dynamic) | Calculates DWMA & commits new prices | **YES** | **YES** |
| `MarketCrashService.java` | `crashTimerInterval` | 1s ticker (180s total) | Counts down crash duration | **YES** | **YES** |
| `LiveMarketSimulatorService.java` | `scheduledSimulationTick()` | 3s–8s random | Synthetic checkout injector | Indirectly | NO |
| `customer-web/src/led-display.html` | `checkMarketCrashStatus()` | 5s polling | Crash status safety poll | NO | NO |
| `admin-panel/src/index.html` | `pollEngineTelemetry()` | 5s polling | Admin telemetry backup poll | NO | NO |

---

## 30. CONFIGURATION VERSIONING

* Every configuration modification increments `pricing_engine_config.version` monotonically (currently version **428**).
* Each settlement cycle logs the active configuration version in `pricing_audit_log`, providing a complete historical audit trail of parameter changes.

---

## 31. CYCLE ID / IDEMPOTENCY

* Each settlement cycle generates a unique timestamped Execution ID (`UUID` + cycle epoch).
* Idempotency is enforced by calculating prices using deterministic timestamp boundaries $[t - \Delta t, t)$ and logging unique cycle IDs in `price_history`.

---

## 32. MONEY HANDLING

* **Database**: `NUMERIC(10, 2)` across all price and transaction columns.
* **Java Backend**: `java.math.BigDecimal` with `RoundingMode.HALF_UP` for all intermediate calculations.
* **Customer Facing**: Prices strictly adhere to whole rupee values (`₹20.00`, `₹25.00`, `₹30.00`) under step sizing of ₹1.00. Floating point precision errors (`₹24.99999`) are completely avoided.

---

## 33. ADMIN VALIDATION

In `PricingConfigurationService.java`:
* `minCupPrice <= defaultCupPrice`: Enforced (Throws `IllegalArgumentException` if violated).
* `defaultCupPrice <= maxCupPrice`: Enforced.
* `minCupPrice < maxCupPrice`: Enforced.
* `price > 0`: Negative and zero prices rejected.
* `settlementIntervalSeconds >= 5`: Micro-intervals below 5 seconds rejected.
* `targetSalesPer1Minute > 0`: Non-positive targets rejected.

---

## 34. REAL-WORLD TEST CASES

* **Case 1 (Zero Demand)**: $S_w = 0 \implies R_d = 0.6000 < 0.90 \implies \Delta P = -1.00$. Price drops ₹1.00 every 60s cooldown down to ₹20.00 floor.
* **Case 2 (Target Demand)**: Customer buys exactly matching target demand ($S_w = T_{\text{norm}}) \implies R_d = 1.0000$. Falls in stable band $[0.90, 1.10) \implies \Delta P = 0.00$. Price remains stable.
* **Case 3 (Surge Demand)**: Customer buys 3 cups in 1 minute ($T_{\text{norm}} = 0.50) \implies R_d \approx 2.18 \ge 1.10 \implies \Delta P = +1.00$. Price rises to ₹26.00.
* **Case 4 (Ceiling Hit)**: Price reaches ₹30.00. Heavy purchases continue. Clamping holds price at ₹30.00.
* **Case 5 (Floor Hit)**: Price reaches ₹20.00. Zero purchases continue. Clamping holds price at ₹20.00.

---

## 35. 10-CYCLE SIMULATION ANALYSIS

Simulation of Fresh Mango Juice ($P_{\text{base}} = ₹25.00, P_{\text{min}} = ₹20.00, P_{\text{max}} = ₹30.00, T = 0.50, \Delta t = 60\text{s}$):

| Cycle | Sales ($w_0$) | DWMA ($S_w$) | Demand Ratio ($R_d$) | Decision | Old Price | New Price | Price Movement Reason |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **1** | 2 | 1.14 | 1.51 | SURGE | ₹25.00 | **₹26.00** | HIGH_DEMAND_SURGE |
| **2** | 3 | 2.00 | 2.20 | SURGE | ₹26.00 | **₹27.00** | HIGH_DEMAND_SURGE |
| **3** | 2 | 2.14 | 2.31 | SURGE | ₹27.00 | **₹28.00** | HIGH_DEMAND_SURGE |
| **4** | 0 | 1.14 | 1.51 | SURGE | ₹28.00 | **₹29.00** | HIGH_DEMAND_SURGE (W1 lag) |
| **5** | 0 | 0.43 | 0.94 | STABLE | ₹29.00 | **₹29.00** | BALANCED_DEMAND |
| **6** | 0 | 0.00 | 0.60 | DECAY | ₹29.00 | **₹28.00** | LOW_DEMAND_DECAY |
| **7** | 0 | 0.00 | 0.60 | DECAY | ₹28.00 | **₹27.00** | LOW_DEMAND_DECAY |
| **8** | 0 | 0.00 | 0.60 | DECAY | ₹27.00 | **₹26.00** | LOW_DEMAND_DECAY |
| **9** | 0 | 0.00 | 0.60 | DECAY | ₹26.00 | **₹25.00** | LOW_DEMAND_DECAY |
| **10**| 0 | 0.00 | 0.60 | DECAY | ₹25.00 | **₹24.00** | LOW_DEMAND_DECAY |

---

## 36. LONG-RUN ANALYSIS

1. **Oscillation Damping**: The combination of 3-window DWMA weighting and Bayesian smoothing ($K=1.5$) eliminates rapid jitter. Prices cannot oscillate $+1$ and $-1$ in consecutive seconds.
2. **Runaway Prevention**: Absolute mathematical boundaries $[₹20.00, ₹30.00]$ prevent runaway hyperinflation or negative pricing.
3. **Cumulative Demand Leaks**: Because window queries re-aggregate actual sales from `order_items` each cycle rather than maintaining an accumulative floating counter, error accumulation is zero.

---

## 37. HARDCODED VALUES

| Hardcoded Value | Location in Code | Operational Purpose | Should Be Configurable? |
| :--- | :--- | :--- | :---: |
| `180 seconds` | `MarketCrashService.java` | Market crash duration | Yes (exposed in Admin) |
| `₹20.00` | Flyway `V1__init_schema.sql` | Minimum price floor | Yes (stored in DB) |
| `₹30.00` | Flyway `V1__init_schema.sql` | Maximum price ceiling | Yes (stored in DB) |
| `₹25.00` | Flyway `V1__init_schema.sql` | Base reference price | Yes (stored in DB) |
| `60 seconds` | `PriceAdjustmentService.java` | Minimum decay cooldown threshold | Yes (should scale with interval) |
| `1.5` | `PriceAdjustmentService.java` | Bayesian prior confidence factor ($K$) | Recommended |

---

## 38. INTENDED VS ACTUAL

| Feature | Intended Behavior | Actual Implementation | Implementation Status |
| :--- | :--- | :--- | :--- |
| **Pricing Interval** | Configurable, dynamic interval | Controlled via DB & dynamic scheduler | **IMPLEMENTED** |
| **Price Step Size** | Step-by-step $\pm ₹1.00$ movement | Strictly enforced $\Delta P \in \{+1, 0, -1\}$ | **IMPLEMENTED** |
| **SSoT Authority** | PostgreSQL authoritative | PostgreSQL `products` table enforced | **IMPLEMENTED** |
| **Market Crash Duration** | Exactly 180 seconds | 180s countdown with live websocket | **IMPLEMENTED** |
| **Pre-Crash Restore** | Restore pre-crash snapshot | Restores memory snapshot; fallback ₹25 | **IMPLEMENTED** |
| **POS Cart Protection** | Temporary price lock | 10s token lock via `price_locks` table | **IMPLEMENTED** |
| **Crash Sales Exclusion**| Crash sales don't trigger surge | `is_crash_sale = TRUE` excluded from DWMA | **IMPLEMENTED** |
| **Micro-Interval Decay** | 10s interval decays every 10s | Cooldown forces 60s minimum decay | **IMPLEMENTED (WITH QUIRK)** |

---

## 39. BUGS AND RISKS

| Bug / Risk Description | Code Location | Root Cause | Impact | Severity | Recommended Fix |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **Micro-Interval Decay Throttling** | `PriceAdjustmentService.java` | Hardcoded `Math.max(60, intervalSec)` in decay cooldown | At 10s intervals, price only drops every 60s | **MEDIUM** | Change cooldown to `Math.max(intervalSec, 10)`. |
| **In-Memory Crash Snapshot Lost on Reboot** | `MarketCrashService.java` | Snapshot map is stored in RAM (`ConcurrentHashMap`) | If backend crashes during market crash, restores to base ₹25.00 instead of pre-crash price | **LOW** | Persist crash snapshot into Redis or PostgreSQL table. |
| **Simulator Bot Active Risk** | `LiveMarketSimulatorService.java` | Simulator places real orders in production DB | If enabled in production, deducts real batch inventory | **HIGH** | Enforce production profile guard disabling simulator Bean. |

---

## 40. COMPLETE REAL-WORLD EXAMPLE

```text
1. 09:30:00 - Fresh Mango Juice is at ₹25.00.
2. 09:30:15 - Customer orders 2 cups of Fresh Mango Juice at POS.
3. 09:30:16 - `POSService.processCheckout()` verifies price (₹25.00), inserts into `orders`
              and `order_items`, and deducts 500 ml from Mango Batch #104.
4. 09:31:00 - 60-second settlement cycle triggers in `PricingSettlementCoordinator`.
5. 09:31:01 - Aggregates sales: W0 = 2, W1 = 0, W2 = 0.
              DWMA Sw = (1.0*2 + 0.5*0 + 0.25*0)/1.75 = 1.1429.
              Normalized Target Tnorm = 0.50.
              Demand Ratio Rd = (1.1429 + 1.5*0.50) / (2.5*0.50) = 1.5143.
6. 09:31:01 - Rd = 1.5143 >= 1.10 -> Decision: Surge (+₹1.00).
7. 09:31:02 - New price calculated: ₹25.00 + ₹1.00 = ₹26.00 (within limits [20, 30]).
8. 09:31:02 - PostgreSQL transaction commits:
              - `products.current_cup_price` becomes ₹26.00.
              - `price_history` record inserted (Old: 25, New: 26, Change: +1).
9. 09:31:02 - STOMP broadcast dispatched to `/topic/price-updates` and `/topic/led-display`.
10. 09:31:03 - Customer POS, Admin Cockpit, and Venue LED Display immediately show ₹26.00.
```

---

## 41. FINAL SIMPLE EXPLANATION

To explain this system to a newcomer or non-technical stakeholder:
1. **Customer Buys Juice**: You order a juice at the counter. The register asks the server for the current official price.
2. **System Watches the Clock**: Every minute, the system counts how many cups were sold.
3. **The Engine Evaluates Demand**:
   * If people bought more than the target, the price goes up by **₹1.00**.
   * If people bought what was expected, the price **stays the same**.
   * If nobody bought anything, the price goes down by **₹1.00** to encourage people to buy.
4. **Safety Limits**: Prices can never go above **₹30.00** or below **₹20.00**.
5. **Instant Screen Updates**: The moment a price changes, every TV screen and register in the venue updates instantly without needing a page refresh.
6. **Market Crash**: The manager can trigger a "Market Crash" where all juices drop to ₹20.00 for 3 minutes with emergency alarm sirens, giving customers a flash discount!

---

## 42. FINAL PRICING FLOW DIAGRAM

```text
+-----------------------------------------------------------------------------------------+
|                                    CUSTOMER CHECKOUT                                    |
|  Customer selects beverage -> POS fetches server price -> Completes transaction        |
+--------------------------------------------+--------------------------------------------+
                                             |
                                             v
+-----------------------------------------------------------------------------------------+
|                                 DATABASE TRANSACTION                                    |
|  - PostgreSQL inserts `orders` (status = COMPLETED)                                     |
|  - PostgreSQL inserts `order_items` (quantity, beverage_id, is_crash_sale = false)      |
|  - PostgreSQL decrements `batches` stock (SELECT ... FOR UPDATE)                        |
+--------------------------------------------+--------------------------------------------+
                                             |
                                             v
+-----------------------------------------------------------------------------------------+
|                              SETTLEMENT SCHEDULER TICK                                  |
|  ThreadPoolTaskScheduler fires every settlementIntervalSeconds (Default: 60s)           |
+--------------------------------------------+--------------------------------------------+
                                             |
                                             v
+-----------------------------------------------------------------------------------------+
|                                  DWMA DEMAND ENGINE                                     |
|  - Query W0 [t-Dt, t), W1 [t-2Dt, t-Dt), W2 [t-3Dt, t-2Dt)                              |
|  - Weighted Sales: Sw = (1.0*W0 + 0.5*W1 + 0.25*W2) / 1.75                              |
|  - Target Normalization: Tnorm = T_1min * (Dt / 60)                                     |
|  - Bayesian Laplace Ratio: Rd = (Sw + 1.5*Tnorm) / (2.5*Tnorm)                          |
+--------------------------------------------+--------------------------------------------+
                                             |
                                             v
+-----------------------------------------------------------------------------------------+
|                               STEP DECISION & CLAMPING                                  |
|  - Rd >= 1.10        -> Surge (+₹1.00)                                                  |
|  - 0.90 <= Rd < 1.10 -> Stable (₹0.00)                                                  |
|  - Rd < 0.90         -> Decay (-₹1.00, subject to cooldown)                             |
|  - Enforce Clamping: P_new = min(max(P + DeltaP, ₹20.00), ₹30.00)                       |
+--------------------------------------------+--------------------------------------------+
                                             |
                                             v
+-----------------------------------------------------------------------------------------+
|                             POSTGRESQL COMMIT & PUBLISH                                 |
|  - UPDATE `products` SET current_cup_price = P_new, price_version = version + 1         |
|  - If (P_new != P_old) INSERT INTO `price_history`                                      |
|  - INSERT INTO `pricing_audit_log`                                                      |
|  - COMMIT TRANSACTION                                                                   |
|  - Refresh Redis Key `juice:prices`                                                     |
|  - Broadcast WebSocket payload to `/topic/price-updates` and `/topic/led-display`       |
+-----------------------------------------------------------------------------------------+
```

---

## 43. FINAL STATUS

### IMPLEMENTED
* Full server-side price authority ignoring client-side manipulated prices.
* Bayesian-smoothed DWMA demand ratio engine with 3 backward-looking windows ($W_0, W_1, W_2$).
* Boundary clamping strictly between ₹20.00 and ₹30.00.
* Step movement strictly restricted to $+₹1.00, ₹0.00, -₹1.00$.
* Dynamic runtime pricing interval modification without requiring server restarts.
* 180-second Market Crash with pre-crash snapshot restoration and crash sales exclusion.
* Full-width, high-visibility stadium LED Display takeover with persistent ticker banner.
* Transactional append-only `price_history` and `pricing_audit_log`.

### PARTIALLY IMPLEMENTED
* **Zero-Demand Decay Reason Labeling**: In `PriceAdjustmentService.java`, zero sales produces $R_d = 0.6000$. Because the code tests `if (demandRatio < lowDemandThreshold)` where `lowDemandThreshold = 0.50`, it classifies the reason as `BELOW_NORMAL_DEMAND_DECAY` instead of `ZERO_DEMAND_DECAY`. Both branches yield $\Delta P = -1.00$, but audit labels differ.

### BUGS
* **Micro-Interval Decay Cooldown**: `minDecayCooldownSeconds = Math.max(60, intervalSec)` prevents downward decay on intervals below 60s from occurring every cycle.

### RISKS
* **Simulator in Production**: `LiveMarketSimulatorService` interacts directly with production database tables if enabled. `market.simulator.enabled` must strictly remain `false` in production.

### CANNOT VERIFY FROM CURRENT PROJECT
* External hardware LED RS232/DMX serial controller integration (all LED implementations in this project are web-based STOMP/WebSocket displays).

### REQUIRED CHANGES (RECOMMENDED FOR PERFECTION)
1. In `PriceAdjustmentService.java`, change `minDecayCooldownSeconds = Math.max(60, intervalSec)` to `Math.max(intervalSec, 10)` to allow micro-intervals (10s, 15s, 30s) to decay gracefully.
2. In `PriceAdjustmentService.java`, adjust `lowDemandThreshold` or test `if (weightedSales == 0)` first so zero-sales decays log accurately as `ZERO_DEMAND_DECAY`.
3. In `MarketCrashService.java`, persist the pre-crash price snapshot in Redis or PostgreSQL so a sudden backend crash during the 180-second crash window will still restore exact pre-crash prices instead of falling back to default ₹25.00.
