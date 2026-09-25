# PROJECT INVENTORY REPORT
## Bar Exchange / Noida Pub Exchange - Dynamic Beverage Stock Market Platform
**Phase 0 Complete Project Inspection**

---

### 1. Existing Frontend Files
- `customer-web/src/index.html`: Customer POS Order Terminal, Market Ticker Bar, Shopping Cart, Receipt Modal, Local Dynamic Price Shift Engine.
- `customer-web/src/led-display.html`: Full-screen LED Digital Stock Exchange Ticker Board, Gainers/Losers Leaderboards, QR Ordering Widget.
- `customer-web/src/bridge.html`: Hidden iframe cross-origin communications bridge (Port 8000 <-> Port 8001).
- `customer-web/src/styles.css`: Additional styling tokens and responsive rules for Customer POS.
- `customer-web/src/main.ts`: Entry point script for TypeScript build setup.
- `admin-panel/src/index.html`: Admin Operations Control Panel, 20L Container Batch Register, Dynamic Pricing Engine Control, In-Memory Sandbox Simulator, Staff User RBAC Manager, Audit Logs, Sales Reports, ApexCharts Telemetry.
- `admin-panel/src/styles.css`: Additional CSS utility classes for Admin Panel.
- `admin-panel/src/constants/app.constants.js`: System configuration constants and default product defaults.
- `admin-panel/src/services/api.service.js`: REST API client wrapper.
- `admin-panel/src/services/broadcast.service.js`: BroadcastChannel service wrapper.
- `admin-panel/src/utils/aria.utils.js`: Accessibility and keyboard interaction helpers.

---

### 2. Existing Backend Files
- `backend/pom.xml`: Spring Boot 3.3.x dependencies (Spring Web, Spring Data JPA, H2/PostgreSQL, Redis, WebSocket STOMP, Actuator, Flyway).
- `backend/src/main/resources/application.yml`: Backend environment configuration (Port 8088, H2/PostgreSQL settings, Redis host, STOMP endpoint).
- `backend/src/main/resources/db/migration/V1__init_schema.sql`: Database schema definition for products, batches, orders, order items, price history, market events.
- `backend/src/main/resources/db/migration/V10__juice_seed_data.sql`: Seed data for juice flavours and 20L container batches.
- `backend/src/main/java/com/retailpos/BackendApplication.java`: Main Spring Boot Application class.
- `backend/src/main/java/com/retailpos/pos/POSController.java`: REST controller for POS endpoints (`/api/pos/products`, `/api/pos/orders`).
- `backend/src/main/java/com/retailpos/pos/POSService.java`: Service logic for processing orders and liquid volume deductions.
- `backend/src/main/java/com/retailpos/pricing/PricingEngineService.java`: Automated 60-second pricing engine cycle scheduler.
- `backend/src/main/java/com/retailpos/pricing/PricingController.java`: REST endpoints for manual pricing evaluation, history, and market crash controls.
- `backend/src/main/java/com/retailpos/pricing/MarketCrashService.java`: Backend Market Crash routine manager.
- `backend/src/main/java/com/retailpos/websocket/WebSocketGatewayController.java`: WebSocket gateway and topic broadcaster (`/topic/prices`, `/topic/led-display`).
- `backend/src/main/java/com/retailpos/domain/*`: JPA Entities (`Product`, `JuiceBatch`, `SalesOrder`, `SalesOrderItem`, `PriceHistory`, `MarketCrashEvent`, `User`, `AuditLog`).

---

### 3. Existing Database Infrastructure & Migrations
- PostgreSQL / H2 Database configured on port 5432 (or embedded H2 for dev).
- Flyway SQL Migrations enabled (`V1` to `V10`).
- Tables defined: `products`, `juice_batches`, `sales_orders`, `sales_order_items`, `price_history`, `market_crash_events`, `users`, `audit_logs`.

---

