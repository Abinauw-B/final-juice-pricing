# 📖 Pub Exchange Operations Runbook

## 1. Incident Response Procedures

### 🚨 Scenario A: Database Unavailable / Connection Loss
- **Symptoms**: `/api/readiness` returns `500 Internal Server Error` with `readiness: false`. Customer POS checkout fails safely with transaction rollback.
- **Action Steps**:
  1. Inspect PostgreSQL service status: `pg_isready -h localhost -p 5432`.
  2. Verify network connectivity between backend container and database.
  3. Inspect connection pool saturation via `/api/metrics`.
  4. Restart PostgreSQL daemon if unresponsive and monitor automatic backend reconnect.

### 🚨 Scenario B: High Checkout Failure Rate / Concurrency Spikes
- **Symptoms**: Customer checkout timeouts or unexpected 5xx responses during massive surge events.
- **Action Steps**:
  1. Verify HikariCP connection pool metrics via `/api/metrics`.
  2. Check Tomcat worker thread count (`TOMCAT_MAX_THREADS` configured to 300).
  3. Audit `sales_orders` table for deadlocks or lock contention.
  4. Confirm idempotency deduplication is absorbing client retries properly.

### 🚨 Scenario C: STOMP WebSocket Disconnection / Stale Prices
- **Symptoms**: Live prices on POS or LED Display freeze or stop updating.
- **Action Steps**:
  1. Test WebSocket endpoint health: `GET http://localhost:8088/ws/info`.
  2. Verify Spring Boot STOMP broker is broadcasting events on `/topic/prices`.
  3. Client POS will automatically re-fetch full authoritative state from `GET /api/pos/products` upon reconnect.

### 🚨 Scenario D: Market Crash Triggered
- **Symptoms**: All active product prices plummet to their configured floor bounds.
- **Action Steps**:
  1. Verify Market Crash notification received on `/topic/market-crash`.
  2. Verify `priceVersion` has incremented across all products in `products` table.
  3. Admin panel can issue Market Crash Stop trigger via `POST /api/pricing/market-crash/stop`.

---

## 2. Maintenance & Operations Tasks

### 🔄 Database Backup Procedure
Execute scheduled database dump every 6 hours:
```bash
pg_dump -h localhost -p 5432 -U postgres -d retailposdb -F c -b -v -f /var/backups/retailpos_$(date +%Y%m%d_%H%M%S).dump
```

### 🧹 Log Rotation & Retention
- Request correlation IDs (`X-Request-ID: REQ-XXXXXXXX`) enable tracing individual checkouts across SLF4J / MDC log files.
- Application logs rotated automatically at 100MB per log file with 30-day retention. Audit history in `price_history` table is permanently retained.

### 🔐 Secret & Certificate Renewal
- JWT Secret configured via `JWT_SECRET` environment variable. Rotate secret by updating `.env` and triggering rolling restart of Spring Boot containers.
