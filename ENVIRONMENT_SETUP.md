# 🛠️ Environment Setup & Quickstart Guide

This guide walks you through setting up and running the **Juice Bar Stock Exchange (Pub Exchange)** on local development or production servers.

---

## 1. System Prerequisites

- **Java Development Kit (JDK)**: Version 21 or later
- **PostgreSQL**: Version 15+ (PostgreSQL 16 or 18 recommended)
- **Node.js**: Version 18+ (for frontend dev servers if needed)
- **Redis** (Optional): Cache & cross-instance pub/sub (gracefully bypassed if absent)
- **Maven**: Included via `backend/mvnw.cmd` wrapper

---

## 2. Environment Variables Configuration

Copy `.env.example` to `.env` in the project root:

```bash
cp .env.example .env
```

Key environment variables:
| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8088` | Backend Spring Boot HTTP port |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `retailposdb` | Database name |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `JWT_SECRET` | `404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970` | 256-bit HMAC signing key |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:8000,http://localhost:8001` | Permitted browser origins |

---

## 3. Database Setup

1. Create the database in PostgreSQL:
   ```sql
   CREATE DATABASE retailposdb;
   ```
2. Flyway migrations (V1 through V38) will automatically execute upon starting the Spring Boot backend, applying the complete schema, tables, indexes, constraints, and canonical 8-juice product catalog.

---

## 4. Running the Backend

From the `backend` directory:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

The backend starts on `http://localhost:8088`.
- API Health: `http://localhost:8088/actuator/health`
- Swagger UI: `http://localhost:8088/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8088/v3/api-docs`

---

## 5. Running the Frontends

The frontends are static HTML/JS/CSS applications served via any HTTP server (e.g. Python `http.server`, `npx serve`, or Nginx).

### Customer POS & LED Display (Port 8000)
```powershell
cd customer-web
python -m http.server 8000
```
- **Customer POS**: `http://localhost:8000/src/index.html`
- **LED Display Board**: `http://localhost:8000/src/led-display.html`

### Admin Management Panel (Port 8001)
```powershell
cd admin-panel
python -m http.server 8001
```
- **Admin Control Center**: `http://localhost:8001/src/index.html`

### One-Click Startup Script (Windows)
Double-click `Start-Pub-Exchange.bat` to launch all servers concurrently.

---

## 6. Running Tests & Verifications

```powershell
cd backend
.\mvnw.cmd test
```
All 72 tests (unit tests, pricing validation tests, concurrency tests, inventory consistency tests) will execute and verify the application state.