### 4. Existing Endpoints & Communication Mechanisms
- `GET /api/pos/products`: Retrieves active juice flavours with current prices.
- `POST /api/pos/orders`: Submits customer POS checkout order.
- `POST /api/pricing/evaluate`: Triggers 60-second demand evaluation cycle.
- `GET /api/pricing/history`: Fetches price change history.
- `POST /api/pricing/market-crash/trigger`: Starts backend Market Crash routine.
- `POST /api/pricing/market-crash/stop`: Restores standard pricing.
- `GET /api/pricing/market-crash/status`: Gets active Market Crash countdown status.
- `BroadcastChannel ('pubexchange_market_channel')`: Real-time cross-tab event channel.
- `localStorage ('pubexchange_dynamic_products', 'pubexchange_custom_products', 'pubexchange_event', 'pubexchange_pos_order', 'pubexchange_market_crash_*')`: Local storage persistence.

---

### 5. Existing Business Logic Overview
- **POS Demand Shift Engine**: Increases ordered drink price (+₹1 up to ceiling ₹25) and drops unpurchased drink prices (-₹1 down to floor ₹18).
- **20L Inventory Rule**: Each batch contains 20,000ml (80 cups of 250ml). POS checkout deducts $N \times 250\text{ml}$ from the active container batch. When volume reaches 0ml, batch status is marked `DEPLETED`.
- **Market Crash Takeover**: Forces all drink prices to minimum floor (`₹18`), plays Web Audio siren sound, displays alert banners, and starts a 3-minute countdown.

---

### 6. Existing Problems & Missing Implementation
1. **Frontend-Backend Disconnect**: Frontend currently falls back to `localStorage` and client-side calculations when backend endpoints return offline or are unauthenticated.
2. **Missing JWT Authentication**: Spring Security configuration currently permits all requests; JWT token generation, role-based authorization (CASHIER, MANAGER, ADMIN), and login endpoints (`/api/auth/login`) are not fully wired up.
3. **Redis Caching**: Redis templates exist in code but require cache invalidation annotations and pub/sub listener integration.
4. **Authoritative Backend Validation**: POS checkout currently trusts client-calculated cup prices if not strictly overridden by transactional backend database checks.

---

### 7. Files That Must Be Modified & Created in Subsequent Phases

#### Files to Modify:
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/retailpos/pos/POSService.java`
- `backend/src/main/java/com/retailpos/pricing/PricingEngineService.java`
- `backend/src/main/java/com/retailpos/security/SecurityConfig.java`
- `customer-web/src/index.html`
- `customer-web/src/led-display.html`
- `admin-panel/src/index.html`

#### Files to Create:
- `PROJECT_INVENTORY.md` (Created in Phase 0)
- `ARCHITECTURE.md` (Phase 1)
- `IMPLEMENTATION_PROGRESS.md` (Phase 1)
- `API_CONTRACT.md` (Phase 37)
- `SETUP.md` & `.env.example` (Phase 38)
- `PRODUCTION_READINESS.md` (Phase 44)
- `backend/src/main/java/com/retailpos/auth/*` (Auth Controllers, DTOs, JWT Filter, Token Provider)
- `backend/src/main/java/com/retailpos/report/*` (Sales & Inventory Reporting Services)
- `backend/src/main/java/com/retailpos/inventory/*` (Inventory Service & Adjustments API)

---

### 8. Potential Risks & Mitigation Strategies
- **Race Conditions on 20L Inventory**: Simultaneous checkout from multiple POS terminals could lead to negative volume. *Mitigation*: Use `@Lock(LockModeType.PESSIMISTIC_WRITE)` on database rows during checkout transactions.
- **Visual Regression**: Backend API integration could inadvertently break custom CSS gradients or animations. *Mitigation*: Strict adherence to Rules 1–8; UI design and markup will remain 100% untouched.

---

### 9. Recommended Implementation Order
1. **Phase 0**: Project Inspection & Inventory Report (Complete).
2. **Phase 1**: Architecture Definition & Module Mapping.
3. **Phase 2–4**: Database Schema, Flyway Migrations & JPA Entity Locking.
4. **Phase 5**: JWT Security, User Roles & Authorization.
5. **Phase 6–10**: Product REST API, 20L Inventory Engine, POS Transaction Checkout, Dynamic Pricing Engine, Market Crash Routine.
6. **Phase 11–12**: Redis Caching Layer & STOMP WebSocket Live Telemetry.
7. **Phase 13–20**: Centralized Frontend API Client, POS/LED/Admin WebSocket Live Integration, Admin Batch Management, Sandbox Simulator.
8. **Phase 21–44**: Reports, Audit Logs, System Hardening, Testing, Documentation & Production Readiness Audit.
