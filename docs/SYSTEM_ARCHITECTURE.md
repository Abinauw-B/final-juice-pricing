# 🏛️ System Architecture — Pub Exchange Platform

> **CRITICAL ARCHITECTURAL DIRECTIVES**
> * **POSTGRESQL = SINGLE SOURCE OF TRUTH (SSoT)**: All product prices, stock levels, orders, idempotency records, user roles, and audit trails reside authoritatively in PostgreSQL.
> * **SPRING BOOT BACKEND = AUTHORITATIVE BUSINESS LOGIC**: Client applications (POS, Admin, LED) are strictly visual presentation interfaces. The server calculates unit prices, enforces bounds, manages inventory atomicity, and executes dynamic pricing adjustments. Client price overrides are strictly rejected.

---

## 1. System Overview & Component Diagram

```text
                               ┌───────────────────────────┐
                               │     Client Web Browsers   │
                               └─────────────┬─────────────┘
                                             │ HTTPS / WSS
                                             ▼
                               ┌───────────────────────────┐
                               │   NGINX Reverse Proxy     │
                               │  (Port 80/443 Gateway)    │
                               └─────────────┬─────────────┘
                                             │ Proxied HTTP/WS
                                             ▼
                               ┌───────────────────────────┐
                               │ Spring Boot 3.2 Backend   │
                               │   (Authoritative API)     │
                               └──────┬──────┬──────┬──────┘
                                      │      │      │
            ┌─────────────────────────┘      │      └────────────────────────┐
            ▼                                ▼                               ▼
┌──────────────────────┐        ┌──────────────────────┐        ┌──────────────────────┐
│  PostgreSQL 16 DB    │        │  Redis / STOMP Broker│        │ Prometheus / Grafana │
│  (Single Source of   │        │ (Real-Time Push /    │        │ (Metrics & Tracing   │
│       Truth)         │        │  Topic /topic/prices)│        │   Telemetry)         │
└──────────────────────┘        └──────────────────────┘        └──────────────────────┘
```

---

## 2. Platform Component Breakdown

### A. Customer POS (`:8000`)
* **Role**: Customer order entry and point-of-sale catalog display.
* **Architecture**: Vanilla JavaScript / HTML5 SPA communicating with `/api/pos/checkout` and `/api/pos/products`.
* **Security**: Client sends product IDs and quantities; unit price is computed authoritatively by the backend server.

### B. Admin Panel (`:8001`)
* **Role**: Operational management, batch inventory control, dynamic parameter tuning, and Sandbox simulations.
* **Architecture**: Dynamic dashboard powered by Admin REST APIs and secured with JWT Bearer tokens.
* **Capabilities**: Manual price updates, sandbox deployment to live database, trigger/stop emergency Market Crash events.

### C. LED Market Ticker Display
* **Role**: High-visibility real-time price feed and ticker updates for physical pub venue displays.
* **Architecture**: STOMP WebSocket client subscribed to `/topic/prices` and `/topic/market-crash`.
* **Synchronization**: Receives instantaneous broadcasts upon price version increments and enforces monotonic version ordering.

### D. Spring Boot Authoritative Backend (`:8088`)
* **Role**: Single authoritative engine for business logic, pricing evaluation, checkout processing, and security.
* **Technology**: Java 17/24, Spring Boot 3.2, Spring Security (JWT / RBAC), Spring Data JPA, HikariCP, Tomcat.

### E. PostgreSQL Database (`:5432`)
* **Role**: Absolute Single Source of Truth (SSoT).
* **Tables**: `products`, `sales_orders`, `sales_order_items`, `inventory`, `inventory_transactions`, `pricing_history`, `users`, `roles`, `system_config`.
* **Migrations**: Automated schema version control via Flyway (`V1__` through `V16__`).

---

## 3. Dynamic Pricing Engine & Closed-Loop Velocity

1. **Order Velocity Tracking**: Rolling 30-second window calculates order volume per product.
2. **Demand Score**: Scaled demand metric (0.0 to 100.0) calculated from recent sales velocity versus target threshold.
3. **Price Movements**:
   * **Surge**: High velocity triggers step price increases (+₹1 step) up to `maxCupPrice`.
   * **Decay**: Zero velocity triggers gradual price decay (-₹1 step) down to `minCupPrice`.
   * **Bounds Clamping**: Strict hard floors and ceilings enforced at database layer (`V16` bounds: ₹18 - ₹25 for Mango).
4. **Price Versioning**: Every price change increments `price_version` monotonically and emits a STOMP payload over `/topic/prices`.

---

## 4. Operational & Security Mechanisms

* **Authentication & RBAC**: JWT Bearer token authentication with Role-Based Access Control (`ROLE_ADMIN`, `ROLE_CUSTOMER`).
* **Idempotency Deduplication**: Unique database index on `sales_orders.idempotency_key` prevents duplicate charging under network retries.
* **Inventory Deduction Atomicity**: Pessimistic database locking (`PESSIMISTIC_WRITE`) guarantees accurate multi-batch stock deductions without race conditions.
* **Market Crash Trigger**: Emergency event overriding dynamic pricing calculations and locking all product prices to configured min bounds until cleared.
* **Sandbox Simulator**: In-memory price trajectory simulator allowing admins to preview parameter adjustments before live deployment.
* **Monitoring & Observability**: SLF4J / MDC request correlation (`X-Request-ID`), health probes (`/api/health`, `/api/readiness`), and metrics exposition (`/api/metrics`).
* **Backup & Recovery**: Daily automated `pg_dump` backups with documented clean `pg_restore` recovery procedures.
