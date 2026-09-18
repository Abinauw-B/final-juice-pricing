# Comprehensive Admin Pricing Control & Error Occurrence Audit Report
**Project:** Mojito Exchange (Juice Bar Stock Exchange / Snack Exchange)  
**Target Audience:** Engineering Leads, System Architects, LLM/ChatGPT Context Ingestion  
**Audit Date:** September 18, 2026  
**Scope:** End-to-end Codebase Audit of Frontend, Backend, PostgreSQL, Redis, STOMP/WebSocket, Schedulers, POS, and Simulator

---

## EXECUTIVE SUMMARY & PRODUCTION READINESS DASHBOARD

| System Component | Production Readiness | Primary Code Finding |
| :--- | :---: | :--- |
| **Admin Pricing Control** | 🚨 **NOT READY** | Mode collision (`MANUAL_LOCK` vs `DYNAMIC`), manual sales offset (`weighted_sales`) skewing algorithmic price discovery, and multi-admin silent overwrite risk. |
| **Pricing Engine** | ⚠️ **NOT READY** | Bayesian smoothing implementation matches theory ($K=1.5$), but micro-interval downward decay is locked by a hardcoded 60-second cooldown (`Math.max(60, intervalSec)`), and volume gating suppresses surge cycles. |
| **Checkout Price Authority** | ⚠️ **NOT READY** | Server enforces PostgreSQL database current price if no lock token is passed; however, `PriceLockService` omits `productId` validation during token redemption, allowing cross-product price hijacking. |
| **Database Consistency** | ⚠️ **NOT READY** | `Product` lacks JPA `@Version` optimistic locking; `POSController` permits invalid/negative prices without lower boundary checks. |
| **Redis Consistency** | ✅ **READY** | Redis acts strictly as an ephemeral cache; backend never falls back to Redis for authoritative price reads or checkout. Redis crashes do not corrupt state. |
| **WebSocket / STOMP** | ⚠️ **NOT READY** | In `PricingConfigurationService`, STOMP updates are dispatched **inside** the `@Transactional` method before the database transaction actually commits. |
| **Market Crash Engine** | 🚨 **NOT READY** | Triggering a market crash while another crash is already active overwrites pre-crash snapshots with the crash floor price, permanently wiping out original prices. |
| **Scheduler System** | 🚨 **NOT READY** | Admin frontend clients execute autonomous countdown timers that fire force-settlements (`POST /api/pricing/evaluate`), duplicating the backend scheduler. |
| **Security & Permissions** | 🚨 **CRITICAL RISK** | `SecurityConfig.java:93` contains `.requestMatchers("/api/pricing/**", "/api/pos/**", "/api/admin/pricing/**", ...).permitAll()`, leaving all pricing endpoints publicly modifiable. |
| **Concurrency / Purchases** | ⚠️ **NOT READY** | Inactive (`isActive=false`) products can still be purchased; concurrent admin price updates can overwrite active settlements without version gating. |

---

## 1. COMPLETE MAP OF ALL ADMIN PRICING CONTROLS

The following matrix maps every admin-configurable control across the UI, API, service, and database layers:

| Admin Control | Current Default | Allowed Range | What It Changes | Backend API | DB Table / Column | Immediate Effect? | Next Cycle Effect? |
| :--- | :---: | :---: | :--- | :--- | :--- | :---: | :---: |
| **Base Price** (`defaultCupPrice`) | ₹25.00 | ₹1.00 – ₹10,000+ ($P_{min} \le P_{base} \le P_{max}$) | Baseline anchor price for resets and trend calculation | `PUT /api/admin/pricing/products/{id}/config`, `PUT /api/pricing/config` | `products.default_cup_price`, `pricing_configurations(DEFAULT_CUP_PRICE)` | No | Yes (used in reset & trend pct) |
| **Current Price** (`currentCupPrice`) | ₹25.00 | $P_{min} \le P \le P_{max}$ | Overwrites active product price and sets mode to `MANUAL_LOCK` | `POST /api/pricing/products/{id}/price`, `PUT /api/pos/products/{id}` | `products.current_cup_price` | **Yes** (updates DB, Redis, WS) | Yes (holds price until released) |
| **Floor Price** (`minCupPrice`) | ₹20.00 | ₹0.00 – $P_{max} - 1$ | Absolute hard lower circuit breaker clamp | `PUT /api/admin/pricing/products/{id}/config`, `PUT /api/pricing/config` | `products.min_cup_price`, `pricing_configurations(MIN_CUP_PRICE)` | No | Yes (clamps $P_{new} \ge P_{min}$) |
| **Ceiling Price** (`maxCupPrice`) | ₹30.00 | $P_{min} + 1$ – No limit | Absolute hard upper circuit breaker clamp | `PUT /api/admin/pricing/products/{id}/config`, `PUT /api/pricing/config` | `products.max_cup_price`, `pricing_configurations(MAX_CUP_PRICE)` | No | Yes (clamps $P_{new} \le P_{max}$) |
| **Demand Target** (`targetSales`) | 0.55 cups/min | $> 0.00$ cups/min | 1-minute target baseline for Bayesian demand ratio $R_d$ | `PUT /api/admin/pricing/products/{id}/config` | `products.target_sales_per_1_minute` | No | Yes (normalizes $T_{norm}$) |
| **Demand Ratio** ($R_d$) | 1.0000 | System Calculated | Ratio of weighted sales to target sales | None (Read Only) | None (Transient calculation) | No | Yes (drives $\Delta P$) |
| **Weight W0** | 1.0000 | $\ge 0.0000$ | Weight for current interval $[t-\Delta t, t)$ | `PUT /api/pricing/config` | `pricing_configurations(WEIGHT_W0)` | No | Yes (DWMA numerator) |
| **Weight W1** | 0.5000 | $\ge 0.0000$ | Weight for previous interval $[t-2\Delta t, t-\Delta t)$ | `PUT /api/pricing/config` | `pricing_configurations(WEIGHT_W1)` | No | Yes (DWMA numerator) |
| **Weight W2** | 0.2500 | $\ge 0.0000$ | Weight for 2-intervals-ago $[t-3\Delta t, t-2\Delta t)$ | `PUT /api/pricing/config` | `pricing_configurations(WEIGHT_W2)` | No | Yes (DWMA numerator) |
| **Bayesian Smoothing ($K$)** | 1.5000 | Hardcoded (1.5) | Prior pseudo-count for demand smoothing | None (Hardcoded in Java) | None | No | Yes (prevents micro-bursts) |
| **Pricing Interval** ($\Delta t$) | 60s (or 10s) | 5s – 86400s (24h) | Cadence of scheduled DWMA pricing settlements | `PUT /api/pricing/timing`, `PUT /api/pricing/config` | `pricing_configurations(SETTLEMENT_INTERVAL_SECONDS)` | **Yes** (resets scheduler timer) | Yes (scales $T_{norm}$) |
| **Price Increase Amount** | ₹1.00 | $\le ₹1.00$ (clamped) | Quantum of upward price change per cycle | `PUT /api/pricing/config` | `pricing_configurations(INCREASE_STEP)` | No | Yes (must match $\{+1, 0, -1\}$) |
| **Price Decrease Amount** | ₹1.00 | $\le ₹1.00$ (clamped) | Quantum of downward decay per cycle | `PUT /api/pricing/config` | `pricing_configurations(PRICE_DECREASE_STEP)` | No | Yes (must match $\{+1, 0, -1\}$) |
| **No-Demand Behavior** | -₹1.00 decay | Hardcoded logic | Decrements price by ₹1 if cooldown has passed | None (Coded in `PriceAdjustmentService`) | None | No | Yes (subject to 60s cooldown) |
| **Low-Demand Threshold** | 0.5000 | $< \text{Stable Lower}$ | Threshold below which severe decay occurs | `PUT /api/pricing/config` | `pricing_configurations(LOW_DEMAND_THRESHOLD)` | No | Yes (categorizes demand) |
| **High-Demand Threshold** | 1.1000 | $> \text{Stable Upper}$ | Threshold above which price surge is eligible | `PUT /api/pricing/config` | `pricing_configurations(HIGH_DEMAND_THRESHOLD)` | No | Yes (triggers surge if volume met) |
| **Price Lock Duration** | 10 seconds | Hardcoded (10s) | Duration a quoted price remains guaranteed at POS | None (`PriceLockService.java:41`) | Memory / Redis `quote:{id}` | **Yes** (sets TTL) | No |
| **Market Crash Duration** | 180 seconds | $> 0$ seconds | Length of emergency market crash event | `PUT /api/pricing/config`, `POST /crash/trigger` | `pricing_configurations(MARKET_CRASH_DURATION_SECONDS)` | **Yes** (when triggered) | Yes (suspends DWMA) |
| **Crash Floor Behavior** | ₹20.00 | $\ge P_{min}$ | Prices drop to $\max(\text{CrashPrice}, P_{min})$ | `PUT /api/pricing/config` | `pricing_configurations(MARKET_CRASH_PRICE)` | **Yes** (on crash trigger) | Yes (clamps during crash) |
| **Manual Price Override** | Active price | $P_{min} \le P \le P_{max}$ | Locks beverage at specific price | `POST /api/pricing/products/{id}/price` | `products.current_cup_price`, `pricing_mode` | **Yes** (immediate lock) | Yes (holds price) |
| **Manual Price Lock** | Toggle flag | `DYNAMIC` / `MANUAL_LOCK` | Halts algorithmic adjustments for product | `POST /api/pricing/products/{id}/price` | `products.pricing_mode` | **Yes** | Yes (engine skips product) |
| **Product Active Status** | `true` | Boolean | Determines if product is listed on POS/Engine | `PUT /api/pos/products/{id}` | `products.is_active` | **Yes** | Yes (omitted from `findAllActiveWithLock`) |
| **Product Target Sales** | 0.55 | $> 0.00$ | Product-specific 1-min baseline demand target | `PUT /api/admin/pricing/products/{id}/config` | `products.target_sales_per_1_minute` | No | Yes (normalizes target) |
| **Weighted Sales Offset** | 0.00 | $\ge 0.00$ | Manual demand injection added directly to $S_w$ | `PUT /api/admin/pricing/products/{id}/config` | `products.weighted_sales` | No | **Yes (dangerously forces surge)** |
| **Simulator Bot Toggle** | `false` | Boolean | Toggles background automated trade bot (every 12s) | `POST /api/pricing/simulator/live-bot/toggle` | Memory `LiveMarketSimulatorService.enabled` | **Yes** | Yes (places real POS orders) |
| **Market Pause** | `false` | Boolean | Halts all DWMA price changes across the exchange | `POST /api/pricing/pause`, `POST /resume` | Memory `PriceAdjustmentService.marketPaused` | **Yes** | Yes (holds all prices) |

