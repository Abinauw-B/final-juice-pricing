# Juice Inventory & Batch Management Specification

## Overview
The Juice Shop System tracks liquid inventory using a **Batch & Volume Model** stored with integer precision in millilitres ($\text{ml}$).

```
+-------------------------------------------------------------------+
|                        20 Litre Batch                             |
|                    (20,000 ml Initial Volume)                     |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                     Serving Cup Size = 250 ml                     |
|            Estimated Capacity = floor(Volume / 250) = 80 Cups        |
+-------------------------------------------------------------------+
```

---

## 1. Volume Calculation & Source of Truth

- **Container Capacity**: 20 Litres ($20,000\text{ ml}$).
- **Standard Cup Size**: $250\text{ ml}$.
- **Remaining Cups Formula**:
  $$\text{Estimated Remaining Cups} = \left\lfloor \frac{\text{remaining\_volume\_ml}}{250} \right\rfloor$$
- **Database Representation**: `remaining_volume_ml` is stored as an `INT` column in the `juice_batches` SQL table to prevent floating-point precision loss.

---

## 2. Concurrency Safety & Pessimistic Locking

To handle concurrent checkout requests from multiple POS cashiers without over-selling or race conditions:
1. `JuiceBatchRepository` uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` during batch retrieval.
2. The database row for the active batch is locked exclusively until the checkout transaction completes.
3. If `remaining_volume_ml < total_requested_ml`, the transaction throws an `IllegalStateException` and aborts atomically.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT b FROM JuiceBatch b WHERE b.productId = :productId AND b.status = 'ACTIVE' ORDER BY b.id ASC")
List<JuiceBatch> findActiveBatchesForProductWithLock(@Param("productId") Long productId);
```

---

## 3. Container Batch Lifecycle

```
[ BATCH_CREATED ] ---> ( POS Checkout Deductions ) ---> [ DEPLETED ] (remaining_volume_ml == 0)
```

1. **ACTIVE**: Fresh container mounted and ready for POS checkout deductions.
2. **DEPLETED**: Automatically triggered when `remaining_volume_ml` reaches 0.
3. **EXPIRED**: Container marked expired by admin.
