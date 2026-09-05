# Production Cloud Deployment Guide

This document specifies the end-to-end production architecture, deployment instructions, and operational procedures for the **Juice Dynamic Price Project**.

---

## 1. Production Architecture Overview

The system is split into decoupled cloud layers:

```
┌──────────────────────────────────────────────────────────────┐
│                    VERCEL FRONTENDS (EDGE)                  │
│                                                              │
│   CUSTOMER POS:                 ADMIN PANEL:                 │
│   https://final-juice-          https://final-juice-         │
│   pricing.vercel.app            pricing-admin.vercel.app     │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTPS REST + Secure WSS (SockJS)
                           ▼
┌──────────────────────────────────────────────────────────────┐
│              PUBLIC SPRING BOOT BACKEND (CLOUD)             │
│              https://YOUR-BACKEND-DOMAIN.com                 │
│                                                              │
│   • DWMA Pricing Engine (BigDecimal Precision)              │
│   • STOMP WebSocket Message Broker (/topic/prices, etc.)     │
│   • Centralized Dynamic CORS (APP_CORS_ALLOWED_ORIGINS)      │
│   • Flyway DB Migrations & SSoT Enforcement                  │
└──────────────┬───────────────────────────────┬───────────────┘
               │                               │
               ▼                               ▼
┌───────────────────────────────┐  ┌───────────────────────────┐
│   PostgreSQL 16 (SSoT)        │  │     Redis 7 (Cache)       │
│   Authoritative Source        │  │  Fast In-Memory Cache     │
│   (Supabase / RDS / Railway)  │  │  (Upstash / ElastiCache)  │
└───────────────────────────────┘  └───────────────────────────┘
```

### Core Architecture Principles:
- **PostgreSQL = Single Source of Truth (SSoT):** Products, inventory batches, price history, and orders are authoritatively persisted in PostgreSQL.
- **Redis = Temporary Cache & Distributed Counters:** Accelerates fast lookups. If Redis restarts or is unavailable, the backend automatically falls back to PostgreSQL without interrupting transactions.
- **Frontends = Pure Display & Mutation Dispatchers:** No client-side price authority. All prices and rules originate from the backend.
- **Secure WebSockets:** Automatically negotiates `wss://` on HTTPS environments to eliminate browser mixed-content blocks.

---

## 2. Vercel Frontend Deployments

The two frontends are deployed as static web applications on Vercel:

| Frontend | Production URL | Vercel Root Directory |
| :--- | :--- | :--- |
| **Customer POS** | `https://final-juice-pricing.vercel.app` | `customer-web` |
| **Admin Panel** | `https://final-juice-pricing-admin.vercel.app` | `admin-panel` |

### Routing & Rewrite Strategy
Both frontends include `vercel.json` and a root `index.html` trampoline ensuring that root requests (`/`) seamlessly resolve to `/src/index.html` while preserving direct assets (`/src/*`, `/config.js`, `/bridge.html`).

### API Configuration
Each frontend loads `config.js` at runtime before application logic. It resolves `API_BASE_URL` through the following priority order:
1. `window.__ENV__.API_BASE_URL` (injected via deployment script or header)
2. URL search parameter: `?api_url=https://your-backend.com`
3. `localStorage.getItem('API_BASE_URL')` (useful for operator testing)
4. Localhost auto-detection: If `location.hostname` is `localhost` or `127.0.0.1`, it connects to `http://localhost:8088`.
5. Non-localhost: Defaults to the production backend origin.

---

## 3. Cloud Backend Deployment (Render / Railway / AWS / Docker)

The backend is an enterprise Spring Boot 3 / Java 21 service packaged as an executable JAR or Docker container.

### A. Environment Variables Required

| Variable | Description | Example / Default |
| :--- | :--- | :--- |
| `SERVER_PORT` | Port for Spring Boot | `8088` (or provided by host, e.g. `$PORT`) |
| `DATABASE_URL` | JDBC Connection URL to PostgreSQL | `jdbc:postgresql://ep-xyz.region.aws.neon.tech:5432/retailposdb` |
| `DATABASE_USERNAME` | PostgreSQL user | `retailpos_admin` |
| `DATABASE_PASSWORD` | PostgreSQL password | `super_secure_pass_2026` |
| `REDIS_HOST` | Redis hostname | `redis-cluster.cache.amazonaws.com` or `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_PASSWORD` | Redis authentication token | (optional for local, required for cloud) |
| `APP_CORS_ALLOWED_ORIGINS` | Comma-separated allowed CORS origins | `https://final-juice-pricing.vercel.app,https://final-juice-pricing-admin.vercel.app,http://localhost:8000,http://localhost:8001,http://localhost:8002` |
| `JWT_SECRET` | High-entropy signing secret (min 64 chars) | `PubExchangeSuperSecretKeyForJWTAuth2026EnterpriseProductionEngine!` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `prod` |