---

## 2. DETERMINE ADMIN AUTHORITY & BOUNDARY VALIDATION

Below is the conceptual and code-level evaluation of 23 critical boundary cases:

| Input / Edge Case | Where Checked | Code Validation Status | Actual System Behavior | Safety Verdict |
| :--- | :--- | :--- | :--- | :--- |
| **Negative prices (e.g. -₹5)** | `PricingConfigurationService:555`, `PriceAdjustmentService:573`, `POSController:149` | **Partial** (`POSController` misses negative check) | Rejected in `PricingConfigService` and `PriceAdjustmentService`. **Accepted** if updated via `PUT /api/pos/products/{id}` because `POSController` only checks `effectiveMin >= effectiveMax`. | 🚨 **VULNERABLE** |
| **₹0 Price** | `PricingConfigurationService:555` | Allowed ($P \ge 0$) | Allowed if Floor is set to 0. Drinks can be priced at ₹0.00. | ⚠️ **HIGH RISK** |
| **Decimal prices (e.g. ₹25.75)** | Nowhere | **Not validated** | Allowed. System accepts decimals in manual price and product config. When DWMA runs $\pm ₹1$, the 75 paise fraction persists indefinitely. | ⚠️ **DEFECT** |
| **Extremely large prices (e.g. ₹100,000)** | Nowhere | **No upper ceiling cap** | Allowed. Max price can be set to ₹999,999,999.00 without triggering validation errors. | ⚠️ **MEDIUM RISK** |
| **Floor > Base** | `PricingConfigurationService:561`, `PriceAdjustmentService:874` | Enforced | Throws `IllegalArgumentException: "Default price cannot be below minimum floor price"`. Rejected with HTTP 400. | ✅ **SAFE** |
| **Base > Ceiling** | `PricingConfigurationService:564`, `PriceAdjustmentService:877` | Enforced | Throws `IllegalArgumentException: "Default price cannot exceed maximum ceiling price"`. Rejected with HTTP 400. | ✅ **SAFE** |
| **Floor = Ceiling** | `PricingConfigurationService:558`, `PriceAdjustmentService:871` | Enforced | Throws `IllegalArgumentException: "Maximum price must be strictly greater than minimum price"`. Rejected with HTTP 400. | ✅ **SAFE** |
| **Ceiling < Current Price** | `PricingConfigurationService:452`, `PriceAdjustmentService:883` | Enforced on product; **Missed on Global Config** | Rejected on product config update. However, if Global Config lowers `MAX_CUP_PRICE`, existing product prices are **not** clamped immediately and remain above ceiling until edited. | ⚠️ **INCONSISTENT** |
| **Negative demand target** | `PricingConfigurationService:431` | Enforced | Throws `IllegalArgumentException: "Target sales must be greater than 0"`. Rejected. | ✅ **SAFE** |
| **Zero demand target ($T=0$)** | `PricingConfigurationService:431` | Enforced | Throws `IllegalArgumentException: "Target sales must be greater than 0"`. Rejected. | ✅ **SAFE** |
| **Negative weights ($W < 0$)** | `PricingConfigurationService:567-575` | Enforced | Throws `IllegalArgumentException: "Weight W0 cannot be negative"`. Rejected. | ✅ **SAFE** |
| **Extremely large weights ($W=10,000$)** | Nowhere | **No upper bound** | Allowed. Weights can be set to arbitrary magnitudes, which can distort DWMA division. | ⚠️ **LOW RISK** |
| **Negative pricing interval** | `PricingConfigurationService:617` | Enforced | `isValidInterval(seconds)` checks `seconds >= 5 && seconds <= 86400`. Rejected with HTTP 400. | ✅ **SAFE** |
| **0-second interval** | `PricingConfigurationService:617` | Enforced | Rejected by `isValidInterval(0)` returning `false`. | ✅ **SAFE** |
| **1-second interval** | `PricingConfigurationService:617` | Enforced | Rejected because minimum permitted interval is 5 seconds. | ✅ **SAFE** |
| **Very large interval (>24h)** | `PricingConfigurationService:617` | Enforced | Rejected because maximum permitted interval is 86,400 seconds (24 hours). | ✅ **SAFE** |
| **Negative crash duration** | `PricingConfigurationService:582`, `MarketCrashService:234` | Enforced | Global config throws error if $\le 0$. Trigger API falls back to configured default (180s). | ✅ **SAFE** |
| **0-second crash duration** | `PricingConfigurationService:582` | Enforced | Rejected in global config. API trigger with 0 falls back to default 180s. | ✅ **SAFE** |
| **Extremely long crash duration** | Nowhere | **No upper bound** | Duration of 1,000,000 minutes can be passed to `triggerMarketCrash(durationMinutes)`. Freezes the market indefinitely. | ⚠️ **HIGH RISK** |
| **Negative price lock duration** | `PriceLockService:41` | Hardcoded | Cannot be modified by Admin; hardcoded to `now.plusSeconds(10)`. | ✅ **SAFE** |
| **Invalid product IDs in API** | `PriceAdjustmentService:200`, `POSService:395` | Enforced | Throws `IllegalArgumentException: "Product not found with ID: ..."`. Transaction rolls back. | ✅ **SAFE** |
| **Disabled product receiving checkout** | `POSService:394` | **MISSING CHECK** | `posService.processCheckout()` executes `productRepository.findById()` but **never checks `product.getIsActive()`**. Customers/bots can purchase disabled products! | 🚨 **VULNERABLE** |
| **Duplicate configurations** | Database constraint | Enforced | `pricing_configurations` has unique constraint `uq_pricing_config_key_product(setting_key, product_id)`. | ✅ **SAFE** |

---

## 3. ADMIN VS SYSTEM AUTHORITY SEPARATION

The following table categorizes system variables into who should control them versus who actually controls them:

