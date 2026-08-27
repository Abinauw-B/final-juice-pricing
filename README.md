# Juice Bar Stock Exchange — POS, Inventory & Dynamic Pricing System

A production-ready retail management system for fresh juice bars featuring a **20L liquid volume batch model**, **PostgreSQL 18.3 persistence**, **Flyway schema migrations**, **Pessimistic Row Locking**, and a **1-Minute Automated Enterprise Dynamic Pricing Engine with ₹4 Downward Price Decay**.

---

## 🏗️ System Architecture

```
                                  +-----------------------------+
                                  |    Customer / POS Web       |
                                  |    (Port 8000 / HTML5 SPA)  |
                                  +--------------+--------------+
                                                 |
                                                 v [REST & STOMP / WebSocket]
+-----------------------------+                  |                  +-----------------------------+
|        Admin Panel          |------------------+----------------->|     Spring Boot Backend     |
|    (Port 8001 / HTML5 SPA)  |<----------------------------------->|    (Java 24.0.2 / Port 8088) |
+-----------------------------+                                     +--------------+--------------+
                                                                                   |
                                                                                   v [Flyway Managed]
                                                                    +-----------------------------+
                                                                    |     PostgreSQL 18.3 DB      |
                                                                    |        (retailposdb)        |
                                                                    +-----------------------------+
```

---

## 🌟 Key Features

1. **20L Batch Container & Volume Tracking**:
   - Liquid inventory tracked in `remaining_volume_ml` ($20,000\text{ ml}$ per batch).
   - Standard $250\text{ ml}$ cup serving size ($80\text{ cups per batch}$).
   - `estimated_remaining_cups = floor(remaining_volume_ml / 250)`.

2. **Concurrency Safety & Pessimistic Row Locking**:
   - Uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` during checkout to guarantee atomic volume deductions under concurrent POS cashier requests.

3. **1-Minute Enterprise DWMA Dynamic Pricing Engine**:
   - **Settlement Interval**: Authoritative 60-second cycle executed by Spring Boot scheduler.
   - **Discrete Weighted Moving Average (DWMA)**:
     - $W_0$ (Latest $0-1$ min): Weight $1.00$
     - $W_1$ (Previous $1-2$ min): Weight $0.50$
     - $W_2$ (Previous $2-3$ min): Weight $0.25$
     - $S_w = (1.00 \times W_0) + (0.50 \times W_1) + (0.25 \times W_2)$
   - **Demand Ratio ($R_d$)**:
     $$R_d = \frac{S_w}{\text{TargetSalesPer1Minute}}$$
   - **Settlement Movement Rules**:
     - **High Demand ($R_d \ge 1.10$ and $W_0 > 0$)**: $+₹1.00$
     - **Stable Demand ($0.90 \le R_d < 1.10$ or $R_d \ge 1.10$ with $W_0 = 0$)**: $₹0.00$
     - **Low Demand / Zero Demand ($R_d < 0.90$)**: $-₹4.00$
   - **Downward Step Validation**: Downward delta must satisfy $|\Delta P| \pmod 4 = 0$.
   - **Bounded Clamping & Floor Protection**: $P_{\text{new}} = \max(\text{minCupPrice}, \min(\text{maxCupPrice}, P_{\text{current}} + \Delta P))$.
     *(Example: ₹21.00 with ₹4 decay clamped at floor ₹18.00 yields ₹18.00, not ₹17.00)*.

4. **Market Crash Routine**:
   - Panic floor pricing ($₹18.00$) for all juice varieties.
   - Real-time alert broadcast via STOMP WebSocket on `/topic/market-crash`.

5. **Real-Time WebSocket STOMP Engine**:
   - Live price updates broadcast on `/topic/prices`, `/topic/pricing-config`, and `/topic/led-display`.
   - POS, Admin Panel, LED Display, and Sandbox Simulator receive instant reactive updates.

---

## 🚀 Getting Started

### System Requirements
- **Java 24 / 21+** & **Maven 3.9+**
- **Node.js 18+**
- **PostgreSQL 18.3** service (`postgresql-x64-18`) on `localhost:5432`

---

## 💻 Startup Commands (PowerShell)

### 1. Verify PostgreSQL Database Service
```powershell
Get-Service -Name postgresql-x64-18
```

### 2. Start Spring Boot Backend Server (Port 8088)
```powershell
cd "D:\Juice Dynamic Price Project\backend"
.\mvnw.cmd spring-boot:run
```
- **Backend URL:** `http://localhost:8088`
- **Health Check:** `http://localhost:8088/api/health`

### 3. Start Both Frontend Applications Simultaneously
From the root directory:
```powershell
cd "D:\Juice Dynamic Price Project"
npm run dev
```
- **Customer POS Web:** `http://localhost:8000`
- **Admin Control Center:** `http://localhost:8001`

---

## 🧪 Running Automated Tests

Run the full Maven JUnit test suite:
```powershell
cd "D:\Juice Dynamic Price Project\backend"
.\mvnw.cmd clean test
```

Build production JAR package:
```powershell
cd "D:\Juice Dynamic Price Project\backend"
.\mvnw.cmd clean package
```

---

## 📑 Project Structure

```
├── backend/            # Spring Boot 3.3 Java 24 REST API server & Flyway migrations
├── customer-web/       # Customer POS application (Port 8000)
├── admin-panel/        # Admin Control Center & Pricing Simulator (Port 8001)
├── docs/               # API_DOCUMENTATION, DYNAMIC_PRICING, JUICE_INVENTORY
├── DEMO_CHECKLIST.md   # Pre-demo verification checklist
├── TROUBLESHOOTING.md  # Port cleanup & error resolution guide
└── README.md
```
