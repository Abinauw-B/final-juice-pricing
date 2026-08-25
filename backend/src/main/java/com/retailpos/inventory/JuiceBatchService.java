package com.retailpos.inventory;

import com.retailpos.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class JuiceBatchService {

    private final JuiceBatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final InventoryTransactionRepository transactionRepository;

    public JuiceBatchService(JuiceBatchRepository batchRepository, ProductRepository productRepository, InventoryTransactionRepository transactionRepository) {
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public List<JuiceBatch> getAllBatches() {
        return batchRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<JuiceBatch> getActiveBatches() {
        return batchRepository.findByStatus(JuiceBatch.BatchStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public JuiceBatch getActiveBatchForProduct(Long productId) {
        return batchRepository.findFirstActiveBatchForProduct(productId)
                .orElse(null);
    }

    @Transactional
    public JuiceBatch registerNewBatch(Long productId, Integer containerCapacityMl) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));

        int capacity = (containerCapacityMl != null && containerCapacityMl > 0) ? containerCapacityMl : 20000;
        String batchCode = "BATCH-" + product.getFlavour().substring(0, Math.min(3, product.getFlavour().length())).toUpperCase()
                + "-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();

        JuiceBatch batch = JuiceBatch.builder()
                .productId(productId)
                .batchCode(batchCode)
                .containerCapacityMl(capacity)
                .initialVolumeMl(capacity)
                .remainingVolumeMl(capacity)
                .cupSizeMl(product.getDefaultCupSizeMl())
                .status(JuiceBatch.BatchStatus.ACTIVE)
                .updatedAt(LocalDateTime.now())
                .build();

        JuiceBatch savedBatch = batchRepository.save(batch);

        // Record inventory transaction
        InventoryTransaction tx = InventoryTransaction.builder()
                .productId(productId)
                .batchId(savedBatch.getId())
                .transactionType("BATCH_CREATED")
                .volumeChangeMl(capacity)
                .notes("Registered new 20L juice container batch: " + batchCode)
                .createdAt(LocalDateTime.now())
                .build();
        transactionRepository.save(tx);

        return savedBatch;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public JuiceBatch deductBatchVolume(Long productId, int mlToDeduct) {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                List<JuiceBatch> activeBatches = batchRepository.findActiveBatchesForProductWithLock(productId);
                JuiceBatch activeBatch;

                if (activeBatches.isEmpty()) {
                    activeBatch = registerNewBatch(productId, 20000);
                } else {
                    activeBatch = activeBatches.get(0);
                    if (activeBatch.getRemainingVolumeMl() < mlToDeduct) {
                        activeBatch.setStatus(JuiceBatch.BatchStatus.DEPLETED);
                        batchRepository.save(activeBatch);
                        activeBatch = registerNewBatch(productId, 20000);
                    }
                }

                activeBatch.deductVolume(mlToDeduct);

                JuiceBatch updatedBatch = batchRepository.save(activeBatch);

                // Log transaction
                InventoryTransaction tx = InventoryTransaction.builder()
                        .productId(productId)
                        .batchId(updatedBatch.getId())
                        .transactionType("POS_SALE")
                        .volumeChangeMl(-mlToDeduct)
                        .notes("Deducted " + mlToDeduct + " ml for sale. Remaining: " + updatedBatch.getRemainingVolumeMl() + " ml")
                        .createdAt(LocalDateTime.now())
                        .build();
                transactionRepository.save(tx);

                return updatedBatch;
            } catch (org.springframework.dao.PessimisticLockingFailureException e) {
                if (attempt == maxAttempts) throw e;
                try { Thread.sleep(25L * attempt); } catch (InterruptedException ignored) {}
            }
        }
        throw new IllegalStateException("Failed to deduct volume after retries");
    }

    @Transactional
    public JuiceBatch updateBatch(String batchCodeOrId, Integer remainingVolumeMl, JuiceBatch.BatchStatus status) {
        JuiceBatch batch = null;
        if (batchCodeOrId.matches("\\d+")) {
            batch = batchRepository.findById(Long.parseLong(batchCodeOrId)).orElse(null);
        }
        if (batch == null) {
            batch = batchRepository.findByBatchCode(batchCodeOrId)
                    .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchCodeOrId));
        }

        if (remainingVolumeMl != null) {
            batch.setRemainingVolumeMl(remainingVolumeMl);
        }
        if (status != null) {
            batch.setStatus(status);
        }
        batch.setUpdatedAt(LocalDateTime.now());
        return batchRepository.save(batch);
    }

    @Transactional
    public JuiceBatch restockBatch(Long batchId, Integer additionalMl) {
        JuiceBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found with ID: " + batchId));
        int volToAdd = additionalMl != null ? additionalMl : 20000;
        int newRemaining = Math.min(batch.getContainerCapacityMl(), batch.getRemainingVolumeMl() + volToAdd);
        batch.setRemainingVolumeMl(newRemaining);
        if (newRemaining > 0) {
            batch.setStatus(JuiceBatch.BatchStatus.ACTIVE);
        }
        batch.setUpdatedAt(LocalDateTime.now());

        InventoryTransaction tx = InventoryTransaction.builder()
                .productId(batch.getProductId())
                .batchId(batch.getId())
                .transactionType("BATCH_RESTOCKED")
                .volumeChangeMl(volToAdd)
                .notes("Restocked batch " + batch.getBatchCode() + " by " + volToAdd + " ml")
                .createdAt(LocalDateTime.now())
                .build();
        transactionRepository.save(tx);

        return batchRepository.save(batch);
    }

    @Transactional
    public void deleteBatch(Long batchId) {
        batchRepository.deleteById(batchId);
    }
}
