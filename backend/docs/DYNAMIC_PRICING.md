# Bar Stock Exchange & Dynamic Pricing Engine Specification

## 1. Algorithmic Architecture Overview

The system uses a **Bar Stock Exchange dynamic demand engine** inspired by commercial pub exchange software (such as Shawman / DD). Item prices fluctuate in real time based on order scarcity, container stock pressure, and time multipliers, guarded by hard price boundaries and an automated Market Crash routine.

```
+-------------------------------------------------------------------+
|               Real-Time POS Order Scarcity Feedback               |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                  Dynamic Demand Calculation Engine                 |
|  Demand Score = (0.40 * Velocity) + (0.40 * Stock) + (0.20 * Time) |
+-------------------------------------------------------------------+
                                  |
         +------------------------+------------------------+
         | (Normal Trading)                                | (Market Crash Active)
         v                                                 v
+-----------------------------+               +-----------------------------+
| Step Price Adjustment (+/-₹1)|               |  Instant Drop to Floor ₹18  |
| Min: ₹18  |  Max: ₹25       |               |  Digital Gong & Timer Active|
+-----------------------------+               +-----------------------------+
```

---

## 2. Core Algorithmic Logic

### A. Base Price (Initial State)
- Each juice flavour boots up at an established default **Base Price** (e.g. ₹20).

### B. Price Floors and Ceilings (Safety Boundary)
- **Floor Price (`min_cup_price`)**: ₹18 — Ensures items never sell below baseline cost.
- **Ceiling Price (`max_cup_price`)**: ₹25 — Prevents prices from becoming exorbitantly expensive.

### C. Real-Time Order Scarcity Loop
- **Surge on Demand**: When concurrent orders are placed for a drink, the scarcity index spikes, shifting its price upward (+₹1) for subsequent purchases.
- **Drift on Inactivity**: Untouched inventory gradually drifts downward toward the floor price (₹18).

---

## 3. "Market Crash" Event Routine

### A. Trigger & Execution
- **Trigger**: Executed manually from the Admin Panel (`🚨 TRIGGER MARKET CRASH`) or scheduled via timer.
- **Digital Gong / Siren**: Plays a synthesized digital siren gong alert on POS terminals.
- **Screen Takeover Banner**: Flashing alert banner with a live countdown timer (2 to 5 minutes).
- **Floor Drop**: Instantly overrides supply/demand scoring and drops all drink prices to their hardcoded floor limits (₹18.00).

### B. Objective
Creates a high-energy order surge as customers scramble to buy juice cups at minimum floor prices before the timer expires and normal dynamic pricing resumes.

---

## 4. Mathematical Equations

### Demand Score Equation ($0 - 100$)
$$\text{Demand Score} = (w_v \times S_v) + (w_s \times S_s) + (w_t \times S_t)$$
- **Velocity Weight ($w_v$)**: $0.40$
- **Stock Pressure Weight ($w_s$)**: $0.40$ ($100\% - \text{Remaining Volume \%}$)
- **Time Factor Weight ($w_t$)**: $0.20$
