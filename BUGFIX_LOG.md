# DWMA Pricing Engine Bugfix Log

This document records the audit findings, test-first reproductions, fixes applied, test verification evidence, and residual risk tracking for the Dynamic Weighted Moving Average (DWMA) pricing engine in the **Noida Pub Exchange & Juice Bar** platform.

---

## Bug 1: Weighted Sum Normalization Error (CRITICAL)

- **Bug ID:** `BUG-01-SW-NORMALIZATION`
- **Severity:** Critical
- **Affected Components:**
  - `backend/src/main/java/com/retailpos/pricing/PriceAdjustmentService.java` (`evaluateAndAdjustPrice` and `getCalculationDebug`)
  - `backend/src/main/java/com/retailpos/pricing/PricingSimulationService.java` (`runSimulation`)
  - `backend/src/test/java/com/retailpos/pricing/PriceMovementUnitTest.java` (`calculateDWMAPrice`)
- **Root Cause:**
  The weighted sales formula was computed as:
  $$S_w = (W_0 \times 1.00) + (W_1 \times 0.50) + (W_2 \times 0.25)$$
  Because the weights $1.00 + 0.50 + 0.25 = 1.75$ were not normalized by their sum, any stable demand condition where sales in all three windows matched target ($W_0 = W_1 = W_2 = \text{TargetSales}$) yielded:
  $$S_w = 1.75 \times \text{TargetSales} \implies R_d = \frac{S_w}{\text{TargetSales}} = 1.75$$
  Because $R_d = 1.75 \ge 1.10$, the engine falsely classified completely ordinary, stable demand as `HIGH_DEMAND_SURGE` and continuously incremented the price by $+₹1.00$, introducing a severe systemic upward price bias across the platform.

- **Fix Applied:**
  Normalized the weighted sum calculation by the sum of configured window weights:
  $$\text{sumWeights} = \text{weightW0} + \text{weightW1} + \text{weightW2}$$
  $$\text{dwmaLiveSales} = \frac{(W_0 \times \text{weightW0}) + (W_1 \times \text{weightW1}) + (W_2 \times \text{weightW2})}{\text{sumWeights}}$$
  If weights are set to defaults ($1.00, 0.50, 0.25$), the denominator is $1.75$. Under stable demand ($W_0=W_1=W_2=\text{TargetSales}$), $S_w = \text{TargetSales}$, producing $R_d = 1.00$, which correctly falls within the `STABLE_DEMAND` band ($0.90 \le R_d < 1.10$) and results in $\Delta P = ₹0.00$.
  Updated `PriceAdjustmentService.java`, `PricingSimulationService.java`, `PriceMovementUnitTest.java`, and audit breakdown strings.

- **Test Evidence:**
  - **Reproduction Unit Test:** `com.retailpos.pricing.Bug1WeightedSumNormalizationTest`
  - **Before Fix (Failing Output):**
    ```
    === BUG 1 TEST OUTPUT ===
    Target Sales (Normalized): 4.0
    Weighted Sales (Sw):       7.0
    Demand Ratio (Rd):         1.75
    Price Change (deltaP):     1.00
    New Price:                 26.00
    Demand Category:           HIGH
    Explanation:               DWMA 60s Settlement: W0=4, W1=4, W2=4 | S_w=7.00, Target=4.00 cups (60s), R_d=1.7500 => Movement +1. Price: ₹25.00 -> ₹26.00 (HIGH_DEMAND_SURGE) [v1]
    =========================
    [ERROR] Failures: 
    [ERROR]   Bug1WeightedSumNormalizationTest.testStableDemand_ProducesRdNearOne_AndZeroPriceChange:80 Under stable demand where w0=w1=w2=target, Rd must be within [0.90, 1.10]. Actual Rd was: 1.75 ==> expected: <true> but was: <false>
    [INFO] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
    [INFO] BUILD FAILURE
    ```
  - **After Fix (Passing Output):**
    ```
    === BUG 1 TEST OUTPUT ===
    Target Sales (Normalized): 4.0
    Weighted Sales (Sw):       4.0
    Demand Ratio (Rd):         1.0
    Price Change (deltaP):     0.00
    New Price:                 25.00
    Demand Category:           NORMAL
    Explanation:               DWMA 60s Settlement: W0=4, W1=4, W2=4 | S_w=4.00, Target=4.00 cups (60s), R_d=1.0000 => Movement +0. Price: ₹25.00 -> ₹25.00 (STABLE_DEMAND) [v1]
    =========================
    [INFO] Running com.retailpos.pricing.Bug1WeightedSumNormalizationTest
    [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.028 s -- in com.retailpos.pricing.Bug1WeightedSumNormalizationTest
    [INFO] Running com.retailpos.pricing.PriceMovementUnitTest
    [INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.151 s -- in com.retailpos.pricing.PriceMovementUnitTest
    [INFO] Results:
    [INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
    [INFO] BUILD SUCCESS
    ```

- **Residual Risk & Follow-Up:**
  - Zero residual mathematical risk for $S_w$. The division includes a safety fallback preventing divide-by-zero if misconfigured weights sum to $\le 0$.
  - Next task in priority queue: **Bug 8 (Crash Floor Violates Per-Product Floor Invariant)**.
