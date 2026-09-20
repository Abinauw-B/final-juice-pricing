# SYSTEM ARCHITECTURE SPECIFICATION
## Bar Exchange / Noida Pub Exchange - Dynamic Beverage Stock Market Platform
**Phase 1 Target System Architecture**

---

## 🏛️ Overall System Topology

```
                    USERS (Cashiers, Customers, Managers, Admins)
                                       |
       +-------------------------------+-------------------------------+
       |                               |                               |
       v                               v                               v
CUSTOMER POS TERMINAL          LED DISPLAY SIGNAGE            ADMIN CONTROL CENTER
 (HTML5 / JS @ 8000)           (HTML5 / JS @ 8000)            (HTML5 / JS @ 8001)
       |                               |                               |
       +-------------------------------+-------------------------------+
                                       |
                            REST API / STOMP WebSocket
                                       |
                                       v
                         SPRING BOOT BACKEND SERVER (:8088)
                                       |
       +-------------------------------+-------------------------------+
       |                               |                               |
       v                               v                               v
 PostgreSQL 16 DB                 Redis 7 Cache              STOMP WebSocket Engine
 (Transactional Truth)         (Live Prices & Session)        (Real-time Live Events)
```

---

## 📦 Backend Module Architecture (`com.retailpos`)

```
com.retailpos
├── auth          - Authentication, JWT token lifecycle, refresh tokens, login/logout handlers
├── user          - User entities, RBAC roles (SUPER_ADMIN, ADMIN, MANAGER, CASHIER), permissions
├── product       - Juice products, base prices, min/max price bounds, active status
├── inventory     - 20L liquid container batches, volume tracking (ML), automated batch depletion
├── order         - POS orders, itemized line items, pessimistic transaction locking
├── payment       - Payment processing (CASH, UPI, CARD), payment reference records
├── pricing       - Dynamic pricing engine, 60s automated evaluation scheduler, price history audit
├── market        - Market Crash trigger/stop routines, countdown timer, siren event broadcasting
├── report        - Revenue, sales volume, inventory depletion, pricing movement reporting engine
├── audit         - Comprehensive system audit logging for security and operational actions
├── notification - Real-time system notifications drawer and WebSocket alert broadcaster
├── settings      - Store configuration, base theme parameters, operational defaults
├── websocket     - STOMP WebSocket configuration, topic routes (/topic/prices, /topic/market)
├── security      - Spring Security filter chain, CORS origins configuration, password encoders
├── exception     - Global exception handler, standardized error responses
└── common        - Common DTOs, base entity classes, utility helpers
```

---

## 🔄 Data Synchronization & Concurrency Flow

1. **Transactional Integrity**: PostgreSQL serves as the absolute source of truth. POS checkouts execute inside database transactions (`@Transactional`) with pessimistic write locks (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on active 20L container batch records.
2. **Live Caching Layer**: Redis caches current product prices (`pubexchange:products`), market crash status (`pubexchange:market-crash`), and active inventory state (`pubexchange:inventory`) for sub-millisecond API responses.
3. **Real-Time Event Broadcasting**: When orders complete or pricing rules adjust, STOMP WebSocket events are published immediately to `/topic/prices`, `/topic/inventory`, and `/topic/market`, syncing POS, LED Display, and Admin Panel across tabs and devices.
