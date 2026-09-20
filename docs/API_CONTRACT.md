# 📜 Juice Bar Stock Exchange — API Contract Specification

> **Specification Version:** 1.0.0  
> **Server Base URL (Local):** `http://localhost:8088`  
> **Server Base URL (Production):** `https://api.juicebar.example.com`  
> **WebSocket STOMP URL:** `ws://localhost:8088/ws` (SockJS fallback: `http://localhost:8088/ws`)  
> **OpenAPI JSON Docs:** `http://localhost:8088/v3/api-docs`  
> **Swagger UI:** `http://localhost:8088/swagger-ui/index.html`  

---

## 1. Global Invariants & Standards

1. **Content-Type**: `application/json` for all REST request bodies and responses.
2. **Server-Authoritative Pricing**: Clients never dictate prices. Unit prices and totals are strictly calculated and enforced on the server.
3. **Allowed Price Movements**: Per 60-second settlement round, price moves strictly by **+₹1.00**, **₹0.00**, or **-₹1.00**, bounded by `minCupPrice` and `maxCupPrice`.
4. **Product Independence**: Every product prices solely on its own sales demand ($W_0, W_1, W_2$) and targets. No product price is coupled to another product.
5. **Standardized Error Envelope**:
   ```json
   {
     "success": false,
     "status": 400,
     "error": "Bad Request",
     "message": "Detailed description of error",
     "timestamp": "2026-09-09T10:00:00Z",
     "path": "/api/pos/checkout"
   }
   ```

---

## 2. Authentication & Authorization

### Authentication Schemes
- **Public**: No header required.
- **Bearer JWT**: Header: `Authorization: Bearer <token>`
- **Admin Panel Header Compatibility**: `X-User-Role: ADMIN`

### Roles
- `ROLE_SUPER_ADMIN`: Full access across administrative mutation, pricing config, user management.
- `ROLE_ADMIN`: Price overrides, market crashes, batch creation, inventory restock.
- `ROLE_CASHIER` / `ROLE_CUSTOMER`: Checkout execution, public price telemetry.

---

## 3. Endpoints Matrix

### 3.1 Authentication (`/api/auth`)

#### `POST /api/auth/login`
- **Auth**: Public
- **Request**:
  ```json
  {
    "username": "admin",
    "password": "password123"
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "success": true,
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "type": "Bearer",
    "username": "admin",
    "role": "ADMIN",
    "expiresIn": 86400000
  }
  ```

#### `GET /api/auth/profile`
- **Auth**: Bearer JWT
- **Response (200 OK)**:
  ```json
  {
    "id": 1,
    "username": "admin",
    "role": "ADMIN",
    "email": "admin@juicebar.com"
  }
  ```

---

### 3.2 Product Catalog & POS (`/api/pos`, `/api/products`)

#### `GET /api/products` or `GET /api/pos/products`
- **Auth**: Public
- **Response (200 OK)**:
  ```json
  [
    {
      "id": 1,
      "name": "Fresh Mango Juice",
      "flavour": "FRESH_MANGO_JUICE",
      "defaultCupPrice": 25.00,
      "currentCupPrice": 26.00,
      "minCupPrice": 20.00,
      "maxCupPrice": 30.00,
      "defaultCupSizeMl": 250,
      "pricingMode": "DYNAMIC",
      "orderCount": 4,
      "isActive": true
    }
  ]
  ```

#### `POST /api/pos/checkout`
- **Auth**: Public
- **Headers**: `Idempotency-Key: <unique-uuid>` (optional in header, or in body)
- **Request**:
  ```json
  {
    "items": [
      {
        "productId": 1,
        "quantity": 2,
        "cupSizeMl": 250,
        "priceLockToken": "optional-token-guid"
      }
    ],
    "paymentMethod": "CASH",
    "idempotencyKey": "ORDER-UUID-12345"
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "success": true,
    "orderId": 501,
    "orderNumber": "ORD-1788949650-8B93",
    "message": "Order processed successfully",
    "totalAmount": 52.00,
    "paymentMethod": "CASH",
    "paymentStatus": "COMPLETED",
    "timestamp": "2026-09-09T10:00:00",
    "items": [
      {
        "productName": "Fresh Mango Juice",
        "quantity": 2,
        "cupSizeMl": 250,
        "unitPrice": 26.00,
        "totalPrice": 52.00,
        "volumeDeductedMl": 500
      }
    ]
  }
  ```