| Parameter / Action | Intended Authority | Actual Authority in Code | Risk Level | Code Finding & Location |
| :--- | :--- | :--- | :---: | :--- |
| **Base Price ($P_{base}$)** | Admin | Admin | ✅ Low | Configured via `PricingConfigurationService.java:483`. |
| **Floor & Ceiling Bounds** | Admin | Admin | ✅ Low | Configured via `PricingConfigurationService.java:484-485`. |
| **Demand Target ($T$)** | Admin | Admin | ✅ Low | Configured via `PricingConfigurationService.java:471`. |
| **Settlement Cadence ($\Delta t$)** | Admin | Admin | ✅ Low | Configured via `PricingConfigurationService.java:329`. |
| **DWMA Weights ($W_0, W_1, W_2$)** | Admin | Admin | ✅ Low | Configured via `PricingConfigurationService.java:330-332`. |
| **Current Live Price ($P_{current}$)** | **System Only** | **Admin Overridable** | 🚨 **HIGH RISK** | Admin can directly overwrite `currentCupPrice` via `POST /api/pricing/products/{id}/price` or `PUT /api/pos/products/{id}`, bypassing the algorithmic engine and locking the product into `MANUAL_LOCK`. |
| **Weighted Sales Base Offset** | **System Only** | **Admin Overridable** | 🚨 **HIGH RISK** | Admin can set `product.weightedSales` via `PUT /api/admin/pricing/products/{id}/config`. In `PriceAdjustmentService.java:367`, this is added directly to live sales ($S_w = \text{adminOffset} + \text{dwmaLiveSales}$), creating artificial surges. |
| **Actual Checkout Price** | **System Only** | System Only | ✅ Low | POS uses PostgreSQL `currentCupPrice` or valid unexpired lock token. |
| **Demand Calculation** | **System Only** | System Only | ✅ Low | `PriceAdjustmentService.java:329-331` queries actual sales from `sales_order_items`. |
| **Price Movement ($\Delta P$)** | **System Only** | System Only | ✅ Low | Engine strictly enforces $\Delta P \in \{+1.00, 0.00, -1.00\}$ via `PricingConfigurationService.validatePriceMovement()`. |
| **Price History Logging** | **System Only** | System Only | ✅ Low | Written during settlements and admin actions. |
| **Crash Restoration** | **System Only** | System Only | ⚠️ Medium | System restores snapshot, but double-triggering can overwrite snapshot prices. |
| **Settlement Execution** | **System Only** | **Admin Triggerable** | ⚠️ Medium | Admin can invoke `POST /api/pricing/evaluate` to force settlements at any time. |

---

## 4. END-TO-END PRICE FLOW TRACE

The following trace maps the complete path of a price from Admin configuration to customer visibility:

```text
[ADMIN UI: admin-panel/src/index.html]
   ↓ (1) User inputs Base=₹25, Floor=₹20, Ceiling=₹30, Target=0.55 cups/min
[Frontend API Request: apiFetch()]
   ↓ (2) PUT /api/admin/pricing/products/1/config { minCupPrice: 20, maxCupPrice: 30, defaultCupPrice: 25, targetSales: 0.55 }
[Controller: PricingController.java:383]
   ↓ (3) Invokes updateProductConfig(productId=1, productConfig, actor="ADMIN")
[Service & Validation: PricingConfigurationService.java:417-455]
   ↓ (4) Validates: min >= 0, min < max, base >= min, base <= max. Increments version.
[PostgreSQL Commit: ProductRepository.saveAndFlush()]
   ↓ (5) Persists to table `products` (columns: min_cup_price, max_cup_price, default_cup_price, target_sales_per_1_minute)
[Pricing Engine Coordinator: PricingSettlementCoordinator.java:186]
   ↓ (6) Scheduled trigger or force trigger runs. Acquires PG Advisory Lock (ID=788325001L) + JVM ReentrantLock.
[Price Calculation: PriceAdjustmentService.java:306-460]
   ↓ (7) Computes W0, W1, W2 sales; normalizes Tnorm; calculates Bayesian Rd; determines deltaP (+1, 0, -1); clamps to [Floor, Ceiling].
[PostgreSQL Update: ProductRepository.saveAndFlush()]
   ↓ (8) Updates `products.current_cup_price` = ₹26.00, increments `price_version`, writes `price_history`. Transaction commits.
[Redis Cache Update: PricingRedisRepository.java:27]
   ↓ (9) Writes "price:juice:1" -> "26.00", increments "market:version".
[WebSocket / STOMP: SimpMessagingTemplate.convertAndSend()]
   ↓ (10) Dispatches to /topic/prices, /topic/led-display, /topic/settlement.
[Customer Web POS / LED Display: customer-web/src/index.html & led-display.html]
   ↓ (11) Receives STOMP payload, updates DOM price cards with live animation.
```

### Flow Component Specifications

| Step | File | Class | Method | API / Channel | DB Table / Key | Validation / Guard | Failure Mode & Recovery |
| :---: | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **1-2** | `admin-panel/src/index.html` | Client JS | `saveGlobalPricingConfiguration()` | `PUT /api/admin/pricing/config` | N/A | Client bounds check | Network error toast; retries on next save |
| **3** | `PricingController.java` | `PricingController` | `updateConfig()` | `PUT /api/pricing/config` | N/A | Spring Jackson deserialization | 400 Bad Request if types mismatched |
| **4** | `PricingConfigurationService.java` | `PricingConfigurationService` | `validateGlobalConfig()` | Internal call | N/A | Strict boundary checks | Throws `IllegalArgumentException` |
| **5** | `PricingConfigurationRepository.java` | JPA Repo | `save()` | DB SQL `UPDATE` | `pricing_configurations` | Unique constraint on key+product | Transaction rollback on SQL violation |
| **6** | `PricingSettlementCoordinator.java` | `PricingSettlementCoordinator` | `executeSettlement()` | Internal / Scheduled | `pg_locks` | PG Advisory Lock & Idempotency Key | Skips duplicate cycle if lock held |
| **7** | `PriceAdjustmentService.java` | `PriceAdjustmentService` | `evaluateAndAdjustPrice()` | Internal call | `sales_order_items` | Low-sample gate & cooldown check | Falls back to safe price preservation on DB query failure |
| **8** | `ProductRepository.java` | JPA Repo | `saveAndFlush()` | DB SQL `UPDATE` | `products.current_cup_price` | `@Lock(PESSIMISTIC_WRITE)` | Serialization failure causes retry loop |
| **9** | `PricingRedisRepository.java` | `PricingRedisRepository` | `setProductPrice()` | Redis `SET` | `price:juice:{id}` | Try/catch block | Non-fatal bypass; system continues from DB |
| **10** | `PricingSettlementCoordinator.java` | `SimpMessagingTemplate` | `convertAndSend()` | STOMP WS | `/topic/prices`, `/topic/settlement` | Post-commit execution | WS drop does not affect DB state; client reconnects |
| **11** | `customer-web/src/index.html` | Client JS | `onMessage()` | STOMP Client | DOM state | JSON parse validation | Resyncs via REST polling (`/api/pricing/live`) |

---

## 5. COMPLETE PRICING ERROR MAP

### A. Admin Frontend Vulnerabilities
1. **Concurrent Admin Edit Overwrites:** When two admins have `admin-panel/src/index.html` open simultaneously, each loads the current configuration into input fields. If Admin A changes timing to 10s and saves, and Admin B changes Max Price to ₹35 and saves 2 seconds later, Admin B's payload sends Admin B's stale timing (60s), reverting Admin A's changes without warning.
2. **Double-Click Submission Race:** Buttons such as `btnSavePricingDetails` (`admin-panel/src/index.html:7331`) lack idempotency tokens. Fast double-clicking issues parallel `PUT` requests, producing duplicate entries in `pricing_config_audit_logs`.
3. **Client-Side Countdown Loop Overlap:** `admin-panel/src/index.html:6728` contains an autonomous timer (`startSettlementCountdownTicker`) that triggers `POST /api/pricing/evaluate` whenever the client-side countdown reaches zero. Multiple open admin tabs cause overlapping force-settlement requests to the backend.

