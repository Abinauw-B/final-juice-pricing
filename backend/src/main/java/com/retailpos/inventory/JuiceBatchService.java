package com.retailpos.inventory;

import com.retailpos.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@SuppressWarnings("null")
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
                .cupSizeMl(product.getDefaultCupSizeMl() != null ? product.getDefaultCupSizeMl() : 250)
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

    @Transactional
    public JuiceBatch deductBatchVolume(Long productId, int mlToDeduct) {
        List<JuiceBatch> activeBatches = batchRepository.findByProductIdAndStatus(productId, JuiceBatch.BatchStatus.ACTIVE);
        if (activeBatches.isEmpty()) {
            throw new IllegalStateException("Insufficient inventory for product ID " + productId + ": No active juice batch available");
        }

        // 1. Single batch non-blocking atomic fast path
        for (JuiceBatch b : activeBatches) {
            if (b.getRemainingVolumeMl() >= mlToDeduct) {
                int updatedRows = batchRepository.deductVolumeAtomic(b.getId(), mlToDeduct);
                if (updatedRows > 0) {
                    JuiceBatch updatedBatch = batchRepository.findById(b.getId()).orElse(b);
                    if (updatedBatch.getRemainingVolumeMl() <= 0) {
                        updatedBatch.setStatus(JuiceBatch.BatchStatus.DEPLETED);
                        batchRepository.save(updatedBatch);
                    }

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
                }
            } else if (b.getRemainingVolumeMl() == 0) {
                b.setStatus(JuiceBatch.BatchStatus.DEPLETED);
                batchRepository.save(b);
            }
        }

                // 2. Multi-batch transactional split deduction (Phase 14)
                int totalAvailableMl = activeBatches.stream()
                        .mapToInt(JuiceBatch::getRemainingVolumeMl)
                        .sum();

                if (totalAvailableMl < mlToDeduct) {
                    throw new IllegalStateException("Insufficient inventory for product ID " + productId +
                            ": Requested " + mlToDeduct + " ml, but total available stock across all active batches is only " + totalAvailableMl + " ml");
                }

                int remainingToDeduct = mlToDeduct;
                JuiceBatch lastUpdatedBatch = null;

                for (JuiceBatch b : activeBatches) {
                    int available = b.getRemainingVolumeMl();
                    if (available <= 0) {
                        b.setStatus(JuiceBatch.BatchStatus.DEPLETED);
                        batchRepository.save(b);
                        continue;
                    }

                    int deductFromThisBatch = Math.min(available, remainingToDeduct);
                    b.deductVolume(deductFromThisBatch);
                    if (b.getRemainingVolumeMl() == 0) {
                        b.setStatus(JuiceBatch.BatchStatus.DEPLETED);
                    }
                    lastUpdatedBatch = batchRepository.save(b);

                    InventoryTransaction tx = InventoryTransaction.builder()
                            .productId(productId)
                            .batchId(lastUpdatedBatch.getId())
                            .transactionType("POS_SALE_SPLIT")
                            .volumeChangeMl(-deductFromThisBatch)
                            .notes("Split deducted " + deductFromThisBatch + " ml for sale (portion of " + mlToDeduct + " ml). Remaining in batch: " + lastUpdatedBatch.getRemainingVolumeMl() + " ml")
                            .createdAt(LocalDateTime.now())
                            .build();
                    transactionRepository.save(tx);

                    remainingToDeduct -= deductFromThisBatch;
                    if (remainingToDeduct <= 0) {
                        return lastUpdatedBatch;
                    }
                }
                if (lastUpdatedBatch != null) {
                    return lastUpdatedBatch;
                }
        throw new IllegalStateException("Insufficient inventory for product ID " + productId + ": Unable to deduct remaining volume");
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
        batchRepository.findById(batchId).ifPresent(batch -> {
            productRepository.findById(batch.getProductId()).ifPresent(p -> {
                p.setIsActive(false);
                p.setPricingMode("INACTIVE");
                productRepository.saveAndFlush(p);
            });
            batchRepository.deleteById(batchId);
        });
    }
}
