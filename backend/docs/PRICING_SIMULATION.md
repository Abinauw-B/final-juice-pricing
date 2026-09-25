# Dynamic Pricing Simulator Sandbox Manual

## Purpose & Isolation

The **Dynamic Pricing Simulator** is a sandbox testing module in the Admin Control Center (`/admin-panel` on port 8001) designed to allow store managers and administrators to test dynamic pricing parameters across virtual customer purchasing patterns.

> [!IMPORTANT]
> **State Isolation Guarantee**: The simulator executes completely in-memory. It does **NOT** mutate live database tables, active container batch volumes, or production price histories.

---

## Operating Instructions

1. Open the Admin Panel at `http://localhost:8001`.
2. Click on **"🎮 Pricing Sandbox Simulator"** in the sidebar.
3. Configure simulation inputs:
   - **Juice Flavour**: Name of the flavour (e.g. Fresh Mango Juice).
   - **Initial Volume**: Container volume in ML (e.g. 20,000 ML = 20L).
   - **Start Price**: Initial cup price in ₹ (e.g. ₹20).
   - **Cups per Step**: Purchases simulated per interval (e.g. 4 cups every 5 mins).
   - **Min / Max Boundaries**: Bounded price range (e.g. ₹18 - ₹25).
4. Click **"▶️ Run Sandbox Simulation"**.

---

## Output Metrics & Timeline Table

The simulator generates:
- **Summary Cards**: Final price, total cups sold, volume consumed.
- **Step Trajectory Timeline Table**:
  - Step Index & Time Stamp
  - Remaining ML Volume & Estimated Remaining Cups
  - Demand Score ($0-100$)
  - Price Movement Badge ($+₹1$, $-₹1$, $\text{UNCHANGED}$)
  - Current Price
  - Explanation Log