### B. Backend API Vulnerabilities
1. **Missing Authentication on Pricing Endpoints:** In `SecurityConfig.java:93`, `.requestMatchers("/api/pricing/**", "/api/pos/**", "/api/admin/pricing/**", ...).permitAll()` allows any unauthenticated HTTP client to modify configurations, trigger crashes, reset prices, or delete products.
2. **Unvalidated Negative Prices in `POSController`:** While `PricingConfigurationService` validates that prices are non-negative, `POSController.java:140` (`PUT /api/pos/products/{id}`) only checks that `minCupPrice < maxCupPrice`, permitting negative prices if both values are negative.
3. **Premature WebSocket Broadcasts:** In `PricingConfigurationService.java:398-408`, STOMP messages are broadcast **inside** the `@Transactional` method prior to transaction commit. If a database exception occurs at commit time, clients have already received false price/config updates.

### C. Pricing Engine Vulnerabilities
1. **DWMA Sales Window Boundary Flaw:** In `PriceAdjustmentService.java:329-331`, window boundaries are computed as:
   * $W_0 = [now - \Delta t, now)$
   * $W_1 = [now - 2\Delta t, now - \Delta t)$
   * $W_2 = [now - 3\Delta t, now - 2\Delta t)$
   If settlements are delayed due to CPU load or database locks, sales occurring during the delay window are assigned to $W_1$ instead of $W_0$, artificially dampening demand calculations.
2. **Manual Weighted Sales Injection:** `PriceAdjustmentService.java:367` adds `product.weightedSales` directly to DWMA live sales ($S_w = \text{adminBase} + \text{dwmaLiveSales}$). This allows an admin to force perpetual upward price adjustments.

---

## 6. FORMULA AUDIT: DOCUMENTATION VS ACTUAL CODE

```text
DOCUMENTATION:
Sw = (1.0 × W0 + 0.5 × W1 + 0.25 × W2) / 1.75
Tnorm = T_1min × (Δt / 60)
Rd = (Sw + 1.5 × Tnorm) / (2.5 × Tnorm)
Decision:
  Rd >= 1.10         → +₹1
  0.90 <= Rd < 1.10  →  ₹0
  Rd < 0.90          → -₹1
Boundary:
  Pnew = min(max(Pcurrent + ΔP, Pmin), Pmax)

ACTUAL CODE (PriceAdjustmentService.java:351-460):
1. Sw:
   BigDecimal sumWeights = weightW0.add(weightW1).add(weightW2); // default 1.7500
   BigDecimal rawWeightedSum = (w0 * W0) + (w1 * W1) + (w2 * W2);
   BigDecimal dwmaLiveSales = rawWeightedSum.divide(sumWeights, 4, RoundingMode.HALF_UP);
   BigDecimal sw = adminBaseWeightedSales + dwmaLiveSales;

2. Tnorm:
   double normalizedTarget = baseTargetPer1Min * ((double) intervalSec / 60.0);

3. Rd (Bayesian Prior K=1.5):
   double smoothedNumerator = weightedSales + (1.5 * normalizedTarget);
   double smoothedDenominator = (1.0 + 1.5) * normalizedTarget; // 2.5 * normalizedTarget
   double smoothedRd = smoothedNumerator / smoothedDenominator;

4. Decision Rules:
   if (rd >= highThresh) {
       boolean hasSufficientVolume = (w0 + w1 >= 2) || (targetSalesBd >= 1 && w0 >= 1);
       if (sw > 0 && hasSufficientVolume) {
           deltaP = +1.00;
       } else {
           deltaP = 0.00; // HOLD: Low sample volume or zero current sales
       }
   } else if (rd >= stableLow) {
       deltaP = 0.00;
   } else {
       deltaP = -1.00;
   }

   // Decay Cooldown Override:
   if (movement < 0) {
       int minDecayCooldownSeconds = Math.max(60, intervalSec);
       if (now.isBefore(lastChange.plusSeconds(minDecayCooldownSeconds))) {
           deltaP = 0.00; // HOLD: Decay suppressed by cooldown
       }
   }

   // Circuit Breaker Bounds:
   Pnew = max(floor, min(ceiling, Pcurrent + deltaP));

MATCH:
PARTIAL

DIFFERENCE:
1. Low-Sample Protection Gate: When Rd >= 1.10, code does NOT automatically increase price. It requires (w0 + w1 >= 2), preventing single-order price spikes on small intervals.
2. Decay Cooldown Suppression: When Rd < 0.90, code suppresses price decay if less than Math.max(60, intervalSec) has elapsed since the last price change. At a 10s interval, downward adjustments are locked to once per 60 seconds.
3. Manual Base Injection: `adminBaseWeightedSales` is added directly to `dwmaLiveSales`, distorting raw algorithmic demand.

RISK:
MEDIUM (Formula logic is sound and safe from runaways, but cooldown and volume checks cause behavior that diverges from documentation).
```

---

## 7. 10-SECOND PRICING INTERVAL BEHAVIOR

When the settlement interval is configured to **10 seconds** (`SETTLEMENT_INTERVAL_SECONDS = 10`):

* **Scheduler Execution Cadence:** The backend scheduler (`DynamicPricingSchedulerConfig.java:30-58`) executes every **10 seconds** based on `nextSettlementTime`.
* **Upward Price Movement:** If $\ge 2$ cups are sold within a 10s window ($W_0 \ge 2$), $R_d \ge 1.10$ and `hasSufficientVolume` evaluates to `true`. Price **increases by +₹1 every 10 seconds** (e.g. ₹25 $\rightarrow$ ₹26 $\rightarrow$ ₹27 $\rightarrow$ ₹28 in 30 seconds).
* **Downward Price Decay:** In `PriceAdjustmentService.java:435`:
  ```java
  int minDecayCooldownSeconds = Math.max(60, intervalSec); // Math.max(60, 10) = 60 seconds!
  ```
  Under zero demand, the price drops by -₹1 on cycle 1 ($t=0$). On cycles at $t=10s, 20s, 30s, 40s, 50s$, the engine enters `ZERO_DEMAND_COOLDOWN_HOLD` and holds the price steady. The next -₹1 drop occurs at $t=60s$.
* **Time to Floor:** To decay from Base (₹25) to Floor (₹20) at a 10s interval requires $5 \times 60\text{s} = \mathbf{300\text{ seconds}}$ (**5 minutes**), not 50 seconds.
* **Asymmetry:** Price surges can occur every **10 seconds**, while price decay is restricted to every **60 seconds**.

---

## 8. MULTI-ADMIN CONCURRENCY ANALYSIS

### Scenario 1: Conflicting Boundary Updates
* **Action:** Admin A sets Base=₹25, Floor=₹20, Ceiling=₹30. Simultaneously, Admin B sets Base=₹28, Floor=₹22, Ceiling=₹35.
* **Code Trace:** In `PricingConfigurationService.java:358-364`, rows in `pricing_configurations` are updated by key without database row-level locking (`SELECT ... FOR UPDATE`).
* **Outcome:** The transaction that commits last overwrites the earlier one. In-memory `globalConfigCache` is updated via `ConcurrentHashMap.putAll()`. However, `Product` records in `products` retain their existing values until a product-specific configuration update or reset is performed.

### Scenario 2: Dynamic Interval Race
* **Action:** Admin A updates interval to 10s; Admin B updates interval to 60s.
* **Code Trace:** `PricingController.updatePricingTiming()` executes `pricingEngineService.resetSettlementTiming(selectedInterval)` and updates `SETTLEMENT_INTERVAL_SECONDS`.
* **Outcome:** Last write wins. The Spring `SchedulingConfigurer` trigger dynamically reads `pricingConfigService.getSettlementIntervalSeconds()` on every tick. The scheduler does not spawn duplicate tasks; it adjusts its next execution instant based on the latest value.

### Scenario 3: Admin Price Update During Active Settlement Cycle
* **Action:** Admin updates price manually while `PricingSettlementCoordinator` is executing.
* **Code Trace:**
  * `PricingSettlementCoordinator.java:188` executes `productRepository.findAllActiveWithLock()`, which applies `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`) on all active product rows in PostgreSQL.
  * `PriceAdjustmentService.updateManualPrice()` attempts `productRepository.findById(productId)` and waits for the transaction lock to release.
* **Outcome:** No dirty read or silent overwrite occurs at the database level. Admin's manual update waits for settlement to commit, then sets `pricing_mode = "MANUAL_LOCK"` and applies the manual price. Subsequent settlement cycles see `MANUAL_LOCK` and hold the price constant.

---

## 9. CURRENT PRICE AUTHORITY MATRIX

