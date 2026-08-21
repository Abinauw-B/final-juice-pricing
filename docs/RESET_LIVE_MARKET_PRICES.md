# ↺ Reset Live Market Prices — Architecture & Production Hardening Specification

## Overview
The **Reset Live Market Prices** capability enables authorized administrators to instantly restore all active beverage products in the Pub Stock Exchange catalog to their configured default base prices (`product.defaultCupPrice`) stored in PostgreSQL.

---

## 🏗 Architecture & Design System

```text
Admin User (UI / API)
        ↓
JWT Auth & RBAC Check (Admin / Super Admin)
        ↓
POST /api/pricing/reset-all (X-Request-ID Header)
        ↓
Spring Boot @Transactional Boundary
        ↓
1. Validate Default Price Bounds (min <= default <= max)
2. Auto-Deactivate Market Crash if active
3. Update currentCupPrice = defaultCupPrice in PostgreSQL
4. Increment priceVersion (Monotonic ++1)
5. Save PriceHistory entries ("ADMIN_RESET_TO_DEFAULT")
6. Save AuditLog entry ("RESET_ALL_MARKET_PRICES")
        ↓
COMMIT TRANSACTION (PostgreSQL SSoT)
        ↓
Post-Commit STOMP WebSocket Broadcast
        ↓
/topic/prices & /topic/products
        ↓
Customer POS (8000), Admin Panel (8001), LED Ticker Display
```

---

## 🔒 Security & Authorization
- **Authentication**: Requires valid `Bearer <JWT_TOKEN>` header. Anonymous calls return `HTTP 401 Unauthorized`.
- **Authorization**: Restrictive RBAC check. Calling with `X-User-Role: CUSTOMER` or Customer JWT returns `HTTP 403 Forbidden`.
- **Error DTO**: Sanity-filtered response without internal stack trace leaks or DB credentials.

---

## ⚙️ Core Technical Capabilities

1. **Database Single Source of Truth (SSoT)**:
   - Resets use `product.getDefaultCupPrice()` dynamically stored per row in PostgreSQL. No hardcoded prices in backend Java code.
2. **Transactional Atomicity**:
   - Executes inside a Spring `@Transactional` boundary.
   - If any database operation fails, all price updates, price history records, and audit log entries roll back completely.
3. **Post-Commit WebSocket Broadcast**:
   - STOMP messages to `/topic/prices` and `/topic/products` are dispatched **ONLY AFTER** PostgreSQL transaction commits successfully.
4. **Price Versioning & Monotonicity**:
   - Every product's `priceVersion` increments monotonically (`priceVersion = priceVersion + 1`), preventing stale event overwrites.
5. **Market Crash Interaction Rule**:
   - Calling Reset All while a Market Crash event is active automatically deactivates the crash state and establishes normal default trading prices across all portals.
6. **Confirmation Modal & UI State**:
   - Frontend dialog prompts: `🚨 RESET LIVE MARKET PRICES` confirmation before execution.
   - Button is disabled with loading text `⏳ Resetting Market Prices...` during in-flight request.

---

## 🧪 Verification & Audit Suite
The operation is validated by `scripts/stage6_reset_live_prices_validation.js` covering:
- **20 / 20 PASS**: Authentication, Authorization, SSoT PostgreSQL check, Bounds, Price Versioning, Price History, Security Audit Logs, STOMP Payload, Customer POS Sync, Admin Sync, LED Sync, Idempotency, Concurrent Resets (10 parallel), Reset + Checkout Race, Reset + Pricing Eval Race, Error Formatting, Market Crash Auto-Stop, Stale Event Protection, and Database Integrity.