---

### 3.3 Dynamic Pricing & Telemetry (`/api/pricing`)

#### `GET /api/pricing/market`
- **Auth**: Public
- **Response (200 OK)**:
  ```json
  {
    "marketStatus": "OPEN",
    "marketPaused": false,
    "crashActive": false,
    "settlementIntervalSeconds": 60,
    "products": [
      {
        "beverageId": 1,
        "name": "Fresh Mango Juice",
        "currentPrice": 26.00,
        "previousPrice": 25.00,
        "priceDelta": 1.00,
        "trendDirection": "UP",
        "demandRatio": 2.45,
        "floorPrice": 20.00,
        "ceilingPrice": 30.00
      }
    ]
  }
  ```

#### `POST /api/pricing/quote`
- **Auth**: Public
- **Request**:
  ```json
  {
    "productId": 1,
    "ttlSeconds": 30
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "token": "LOCK-93F1-AB42",
    "productId": 1,
    "lockedPrice": 26.00,
    "priceVersion": 4,
    "expiresAt": "2026-09-09T10:00:30"
  }
  ```

#### `POST /api/pricing/settle` (Force Settlement)
- **Auth**: Bearer JWT (ADMIN)
- **Response (200 OK)**: Returns full cycle evaluation result with updated prices.

#### `POST /api/pricing/market-crash/trigger`
- **Auth**: Bearer JWT (ADMIN)
- **Request**:
  ```json
  {
    "durationMinutes": 3,
    "crashPrice": 18.00,
    "reason": "Happy Hour Blitz"
  }
  ```

#### `POST /api/pricing/market-crash/stop`
- **Auth**: Bearer JWT (ADMIN)

---

### 3.4 20L Inventory Batch Management (`/api/batches`)

#### `GET /api/batches`
- **Auth**: Public / Admin
- **Response (200 OK)**:
  ```json
  [
    {
      "id": 10,
      "productId": 1,
      "batchCode": "BATCH-MAN-1234",
      "containerCapacityMl": 20000,
      "initialVolumeMl": 20000,
      "remainingVolumeMl": 19500,
      "cupSizeMl": 250,
      "status": "ACTIVE"
    }
  ]
  ```

#### `POST /api/batches`
- **Auth**: Bearer JWT (ADMIN)
- **Request**:
  ```json
  {
    "productId": 1,
    "containerCapacityMl": 20000
  }
  ```

---

### 3.5 Reports & Analytics (`/api/reports`)

#### `GET /api/reports/summary`
- **Auth**: Public / Admin
- **Response (200 OK)**:
  ```json
  {
    "totalOrders": 120,
    "totalRevenue": 3480.00,
    "cupsSold": 134,
    "activeBatches": 8,
    "averageOrderValue": 29.00
  }
  ```

#### `GET /api/reports/products/sales`
- **Auth**: Public / Admin
- **Response (200 OK)**: Per-product breakdown of revenue, cups sold, and order counts.

---

### 3.6 Audit & System Notifications (`/api/audit`, `/api/notifications`)

#### `GET /api/audit`
- **Auth**: Bearer JWT (ADMIN)
- **Query Params**: `limit=50&module=PRICING`
- **Response (200 OK)**: Paginated audit log records.

#### `GET /api/notifications`
- **Auth**: Public / Admin
- **Response (200 OK)**: Active system notifications.

---

## 4. STOMP WebSocket Live Telemetry

- **Endpoint**: `/ws` (Transport: WebSocket or SockJS)
- **Broker Prefix**: `/topic`
- **Application Prefix**: `/app`

| Topic | Frequency | Payload Description |
|---|---|---|
| `/topic/prices` | Every 60s / Post-settlement | Array of updated product prices, deltas, and trend arrows |
| `/topic/settlement` | Every 60s | Full DWMA evaluation cycle summary and execution metadata |
| `/topic/led-display` | Real-time | High-contrast ticker tape payload for physical or web LED signage |
| `/topic/market-crash` | Event-driven | Market crash countdown timer, affected products, and crash price |
| `/topic/notifications` | Event-driven | System alerts (e.g. low inventory warnings, pricing errors) |