| Data Store / Channel | Price Example | Is Authoritative for Checkout? | Behavior If Divergent |
| :--- | :---: | :---: | :--- |
| **PostgreSQL `products.current_cup_price`** | **₹25.00** | **YES (100% Authoritative)** | POS uses this price when no valid lock token is provided. |
| **Redis `price:juice:{id}`** | ₹24.00 | **NO** | Never read during checkout; backend reads directly from PostgreSQL. |
| **Client Cart / Frontend DOM** | ₹23.00 | **NO** | Client sends `lockedPrice: 23`, but `POSService.java:406` ignores client-submitted prices unless a valid `priceLockToken` is attached. |
| **Price Lock Token (`quote:{id}`)** | ₹24.00 | **YES (for 10 seconds)** | If token is valid and unexpired, POS charges the token's locked price. |

### Conceptual Test Cases
* **Client sends `{ productId: 1, lockedPrice: 10 }` without token (DB = ₹25):**  
  `POSService.java:406` assigns `effectivePrice = product.getCurrentCupPrice()` (₹25). The client's payload price of ₹10 is ignored.
* **Price changes after page loads:**  
  Customer is charged the active database price at the time of checkout. (The frontend receipt displays the server-charged total, but individual line items are rendered using local cached prices, causing a display mismatch).
* **Price changes after lock token creation:**  
  Customer is charged the locked price, provided checkout occurs within 10 seconds.
* **Expired lock token submitted:**  
  `PriceLockService.java:106` throws `IllegalStateException: "QUOTE_EXPIRED"`, and checkout is rejected.

---

## 10. PRICE LOCK ERROR & SECURITY AUDIT

```text
LOCK TOKEN GENERATION & STORAGE:
- Format: "QUOTE-" + timestamp + "-" + 6-char UUID (e.g. QUOTE-1726640000000-A1B2C3)
- Primary Storage: In-memory ConcurrentHashMap<String, PriceQuote>
- Secondary Storage: Redis key "quote:" + quoteId (TTL = 15 seconds)
- Validity Duration: Strict 10 seconds (expiresAt = now.plusSeconds(10))
```

### Vulnerabilities Found in `PriceLockService` & `POSService`
1. **Missing Product ID Validation on Token Redemption (CRITICAL):**
   * In `POSService.java:410`:
     ```java
     PriceLockService.LockedPriceVersion lock = priceLockService.validateAndRedeemLock(itemReq.getPriceLockToken());
     effectivePrice = lock.getLockedPrice();
     ```
   * In `PriceLockService.java:135-136`:
     ```java
     public LockedPriceVersion validateAndRedeemLock(String lockToken) {
         PriceQuote q = validateAndRedeemQuote(lockToken, null); // productId passed as NULL!
     ```
   * In `PriceLockService.java:93`:
     ```java
     if (productId != null && !quote.getProductId().equals(productId)) { ... }
     ```
   * **Exploit Scenario:** Because `productId` is passed as `null`, the product ID check is skipped. A customer can obtain a price lock for Lime Juice at ₹20, attach that `priceLockToken` to an order for Avocado Juice at ₹35, and the backend will apply the ₹20 price to Avocado Juice.
2. **Frontend Omits Price Lock Tokens:**
   * In `customer-web/src/index.html:3120-3132`, `processCheckout()` populates `lockedPrice` but **never calls `/api/pricing/quote` or `/api/pricing/lock`**.
   * Consequently, regular customer checkouts never utilize price locks in the current frontend implementation.

---

## 11. REDIS VS POSTGRESQL CONSISTENCY MATRIX

| Scenario | PostgreSQL | Redis | Customer POS View | Checkout Charge | Reconciliation & Recovery | Risk Level |
| :--- | :---: | :---: | :---: | :---: | :--- | :---: |
| **A: Stale Redis Cache** | ₹25.00 | ₹24.00 | Shows ₹25 via REST; shows ₹24 if viewing legacy Redis feed | **₹25.00** | Next settlement cycle overwrites Redis with PostgreSQL price. | Low |
| **B: Uncommitted Redis Write** | ₹24.00 | ₹25.00 | Shows ₹24 via REST | **₹24.00** | PostgreSQL is authoritative; next settlement syncs Redis. | Low |
| **C: Redis Service Down** | ₹25.00 | Offline | Normal operation via REST & PostgreSQL | **₹25.00** | `PricingRedisRepository` catches exceptions and logs warnings; checkout and settlements proceed normally. | Low |
| **D: PostgreSQL Down** | Offline | Online | Shows 500 Error | **Fails (No Order)** | Application rejects checkouts and settlements until database connectivity is restored. | Critical |
| **E: App Restart** | ₹25.00 | Stale | Loads ₹25 from DB on boot | **₹25.00** | `PricingConfigurationService.reloadFromDatabase()` runs on `@PostConstruct` to re-populate caches from PostgreSQL. | Low |
| **F: Redis Restart** | ₹25.00 | Empty | Normal operation | **₹25.00** | Redis cache warms up on the next settlement cycle. | Low |

---

## 12. WEBSOCKET / STOMP ERROR & ORDERING AUDIT

```text
ACTIVE TOPICS:
- /topic/prices           (Full product price evaluation payload)
- /topic/products         (Active product entity list)
- /topic/settlement       (Settlement heartbeat, timing, next cycle timestamp)
- /topic/led-display      (Digital signage feed)
- /topic/pricing-config   (Global pricing configuration DTO)
- /topic/market-status    (PAUSED / OPEN status events)
- /topic/market-crash     (Crash state, remaining seconds, affected IDs)
```

### Audit Findings
1. **Ordering in `PricingSettlementCoordinator` (CORRECT):** In `PricingSettlementCoordinator.java:273-311`, STOMP messages are broadcast **after** `transactionTemplate.execute()` commits to PostgreSQL. Price updates are not broadcast if the database transaction fails.
2. **Ordering in `PricingConfigurationService` (INCORRECT):** In `PricingConfigurationService.java:394-411`, STOMP broadcasts occur **inside** the `@Transactional` update method. If database constraints fail during commit, invalid configuration updates will have already been dispatched to connected clients.
3. **Missing Message Sequence Numbers:** STOMP payloads contain timestamps but lack monotonically increasing sequence IDs. If network latency delivers packets out of order, a client could momentarily display older price data until the next broadcast arrives.

---

## 13. MARKET CRASH ENGINE AUDIT

```text
SNAPSHOT ARCHITECTURE:
- Database Table: market_crash_snapshots (crash_code, product_id, pre_crash_price, created_at)
- Redis Cache: redisRepository.setCrashSnapshot(productId, preCrashPrice)
- Persistence: Snapshots ARE persisted to disk in PostgreSQL.
```

### Critical Vulnerability: The Double-Crash Price Wipe
In `MarketCrashService.java:231-265`:
```java
@Transactional
public synchronized MarketCrashStatus triggerMarketCrash(int durationMinutes, String triggerType) {
    this.crashActive = true;
    this.currentCrashCode = "CRASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    ...
    for (Product p : allProducts) {
        BigDecimal preCrashPrice = p.getCurrentCupPrice() != null ? p.getCurrentCupPrice() : p.getDefaultCupPrice();
        // PROBLEM: If a crash is already active, p.getCurrentCupPrice() is ALREADY at the floor (₹20)!
        MarketCrashSnapshot snapshot = MarketCrashSnapshot.builder()
                .crashCode(currentCrashCode)
                .productId(p.getId())
                .preCrashPrice(preCrashPrice) // Saves ₹20 as pre-crash price!
                .build();
        snapshotRepository.save(snapshot);
        ...
    }
}
```
* **Failure Mechanism:** The method does not check `if (this.crashActive)`. If Admin triggers a second crash (or extends an active crash), the code reads `product.getCurrentCupPrice()`—which is **already at the crash price of ₹20.00**—and saves ₹20.00 as the new pre-crash snapshot.
* **Impact:** When the crash ends, the system restores from the latest snapshot, setting prices permanently to ₹20.00. **The original pre-crash prices (e.g. ₹28, ₹30) are lost.**

### Recovery on Server Restart
* Handled in `MarketCrashService.java:57-91` (`initCrashStateFromStorage`). If the server restarts during an active crash, it reads crash metadata from Redis. If the crash timer has expired while offline, it executes `stopMarketCrash()` and restores snapshot prices. If Redis was also cleared, prices remain at the crash floor until manually reset.

---

