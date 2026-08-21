# 🗄️ PostgreSQL Database Backup & Recovery Procedure

## Overview
This document specifies the authoritative, production-grade backup and disaster recovery procedures for the Pub Exchange Dynamic Pricing system.

---

## 1. Primary Database Schema & Persistence Inventory
The PostgreSQL database `retailposdb` (Port `5432`) serves as the absolute **Single Source of Truth (SSoT)**. Backup artifacts include:

* `products`: Product catalog, current prices (`current_cup_price`), floor/ceiling bounds (`min_cup_price`, `max_cup_price`), and `price_version`.
* `sales_orders`: Transactional sales headers with unique `idempotency_key` constraints.
* `sales_order_items`: Granular order items containing unit prices and volume quantities.
* `inventory`: Product raw material volume tracking.
* `inventory_transactions`: Immutable audit ledger for batch volume deductions.
* `pricing_history`: Complete audit trail of price changes, demand scores, and explanations.
* `users` & `roles`: User accounts, security role associations, and password hashes.

---

## 2. PostgreSQL Backup Command
To perform a complete custom-format backup of the live PostgreSQL database:

```bash
pg_dump -h localhost -p 5432 -U postgres -d retailposdb -F c -b -v -f ./backups/retailposdb_backup_$(date +%Y%m%d_%H%M%S).dump
```

---

## 3. Database Recovery & Restore Procedure
To restore the system from a verified backup dump into a clean test database:

```bash
# 1. Create fresh target database
createdb -h localhost -p 5432 -U postgres retailposdb_restored

# 2. Execute restore command
pg_restore -h localhost -p 5432 -U postgres -d retailposdb_restored -v ./backups/retailposdb_backup_20260821.dump

# 3. Verify Table Integrity
psql -h localhost -p 5432 -U postgres -d retailposdb_restored -c "SELECT COUNT(*) FROM products; SELECT COUNT(*) FROM sales_orders; SELECT COUNT(*) FROM pricing_history;"
```

---

## 4. Disaster Recovery Verification Checklist
- [x] All 8 active products present with accurate `current_cup_price` and `price_version`.
- [x] Unique constraint on `sales_orders.idempotency_key` preserved.
- [x] Referential integrity between `sales_orders` and `sales_order_items` intact.
- [x] Foreign key constraints between `inventory_transactions` and `products` valid.
