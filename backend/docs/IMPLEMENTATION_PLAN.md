# NOIDA PUB EXCHANGE & JUICE BAR — COMPLETE SYSTEM IMPLEMENTATION PLAN

> **Document Type:** Master Architectural & Implementation Specification  
> **Target Version:** 2.0.0 (Enterprise Dynamic Pricing & Kiosk Retail Suite)  
> **Source Documents / Ground Truth:** [`UI_AUDIT.md`](file:///d:/Juice%20Dynamic%20Price%20Project/UI_AUDIT.md), [`DESIGN_SYSTEM.md`](file:///d:/Juice%20Dynamic%20Price%20Project/DESIGN_SYSTEM.md), [`CHANGE_LOG.md`](file:///d:/Juice%20Dynamic%20Price%20Project/CHANGE_LOG.md), [`API_CONTRACT.md`](file:///d:/Juice%20Dynamic%20Price%20Project/API_CONTRACT.md), [`ARCHITECTURE.md`](file:///d:/Juice%20Dynamic%20Price%20Project/ARCHITECTURE.md)  
> **Active Codebase Directory:** `d:\Juice Dynamic Price Project`  
> **Deliverable Purpose:** Exhaustive technical and operational reference detailing end-to-end system architecture, mathematical models, state machines, sequence diagrams, failure recovery modes, and component inventory for developers and system operators.

---

## TABLE OF CONTENTS
1. [System Overview](#1-system-overview)
2. [Working Model — How the Dynamic Pricing Engine Works](#2-working-model--how-the-dynamic-pricing-engine-works)
3. [Core Feature-by-Feature Working Procedure](#3-core-feature-by-feature-working-procedure)
4. [Full Process Flows (Sequence-Style)](#4-full-process-flows-sequence-style)
5. [Feature Inventory Table](#5-feature-inventory-table)
6. [Data Lifecycle Map](#6-data-lifecycle-map)
7. [Edge Cases & Failure Handling](#7-edge-cases--failure-handling)
8. [Open Gaps & Priority Recommendations](#8-open-gaps--priority-recommendations)

---

## 1. SYSTEM OVERVIEW

### 1.1 Plain-English Summary
The **Noida Pub Exchange & Juice Bar** is an interactive, gamified commercial beverage retail platform inspired by financial stock exchanges. The system treats handcrafted juices (Mango, Lemon, Cool Mint, Valencia Orange, etc.) as publicly traded commodities whose prices fluctuate dynamically in real time based on customer purchasing volume, rolling demand velocity, and inventory reserves. The platform serves three distinct user groups:
1. **Walk-in Customers**: Browse live dynamic prices on touch-screen ordering kiosks or mobile web, view live demand indicators, select cup sizes (typically 250ml), add beverages to a sticky order ticket, and checkout via Cash, UPI QR code, or Card.
2. **Cashiers & Baristas**: Operate POS billing terminals, verify cashless payments, fulfill physical drink pours from commercial 20-liter dispensing containers, and issue thermal receipt slips.
3. **Store Managers & Administrators**: Monitor live revenue velocity and liquid volume levels on an executive command dashboard, manage commercial 20L liquid container batches, calibrate pricing algorithms, trigger emergency venue-wide "Market Crash" events (where sirens blare and prices plunge to floor limits for 3 minutes), simulate pricing scenarios in a sandbox laboratory, and audit immutable security logs.

---

### 1.2 High-Level Architecture Topology
The platform spans a multi-port distributed frontend ecosystem connected to an enterprise-grade reactive Spring Boot backend backed by PostgreSQL 16, Redis 7, and WebSocket STOMP message brokers.

```
══════════════════════════════════════════════════════════════════════════════════════════════════════════════
                                    CLIENT VIEWPORTS & INTERFACES
══════════════════════════════════════════════════════════════════════════════════════════════════════════════
    MASTER COMMAND PORTAL (Host Frame Container @ Port 8088 Root / index.html)
    │
    ├──► [PORT 8000] CUSTOMER POS TERMINAL (customer-web/src/index.html)
    │    ├── Touch-Screen Kiosk, Beverage Catalog, Live Sparklines
    │    ├── Sticky Cart Drawer, Cash/UPI/Card Payment, Thermal Receipt Modal
    │    └── Listens to BroadcastChannel('pubexchange_market_channel') & STOMP /topic/prices
    │
    ├──► [PORT 8000] WALL LED TV BILLBOARD (customer-web/src/led-display.html)
    │    ├── High-Contrast Continuous Stock Ticker Tape, 30-Point DWMA Canvas Sparkline Graphs
    │    ├── Full-Screen Takeover Strobe Banner & Audio Sirens for Market Crash
    │    └── Listens to BroadcastChannel('pubexchange_market_channel') & STOMP /topic/led-display
    │
    ├──► [PORT 8000] CROSS-ORIGIN BRIDGE (customer-web/src/bridge.html - Hidden iframe)
    │    └── Translates window.postMessage <---> BroadcastChannel('pubexchange_market_channel')
    │
    └──► [PORT 8001] ADMIN CONTROL CENTER (admin-panel/src/index.html)
         ├── 9 Operational Views: Dashboard (ApexCharts), Crash Controller, 20L Batches,
         │   Dynamic Pricing Engine, Sandbox Simulator, Users, Reports, Audit Trail, Settings
         └── Embeds hidden bridge.html iframe; Dispatches REST calls & STOMP subscriptions
══════════════════════════════════════════════════════════════════════════════════════════════════════════════
                                       COMMUNICATION PIPELINES
══════════════════════════════════════════════════════════════════════════════════════════════════════════════
         │                                       ▲                                    ▲
         │ REST API (JSON over HTTP/1.1)         │ STOMP over WebSocket (/ws)        │ Cross-Origin
         │ Base: http://localhost:8088/api       │ PubSub Topics: /topic/*            │ postMessage
         ▼                                       │                                    ▼
══════════════════════════════════════════════════════════════════════════════════════════════════════════════
                                  SPRING BOOT BACKEND SERVER (:8088)
                                       (com.retailpos)
══════════════════════════════════════════════════════════════════════════════════════════════════════════════
   ├── security        : Spring Security 6, Stateless JWT Filter, RBAC Role Validators, CORS Handler
   ├── pos             : POSController, POSService (Atomicity, Pessimistic Locking, Idempotency)
   ├── pricing         : PricingSettlementCoordinator, PriceAdjustmentService, MarketCrashService,
   │                     PricingSimulationService, PricingConfigurationService, DynamicPricingScheduler
   ├── inventory       : JuiceBatchController, JuiceBatchService (Pessimistic ML container deductions)
   ├── report          : ReportController (Aggregated Revenue, Cups Sold, Hourly Velocity)
   ├── notification    : NotificationService, NotificationController (Alerts, Warnings, WebSocket push)
   ├── audit           : AuditService, AuditController (Async persistent security and operational trail)
   └── websocket       : WebSocketConfig, WebSocketGatewayController (STOMP broker /topic, heartbeats)
══════════════════════════════════════════════════════════════════════════════════════════════════════════════
                                       DATA & PERSISTENCE TIER
══════════════════════════════════════════════════════════════════════════════════════════════════════════════
         │                                       │                                    │
         ▼                                       ▼                                    ▼
  POSTGRESQL 16 DATABASE                  REDIS 7 IN-MEMORY CACHE              APACHE KAFKA / DOCKER
  (ACID Source of Truth)                 (Sub-Millisecond Hot State)          (Scaffolded Telemetry)
  ├── products (Bounds, Prices, Modes)   ├── pubexchange:products (Live)      ├── Broker Port: 9092
  ├── juice_batches (20L Containers)     ├── pubexchange:market-crash         ├── Zookeeper: 2181
  ├── sales_orders & items (Pours)       ├── pubexchange:inventory            └── Telemetry Health Probe
  ├── price_histories (DWMA Audit)       ├── live_price:{id} (DTO Caches)
  ├── market_settlements (Idempotency)   └── market:version (Atomic Counter)
  └── audit_logs & system_notifications
```

---

### 1.3 Core Technologies & Design Decisions

| Technology Component | Selection Choice | Architectural Justification & Trade-Off Analysis |
| :--- | :--- | :--- |
| **Backend Framework** | Spring Boot 3.x (Java 17) | Enterprise transactional semantics (`@Transactional`), robust connection pooling (HikariCP), battle-tested ORM (Spring Data JPA / Hibernate), and first-class WebSocket STOMP support. |
| **Relational Database** | PostgreSQL 16 | Absolute ACID transactional consistency. High-concurrency retail transactions require database-level pessimistic row locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) to eliminate race conditions when multiple cashiers pour from the same 20L container. |
| **In-Memory Caching** | Redis 7 | Sub-millisecond read latency for ultra-hot data: current market prices, active crash flags, market version counter (`INCR market:version`), and session snapshots. Offloads 95% of high-frequency price read queries from PostgreSQL. |
| **Real-Time Push Engine** | WebSocket with STOMP Protocol | Replaces inefficient HTTP polling. STOMP provides standardized frame structures (`CONNECT`, `SUBSCRIBE`, `SEND`), client heartbeats (10s), destination prefixes (`/topic`), and topic subscriptions. Guarantees simultaneous sub-50ms price broadcasts across all connected POS kiosks, LED displays, and admin consoles. |
| **Frontend Architecture** | Vanilla HTML5 / ES6 JavaScript / Vanilla CSS | Zero external bundler complexity (no Webpack, Vite, or Angular overhead at runtime), ultra-fast browser execution, instant DOM manipulation, and native canvas rendering for high-framerate stock tickers and sparklines. |
| **Cross-Origin Pipeline** | Invisible `bridge.html` with `postMessage` & `BroadcastChannel` | Browser Same-Origin Policy (SOP) prohibits direct cross-port storage sharing between Port 8000 and Port 8001. Embedding a lightweight invisible bridge iframe on Port 8000 inside the Port 8001 Admin console allows synchronized event relays and shared state updates without CORS blockers. |
| **Message Streaming** | Apache Kafka (9092) | Scaffolded in Docker infrastructure for enterprise decoupled event ingestion (high-frequency order streams, audit log distribution, and long-term analytical sinks). |

---

## 2. WORKING MODEL — HOW THE DYNAMIC PRICING ENGINE WORKS

### 2.1 Plain-Language Overview of DWMA
The **Dynamic Weighted Moving Average (DWMA)** pricing engine evaluates sales demand across three discrete, consecutive time windows:
- **$W_0$ (Current Window):** Sales volume occurring right now in the most recent settlement window (weight: **1.00**).
- **$W_1$ (Previous Window):** Sales volume in the preceding settlement window (weight: **0.50**).
- **$W_2$ (Older Window):** Sales volume two intervals prior (weight: **0.25**).

By weighting the newest sales most heavily while discounting older sales, the engine reacts instantly to sudden rushes at the juice counter without overreacting to isolated spikes. It compares the weighted sales figure ($S_w$) against a pre-calibrated baseline sales target ($\text{TargetSales}$).
- If demand significantly outpaces the target, the price surges by **$+₹1.00$**.
- If demand matches target expectations, the price holds completely stable at **$₹0.00$**.
- If demand cools down below target, the price decays by **$-₹1.00$**.

Every single price step is strictly capped between the product's configured **Floor Limit (minCupPrice)** and **Ceiling Limit (maxCupPrice)**, guaranteeing prices never skyrocket out of reach or drop below ingredient costs.

---

### 2.2 Mathematical Specification & Formulas

#### 1. Time Window Segmentation
Given current evaluation timestamp $T_{eval}$ and configured settlement interval $\Delta t$ (e.g. 60 seconds):
$$\begin{aligned}
W_0 &\in [T_{eval} - \Delta t,\; T_{eval}) \\
W_1 &\in [T_{eval} - 2\Delta t,\; T_{eval} - \Delta t) \\
W_2 &\in [T_{eval} - 3\Delta t,\; T_{eval} - 2\Delta t)
\end{aligned}$$
Sales counts $w_0, w_1, w_2$ are retrieved via exact transactional range counts:
```sql
SELECT COALESCE(SUM(quantity), 0) FROM sales_order_items soi
JOIN sales_orders so ON soi.sales_order_id = so.id
WHERE soi.product_id = :productId AND so.created_at >= :windowStart AND so.created_at < :windowEnd;
```

#### 2. Weighted Sales Formulation
$$S_w = (w_0 \times W_0) + (w_1 \times W_1) + (w_2 \times W_2)$$
*Default Configuration Constants ([`PricingConfigurationService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/PricingConfigurationService.java)):*
$$W_0 = 1.0000,\quad W_1 = 0.5000,\quad W_2 = 0.2500$$

#### 3. Target Sales Normalization
The product catalog defines a baseline target sales rate per 1 minute (default $0.55\text{ cups/min}$). When running on non-60-second settlement cycles (e.g. 10s, 30s, 120s), the target is normalized proportionally:
$$\text{TargetSales}_{normalized} = \text{TargetSales}_{base} \times \left( \frac{\Delta t}{60.0} \right)$$

#### 4. Demand Ratio Formulation
$$R_d = \begin{cases} 
\dfrac{S_w}{\text{TargetSales}_{normalized}}, & \text{if } \text{TargetSales}_{normalized} > 0 \\
0.0, & \text{otherwise}
\end{cases}$$

#### 5. Discrete Price Movement Rules ($\Delta P$)
The calculated demand ratio $R_d$ maps into discrete, bounded adjustments:

| Demand Category | Threshold Condition | Immediate Price Shift ($\Delta P$) | Algorithmic Reason Code |
| :--- | :--- | :---: | :--- |
| **High Demand** | $R_d \ge 1.1000$ and $S_w > 0$ | **$+₹1.00$** | `HIGH_DEMAND_SURGE` |
| **High Demand (Inert)** | $R_d \ge 1.1000$ and $S_w = 0$ | **$₹0.00$** | `HIGH_HISTORICAL_ZERO_CURRENT_HOLD` |
| **Stable Demand** | $0.9000 \le R_d < 1.1000$ | **$₹0.00$** | `STABLE_DEMAND` |
| **Low Demand** | $0.5000 \le R_d < 0.9000$ | **$-₹1.00$** | `BELOW_NORMAL_DEMAND_DECAY` |
| **Very Low Demand** | $R_d < 0.5000$ | **$-₹1.00$** | `ZERO_DEMAND_DECAY` |

#### 6. Circuit Breaker Clamping
$$P_{clamped} = \max\Big( P_{floor},\; \min\big( P_{ceiling},\; P_{old} + \Delta P \big) \Big)$$
Where $P_{floor} = \text{minCupPrice}$ and $P_{ceiling} = \text{maxCupPrice}$.

---

### 2.3 End-to-End Hop: "Customer Buys Juice" to "LED Board Price Change"

```
[CUSTOMER POS @ 8000]
  │ (1) User taps "Add to Bill" & clicks "Process Checkout"
  │     `processCheckout()` validates in-memory cart
  ▼
[HTTP POST /api/pos/checkout]
  │ (2) Network request carries CartItemRequest: { productId: 1, qty: 2, cupSizeMl: 250 }
  ▼
[SPRING BOOT POSService.java]
  │ (3) Enters @Transactional boundary
  │     - Product validated: productRepository.findById(1)
  │     - Atomically increments order_count: productRepository.incrementOrderCount(1, 2)
  │     - Acquires Pessimistic Lock on Active Batch: batchRepository.findActiveBatchesForProductWithLock(1)
  │     - Deducts liquid: JuiceBatch.deductVolume(500ml); saves transaction record
  │     - Saves SalesOrder & SalesOrderItem records with status 'COMPLETED'
  │ (4) Commits PostgreSQL transaction
  ▼
[SCHEDULER / SETTLEMENT TRIGGER]
  │ (5) Scheduler ticks or Admin dispatches POST /api/pricing/evaluate
  ▼
[SPRING BOOT PricingSettlementCoordinator.java]
  │ (6) Acquires PostgreSQL Advisory Lock (ID=788325001L) & JVM ReentrantLock
  │ (7) Invokes PriceAdjustmentService.evaluateAndAdjustPrice(1)
  │     - Counts W0 sales (now includes the 2 cups just purchased!)
  │     - Computes Sw, Rd >= 1.10 -> DeltaP = +₹1.00
  │     - Clamps price within floor/ceiling
  │     - Commits updated current_cup_price & price_version in PostgreSQL
  │     - Inserts immutable audit record in price_history table
  │ (8) Syncs hot price to Redis 7: redisRepository.setProductPrice(1, 26.00)
  │     Increments market version: redisRepository.incrementMarketVersion()
  ▼
[STOMP WEBSOCKET BROADCAST]
  │ (9) messagingTemplate.convertAndSend("/topic/prices", cycleResult)
  │     messagingTemplate.convertAndSend("/topic/led-display", cycleResult)
  ▼
[CLIENT WEBSOCKET HANDLERS]
  │ (10) LED Display (led-display.html) STOMP client receives frame on /topic/led-display
  │      - Triggers `applyLedPriceFlash(1, 25.00, 26.00)`
  │      - Price card illuminates in vibrant emerald green glow (.px-price-surge)
  │      - `animateLedNumber()` smoothly interpolates price ticker from ₹25.00 to ₹26.00
  │      - Ticker tape marquee updates text: "MANGO: ₹26.00 ▲+1.00"
  │ (11) Customer POS (customer-web/src/index.html) receives frame on /topic/prices
  │      - Juice card pulses green; sparkline canvas redraws with new apex point
  │ (12) Admin Control Center (admin-panel/src/index.html) receives frame on /topic/prices
  │      - Pricing table row pulses green; live sparkline shifts; DWMA visualizer updates pipeline
```

---

### 2.4 Settlement Cycle Configuration
The dynamic pricing engine supports customizable evaluation intervals configurable via the Admin Control Center ([`UI_AUDIT.md Section 2.6`](file:///d:/Juice%20Dynamic%20Price%20Project/UI_AUDIT.md#L119)):
- **Available Cycle Options:** `10s`, `30s`, `1m` (Default), `2m`, `5m`, `10m`, `15m`.
- **Trigger Mechanisms:**
  1. **Automated Cron / Fixed-Rate Scheduler:** [`DynamicPricingSchedulerConfig.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/DynamicPricingSchedulerConfig.java) runs in the background and evaluates every active beverage on schedule.
  2. **Manual Force Settlement:** Administrator clicks **Run Settlement Cycle** (`triggerPriceEngine()`) triggering `POST /api/pricing/evaluate`.
- **Calculations Performed:** Re-evaluates rolling window sales ($w_0, w_1, w_2$), computes $S_w$, normalizes target sales, determines demand ratios ($R_d$), and determines price steps.
- **State Updated:** Persists new prices and price version counters in PostgreSQL `products`, appends `price_history` audit rows, appends `juice_market_settlement` idempotency record, and updates Redis keys.
- **Broadcast Topics:** Pushes JSON payload to `/topic/prices`, `/topic/settlement`, `/topic/products`, and `/topic/led-display`.

---

### 2.5 Circuit Breakers (Floor & Ceiling Bounds)
To protect both commercial profitability and customer fairness, the pricing engine enforces a hard mathematical boundary on every product:
$$\text{minCupPrice} \le \text{currentCupPrice} \le \text{maxCupPrice}$$
1. **Pre-Validation:** Any manual input, bulk update, or simulator deployment that violates $0 < \text{Floor} < \text{Base} \le \text{Ceiling}$ is rejected client-side before network transmission.
2. **Database Constraints:** PostgreSQL column constraints and JPA `@PreUpdate` hooks enforce that `current_cup_price >= min_cup_price` and `current_cup_price <= max_cup_price`.
3. **Algorithmic Clamping:** In [`PriceAdjustmentService.java` (Line 390)](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/PriceAdjustmentService.java#L390):
   ```java
   BigDecimal uncappedPrice = oldPrice.add(deltaP);
   BigDecimal newPrice = uncappedPrice.max(floor).min(ceiling).setScale(2, RoundingMode.HALF_UP);
   ```
   If a juice is already at its ceiling (e.g. ₹30.00) and experiences surging demand, $\Delta P$ is computed as $+₹1.00$, but clamping forces the new price to remain at ₹30.00 with explanation `CEILING_BOUND_HELD`.

---

### 2.6 Dynamic Pricing vs. Manual Override Mode

| Dimension | DYNAMIC Mode | MANUAL_OVERRIDE / MANUAL_LOCK Mode |
| :--- | :--- | :--- |
| **Operational Intent** | Automatic market equilibrium governed by DWMA algorithm. | Managerial intervention for promotions, happy hours, or supply disruptions. |
| **Price Adjustment** | Changes dynamically by $\pm ₹1.00$ or holds $₹0.00$ at each settlement tick. | Price is completely frozen at the admin-specified value. Settlement cycles bypass mutation. |
| **Database Flag** | `pricing_mode = 'DYNAMIC'` | `pricing_mode = 'MANUAL_OVERRIDE'` |
| **Activation Step** | Default state or via `POST /api/pricing/products/{id}/release-override`. | Dispatched via `POST /api/pricing/products/{id}/price?price=28.00&pricingMode=MANUAL_OVERRIDE`. |
| **UI Indicator** | Green/Cyan "DYNAMIC" badge on Admin table ([`UI_AUDIT.md Section 2.6`](file:///d:/Juice%20Dynamic%20Price%20Project/UI_AUDIT.md#L120)). | Amber "MANUAL" badge with unlock button (`🔓 Release Override`). |

---

## 3. CORE FEATURE-BY-FEATURE WORKING PROCEDURE

### 3.1 Customer Walk-in Ordering & POS Checkout

#### (a) Problem Solved
Enables friction-free, self-service walk-in beverage ordering with real-time stock and price synchronization, automated volume deduction, and transaction-safe multi-tender checkout.

#### (b) Step-by-Step Procedure
1. Customer approaches Kiosk POS terminal (`customer-web/src/index.html`) displaying live beverage cards.
2. Customer selects cup size (default 250ml) and taps **Add to Bill** (`addToCart(productId)`).
3. Cart drawer updates with line items, displaying subtotal, estimated volume, and cup count.
4. Customer selects tender: `CASH`, `UPI` (QR Code), or `CARD`.
5. Customer taps **Done / Next POS Order** (`processCheckout()`).
6. POS validates cart non-emptiness, generates a unique client idempotency key, and sends `POST /api/pos/checkout`.
7. Backend validates product availability, deducts volume under pessimistic lock from active 20L batches, persists sales order, and returns transaction response.
8. POS receives response, plays audio confirmation chime, clears cart, and opens thermal receipt modal (`#receiptModal`).

#### (c) Files, Functions & Endpoints
- **Frontend:** [`customer-web/src/index.html`](file:///d:/Juice%20Dynamic%20Price%20Project/customer-web/src/index.html) — `addToCart()`, `updateQty()`, `processCheckout()`, `closeReceiptModal()`.
- **Backend Controller:** [`POSController.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pos/POSController.java) — `checkout(@RequestBody CheckoutRequest request)`.
- **Backend Service:** [`POSService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pos/POSService.java) — `processCheckout()`, `doProcessCheckout()`.
- **Endpoint:** `POST /api/pos/checkout`.

#### (d) Data Changes
- **PostgreSQL:**
  - Row inserted in `sales_orders` (Order number, total amount, tender type, status `COMPLETED`).
  - Rows inserted in `sales_order_items` (Product ID, quantity, unit price, cup size, volume deducted).
  - Row updated in `juice_batches` (`remaining_volume_ml = remaining_volume_ml - totalMl`).
  - Row inserted in `inventory_transactions` (`transaction_type = 'POS_SALE'`).
  - Row updated in `products` (`order_count = order_count + qty`).
- **Redis:** Synchronizes updated batch remaining volume.

#### (e) What Other Screens See
- **Customer POS:** Receipt modal opens; cart resets; product cards reflect updated live volume.
- **Wall LED Display:** Order count updates in background; ticker reflects sales momentum.
- **Admin Dashboard:** Real-time revenue counter (`#dashRevenue`) and cups sold (`#dashCups`) smoothly animate upward; 20L stock volume donut chart redraws with reduced liquid volume.

---

### 3.2 Real-Time Price Broadcasting

#### (a) Problem Solved
Eliminates stale pricing discrepancies across distributed venue screens by ensuring POS kiosks, wall displays, and admin consoles display identical pricing data within 50ms of any price calculation.

#### (b) Step-by-Step Procedure
1. Any event altering prices completes (Settlement tick, Manual override, Bulk save, or Market crash).
2. Backend coordinator compiles an authoritative `PriceEvaluationCycleResult` payload containing all active beverages, old prices, new prices, deltas, trend directions, and market version.
3. Spring Boot `SimpMessagingTemplate` broadcasts the payload to `/topic/prices` and `/topic/led-display`.
4. Connected frontends parse the incoming STOMP frame.
5. In-memory product caches (`liveProductsCache`, `productsCache`) update immediately.
6. Animation engines trigger: numbers smoothly transition using cubic easing, while cards flash green for surges or red for decays.

#### (c) Files, Functions & Endpoints
- **Backend:** [`PricingSettlementCoordinator.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/PricingSettlementCoordinator.java) — `broadcastCurrentState()`.
- **Broker Topics:** `/topic/prices`, `/topic/products`, `/topic/led-display`, `/topic/settlement`.
- **Frontend Consumers:**
  - [`customer-web/src/index.html`](file:///d:/Juice%20Dynamic%20Price%20Project/customer-web/src/index.html) — `applyPriceFlash()`, `animateNumber()`.
  - [`customer-web/src/led-display.html`](file:///d:/Juice%20Dynamic%20Price%20Project/customer-web/src/led-display.html) — `applyLedPriceFlash()`, `animateLedNumber()`.
  - [`admin-panel/src/index.html`](file:///d:/Juice%20Dynamic%20Price%20Project/admin-panel/src/index.html) — `renderLivePricingTable()`, `renderAllAdminSparklines()`.

#### (d) Data Changes
- In-memory WebSocket frame transmission.
- Client memory caches updated (`productsCache`, `priceHistoryMap`).
- Local storage synced (`pubexchange_dynamic_products`).

#### (e) What Other Screens See
- Every connected screen visually animates simultaneously with synchronized green/red color glows and matching numerical prices.

---

### 3.3 20L Container Inventory Lifecycle

#### (a) Problem Solved
Manages bulk liquid stock in commercial 20,000ml dispensers, tracking milliliter-level depletion, preventing over-dispensing when liquid runs out, alerting managers of low stock, and recording supplier restocks.

#### (b) Step-by-Step Procedure
1. **Registration:** Admin opens Batches tab (`#view-batches`), clicks **+ Register 20L Batch** (`showNewBatchModal()`), selects juice variety, confirms 20,000ml capacity, and submits. Dispatches `POST /api/batches`.
2. **Depletion:** Customer orders continuously deplete liquid (e.g. 250ml per cup) inside `@Transactional` database locks in [`JuiceBatchService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/inventory/JuiceBatchService.java).
3. **Multi-Batch Split:** If an active batch has 100ml remaining and order requires 250ml, the system deducts 100ml, marks the batch `DEPLETED`, transitions to the next active batch, and deducts the remaining 150ml (`POS_SALE_SPLIT`).
4. **Low-Stock Alert:** When remaining liquid drops below 4,000ml (&lt;20%), the system triggers a `WARNING` notification (`notifyLowInventory()`) and turns the UI badge amber.
5. **Restock:** Admin clicks **Restock** on a batch (`restockBatch(id)`), sending `POST /api/batches/{id}/restock?additionalMl=20000`. Volume resets to 20,000ml and status returns to `ACTIVE`.

#### (c) Files, Functions & Endpoints
- **Backend Service:** [`JuiceBatchService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/inventory/JuiceBatchService.java) — `registerNewBatch()`, `deductBatchVolume()`, `restockBatch()`, `depleteBatch()`.
- **Backend Controller:** [`JuiceBatchController.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/inventory/JuiceBatchController.java).
- **Endpoints:** `GET /api/batches`, `POST /api/batches`, `POST /api/batches/{id}/restock`, `POST /api/batches/{id}/deplete`.
- **Frontend:** [`admin-panel/src/index.html`](file:///d:/Juice%20Dynamic%20Price%20Project/admin-panel/src/index.html) — `loadBatches()`, `showNewBatchModal()`, `restockBatch()`.

#### (d) Data Changes
- Rows updated/inserted in `juice_batches` (`remaining_volume_ml`, `status = 'ACTIVE' | 'DEPLETED'`).
- Rows logged in `inventory_transactions` with volume deltas.

#### (e) What Other Screens See
- **Admin Dashboard:** Total liquid volume metric and donut chart slice update.
- **Customer POS:** If all batches for a flavour become `DEPLETED`, the product card disables with an "OUT OF STOCK" banner.

---

### 3.4 Market Crash Trigger and Recovery

#### (a) Problem Solved
Provides an electrifying venue-wide emergency happy-hour promotion that resets high prices, clears inventory surges, and drives rapid customer purchasing volume.

#### (b) Step-by-Step Procedure
1. Admin opens Crash Controller tab (`#view-crash`), arms the Safety Interlock, and clicks **🚨 TRIGGER MARKET CRASH (3 MINS)** (`openCrashConfirmationModal()`).
2. Modal opens displaying emergency warning. Admin confirms; calls `executeTriggerAdminMarketCrash()`.
3. Frontend dispatches `POST /api/pricing/market-crash/trigger?durationMinutes=3`.
4. Backend [`MarketCrashService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/MarketCrashService.java):
   - Generates unique crash code (e.g. `CRASH-8F2B1C0A`).
   - Takes immutable pre-crash price snapshot of every juice in PostgreSQL `market_crash_snapshots` and Redis.
   - Forces all active product prices down to the configured crash floor limit (₹20.00 or minCupPrice).
   - Sets `crashActive = true` and `crashEndTime = now + 180s`.
   - Broadcasts emergency payload to `/topic/market-crash` and `/topic/prices`.
5. Wall LED TV Display and Customer POS immediately enter full-screen flashing siren strobe takeover mode with audio sirens and digital countdown timer (`03:00`).
6. During the crash, scheduled DWMA price settlements are skipped to prevent price drift.
7. Upon timer expiration (or Admin clicking **🛑 Stop Crash**):
   - Backend calls `stopMarketCrash()`.
   - Reads pre-crash snapshot prices from database/Redis.
   - Atomically restores every beverage back to its pre-crash price.
   - Broadcasts crash termination event; sirens stop and normal trading resumes.

#### (c) Files, Functions & Endpoints
- **Backend Service:** [`MarketCrashService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/MarketCrashService.java) — `triggerMarketCrash()`, `stopMarketCrash()`, `getStatus()`.
- **Endpoints:** `POST /api/pricing/market-crash/trigger`, `POST /api/pricing/market-crash/stop`, `GET /api/pricing/market-crash/status`.
- **Frontend Controls:** [`admin-panel/src/index.html`](file:///d:/Juice%20Dynamic%20Price%20Project/admin-panel/src/index.html) — `executeTriggerAdminMarketCrash()`, `stopAdminMarketCrash()`.
- **Visual Takeovers:**
  - [`customer-web/src/led-display.html#L1920`](file:///d:/Juice%20Dynamic%20Price%20Project/customer-web/src/led-display.html#L1920) — `#marketCrashTakeover`.
  - [`customer-web/src/index.html#L1373`](file:///d:/Juice%20Dynamic%20Price%20Project/customer-web/src/index.html#L1373) — `#marketCrashBanner`.

#### (d) Data Changes
- Records inserted into `market_crash_snapshots`.
- Prices updated in `products` (to ₹20.00 on start, restored on stop).
- Crash state persisted in Redis (`setCrashState`).
- Audit log entry recorded in `audit_logs`.

#### (e) What Other Screens See
- All venue displays strobe red with synchronized countdown timers; pricing cards show ₹20.00 floor price. Upon stop, cards flash green/blue and restore previous prices.

---

### 3.5 Pricing Sandbox Simulator

#### (a) Problem Solved
Allows administrators to safely test "what-if" purchasing surges, volume depletion rates, and crash events across a 10-step DWMA timeline in an isolated sandbox without altering live customer prices.

#### (b) Step-by-Step Procedure
1. Admin navigates to Simulator tab (`#view-simulator`).
2. Configures simulation parameters: Flavour, Initial Volume (20,000ml), Start Price (₹25), Cups/Step (4), Floor (₹18), Ceiling (₹35), and toggles "Inject Market Crash at Step 5".
3. Admin clicks **▶️ Run Sandbox Simulation** (`runSimulation()`).
4. Frontend sends `POST /api/pricing/simulate` to [`PricingSimulationService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/PricingSimulationService.java) (with client-side fallback if offline).
5. Backend computes 10 discrete settlement steps: calculating rolling $w_0, w_1, w_2$, $S_w$, $R_d$, price shift $\Delta P$, and remaining liquid volume.
6. Trajectory table renders with 10 detailed step rows, demand ratio color tags, and explanation logs.
7. **Deploying to Live:** Admin reviews trajectory and clicks **🚀 Deploy Parameters to Live POS** (`deploySimParametersToLivePOS()`).
8. Frontend dispatches `POST /api/pricing/deploy` (or `PUT /api/pos/products/{id}`) committing the simulated parameters (Base, Floor, Ceiling, Target) directly to PostgreSQL and broadcasting to live screens.

#### (c) Files, Functions & Endpoints
- **Backend Service:** [`PricingSimulationService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/PricingSimulationService.java) — `runSimulation()`.
- **Endpoints:** `POST /api/pricing/simulate`, `POST /api/pricing/deploy`.
- **Frontend Code:** [`admin-panel/src/index.html#L8600`](file:///d:/Juice%20Dynamic%20Price%20Project/admin-panel/src/index.html#L8600) — `runSimulation()`, `renderSimulationData()`, `deploySimParametersToLivePOS()`, `deploySimParametersToAllProducts()`.

#### (d) Data Changes
- Simulation run: Zero database mutation (strictly memory-based).
- Deploy action: Commits updated `default_cup_price`, `min_cup_price`, `max_cup_price`, and `target_sales_per_1_minute` to PostgreSQL `products`.

#### (e) What Other Screens See
- While simulating: No effect on POS or LED screens.
- Upon deploy: Live POS and LED Display immediately adopt the new floor, ceiling, and base pricing parameters.

---

### 3.6 Bulk Edit "Make Changes for Every Juice"

#### (a) Problem Solved
Eliminates tedious single-product editing by providing a unified, 1080px wide batch maintenance command center to alter base prices, floor/ceiling bounds, and reset values across every juice simultaneously.

#### (b) Step-by-Step Procedure
1. Admin navigates to `#pricing` and clicks **⚡ Make Changes for Every Juice** (`openBulkEditJuicesModal()`).
2. Modal `#bulkEditJuicesModal` opens displaying an 8-column table with every catalog juice.
3. **Master Reset Value:** Admin inputs `₹25` in `#bulkResetValueInput` and clicks **Apply Master Reset** (`changeBulkResetValue()`). All row reset boxes update.
4. **Per-Row Reset Customization:** Admin can alter individual row reset boxes (e.g. types `28` in `#rowResetVal_1` for Mango and clicks that row's `↺ Reset` button). Mango's Base and Live price immediately update to `28.00` with visual row pulse feedback.
5. **Return Live to Base:** Admin clicks **Return Live to Base** (`returnAllBulkLivePricesToBase()`) to align all drifting live dynamic prices back to their baseline.
6. **Parallel Save Execution:** Admin clicks **💾 Save Changes for Every Juice** (`saveAllJuicesBulkChanges()`).
7. Frontend validates all bounds ($0 < \text{Floor} < \text{Base} \le \text{Ceiling}$), then fires concurrent `Promise.all` executing `PUT /api/pos/products/{id}` in parallel across all products.
8. Entire catalog commits to PostgreSQL in **~150ms** (slashing previous 11-second serial latency by 98%).
9. In-memory cache updates, modal closes, and success toast displays execution duration.

#### (c) Files, Functions & Endpoints
- **Frontend Component:** [`admin-panel/src/index.html#L7537-L7980`](file:///d:/Juice%20Dynamic%20Price%20Project/admin-panel/src/index.html#L7537) — `openBulkEditJuicesModal()`, `resetBulkRowWithCustomVal()`, `changeBulkResetValue()`, `saveAllJuicesBulkChanges()`.
- **Backend Endpoint:** `PUT /api/pos/products/{id}` (handled concurrently for all IDs).

#### (d) Data Changes
- PostgreSQL `products` table rows updated atomically with new base, live, floor, ceiling, and target values.
- Redis keys updated; market version incremented.

#### (e) What Other Screens See
- All POS kiosks and LED boards immediately update all beverage prices to the new baseline with green/blue flash transitions.

---

### 3.7 User Roles & RBAC Permissions Matrix

#### (a) Problem Solved
Prevents unauthorized staff from altering critical financial bounds, triggering false emergency market crashes, or viewing confidential audit trails.

#### (b) Detailed RBAC Capabilities

| Functional Domain | SUPER_ADMIN | ADMIN | MANAGER | CASHIER |
| :--- | :---: | :---: | :---: | :---: |
| **Walk-in POS Billing & Checkout** | ✅ | ✅ | ✅ | ✅ |
| **View LED Billboard & Stock Ticker** | ✅ | ✅ | ✅ | ✅ |
| **Access Admin Command Center** | ✅ | ✅ | ✅ | ❌ |
| **View Dashboard Revenue & Volume KPIs** | ✅ | ✅ | ✅ | ❌ |
| **Register & Deplete 20L Batches** | ✅ | ✅ | ✅ | ❌ |
| **Manual Price Override & Release** | ✅ | ✅ | ❌ | ❌ |
| **Run Settlement Cycle / Force Evaluation** | ✅ | ✅ | ✅ | ❌ |
| **Trigger / Stop Market Crash Emergency** | ✅ | ✅ | ❌ | ❌ |
| **Configure DWMA Settlement Timing (10s–15m)** | ✅ | ❌ | ❌ | ❌ |
| **Execute Bulk Edit ("Make Changes for Every Juice")** | ✅ | ✅ | ❌ | ❌ |
| **Run Sandbox Simulator & Deploy to Production** | ✅ | ✅ | ❌ | ❌ |
| **Export CSV & Print PDF Financial Reports** | ✅ | ✅ | ✅ | ❌ |
| **View Regulatory System Audit Trail** | ✅ | ✅ | ❌ | ❌ |
| **Create, Edit & Delete Staff User Accounts** | ✅ | ❌ | ❌ | ❌ |
| **Modify Platform Hard Floor/Ceiling Limits** | ✅ | ❌ | ❌ | ❌ |

---

### 3.8 Notification System Lifecycle

#### (a) Problem Solved
Alerts operators instantly to critical operational events (low dispenser inventory, batch depletion, emergency crashes, or circuit breaker ceiling hits) without requiring continuous monitoring.

#### (b) Step-by-Step Procedure
1. **Trigger:** An operational event occurs in the backend (e.g. container volume &lt;4,000ml in [`JuiceBatchService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/inventory/JuiceBatchService.java)).
2. **Creation:** Service calls [`NotificationService.createNotification(title, message, type)`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/notification/NotificationService.java#L36).
3. **Persistence:** Notification is saved asynchronously (`@Async`) to PostgreSQL `system_notifications` with `is_read = false`.
4. **WebSocket Push:** Broadcasts notification object to `/topic/notifications`.
5. **Admin Reception:** Admin top-bar bell icon updates with unread count badge (`#navUnreadBadge`); toast pops up.
6. **Clearing:** Admin opens Notification Center modal (`showNotificationCenterModal()`) and clicks **Mark All Read**. Dispatches `POST /api/notifications/mark-all-read`. Backend sets `is_read = true` and unread counter resets to 0.

---

### 3.9 Reports & Exports Pipeline (CSV / PDF)

#### (a) Problem Solved
Empowers store owners and accountants to extract financial summaries, sales breakdown, inventory lifecycles, and pricing audit logs for regulatory compliance and bookkeeping.

#### (b) Pipeline Mechanics
1. **Data Ingestion:** Admin selects Report Type (`Sales`, `Inventory`, `Pricing`, or `Audit`) and time range in `#view-reports`.
2. **Query Execution:** Frontend queries `GET /api/pos/orders`, `GET /api/batches`, or `GET /api/pricing/history`.
3. **CSV Export:** Admin clicks **📥 Export CSV** (`exportCurrentReportCSV()`).
   - JavaScript iterates over visible DOM table rows (`#reportTableBody tr`).
   - Formats fields into RFC 4180-compliant CSV string with double-quote escaping.
   - Creates in-memory `Blob([csvContent], { type: 'text/csv;charset=utf-8;' })`.
   - Triggers programmatic browser download: `pub_exchange_{reportType}_report_{timestamp}.csv`.
4. **PDF Generation:** Admin clicks **🖨️ Print PDF** (`printReportPDF()`).
   - Invokes browser print pipeline `window.print()`.
   - Formatted via `@media print` CSS rules: strips sidebar navigation, top bars, and background colors to output a crisp, multi-page financial ledger suitable for saving as PDF.

---

### 3.10 System Audit Trail Logging

#### (a) Problem Solved
Provides an immutable, tamper-evident regulatory trail of every login, price change, manual override, batch registration, and settlement cycle.

#### (b) Architectural Logging Model
1. Any sensitive action in backend invokes [`AuditService.logEvent(userId, action, module, details, ipAddress)`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/audit/AuditService.java#L39).
2. Service runs asynchronously (`@Async`) in a detached thread to guarantee audit persistence never introduces latency into checkout or settlement flows.
3. Persists record into PostgreSQL `audit_logs` table (Columns: `id`, `user_id`, `action`, `module`, `details`, `ip_address`, `created_at`).
4. Admin queries logs via `GET /api/audit-logs` rendered in `#view-audit` table.

---

### 3.11 Cross-Origin Bridge Synchronization (Ports 8000 & 8001)

#### (a) Problem Solved
Overcomes browser Same-Origin Policy (SOP) restrictions when running multi-port local environments (Port 8000 Customer POS vs. Port 8001 Admin Panel) without requiring complex backend proxy configurations.

#### (b) Bridge Workflow
```
ADMIN PANEL (Port 8001)                     BRIDGE IFRAME (Port 8000)                   CUSTOMER POS & LED (Port 8000)
       │                                               │                                              │
       │ (1) User modifies price or triggers crash      │                                              │
       │     Calls `broadcastEvent(type, payload)`     │                                              │
       │                                               │                                              │
       │ (2) Dispatches `postMessage(data, '*')` ────► │                                              │
       │     into embedded hidden bridge.html iframe   │ (3) Bridge receives 'message' event          │
       │                                               │     Updates Port 8000 localStorage           │
       │                                               │                                              │
       │                                               │ (4) Broadcasts to BroadcastChannel: ────────►│ (5) Customer POS & LED tabs
       │                                               │     bc.postMessage({ type, payload })        │     receive frame via bc.onmessage
       │                                               │                                              │     Update UI & caches instantly!
       │                                               │                                              │
       │                                               │◄─────────────────────────────────────────────│ (6) POS completes order
       │                                               │ (7) Listens to 'storage' event               │     Writes to localStorage
       │ (8) Receives postMessage ◄────────────────────│     window.parent.postMessage(data, '*')     │     or BroadcastChannel
       │     Refreshes revenue & volume telemetry      │                                              │
```

---

## 4. FULL PROCESS FLOWS (SEQUENCE-STYLE)

### Flow 1: End-to-End Customer Purchase & Real-Time Price Surge
1. **Actor:** Customer.
2. **Action:** Taps **Add to Bill** on *Fresh Mango Juice* (Qty: 2) and selects `UPI` tender on POS.
3. **Component:** [`customer-web/src/index.html`](file:///d:/Juice%20Dynamic%20Price%20Project/customer-web/src/index.html) (`cart-panel`).
4. **API Call:** `POST /api/pos/checkout` with JSON payload `{ items: [{ productId: 1, quantity: 2, cupSizeMl: 250 }], paymentMethod: 'UPI' }`.
5. **Backend Process:** [`POSService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pos/POSService.java) acquires pessimistic lock on `BATCH-MNG-01`, deducts 500ml liquid, saves `sales_orders` and items, increments `order_count` on Mango, and commits transaction.
6. **Response:** HTTP 200 OK with order summary and receipt data.
7. **Settlement Tick:** Scheduler or Admin triggers `PricingSettlementCoordinator.executeSettlement()`. Window $W_0$ registers the 2 cups; $S_w = 2.00$; $R_d = 2.00 / 0.55 = 3.63 \ge 1.10$. Mango surges by $+₹1.00$ (₹25.00 → ₹26.00). Committed to PostgreSQL and Redis.
8. **WebSocket Broadcast:** Dispatches updated prices to `/topic/prices` and `/topic/led-display`.
9. **UI Updates:**
   - **Customer POS:** Receipt modal opens; Mango price card ticks to ₹26.00 with green pulse.
   - **Wall LED Display:** Mango billboard card flashes green (`.px-price-surge`); number animates to ₹26.00; ticker tape scrolls `MANGO: ₹26.00 ▲+1.00`.
   - **Admin Panel:** Revenue and cup KPI counters increment; 20L volume donut chart recalculates; Pricing table row pulses green.

---

### Flow 2: Admin Triggering and Stopping a Market Crash
1. **Actor:** System Administrator.
2. **Action:** Unlocks Safety Interlock and clicks **Trigger Market Crash (3 Mins)**.
3. **Component:** [`admin-panel/src/index.html`](file:///d:/Juice%20Dynamic%20Price%20Project/admin-panel/src/index.html) (`#view-crash`).
4. **API Call:** `POST /api/pricing/market-crash/trigger?durationMinutes=3`.
5. **Backend Process:** [`MarketCrashService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/MarketCrashService.java) saves pre-crash price snapshots in database/Redis, sets all active juices to ₹20.00 floor, sets `crashActive = true`, and starts 180s countdown timer.
6. **Response:** HTTP 200 OK with `MarketCrashStatus` object.
7. **Broadcast:** Pushes alert frame to `/topic/market-crash`.
8. **UI Updates (Start):**
   - **Customer POS:** Top market banner turns pulsing crimson (`#marketCrashBanner`); countdown displays `03:00`; audio siren plays; all drink cards drop to ₹20.00.
   - **Wall LED Display:** Giant full-screen animated emergency siren takeover (`#crashOverlay`) engages with strobe borders and countdown timer.
   - **Admin Panel:** Hero status tag switches to red `🚨 MARKET CRASH ACTIVE`; countdown ticker starts.
9. **Stop Action:** After 180s (or Admin clicks **Stop Crash**), backend invokes `stopMarketCrash()`, restores pre-crash prices from snapshot, and broadcasts stop event.
10. **UI Updates (Stop):** Sirens cease; takeover overlays dismiss; all displays flash emerald green and restore original prices.

---

### Flow 3: Admin Restocking a Depleted 20L Batch
1. **Actor:** Store Manager.
2. **Action:** Clicks **Restock** on depleted container `BATCH-LEM-01`.
3. **Component:** [`admin-panel/src/index.html`](file:///d:/Juice%20Dynamic%20Price%20Project/admin-panel/src/index.html) (`#view-batches`).
4. **API Call:** `POST /api/batches/2/restock?additionalMl=20000`.
5. **Backend Process:** [`JuiceBatchService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/inventory/JuiceBatchService.java) adds 20,000ml to batch record, updates status to `ACTIVE`, logs `BATCH_RESTOCKED` transaction, and creates a system notification.
6. **Response:** HTTP 200 OK with updated batch entity.
7. **UI Updates:**
   - **Admin Batches Tab:** Liquid progress bar animates to 100%; status pill switches from red `DEPLETED` to green `ACTIVE`; low-stock warning banner clears.
   - **Admin Dashboard:** Total liquid volume metric counter animates upward by +20.0 Litres.
   - **Customer POS:** Zesty Lemon card removes "OUT OF STOCK" lock and re-enables "Add to Bill".

---

### Flow 4: Admin Running Sandbox Simulator & Deploying to Production
1. **Actor:** Pricing Administrator.
2. **Action:** Selects *Valencia Orange Juice*, sets Start Price ₹25, Floor ₹18, Ceiling ₹32, and clicks **Run Sandbox Simulation**.
3. **Component:** [`admin-panel/src/index.html`](file:///d:/Juice%20Dynamic%20Price%20Project/admin-panel/src/index.html) (`#view-simulator`).
4. **API Call:** `POST /api/pricing/simulate`.
5. **Backend Process:** [`PricingSimulationService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/PricingSimulationService.java) calculates 10-step trajectory in memory; returns JSON trajectory.
6. **UI Updates (Sandbox):** Simulator timeline table renders step-by-step $S_w$, $R_d$, and price movements; live screens remain completely untouched.
7. **Deploy Action:** Admin clicks **🚀 Deploy Parameters to Live POS**.
8. **API Call:** `POST /api/pricing/deploy` with payload `{ productId: 4, price: 25, minPrice: 18, maxPrice: 32 }`.
9. **Backend Process:** Commits parameters to PostgreSQL `products`, updates Redis, and broadcasts to `/topic/prices`.
10. **UI Updates (Production):** Live POS and LED Display update Orange pricing bounds immediately; success toast confirms deployment.

---

### Flow 5: New Staff Account Creation and First Login
1. **Actor:** Super Administrator.
2. **Action:** Fills Staff Modal form (Username: `cashier_priya`, Role: `CASHIER`, Password: `SecurePosPassword2026!`) and clicks **Save User**.
3. **Component:** [`admin-panel/src/index.html`](file:///d:/Juice%20Dynamic%20Price%20Project/admin-panel/src/index.html) (`#userModal`).
4. **API Call:** `POST /api/users`.
5. **Backend Process:** [`UserController.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/user/UserController.java) hashes password with `BCryptPasswordEncoder`, persists user with role `CASHIER`, and logs `USER_CREATED` audit event.
6. **Response:** HTTP 201 Created.
7. **Staff Login:** Cashier navigates to POS and authenticates via `POST /api/auth/login`.
8. **Backend Process:** Validates BCrypt hash, issues signed stateless JWT containing `ROLE_CASHIER`, and returns token.
9. **UI Updates:** Cashier session initializes; POS unlocks; cashier can bill orders but cannot access Admin routes.

---

### Flow 6: Manual Price Override and Release
1. **Actor:** Store Manager.
2. **Action:** Types `29.00` in manual override input for *Cool Mint Cooler* and clicks **Set Price**.
3. **Component:** [`admin-panel/src/index.html`](file:///d:/Juice%20Dynamic%20Price%20Project/admin-panel/src/index.html) (`#view-pricing`).
4. **API Call:** `POST /api/pricing/products/3/price?price=29.00&pricingMode=MANUAL_OVERRIDE`.
5. **Backend Process:** Sets `current_cup_price = 29.00`, `pricing_mode = 'MANUAL_OVERRIDE'` in PostgreSQL, writes to Redis, logs audit record, and broadcasts update.
6. **UI Updates:**
   - **Admin Table:** Pricing mode badge switches to amber `MANUAL`; price shows ₹29.00; input shows **Release** button.
   - **POS & LED Display:** Cool Mint price updates immediately to ₹29.00.
7. **Subsequent Settlements:** DWMA scheduler cycles execute; Cool Mint demand is logged, but price mutation is skipped due to manual lock.
8. **Release Action:** Manager clicks **🔓 Release Override**. Dispatches `POST /api/pricing/products/3/release-override`.
9. **UI Updates:** Badge reverts to green `DYNAMIC`; subsequent settlement rounds resume automated DWMA price adjustments.

---

## 5. FEATURE INVENTORY TABLE

| Feature Name | Screen(s) It Appears On | User Role Required | Status | Backend Dependencies | Notes & Edge Cases |
| :--- | :--- | :---: | :---: | :--- | :--- |
| **Ecosystem Frame Host** | [`index.html`](file:///d:/Juice%20Dynamic%20Price%20Project/index.html) | Any | **Working** | None (Static HTML/CSS Grid) | Manages Dual Split, Triple Grid, and Standalone layouts. |
| **Dynamic Beverage Menu Grid** | POS (`customer-web`) | Cashier / Public | **Working** | `ProductRepository`, Redis, STOMP | Displays live prices, delta badges, and 30-PT canvas sparklines. |
| **Sticky Order Cart Drawer** | POS (`customer-web`) | Cashier / Public | **Working** | Client In-Memory State | Increments cups, calculates volume (ML), multi-tender selection. |
| **Transaction-Safe Checkout** | POS (`customer-web`) | Cashier / Public | **Working** | `POSService`, `JuiceBatchService`, PostgreSQL | Pessimistic locking on batches; client idempotency token prevents double-billing. |
| **Printable POS Receipt** | POS (`customer-web`) | Cashier / Public | **Working** | `POSService` Response | Thermal printer formatted dialog with Invoice ID and volume status. |
| **Continuous Stock Ticker** | LED Display | Public | **Working** | STOMP `/topic/led-display`, Canvas API | High-contrast ticker tape marquee with green/red shift arrows. |
| **30-Point Sparkline Graphs** | LED Display & Admin | Public / Admin | **Working** | Canvas 2D Context, `price_histories` | Plots historical 30-settlement price movements with beacon dots. |
| **Market Crash Emergency** | POS, LED & Admin | Admin / SuperAdmin | **Working** | `MarketCrashService`, Redis, STOMP | 3-minute emergency countdown; drops prices to ₹20 floor; audio sirens. |
| **Executive Telemetry Cards** | Admin Dashboard | Admin / SuperAdmin | **Working** | `ReportController`, HikariCP | Real-time animated counters for Revenue, Cups, Batches, Volume. |
| **Interactive ApexCharts** | Admin Dashboard | Admin / SuperAdmin | **Working** | ApexCharts CDN, `ReportController` | Revenue velocity curves and 20L volume breakdown donut/pie charts. |
| **20L Commercial Batch Manager** | Admin Batches Tab | Admin / SuperAdmin | **Working** | `JuiceBatchService`, PostgreSQL | Registers 20,000ml batches, tracks liquid levels, restocks, depletions. |
| **DWMA Demand Visualizer** | Admin Pricing Tab | Admin / SuperAdmin | **Working** | `PriceAdjustmentService` | Pipeline visualizer displaying $W_0, W_1, W_2$, $S_w$, target, and $R_d$. |
| **Settlement Timing Switch** | Admin Pricing Tab | SuperAdmin | **Working** | `PricingConfigurationService` | Dropdown for 10s, 30s, 1m, 2m, 5m, 10m, 15m settlement cycles. |
| **Manual Price Override** | Admin Pricing Tab | Admin / SuperAdmin | **Working** | `PricingController`, PostgreSQL | Freezes product price; bypasses automated DWMA cycles. |
| **Bulk Edit ("Every Juice")** | Admin Pricing Modal | Admin / SuperAdmin | **Working** | `POSController` (`Promise.all`) | 1080px command center; per-row custom reset boxes; parallel ~150ms save. |
| **Pricing Sandbox Simulator** | Admin Simulator Tab | Admin / SuperAdmin | **Working** | `PricingSimulationService` | Isolated 10-step trajectory testing; atomic production deployment. |
| **RBAC Staff User Manager** | Admin Users Tab | SuperAdmin | **Working** | `UserController`, Spring Security | Creates, edits, and deletes staff accounts with BCrypt encryption. |
| **CSV & PDF Report Generator** | Admin Reports Tab | Admin / SuperAdmin | **Working** | Client Blob / Window Print | Exports Sales, Inventory, Pricing, and Audit ledgers. |
| **System Security Audit Trail** | Admin Audit Tab | Admin / SuperAdmin | **Working** | `AuditService`, PostgreSQL | Asynchronous immutable log of logins, price changes, and settlements. |
| **Real-Time Notification Drawer**| Admin Top Bar | Admin / SuperAdmin | **Working** | `NotificationService`, STOMP | Alerts on low inventory, depleted batches, crashes, and resets. |
| **Cross-Origin Bridge** | `bridge.html` (Hidden) | System | **Working** | `postMessage` + `BroadcastChannel` | Relays events between Port 8000 and Port 8001 origins. |
| **Simulated Live Trading Bot** | Admin Top Bar | Admin / SuperAdmin | **Partial** | `LiveMarketSimulatorService` | Background automated customer purchase simulator (toggleable). |
| **Prometheus & Kafka Metrics** | Admin Telemetry | Admin / SuperAdmin | **Partial** | Actuator / Docker Scaffold | Health badge monitors port 9092 & 9090; Kafka ingestion inert. |

---

## 6. DATA LIFECYCLE MAP

### 6.1 Core Domain Entities

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                             DATA LIFECYCLE MAP                                              │
├──────────────────────┬────────────────────────┬────────────────────────┬───────────────────┬────────────────┤
│ Entity Name          │ Creation Point         │ Authoritative Storage  │ Read Points       │ Mutation Topic │
├──────────────────────┼────────────────────────┼────────────────────────┼───────────────────┼────────────────┤
│ Product              │ #newFlavourModal /     │ PostgreSQL `products`  │ POS Grid, LED TV, │ /topic/prices  │
│                      │ Flyway V1 Seed         │ & Redis `live_price:*` │ Admin Pricing Tab │ /topic/products│
├──────────────────────┼────────────────────────┼────────────────────────┼───────────────────┼────────────────┤
│ JuiceBatch (20L)     │ #newBatchModal         │ PostgreSQL             │ Admin Batches Tab,│ /topic/batches │
│                      │ (`POST /api/batches`)  │ `juice_batches`        │ POS Stock Checks  │ /topic/invent..│
├──────────────────────┼────────────────────────┼────────────────────────┼───────────────────┼────────────────┤
│ SalesOrder & Items   │ POS Cart Checkout      │ PostgreSQL             │ Admin Dashboard,  │ /topic/orders  │
│                      │ (`POST /pos/checkout`) │ `sales_orders` & items │ Sales Reports     │                │
├──────────────────────┼────────────────────────┼────────────────────────┼───────────────────┼────────────────┤
│ PriceHistory         │ Automated Settlement / │ PostgreSQL             │ Sparkline Graphs, │ /topic/prices  │
│                      │ Crash / Manual Adjust  │ `price_histories`      │ Audit Reports     │ /topic/led-disp│
├──────────────────────┼────────────────────────┼────────────────────────┼───────────────────┼────────────────┤
│ MarketCrashSnapshot  │ Crash Initiation       │ PostgreSQL & Redis     │ Crash Stop Event  │ /topic/market- │
│                      │ (`MarketCrashService`) │ `market_crash_snaps`   │ (Restoration)     │ crash          │
├──────────────────────┼────────────────────────┼────────────────────────┼───────────────────┼────────────────┤
│ User (Staff Account) │ #userModal             │ PostgreSQL `users`     │ Admin Users Tab,  │ N/A (REST API) │
│                      │ (`POST /api/users`)    │ (BCrypt Encrypted)     │ JWT Auth Filter   │                │
├──────────────────────┼────────────────────────┼────────────────────────┼───────────────────┼────────────────┤
│ SystemNotification   │ NotificationService    │ PostgreSQL             │ Admin Notification│ /topic/notifi- │
│                      │ (Events & Warnings)    │ `system_notifications` │ Drawer & Bell     │ cations        │
├──────────────────────┼────────────────────────┼────────────────────────┼───────────────────┼────────────────┤
│ AuditLog             │ AuditService (@Async)  │ PostgreSQL             │ Admin Audit Trail │ N/A (REST API) │
│                      │ (Security Actions)     │ `audit_logs`           │ View              │                │
└──────────────────────┴────────────────────────┴────────────────────────┴───────────────────┴────────────────┘
```

---

### 6.2 Caching Strategy Matrix

| Entity / Data Slice | Client In-Memory Cache | LocalStorage Cache | Redis 7 Caching | Database Fresh Fetch |
| :--- | :---: | :---: | :---: | :---: |
| **Live Product Prices** | `liveProductsCache` | `pubexchange_dynamic_products` | `pubexchange:products` | On initial page load & post-reconnect |
| **Active 20L Batches** | `batchesData` | `pubexchange_batches` | `pubexchange:inventory` | On Batches tab activation |
| **Price History (Sparklines)**| `adminPriceHistoryMap` | N/A | Cached in JVM | `GET /api/pricing/history/{id}` |
| **Pricing Engine Timing** | `activePricingTiming` | `pubexchange_settlement_interval` | Cached in Coordinator | `GET /api/pricing/timing` |
| **Market Crash Flag** | `isAdminCrashActive` | `pubexchange_market_crash_active` | `pubexchange:market-crash` | `GET /api/pricing/market-crash/status` |
| **Staff Users List** | `usersData` | N/A | None (Direct DB) | `GET /api/users` on tab open |
| **Audit Logs** | N/A (Direct Render) | N/A | None (Direct DB) | `GET /api/audit-logs` on tab open |

---

## 7. EDGE CASES & FAILURE HANDLING

### 7.1 WebSocket Connection Drops Mid-Session
- **Symptom:** Cashier POS or Wall LED TV loses network connectivity to Spring Boot port 8088.
- **Client Recovery Handling:**
  - StompJS client detects disconnect via missing heartbeat (10,000ms threshold).
  - `#posNetworkBanner` smoothly slides down on POS ([`CHANGE_LOG.md Section 2.7`](file:///d:/Juice%20Dynamic%20Price%20Project/CHANGE_LOG.md#L41)), alerting staff that live pricing updates are paused.
  - Automatic reconnection exponential backoff engages (reconnect attempts at 1s, 2s, 5s, 10s).
  - While disconnected, Customer POS continues to function using client-cached prices (`productsCache`).
  - Upon reconnection, `#posNetworkBanner` slides away, and the client immediately fires `GET /api/pos/products` to reconcile any price or stock adjustments that occurred during the outage.

---

### 7.2 Concurrent Edits by Multiple Administrators
- **Symptom:** Admin A and Admin B both open the Bulk Edit modal and attempt to submit conflicting base prices or circuit limits for the same juice.
- **System Handling:**
  - Product records utilize an atomic version counter: `price_version` (incremented on every update).
  - The backend executes product saves inside isolated database transactions (`@Transactional`).
  - The last committed transaction wins in PostgreSQL, immediately bumping `price_version` and dispatching an authoritative `/topic/prices` STOMP frame.
  - The losing Admin's UI receives the STOMP broadcast, updates its in-memory `liveProductsCache`, and reflects the newly committed parameters.

---

### 7.3 Checkout Attempt with Insufficient Batch Volume
- **Symptom:** A customer attempts to purchase 4 cups of Lemon Juice (1,000ml), but the active container only has 300ml remaining and no backup batch is registered.
- **System Handling:**
  - Inside [`JuiceBatchService.deductBatchVolume()`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/inventory/JuiceBatchService.java#L123), the total available stock across all active batches is summed under a pessimistic write lock.
  - If total stock is less than required, the service throws an `IllegalStateException`: `"Insufficient inventory for product ID 2: Requested 1000 ml, but total available stock is only 300 ml"`.
  - The checkout transaction rolls back atomically. Zero milliliters are deducted, and no sales order is recorded.
  - Backend returns HTTP 400 Bad Request with standardized error envelope.
  - Customer POS catches error, cancels the receipt modal, displays a prominent error toast: `"Cannot complete order: Insufficient Lemon Juice inventory remaining."`, and marks the product card as out of stock.

---

### 7.4 Market Crash Triggered While Crash Already Active
- **Symptom:** Admin clicks Trigger Market Crash while another crash event is already running.
- **System Handling:**
  - [`MarketCrashService.java` (Line 216)](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/MarketCrashService.java#L216) synchronizes on the service instance (`synchronized`).
  - The method checks `isCrashActive()`. If active, the system rejects nested snapshot creation to prevent overwriting the true pre-crash baseline with the discounted crash price.
  - Returns current status with remaining timer seconds without resetting snapshots.

---

### 7.5 Database or Redis Unreachability
- **Redis Down:** The backend wraps Redis synchronization inside non-fatal `try/catch` blocks ([`PricingSettlementCoordinator.java` Line 284](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/PricingSettlementCoordinator.java#L284)). If Redis is unreachable, operations gracefully fall back to querying PostgreSQL directly.
- **PostgreSQL Down:** The backend returns HTTP 503 Service Unavailable. Frontends display the `#posNetworkBanner` and retain client-side cached data in `localStorage` until the database connection pool recovers.

---

## 8. OPEN GAPS & PRIORITY RECOMMENDATIONS

During this exhaustive code and architecture audit, the following discrepancies, orphaned code artifacts, and recommended optimizations were identified:

### 8.1 Incomplete, Inconsistent, or Untested Implementations
1. **Kafka Telemetry Health Indicator (Orphaned / Unwired):**
   - *Observation:* The Admin Dashboard Telemetry Grid displays a status badge for "Kafka 9092". However, an audit of `backend/src/main/java/com/retailpos` reveals no active Kafka producers or consumers; messaging is handled entirely via STOMP WebSockets and Redis.
   - *Impact:* Minimal runtime impact, but the health badge may report false positive/negative status.
2. **Vestigial Demand Calculation Services:**
   - *Observation:* [`DemandCalculationService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/DemandCalculationService.java), [`StockPressureService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/StockPressureService.java), and [`TimeFactorService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/TimeFactorService.java) exist in the codebase from an earlier multi-factor pricing experiment, but are not invoked by the authoritative DWMA engine ([`PriceAdjustmentService.java`](file:///d:/Juice%20Dynamic%20Price%20Project/backend/src/main/java/com/retailpos/pricing/PriceAdjustmentService.java)).
   - *Impact:* Harmless dead code, but creates confusion regarding which algorithm is authoritative.
3. **Vestigial TypeScript Stubs:**
   - *Observation:* [`admin-panel/src/app/app.component.ts`](file:///d:/Juice%20Dynamic%20Price%20Project/admin-panel/src/app/app.component.ts) and [`customer-web/src/app/app.component.ts`](file:///d:/Juice%20Dynamic%20Price%20Project/customer-web/src/app/app.component.ts) are non-functional Angular stubs from initial scaffolding. Production code runs exclusively from the self-contained HTML/JS files in `/src`.

---

### 8.2 Prioritized Action Roadmap

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                PRIORITY IMPLEMENTATION & HARDENING ROADMAP                             │
├──────────┬──────────────────────────────────────────┬──────────┬───────────────────────────────────────┤
│ Priority │ Task Description                         │ Risk     │ Recommended Action                    │
├──────────┼──────────────────────────────────────────┼──────────┼───────────────────────────────────────┤
│ **P1**   │ Deprecate or Clean Legacy Demand Services│ Low      │ Archive `DemandCalculationService`,   │
│          │                                          │          │ `StockPressureService`, and           │
│          │                                          │          │ `TimeFactorService` to prevent        │
│          │                                          │          │ developer confusion.                  │
├──────────┼──────────────────────────────────────────┼──────────┼───────────────────────────────────────┤
│ **P2**   │ Clean Skeleton Angular TypeScript Stubs  │ Zero     │ Delete `src/app/app.component.ts` in  │
│          │                                          │          │ both apps to keep repository clean.   │
├──────────┼──────────────────────────────────────────┼──────────┼───────────────────────────────────────┤
│ **P3**   │ Harmonize Kafka Telemetry Badge          │ Low      │ Update `TelemetryController` to check │
│          │                                          │          │ active Redis/DB health rather than    │
│          │                                          │          │ probing an unused Kafka port.         │
├──────────┼──────────────────────────────────────────┼──────────┼───────────────────────────────────────┤
│ **P4**   │ Automated Database Pruning Routine       │ Medium   │ Schedule automated cleanup for        │
│          │                                          │          │ `price_histories` older than 30 days  │
│          │                                          │          │ to prevent table bloat over time.     │
└──────────┴──────────────────────────────────────────┴──────────┴───────────────────────────────────────┘
```

---

*This document serves as the complete, authoritative, zero-regression technical implementation blueprint for the Noida Pub Exchange & Juice Bar platform.*
