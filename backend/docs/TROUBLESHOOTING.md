# Juice Dynamic Pricing System — Troubleshooting Guide

---

## 1. PostgreSQL Service Connection Issues

### Issue: Backend fails to connect to database or HikariPool timeout
- **Cause:** PostgreSQL 18.3 service `postgresql-x64-18` is stopped or port 5432 is blocked.
- **Resolution:**
  ```powershell
  Get-Service -Name postgresql-x64-18
  Start-Service -Name postgresql-x64-18
  ```

---

## 2. Port Occupied Errors

### Issue: `Port 8088 was already in use`
- **Cause:** A previous Spring Boot process is already running on port 8088.
- **Resolution:**
  ```powershell
  $proc = Get-NetTCPConnection -LocalPort 8088 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess
  if ($proc) { Stop-Process -Id $proc -Force }
  ```

### Issue: `Port 8000` or `Port 8001` occupied
- **Resolution:**
  ```powershell
  $p8000 = Get-NetTCPConnection -LocalPort 8000 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess
  if ($p8000) { Stop-Process -Id $p8000 -Force }
  $p8001 = Get-NetTCPConnection -LocalPort 8001 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess
  if ($p8001) { Stop-Process -Id $p8001 -Force }
  ```

---

## 3. Flyway Migration Failures

### Issue: Flyway schema validation mismatch
- **Cause:** Tables were altered manually without Flyway DDL migration.
- **Resolution:**
  Flyway is the sole database migration manager. Ensure all schema changes are added as `V<N>__<description>.sql` in `backend/src/main/resources/db/migration/`.

---

## 4. Frontend Unable to Connect / CORS Errors

### Issue: CORS warning in browser console
- **Cause:** Origin not matched in Spring Boot `SecurityConfig.java`.
- **Resolution:** Origin patterns `http://localhost:3000`, `http://localhost:8000`, `http://localhost:8001` are explicitly enabled in backend `SecurityConfig.java`.

---

## 5. WebSocket Disconnections

### Issue: SockJS or STOMP fail to connect
- **Cause:** Backend `/ws` endpoint unreachable or network blocked.
- **Resolution:** Verify backend is active on `http://localhost:8088/api/health`. Frontend auto-reconnects every 5 seconds.