> [!IMPORTANT]
> PostgreSQL JDBC URLs must begin with `jdbc:postgresql://`. If cloud providers output `postgres://` or `postgresql://`, format it with `jdbc:postgresql://`.

### B. Building the Production JAR
```bash
cd backend
./mvnw clean package -DskipTests
```
The output artifact is generated at `backend/target/backend-1.0.0-SNAPSHOT.jar`.

### C. Docker Deployment
```bash
docker build -t juice-backend:prod ./backend
docker run -d \
  -p 8088:8088 \
  -e DATABASE_URL="jdbc:postgresql://host:5432/db" \
  -e DATABASE_USERNAME="user" \
  -e DATABASE_PASSWORD="secret" \
  -e REDIS_HOST="redis-host" \
  -e APP_CORS_ALLOWED_ORIGINS="https://final-juice-pricing.vercel.app,https://final-juice-pricing-admin.vercel.app" \
  juice-backend:prod
```

---

## 4. Database Setup & Flyway Migrations

1. Ensure the PostgreSQL database exists with UTF-8 encoding.
2. Spring Boot automatically executes all migrations in `backend/src/main/resources/db/migration/` upon startup:
   - Initial schema, products, user roles, sales orders.
   - Floor and ceiling constraints (`V16__fix_mango_floor_ceiling.sql`).
   - Dynamic pricing configurations and crash snapshot tables.
3. Verification query:
   ```sql
   SELECT id, name, flavour, current_cup_price, min_cup_price, max_cup_price, is_price_locked FROM products;
   ```

---

## 5. WebSocket & Real-Time Synchronization

The backend exposes a STOMP broker at `/ws`.
- **SockJS Support:** Enabled for fallbacks where raw WebSockets are constrained.
- **WSS Auto-Negotiation:** On HTTPS pages (`https://...`), the frontend switches to `wss://...` to prevent mixed-content blocks.
- **Active STOMP Topics:**
  - `/topic/prices` — Real-time price updates broadcast to Customer POS, LED display, and Admin.
  - `/topic/settlement` — Notifies clients of DWMA settlement cycles.
  - `/topic/led-display` — High-visibility ticker updates.
  - `/topic/market-crash` — Instant crash alerts (setting products to floor) and recovery events.
  - `/topic/products` — Product catalogue modifications.

---

## 6. Local Development Workflow

Run all three components concurrently from the project root:
```bash
npm run dev
```
This boots:
1. **Backend:** `http://localhost:8088` (Spring Boot)
2. **Customer POS:** `http://localhost:8000` (`customer-web`)
3. **Admin Panel:** `http://localhost:8001` (`admin-panel`)
4. **Unified Portal:** `http://localhost:8002` (or open `index.html`)

---

## 7. Production Verification Checklist

1. **Health Verification:**
   ```bash
   curl -s https://YOUR-BACKEND-DOMAIN.com/api/health
   ```
   Must return HTTP 200 with:
   ```json
   {
     "status": "UP",
     "database": "CONNECTED",
     "service": "dynamic-pricing-backend"
   }
   ```
2. **Customer POS Verification:**
   - Open `https://final-juice-pricing.vercel.app`.
   - Verify top header shows `BACKEND ONLINE`.
   - Confirm products load with real INR prices.
   - Complete a 1-cup checkout; verify order confirmation and instant inventory decrement.
3. **Admin Panel Verification:**
   - Open `https://final-juice-pricing-admin.vercel.app`.
   - Verify live pricing table matches backend database.
   - Trigger Manual Price Lock on a juice flavour; verify it persists across hard refresh.
   - Trigger Market Crash; verify prices immediately drop to floor (`₹18.00`).
   - End Market Crash; verify pre-crash price snapshot restores accurately.
4. **WebSocket Loop & Health Check Audit:**
   - Inspect browser network panel.
   - Confirm health check polls at a healthy interval (e.g. 15-30s) with 0 cascading request loops.
   - Confirm WebSocket connection is established once with 0 duplicate subscriptions.
