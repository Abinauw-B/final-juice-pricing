# 🚀 Pub Exchange Production Deployment Guide

## 1. Executive Overview
This guide documents the full enterprise production deployment procedure for the **Juice Bar Stock Exchange Dynamic Pricing Platform**. The system uses a **PostgreSQL Single Source of Truth (SSoT)** database, **Spring Boot 8088** backend, **STOMP real-time WebSocket synchronization**, and NGINX reverse proxy for TLS/HTTPS termination.

---

## 2. System Architecture & Port Mapping

```text
                    INTERNET
                        │
                        ▼
              ┌──────────────────┐
              │ Reverse Proxy    │
              │ HTTPS / TLS      │
              └────────┬─────────┘
                       │
          ┌────────────┴────────────┐
          ▼                         ▼
 ┌─────────────────┐       ┌─────────────────┐
 │ Customer POS    │       │ Admin Panel     │
 │ HTTPS (:8000)   │       │ HTTPS (:8001)   │
 └────────┬────────┘       └────────┬────────┘
          │                         │
          └────────────┬────────────┘
                       ▼
              ┌──────────────────┐
              │ Spring Boot      │
              │ REST + STOMP     │
              │ Port :8088       │
              └────────┬─────────┘
                       │
                       ▼
              ┌──────────────────┐
              │ PostgreSQL       │
              │ Single Source    │
              │ Port :5432       │
              └──────────────────┘
```

---

## 3. Environment Prerequisites & Secret Injection
1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
2. Configure mandatory production secrets in `.env`:
   - `JWT_SECRET`: High-entropy key (minimum 64 characters).
   - `DB_PASSWORD`: Dedicated PostgreSQL non-superuser account password.
   - `CORS_ALLOWED_ORIGINS`: Approved production domain URLs.
   - `PUBLIC_API_URL` & `PUBLIC_WS_URL`: `https://api.pubexchange.internal/api` and `wss://api.pubexchange.internal/ws`.

---

## 4. Database Setup & Flyway Migration
1. Ensure PostgreSQL is active on port `5432`.
2. Run automated Flyway schema migrations:
   ```bash
   cd backend
   ./mvnw flyway:migrate
   ```
3. Flyway migration `V16__fix_mango_floor_ceiling.sql` ensures Mango bounds are set to `minCupPrice = ₹18` and `maxCupPrice = ₹25`.

---

## 5. Docker Container Deployment
Start the production container stack using Docker Compose:

```bash
docker compose up -d --build
```

Health check verification:
- Backend Liveness Probe: `GET http://localhost:8088/api/liveness`
- Backend Readiness Probe: `GET http://localhost:8088/api/readiness`
- PostgreSQL Health: `pg_isready -U postgres -d retailposdb`

---

## 6. Reverse Proxy & SSL/TLS Configuration
NGINX forwards requests to the Spring Boot upstream:
- `https://api.pubexchange.internal/api/` → `http://backend:8088/api/`
- `wss://api.pubexchange.internal/ws/` → `http://backend:8088/ws/` (with `Upgrade` and `Connection: Upgrade` headers)
- Enforces OWASP headers: `X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`.

---

## 7. Operational Backup & Restoration
- Custom backup dump execution:
  ```bash
  pg_dump -h localhost -p 5432 -U postgres -d retailposdb -F c -b -v -f ./backups/retailpos_prod_$(date +%Y%m%d_%H%M%S).dump
  ```
- Target restore test:
  ```bash
  pg_restore -h localhost -p 5432 -U postgres -d retailpos_restore_test -v ./backups/retailpos_prod_backup.dump
  ```

---

## 8. Rollback & Emergency Recovery
In case of deployment failure:
1. Stop application traffic via Reverse Proxy maintenance page.
2. Revert Docker image tag to previous stable build.
3. Restore database snapshot from `./backups/` if Flyway schema version rollback is required.
4. Execute health probes to verify system status prior to re-enabling traffic.