## 14. SCHEDULER & CONCURRENCY AUDIT

### Scheduler Registry Inspection

| Class | Method | Cadence | Purpose | Overlap Guard |
| :--- | :--- | :--- | :--- | :--- |
| `DynamicPricingSchedulerConfig` | `configureTasks()` | Dynamic Trigger ($\Delta t$ seconds) | Central DWMA pricing settlement | PostgreSQL Advisory Lock (`788325001L`) + ReentrantLock |
| `LiveMarketSimulatorService` | `simulateLiveMarketTrades()` | Fixed Delay 12,000ms | Autonomous trade generation | `volatile boolean enabled` flag |

### Findings
1. **No Duplicate Java Schedulers:** Only one dynamic scheduler task is registered in the Spring container.
2. **Frontend Dual-Scheduler Issue:** `admin-panel/src/index.html:6728` executes a client-side timer that issues `POST /api/pricing/evaluate` on completion. If three admin dashboard instances are open, the backend receives concurrent force-settlement requests alongside its own internal timer.
3. **Idempotency Bypass on Force-Settlement:** In `PricingSettlementCoordinator.java:164-168`:
   ```java
   String windowKey = force
           ? "SETTLEMENT_FORCE_" + epochSeconds + "_" + UUID.randomUUID().toString().substring(0, 4)
           : "SETTLEMENT_" + intervalSeconds + "_" + bucket;

   if (!force && settlementRepository.existsByIdempotencyKey(windowKey)) { ... }
   ```
   Force-settlements bypass the window idempotency check. Successive client requests will trigger back-to-back recalculation rounds once the previous round releases the lock.

---

## 15. SIMULATOR PRODUCTION RISK AUDIT

In `LiveMarketSimulatorService.java:92-105`:
```java
POSService.CheckoutRequest checkoutRequest = new POSService.CheckoutRequest(
        items, "BOT_CASH", simKey, BigDecimal.ZERO, BigDecimal.ZERO
);
POSService.CheckoutResponse res = posService.processCheckout(checkoutRequest);
```

### Production Risks
1. **Direct Modification of Production Inventory:** The simulation bot executes `posService.processCheckout()`. This calls `juiceBatchService.deductBatchVolume()`, **deducting physical milliliters from active production inventory batches** in PostgreSQL.
2. **Creation of Real Sales Orders:** Bot transactions generate real records in `sales_orders` and `sales_order_items` with payment method `"BOT_CASH"`. These orders appear in financial and accounting reports unless explicitly filtered.
3. **Real Price Movement:** Simulated orders increment `product.orderCount` and are queried by the DWMA engine, directly altering live exchange prices.
4. **Configuration Safety:** In `application.yml`, `market.simulator.enabled` defaults to `${MARKET_SIMULATOR_ENABLED:false}`. However, `POST /api/pricing/simulator/live-bot/toggle` is accessible without authentication, allowing anyone to activate the simulator on a live deployment.

---

## 16. DATABASE CONCURRENCY & TRANSACTION ISOLATION

### Concurrent Checkout Test
* **Scenario:** Customers A, B, and C purchase Mango Juice simultaneously.
* **Mechanism:** In `POSService.java:390-422`:
  * Cart items are sorted deterministically: `itemsToProcess.sort(Comparator.comparing(CartItemRequest::getProductId))`. This prevents cross-product lock ordering deadlocks.
  * Inventory deduction uses a pessimistic write lock in `JuiceBatchService`: `findByIdWithLock()`.
  * `productRepository.incrementOrderCount(productId, qty)` runs as an atomic SQL increment: `UPDATE products SET order_count = COALESCE(order_count, 0) + :qty WHERE id = :id`.
* **Verdict:** Concurrent checkouts are transactionally consistent. Volume is deducted cleanly, and order counts are updated without race conditions.

### Settlement vs Checkout Concurrency
* **Scenario:** Admin/Scheduler executes pricing settlement while a customer checkout is processing.
* **Mechanism:**
  * Settlement calls `productRepository.findAllActiveWithLock()`, locking all active `products` rows.
  * `POSService` reads `Product` to check price. It does not use pessimistic lock on `Product` (only on `JuiceBatch`), allowing it to read committed prices.
  * However, `POSService` executes `productRepository.incrementOrderCount()`, which acquires a row write lock.
  * If settlement is holding the lock, checkout waits up to the database timeout (or retries via `POSService` 10-attempt retry loop).
* **Verdict:** Safe from dirty writes; retry logic prevents checkout failures under lock contention.

---

## 17. PRICE HISTORY & AUDIT TRAIL INTEGRITY

```text
AUDIT TABLES:
1. price_history:
   - Records every algorithmic step, manual override, reset, and crash event.
   - Columns: old_price, new_price, price_change, demand_ratio, weighted_sales, target_sales,
     w0, w1, w2, floor_price, ceiling_price, price_version, config_version, reason, explanation, cycle_id.
2. pricing_config_audit_logs:
   - Records changes to configuration keys.
   - Columns: admin_user, setting_key, product_id, old_value, new_value, version_before, version_after, reason.
```

### Integrity Verification
* **Missing History Risk:** None. All price change paths (`evaluateAndAdjustPrice`, `updateManualPrice`, `deployAdminPricing`, `resetAllProductsToDefault`, `triggerMarketCrash`, `stopMarketCrash`) write to `priceHistoryRepository`.
* **Premature Log Writes:** Audit records are written inside the transaction block; if the transaction rolls back, audit logs roll back with it.
* **Gap Identified:** `POSController.java:140` (`PUT /api/pos/products/{id}`) updates `Product` fields directly without creating a record in `pricing_config_audit_logs` or `price_history`.

---

## 18. CONFIGURATION VERSIONING AUDIT

* **Global Version:** Stored in `pricing_configurations` under key `GLOBAL_CONFIG_VERSION`. Managed via `AtomicLong currentConfigVersion` in memory.
* **Product Version:** Stored in `products.price_version`. Incremented manually on price changes.
* **Versioning Flaws:**
  * `PricingConfiguration` has a JPA `@Version` annotation, but `PricingConfigurationService.java:363` explicitly executes `cfg.setVersion(newVersion)`. Manually setting a `@Version` field interferes with Hibernate's internal optimistic lock management.
  * `Product` lacks JPA `@Version`. If two admins submit product updates simultaneously, the second write silently overwrites the first without an optimistic lock check.

---

## 19. ERROR SCENARIO MATRIX

