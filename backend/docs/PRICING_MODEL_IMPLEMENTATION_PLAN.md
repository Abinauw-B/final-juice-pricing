# PRICING MODEL IMPLEMENTATION PLAN

## 1. PRICING MODEL OVERVIEW

The Dynamic Pricing Model in this project creates a "stock market" experience for beverage purchasing. Rather than fixed flat-rate menu prices, the prices of juices fluctuate in real-time based on actual customer purchasing behavior (demand). This creates urgency, excitement, and gamification for the customers, while allowing the venue to optimize yield and naturally push less popular items by dropping their prices.

**Inputs:**
- **Sales Volume:** The number of cups sold for a specific product in recent time windows.
- **Time / Settlement Interval:** The cycle (e.g., every 60 seconds) at which the system evaluates whether a price should move.
- **Base Target Sales:** The expected "normal" number of cups sold per minute.
- **Manual Intervention (Overrides):** Admin-imposed price floors, ceilings, manual locks, or Market Crash events.

**Outputs:**
- **Current Price:** The final live price published to the POS and LED tickers.
- **Price Mode/Status:** Categorical state indicating if the product is in `NORMAL`, `HIGH` demand, `LOW` demand, or experiencing a `CRASH`.
- **Historical Trail:** An audit log of all price changes containing the old price, new price, differential, and the specific reason (e.g. `HIGH_DEMAND_SURGE`).

**Data Flow Diagram:**
```text
[ POS Client Sales ]       [ Admin Settings ]
         |                         |
         v                         v
( Orders Table in DB )    ( Global/Product Configs )
         \                         /
          \                       /
           v                     v
    [ Pricing Engine Calculation Service ]
           | (Every X seconds)
           v
    [ Evaluated Price Decision ]  ---> [ Clamping & Safety Checks ]
                                                |
                                                v
                                    [ Database Persistence ]
                                                |
                                                v
                                 [ WebSocket Push to Frontend ]
                                 (POS Displays & LED Ticker)
```

---

## 2. COMPLETE MATHEMATICAL SPECIFICATION

The pricing engine uses a Bayesian-smoothed Discrete Weighted Moving Average (DWMA) to calculate the "Demand Ratio" ($R_d$), which dictates how prices move.

### Constants and Parameters

| Parameter | Default Value | Description |
|-----------|---------------|-------------|
| **Interval ($I$)** | 60 seconds | How often the engine recalculates prices. |
| **$W_0$ Weight ($w_0$)** | 1.0000 | Weight given to sales in the current most recent window $[now - I, now)$. |
| **$W_1$ Weight ($w_1$)** | 0.5000 | Weight given to sales in the prior window $[now - 2I, now - I)$. |
| **$W_2$ Weight ($w_2$)** | 0.2500 | Weight given to sales in the oldest tracked window $[now - 3I, now - 2I)$. |
| **Target per Min ($T_{min}$)**| 0.55 cups | The expected baseline sales volume per minute for a product. |
| **Bayesian Prior ($K$)** | 1.5 | Smoothing constant to prevent violent swings on low-volume samples. |
| **High Threshold** | 1.1000 | If $R_d \ge 1.1000$, trigger a price INCREASE (+₹1.00). |
| **Stable Lower Threshold**| 0.9000 | If $R_d \ge 0.9000$ (and $< 1.10$), hold price STABLE (₹0.00). |
| **Low Threshold** | 0.5000 | If $R_d \ge 0.5000$ (and $< 0.90$), trigger a price DECREASE (-₹1.00). |
| **Very Low (Zero)** | $< 0.5000$ | If $R_d < 0.5000$, trigger a price DECREASE (-₹1.00). |

### Step-by-Step Derivation

**Step 1: Normalize Target Sales for the Current Interval**
The target is defined per 60 seconds. We normalize it to the exact configured interval:
$$T_{norm} = T_{min} \times \frac{I}{60}$$

**Step 2: Collect Real Sales Data**
Query the database for the exact number of cups sold in the last three intervals:
- $V_0$: Sales in the last $I$ seconds.
- $V_1$: Sales in the period from $2I$ to $I$ seconds ago.
- $V_2$: Sales in the period from $3I$ to $2I$ seconds ago.

**Step 3: Calculate the Sum of Weights**
$$S_{weights} = w_0 + w_1 + w_2$$
*(Default: $1.0000 + 0.5000 + 0.2500 = 1.7500$)*

**Step 4: Calculate Discrete Weighted Moving Average (DWMA) Sales ($S_w$)**
$$S_{raw} = (V_0 \times w_0) + (V_1 \times w_1) + (V_2 \times w_2)$$
$$S_w = \text{RoundTo2DecimalPlaces}\left(\frac{S_{raw}}{S_{weights}}\right)$$

**Step 5: Calculate Bayesian Smoothed Demand Ratio ($R_d$)**
To prevent wildly fluctuating ratios when volume is low, we inject a "prior" (fake expected sales) controlled by $K$.
$$Numerator = S_w + (K \times T_{norm})$$
$$Denominator = (1 + K) \times T_{norm}$$
$$R_d = \text{RoundTo4DecimalPlaces}\left(\frac{Numerator}{Denominator}\right)$$

**Step 6: Price Decision Branching**
Compare $R_d$ against the configured thresholds to determine the raw price change ($\Delta P$):

| Condition | Internal Reason | $\Delta P$ (Change) |
|-----------|-----------------|---------------------|
| $R_d \ge 1.1000$ AND $V_0 \ge 1$ | `HIGH_DEMAND_SURGE` | +₹1.00 |
| $R_d \ge 1.1000$ AND $V_0 == 0$ | `ZERO_CURRENT_WINDOW_SALES_HOLD` | ₹0.00 |
| $R_d \ge 0.9000$ (and $<1.10$) | `STABLE_DEMAND` | ₹0.00 |
| $R_d \ge 0.5000$ (and $<0.90$) | `BELOW_NORMAL_DEMAND_DECAY` | -₹1.00 |
| $R_d < 0.5000$ | `ZERO_DEMAND_DECAY` | -₹1.00 |

*Safety Cooldown:* If the engine decides to drop the price ($\Delta P < 0$), it checks the last time the price changed. If the last change was within the current interval length, it overrides to $\Delta P = 0$ (`ZERO_DEMAND_COOLDOWN_HOLD`) to prevent the price from dropping too rapidly in asymmetrical setups.

**Step 7: Final Application and Clamping**
$$P_{raw} = P_{old} + \Delta P$$
The new price is firmly bounded between the product's configured `minCupPrice` and `maxCupPrice`:
$$P_{final} = \max(MinPrice, \min(MaxPrice, P_{raw}))$$
