# Juice Bar Stock Exchange — POS, Inventory & Dynamic Pricing System

A production-ready retail management system for fresh juice bars featuring a **20L liquid volume batch model**, **PostgreSQL persistence (SSoT)**, **Redis caching layer**, **Flyway schema migrations**, **Pessimistic Row Locking**, and a **1-Minute Automated Enterprise DWMA Dynamic Pricing Engine**.

---

## 🌐 Production Deployments

The application is deployed across high-availability cloud platforms:

- **Customer POS:** [https://final-juice-pricing.vercel.app](https://final-juice-pricing.vercel.app)
- **Admin Panel:** [https://final-juice-pricing-admin.vercel.app](https://final-juice-pricing-admin.vercel.app)
- **Backend Architecture:** Spring Boot REST + WSS STOMP broker connected to cloud PostgreSQL & Redis.

Detailed deployment instructions and cloud setup are documented in [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

---

## 🏗️ Production Architecture

```text
CUSTOMER POS                               ADMIN PANEL
https://final-juice-pricing.vercel.app     https://final-juice-pricing-admin.vercel.app
             │                                          │
             │ HTTPS REST API                           │ HTTPS REST API
             │ Secure WebSocket/STOMP (WSS)             │ Secure WebSocket/STOMP (WSS)
             ▼                                          ▼
     ┌──────────────────────────────────────────────────────────┐
     │                PUBLIC SPRING BOOT BACKEND               │
     │                https://YOUR-BACKEND-DOMAIN               │
     │   • DWMA Pricing Engine (BigDecimal Precision)          │
     │   • STOMP Broker (/topic/prices, /topic/market-crash)    │
     │   • Configurable Dynamic CORS (APP_CORS_ALLOWED_ORIGINS)│
     └──────────────────────────┬───────────────────────────────┘
                                │
                 ┌──────────────┴──────────────┐
                 ▼                             ▼
     ┌───────────────────────┐     ┌───────────────────────┐
     │  PostgreSQL 16 (SSoT) │     │     Redis 7 Cache     │
     │  Authoritative Source │     │  Fast Distributed     │
     │  (Products, Orders,   │     │  Counters & Fallback  │
     │   Inventory Batches)  │     │  Memory Cache         │
     └───────────────────────┘     └───────────────────────┘
```

---

## 🌟 Key Features

1. **20L Batch Container & Volume Tracking**:
   - Liquid inventory tracked in `remaining_volume_ml` ($20,000\text{ ml}$ per batch).
   - Standard $250\text{ ml}$ cup serving size ($80\text{ cups per batch}$).
   - `estimated_remaining_cups = floor(remaining_volume_ml / 250)`.

2. **Concurrency Safety & Pessimistic Row Locking**:
   - Uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` during checkout to guarantee atomic volume deductions under concurrent POS cashier requests.
   - Idempotent checkout guard prevents accidental double order submissions.

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
     - **Low Demand / Zero Demand ($R_d < 0.90$)**: $-₹1.00$
   - **Step Movement Invariant**: Every normal settlement changes price by strictly $+₹1.00, ₹0.00, \text{ or } -₹1.00$.
   - **Bounded Clamping & Floor Protection**: $P_{\text{new}} = \max(\text{minCupPrice}, \min(\text{maxCupPrice}, P_{\text{current}} + \Delta P))$.

4. **Manual Price Lock Override**:
   - Allows administrators to manually lock a price in PostgreSQL.
   - Dynamic pricing scheduler respects manual locks until released.
   - Persists across hard browser refreshes.

5. **Market Crash Routine & Pre-Crash Recovery**:
   - Instant floor pricing ($₹18.00$) for all juice varieties.
   - Real-time alert broadcast via STOMP WebSocket on `/topic/market-crash`.
   - On crash conclusion, restores exact pre-crash price snapshot to database and clients.

6. **Secure WebSockets & Real-Time Synchronization**:
   - Auto-negotiates `ws://` locally and `wss://` on HTTPS production to eliminate mixed-content errors.
   - Exponential backoff reconnect and duplicate subscription cleanup guards.

---

## 💻 Local Development Setup

### 1. Prerequisites
- **Java 21+** & **Maven 3.9+**
- **Node.js 18+**
- **PostgreSQL 16+** on `localhost:5432`

### 2. Start All Services Concurrently
From the root repository directory:
```powershell
npm run dev
```
This starts:
- **Backend API & WebSocket:** `http://localhost:8088`
- **Customer POS:** `http://localhost:8000`
- **Admin Control Center:** `http://localhost:8001`

### 3. Individual Startup Commands
```powershell
# Spring Boot Backend
cd backend
.\mvnw.cmd spring-boot:run

# Customer POS
cd customer-web
npm start

# Admin Panel
cd admin-panel
npm start
```

---

## 🧪 Running Automated Tests

Run the full JUnit test suite (100% passing):
```powershell
cd backend
.\mvnw.cmd clean test
```

Build production JAR package:
```powershell
cd backend
.\mvnw.cmd clean package -DskipTests
```

---

## 📑 Project Structure

```text
├── admin-panel/        # Admin Control Center & Pricing Simulator (Port 8001 / Vercel)
├── backend/            # Spring Boot REST API, STOMP broker & Flyway migrations (Port 8088)
├── customer-web/       # Customer POS application & LED Display (Port 8000 / Vercel)
├── docs/               # Architecture, API docs, and DEPLOYMENT.md
├── index.html          # Unified multi-panel dev portal
└── package.json        # Concurrent workspace scripts
```