| Error Scenario | Where It Occurs | Current Protection | Expected Behavior | Actual Behavior | Severity | Required Fix |
| :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| **Admin enters negative price** | `POSController.java:140` | None in `POSController` | Reject with HTTP 400 | Accepted and persisted to DB | 🚨 **CRITICAL** | Add `newPrice.compareTo(BigDecimal.ZERO) > 0` validation to `POSController`. |
| **Floor > Ceiling** | `PricingConfigurationService:558` | Validation check | Reject with HTTP 400 | Throws exception, rejected | ✅ Safe | None |
| **Base outside range** | `PricingConfigurationService:561` | Validation check | Reject with HTTP 400 | Throws exception, rejected | ✅ Safe | None |
| **Zero/negative interval** | `PricingConfigurationService:578` | `isValidInterval()` | Reject with HTTP 400 | Throws exception, rejected | ✅ Safe | None |
| **Two Admins update config** | `PricingConfigurationService:325` | `currentConfigVersion` | Optimistic lock error | Last commit overwrites; audit logs may share version | ⚠️ **MEDIUM** | Implement true JPA optimistic concurrency. |
| **Admin update during settlement** | `PriceAdjustmentService:560` | PG row-level lock | Block until commit | Admin update blocks, then applies `MANUAL_LOCK` | ✅ Safe | None |
| **Customer checkout during settlement** | `POSService:331` | 10-attempt retry loop | Process cleanly | Retries after lock contention and succeeds | ✅ Safe | None |
| **Redis unavailable** | `PricingRedisRepository` | Try/catch bypass | Fallback to DB | Logs warning, proceeds using PostgreSQL | ✅ Safe | None |
| **PostgreSQL unavailable** | Global | None | Clean error response | Application fails (500 Error) | 🚨 **CRITICAL** | Implement circuit breakers on health checks. |
| **WebSocket disconnect** | Client STOMP | Auto-reconnect | Reconnect & resync | Reconnects, resyncs via REST polling | ✅ Safe | None |
| **Server restart during crash** | `MarketCrashService:57` | `ApplicationReadyEvent` | Resume or restore | Restores from Redis/DB snapshot successfully | ✅ Safe | None |
| **Crash triggered twice** | `MarketCrashService:231` | None | Reject second trigger | **Overwrites snapshot with floor price** | 🚨 **CRITICAL** | Add `if (crashActive) throw new IllegalStateException()`. |
| **Simulator active in prod** | `LiveMarketSimulatorService` | Config property | Isolate from prod | **Deducts real inventory and creates real orders** | 🚨 **CRITICAL** | Route simulator to mock POS service or virtual inventory. |
| **Expired price lock** | `PriceLockService:101` | Timestamp check | Reject with 400/409 | Throws `IllegalStateException`, rejects order | ✅ Safe | None |
| **Invalid price lock token** | `PriceLockService:89` | Registry lookup | Reject checkout | Throws `IllegalArgumentException`, rejects order | ✅ Safe | None |
| **Lock token product mismatch** | `PriceLockService:136` | **SKIPPED (`null` ID)** | Reject checkout | **Applies product A's locked price to product B** | 🚨 **CRITICAL** | Pass `itemReq.getProductId()` to `validateAndRedeemQuote()`. |
| **Disabled product purchased** | `POSService:394` | None | Reject checkout | **Order processes; inventory deducted** | ⚠️ **HIGH** | Add `if (!product.getIsActive()) throw new IllegalStateException()`. |
| **Decimal price entered** | `POSController` / Admin | None | Round or reject | Accepted; cents persist through $\pm ₹1$ steps | ⚠️ **MEDIUM** | Enforce scale=2 and integer rupee increments in validation. |
| **Zero demand decay pacing** | `PriceAdjustmentService:435` | 60s cooldown | Decay every cycle | Suppressed to once every 60 seconds | ⚠️ **MEDIUM** | Align documentation or make cooldown proportional to interval. |
| **Unauthenticated API access** | `SecurityConfig.java:93` | `permitAll()` | Reject with 401 | **All pricing/admin endpoints are publicly accessible** | 🚨 **CRITICAL** | Require `ROLE_ADMIN` on all pricing mutation endpoints. |

---

## 20. SEVERITY CLASSIFICATION OF FINDINGS

### 🚨 CRITICAL (Must Fix Before Production)
1. **Public Unauthenticated Pricing Endpoints:** `SecurityConfig.java:93` marks `/api/pricing/**`, `/api/admin/pricing/**`, `/api/pos/**`, and `/api/products/**` as `permitAll()`. Any anonymous client can alter prices, trigger crashes, or update configurations.
2. **Double-Crash Snapshot Corruption:** `MarketCrashService.java:231` does not guard against triggering a crash while one is active, causing pre-crash snapshots to be overwritten with the crash floor price.
3. **Cross-Product Price Lock Hijacking:** `PriceLockService.java:136` passes `null` as `productId` during redemption, allowing a price lock token from an inexpensive item to be applied to an expensive item.
4. **Simulator Deducts Real Inventory:** `LiveMarketSimulatorService.java:101` calls `posService.processCheckout()`, consuming physical beverage inventory and injecting simulated financial orders into the live database.

### ⚠️ HIGH (Operational & Financial Inconsistencies)
1. **Disabled Product Checkout:** `POSService.java:394` does not verify `product.getIsActive()`, allowing deactivated beverages to be purchased.
2. **Missing Input Sanitization in `POSController`:** `PUT /api/pos/products/{id}` allows negative prices because it lacks lower-bound checks.
3. **Client-Side Scheduler Overlap:** Open admin dashboard tabs trigger `POST /api/pricing/evaluate` when their local countdown completes, producing duplicate force-settlement calls to the backend.

### ⚠️ MEDIUM (Calculation & UI Quirks)
1. **Decay Cooldown Discrepancy:** At a 10s pricing interval, upward price adjustments occur every 10s, but downward decay is held to once every 60s via `Math.max(60, intervalSec)`.
2. **Premature WebSocket Dispatches:** `PricingConfigurationService` sends STOMP messages inside the transaction boundary before database commit succeeds.
3. **Receipt Total Mismatch:** The POS receipt modal calculates item line totals using client-cached prices rather than server-returned unit prices.
4. **Decimal Cent Persistence:** Manual updates allow fractional values (e.g. ₹25.50), which persist indefinitely because dynamic steps are whole rupees ($\pm ₹1.00$).

---

## 21. ADMIN PERMISSION MODEL AUDIT

```text
CURRENT SECURITY CONFIGURATION (SecurityConfig.java:93):
.requestMatchers("/api/pricing/**", "/api/pos/**", "/api/admin/pricing/**", "/api/products/**", "/api/batches/**").permitAll()
```

### Endpoint Access Matrix

| Endpoint | HTTP Method | Intended Access | Actual Access | Vulnerability |
| :--- | :---: | :---: | :---: | :---: |
| `/api/pricing/config` | PUT | Admin Only | **Public (Anonymous)** | 🚨 Critical Security Hole |
| `/api/pricing/products/{id}/price` | POST | Admin Only | **Public (Anonymous)** | 🚨 Critical Security Hole |
| `/api/pricing/market-crash/trigger` | POST | Admin Only | **Public (Anonymous)** | 🚨 Critical Security Hole |
| `/api/pricing/reset-all` | POST | Admin Only | **Public (Anonymous)** | 🚨 Critical Security Hole |
| `/api/pricing/pause` | POST | Admin Only | **Public (Anonymous)** | 🚨 Critical Security Hole |
| `/api/pricing/timing` | PUT | Admin Only | **Public (Anonymous)** | 🚨 Critical Security Hole |
| `/api/pos/products/{id}` | PUT / DELETE | Admin Only | **Public (Anonymous)** | 🚨 Critical Security Hole |
| `/api/pricing/simulator/live-bot/toggle` | POST | Admin Only | **Public (Anonymous)** | 🚨 Critical Security Hole |
| `/api/pricing/market` | GET | Public | Public | ✅ Intended |
| `/api/pricing/quote` | POST | Public / POS | Public | ✅ Intended |
| `/api/pos/checkout` | POST | Public / POS | Public | ✅ Intended |

---

## 22. FINAL ADMIN CONTROL MAP

| Feature / Action | Customer | Admin | Pricing Engine | System Only | Actual Status in Code |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **View Live Prices** | ✅ | ✅ | — | — | Publicly accessible via REST and STOMP |
| **Change Base Price** | ❌ | ✅ | ❌ | — | Configurable via Admin API & UI |
| **Change Floor Limit** | ❌ | ✅ | ❌ | — | Configurable via Admin API & UI |
| **Change Ceiling Limit** | ❌ | ✅ | ❌ | — | Configurable via Admin API & UI |
| **Change Current Price Directly** | ❌ | ✅ (High Risk) | ✅ | — | Admin can override; locks product into `MANUAL_LOCK` |
| **Trigger Market Crash** | ❌ | ✅ | ❌ | — | Admin triggerable via API & UI button |
| **Manual Price Lock** | ❌ | ✅ | ❌ | — | Admin toggleable per product |
| **Change Settlement Interval** | ❌ | ✅ | ❌ | — | Configurable (5s – 86400s) |
| **Change Demand Target** | ❌ | ✅ | ❌ | — | Configurable per product |
| **Determine Checkout Price** | ❌ | ❌ | ❌ | ✅ | Server-authoritative via PostgreSQL / unexpired lock |
| **Write Price History** | ❌ | ❌ | ✅ | ✅ | Engine writes audit rows on settlements and updates |
| **Apply Discount at Checkout** | ❌ | ✅ (Max 20%) | ❌ | — | Server verifies `ROLE_ADMIN`/`MANAGER` before applying |

---

## 23. PRODUCTION SAFETY CHECKLIST

| Question | Answer | Root Cause & Code Path |
| :--- | :---: | :--- |
| **Can Admin accidentally create an invalid pricing configuration?** | **YES** | `POSController.java:140` permits negative prices; `PricingConfigurationService` has no upper bound on Ceiling or crash duration. |
| **Can Customer manipulate checkout price?** | **YES** | Via `PriceLockService.java:136`: an attacker can lock a cheap product and redeem the token against an expensive product. |
| **Can Redis override PostgreSQL price?** | **NO** | `POSService` and `PriceAdjustmentService` query PostgreSQL directly. Redis is write-only cache. |
| **Can two pricing cycles execute simultaneously?** | **NO** | `PricingSettlementCoordinator.java:126-140` enforces JVM `ReentrantLock` and PostgreSQL Advisory Lock (`788325001L`). |
| **Can two Admin updates overwrite each other?** | **YES** | `PricingConfigurationService.java:325` lacks optimistic lock validation; last writer overwrites previous changes. |
| **Can crash restore the wrong price?** | **YES** | `MarketCrashService.java:231`: triggering a crash while active snapshots the floor price, permanently wiping pre-crash prices. |
| **Can simulator modify production data?** | **YES** | `LiveMarketSimulatorService.java:101` calls `posService.processCheckout()`, consuming physical batch inventory. |
| **Can a stale WebSocket price be used for checkout?** | **NO** | `POSService.java:406` validates the price against PostgreSQL at checkout time. |
| **Can price move outside floor/ceiling?** | **NO** | Clamped via `Pnew = max(floor, min(ceiling, Pcurrent + deltaP))` in `PriceAdjustmentService.java:460`. |
| **Can price change by more than ₹1 during normal pricing?** | **NO** | `PricingConfigurationService.validatePriceMovement()` throws an exception if $\Delta P \notin \{+1.00, 0.00, -1.00\}$. |
| **Can unauthorized users modify pricing?** | **YES** | `SecurityConfig.java:93` permits unauthenticated access to all `/api/pricing/**` endpoints. |

---

## 24. STRUCTURED CODE REMEDIATIONS

### Issue 1: Public Unauthenticated Pricing Endpoints
* **File:** `backend/src/main/java/com/retailpos/security/SecurityConfig.java`
* **Class:** `com.retailpos.security.SecurityConfig`
* **Line:** 93
* **Problem:** `.requestMatchers("/api/pricing/**", "/api/pos/**", "/api/admin/pricing/**", "/api/products/**", "/api/batches/**").permitAll()`
* **Why it fails:** Bypasses JWT filter for administrative mutation operations. Any anonymous client can execute manual price overrides, trigger market crashes, or reconfigure intervals.
* **Severity:** **CRITICAL**
* **Recommended Correction:** Split read-only and administrative endpoints. Restrict mutations to authenticated roles:
  ```java
  .requestMatchers(HttpMethod.GET, "/api/pricing/**", "/api/products/**").permitAll()
  .requestMatchers("/api/pricing/quote", "/api/pricing/lock", "/api/pos/checkout").permitAll()
  .requestMatchers("/api/pricing/**", "/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
  ```

---

### Issue 2: Double Market Crash Snapshot Corruption
* **File:** `backend/src/main/java/com/retailpos/pricing/MarketCrashService.java`
* **Class:** `com.retailpos.pricing.MarketCrashService`
* **Method:** `triggerMarketCrash(int durationMinutes, String triggerType)`
* **Lines:** 231–245
* **Problem:** Does not verify whether a crash is already active before taking price snapshots.
* **Why it fails:** If triggered during an ongoing crash, `product.getCurrentCupPrice()` is already at the crash floor (₹20). The method snapshots ₹20 as the pre-crash price, permanently overwriting the original prices upon restore.
* **Severity:** **CRITICAL**
* **Recommended Correction:** Reject duplicate crash triggers or extend the existing timer without re-taking snapshots:
  ```java
  if (this.crashActive) {
      log.warn("Market crash already in progress. Extending timer without overwriting snapshots.");
      this.crashEndTime = LocalDateTime.now().plusSeconds(durationSeconds);
      return getStatus();
  }
  ```

---

### Issue 3: Cross-Product Price Lock Hijacking
* **File:** `backend/src/main/java/com/retailpos/pricing/PriceLockService.java`
* **Class:** `com.retailpos.pricing.PriceLockService`
* **Method:** `validateAndRedeemLock(String lockToken)`
* **Lines:** 135–137
* **Problem:** Passes `null` as the `productId` parameter to `validateAndRedeemQuote(lockToken, null)`.
* **Why it fails:** Line 93 skips product identity verification when `productId` is null. A quote token created for an inexpensive beverage can be redeemed against an expensive beverage.
* **Severity:** **CRITICAL**
* **Recommended Correction:** Update method signature to require `productId`:
  ```java
  public LockedPriceVersion validateAndRedeemLock(String lockToken, Long expectedProductId) {
      PriceQuote q = validateAndRedeemQuote(lockToken, expectedProductId);
      ...
  ```
  And update `POSService.java:410`:
  ```java
  priceLockService.validateAndRedeemLock(itemReq.getPriceLockToken(), product.getId());
  ```

---

### Issue 4: Simulator Consumes Production Inventory
* **File:** `backend/src/main/java/com/retailpos/pricing/service/LiveMarketSimulatorService.java`
* **Class:** `com.retailpos.pricing.service.LiveMarketSimulatorService`
* **Method:** `simulateLiveMarketTrades()`
* **Lines:** 92–105
* **Problem:** Direct execution of `posService.processCheckout()` within simulation loops.
* **Why it fails:** Invokes actual physical inventory deduction on database batch rows and generates orders in financial tables.
* **Severity:** **CRITICAL**
* **Recommended Correction:** Disconnect simulator from production checkout. If trade simulation is desired, update simulated sales counters directly or record transactions against a designated mock batch without altering production inventory.

---

### Issue 5: Client-Side Countdown Scheduler Overlap
* **File:** `admin-panel/src/index.html`
* **Lines:** 6720–6731
* **Problem:** Frontend JavaScript executes `triggerPriceEngine({ silent: true })` (`POST /api/pricing/evaluate`) whenever local countdown expires.
* **Why it fails:** The backend already has a server-side scheduler (`DynamicPricingSchedulerConfig`). Multiple open admin tabs result in duplicate force-settlements that bypass window idempotency.
* **Severity:** **HIGH**
* **Recommended Correction:** Remove automatic API evaluation from the client-side countdown timer. The frontend countdown should be purely visual, updating its state when the backend broadcasts a settlement event over STOMP (`/topic/settlement`).

---

### Issue 6: Checkout of Deactivated Products Allowed
* **File:** `backend/src/main/java/com/retailpos/pos/POSService.java`
* **Class:** `com.retailpos.pos.POSService`
* **Method:** `doProcessCheckout(CheckoutRequest request)`
* **Lines:** 394–396
* **Problem:** Queries `productRepository.findById()` without verifying `product.getIsActive()`.
* **Why it fails:** Deactivated products can still be ordered if their ID is submitted in a checkout payload.
* **Severity:** **HIGH**
* **Recommended Correction:** Add an active status guard:
  ```java
  if (product.getIsActive() != null && !product.getIsActive()) {
      throw new IllegalArgumentException("Cannot purchase deactivated beverage: " + product.getName());
  }
  ```

---

### Issue 7: Unsanitized Negative Prices in POS Controller
* **File:** `backend/src/main/java/com/retailpos/pos/POSController.java`
* **Class:** `com.retailpos.pos.POSController`
* **Method:** `updateProduct(@PathVariable Long id, @RequestBody Product details)`
* **Lines:** 149–163
* **Problem:** Omits non-negative bounds check (`minCupPrice >= 0`).
* **Why it fails:** If an admin submits negative values for both min and current prices, the check `effectiveMin >= effectiveMax` passes, writing negative prices to PostgreSQL.
* **Severity:** **HIGH**
* **Recommended Correction:** Add lower-bound validation:
  ```java
  if (effectiveMin != null && effectiveMin.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Minimum price cannot be negative");
  }
  ```

---

### Issue 8: Asymmetric Decay Cooldown Lock
* **File:** `backend/src/main/java/com/retailpos/pricing/PriceAdjustmentService.java`
* **Class:** `com.retailpos.pricing.PriceAdjustmentService`
* **Method:** `evaluateAndAdjustPrice()`
* **Lines:** 435–440
* **Problem:** `int minDecayCooldownSeconds = Math.max(60, intervalSec);`
* **Why it fails:** At a 10s interval, upward price adjustments occur every 10s, but downward decay is held to once every 60s, creating an unintended upward bias under symmetric trading volume.
* **Severity:** **MEDIUM**
* **Recommended Correction:** Scale cooldown to match the active interval:
  ```java
  int minDecayCooldownSeconds = intervalSec;
  ```
