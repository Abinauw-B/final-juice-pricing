package com.retailpos.pricing;

import com.retailpos.domain.*;
import com.retailpos.pricing.redis.PricingRedisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Authoritative Centralized Pricing Settlement Coordinator.
 *
 * Enforces:
 * 1. Single market mutation lock (PostgreSQL advisory lock + JVM reentrant lock).
 * 2. Strict DWMA demand evaluation with non-overlapping windows.
 * 3. Exact delta movement rules (+1.00, 0.00, -1.00, -2.00) with explicit validation.
 * 4. Floor (₹20.00) and Ceiling (₹30.00) boundary clamping.
 * 5. Market crash & pause arbitration (settlement skipped when crash is active).
 * 6. Transactional persistence: PostgreSQL commit -> Redis cache sync -> Market version bump -> STOMP broadcast.
 * 7. Unique settlementExecutionId for complete traceability.
 */
@Service
public class PricingSettlementCoordinator {

    private static final Logger log = LoggerFactory.getLogger(PricingSettlementCoordinator.class);
    private static final long PG_ADVISORY_LOCK_ID = 788325001L;

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final JuiceMarketSettlementRepository settlementRepository;
    private final PricingConfigurationService pricingConfigurationService;
    private final PriceAdjustmentService priceAdjustmentService;
    private final MarketCrashService marketCrashService;
    private final PricingRedisRepository pricingRedisRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final DataSource dataSource;
    private final TransactionTemplate transactionTemplate;

    private final ReentrantLock localMutationLock = new ReentrantLock();
    private final AtomicBoolean isExecutionRunning = new AtomicBoolean(false);

    private static volatile LocalDateTime lastSettlementTime = LocalDateTime.now();
    private static volatile LocalDateTime nextSettlementTime = LocalDateTime.now().plusSeconds(60);

    public PricingSettlementCoordinator(ProductRepository productRepository,
                                        PriceHistoryRepository priceHistoryRepository,
                                        JuiceMarketSettlementRepository settlementRepository,
                                        PricingConfigurationService pricingConfigurationService,
                                        PriceAdjustmentService priceAdjustmentService,
                                        MarketCrashService marketCrashService,
                                        PricingRedisRepository pricingRedisRepository,
                                        RedisTemplate<String, Object> redisTemplate,
                                        SimpMessagingTemplate messagingTemplate,
                                        DataSource dataSource,
                                        PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.settlementRepository = settlementRepository;
        this.pricingConfigurationService = pricingConfigurationService;
        this.priceAdjustmentService = priceAdjustmentService;
        this.marketCrashService = marketCrashService;
        this.pricingRedisRepository = pricingRedisRepository;
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.dataSource = dataSource;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public LocalDateTime getLastSettlementTime() {
        return lastSettlementTime;
    }

    public LocalDateTime getNextSettlementTime() {
        int interval = pricingConfigurationService != null ? pricingConfigurationService.getSettlementIntervalSeconds() : 60;
        if (interval <= 0) interval = 60;
        if (nextSettlementTime == null || LocalDateTime.now().isAfter(nextSettlementTime)) {
            nextSettlementTime = LocalDateTime.now().plusSeconds(interval);
        }
        return nextSettlementTime;
    }

    public void resetSettlementTiming(int intervalSeconds) {
        int sec = intervalSeconds > 0 ? intervalSeconds : 60;
        nextSettlementTime = LocalDateTime.now().plusSeconds(sec);
    }

    /**
     * Primary entry point for scheduled settlement.
     */
    public PricingEngineService.PriceEvaluationCycleResult executeScheduledSettlement() {
        return executeSettlement(false, LocalDateTime.now(), "SCHEDULED_TRIGGER");
    }

    /**
     * Primary entry point for admin force settlement.
     */
    public PricingEngineService.PriceEvaluationCycleResult executeForceSettlement(LocalDateTime evaluationTime) {
        return executeSettlement(true, evaluationTime != null ? evaluationTime : LocalDateTime.now(), "ADMIN_FORCE_TRIGGER");
    }

    /**
     * Centralized execution coordinator logic.
     */
    public PricingEngineService.PriceEvaluationCycleResult executeSettlement(boolean force, LocalDateTime evaluationTime, String triggerSource) {
        String executionId = "SETTLE-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        LocalDateTime now = evaluationTime != null ? evaluationTime : LocalDateTime.now();
        int intervalSeconds = pricingConfigurationService != null ? pricingConfigurationService.getSettlementIntervalSeconds() : 60;
        if (intervalSeconds <= 0) intervalSeconds = 60;

        log.info("[SETTLEMENT_STARTED] executionId={} trigger={} force={} interval={}s timestamp={}",
                executionId, triggerSource, force, intervalSeconds, now);

        // 1. Acquire Local Lock
        if (!localMutationLock.tryLock()) {
            log.warn("[SETTLEMENT_LOCKED] executionId={} Another settlement/mutation is currently executing locally. Skipping.", executionId);
            return buildCurrentMarketState(executionId, now, intervalSeconds);
        }

        Connection pgConnection = null;
        boolean pgLockAcquired = false;

        try {
            // 2. Acquire Distributed PostgreSQL Advisory Lock
            try {
                pgConnection = dataSource.getConnection();
                pgLockAcquired = tryAcquirePgAdvisoryLock(pgConnection, PG_ADVISORY_LOCK_ID);
                if (!pgLockAcquired) {
                    log.warn("[SETTLEMENT_LOCKED] executionId={} PostgreSQL Advisory Lock held by another instance. Skipping.", executionId);
                    return buildCurrentMarketState(executionId, now, intervalSeconds);
                }
                log.info("[SETTLEMENT_LOCK_ACQUIRED] executionId={} Distributed PostgreSQL Advisory Lock (ID={}) acquired.",
                        executionId, PG_ADVISORY_LOCK_ID);
            } catch (Exception e) {
                log.warn("[SETTLEMENT_LOCK_WARNING] executionId={} Could not verify PostgreSQL advisory lock: {}. Proceeding with local lock.",
                        executionId, e.getMessage());
            }

            // 3. Market State Validations
            if (PriceAdjustmentService.isMarketPaused()) {
                log.info("[MARKET_STATE_VALIDATED] executionId={} Market is PAUSED. Price calculation skipped.", executionId);
                return buildCurrentMarketState(executionId, now, intervalSeconds);
            }

            if (marketCrashService != null && marketCrashService.isCrashActive()) {
                log.info("[MARKET_STATE_VALIDATED] executionId={} Market Crash is ACTIVE. Dynamic settlement calculation skipped to prevent conflict.", executionId);
                return buildCurrentMarketState(executionId, now, intervalSeconds);
            }

            // 4. Idempotency Check for the Settlement Window
            long epochSeconds = now.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
            long bucket = (epochSeconds / intervalSeconds) * intervalSeconds;
            String windowKey = force
                    ? "SETTLEMENT_FORCE_" + epochSeconds + "_" + UUID.randomUUID().toString().substring(0, 4)
                    : "SETTLEMENT_" + intervalSeconds + "_" + bucket;

            if (!force && settlementRepository.existsByIdempotencyKey(windowKey)) {
                log.info("[SETTLEMENT_DUPLICATE_SKIPPED] executionId={} Settlement for window {} already executed. Skipping.", executionId, windowKey);
                return buildCurrentMarketState(executionId, now, intervalSeconds);
            }

            lastSettlementTime = now;
            nextSettlementTime = now.plusSeconds(intervalSeconds);

            // 5. Execute Transactional Price Calculation & Persistence
            final String finalWindowKey = windowKey;
            final int finalIntervalSec = intervalSeconds;

            SettlementTransactionResult txResult = transactionTemplate.execute(status -> {
                List<Product> products = productRepository.findByIsActiveTrueOrderByIdAsc();
                List<PricingEngineService.ProductPriceDTO> dtos = new ArrayList<>();
                int updatedCount = 0;
                int unchangedCount = 0;

                for (Product product : products) {
                    BigDecimal oldPrice = product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : product.getDefaultCupPrice();

                    // Evaluate demand and exact price movement
                    PriceAdjustmentService.PriceEvaluationResult evalResult =
                            priceAdjustmentService.evaluateAndAdjustPrice(product.getId(), now);

                    Product reloaded = productRepository.findById(product.getId()).orElse(product);
                    BigDecimal newPrice = evalResult.getNewPrice() != null ? evalResult.getNewPrice() : reloaded.getCurrentCupPrice();
                    BigDecimal basePrice = reloaded.getDefaultCupPrice() != null ? reloaded.getDefaultCupPrice() : new BigDecimal("25.00");
                    BigDecimal effectivePrice = (marketCrashService != null) ? marketCrashService.getEffectivePrice(reloaded) : newPrice;
                    BigDecimal priceDelta = evalResult.getPriceChange() != null ? evalResult.getPriceChange() : newPrice.subtract(oldPrice);

                    boolean changed = oldPrice.compareTo(newPrice) != 0;
                    if (changed) {
                        updatedCount++;
                        log.info("[PRICE_UPDATED] executionId={} product='{}' (id={}) oldPrice=₹{} newPrice=₹{} delta=₹{} category={}",
                                executionId, reloaded.getName(), reloaded.getId(), oldPrice, newPrice, priceDelta, evalResult.getDemandLevelCategory());
                    } else {
                        unchangedCount++;
                        log.info("[PRICE_UNCHANGED] executionId={} product='{}' (id={}) price=₹{} category={}",
                                executionId, reloaded.getName(), reloaded.getId(), newPrice, evalResult.getDemandLevelCategory());
                    }

                    double changePct = (basePrice.compareTo(BigDecimal.ZERO) > 0)
                            ? ((newPrice.subtract(basePrice)).doubleValue() / basePrice.doubleValue()) * 100.0
                            : 0.0;
                    String trendDirection = priceDelta.compareTo(BigDecimal.ZERO) > 0 ? "UP" : (priceDelta.compareTo(BigDecimal.ZERO) < 0 ? "DOWN" : "FLAT");

                    PricingEngineService.ProductPriceDTO dto = PricingEngineService.ProductPriceDTO.builder()
                            .beverageId(reloaded.getId())
                            .name(reloaded.getName())
                            .flavour(reloaded.getFlavour())
                            .currentPrice(newPrice)
                            .effectivePrice(effectivePrice)
                            .previousPrice(oldPrice)
                            .priceDelta(priceDelta)
                            .priceChange(priceDelta)
                            .priceVersion(reloaded.getPriceVersion() != null ? reloaded.getPriceVersion() : 1)
                            .priceChangePct(BigDecimal.valueOf(changePct).setScale(1, RoundingMode.HALF_UP).doubleValue())
                            .trendDirection(trendDirection)
                            .demandRatio(evalResult.getDemandRatio())
                            .weightedSales(evalResult.getWeightedSales())
                            .targetSales(evalResult.getTargetSales())
                            .rawW0(evalResult.getRawW0())
                            .rawW1(evalResult.getRawW1())
                            .rawW2(evalResult.getRawW2())
                            .unconsumedW0(evalResult.getUnconsumedW0())
                            .demandLevelCategory(evalResult.getDemandLevelCategory())
                            .isCrashed(marketCrashService != null && marketCrashService.isProductCrashed(reloaded.getId()))
                            .minCupPrice(reloaded.getMinCupPrice())
                            .maxCupPrice(reloaded.getMaxCupPrice())
                            .build();

                    dtos.add(dto);
                }

                // Save Settlement History Record
                JuiceMarketSettlement settlement = JuiceMarketSettlement.builder()
                        .settlementWindowStart(now.minusSeconds(finalIntervalSec))
                        .settlementWindowEnd(now)
                        .idempotencyKey(finalWindowKey)
                        .status("COMPLETED")
                        .createdAt(now)
                        .build();
                settlementRepository.save(settlement);

                log.info("[PRICE_HISTORY_SAVED] executionId={} Persisted {} price audit records and settlement record {}",
                        executionId, dtos.size(), finalWindowKey);

                return new SettlementTransactionResult(dtos, updatedCount, unchangedCount);
            });

            if (txResult == null) {
                throw new IllegalStateException("Settlement transaction returned null result for execution " + executionId);
            }

            log.info("[SETTLEMENT_COMMITTED] executionId={} Database transaction committed. Updated: {}, Unchanged: {}.",
                    executionId, txResult.updatedCount, txResult.unchangedCount);

            // 6. Post-Commit Cache & Market Version Updates
            try {
                if (pricingRedisRepository != null) {
                    for (PricingEngineService.ProductPriceDTO dto : txResult.dtos) {
                        pricingRedisRepository.setProductPrice(dto.getBeverageId(), dto.getCurrentPrice());
                    }
                    int newVersion = pricingRedisRepository.incrementMarketVersion();
                    log.info("[REDIS_UPDATED] executionId={} Synchronized {} product prices to Redis. Market Version: {}.",
                            executionId, txResult.dtos.size(), newVersion);
                }
                if (redisTemplate != null) {
                    for (PricingEngineService.ProductPriceDTO dto : txResult.dtos) {
                        redisTemplate.opsForValue().set("live_price:" + dto.getBeverageId(), dto);
                    }
                }
            } catch (Exception e) {
                log.warn("[REDIS_SYNC_WARNING] executionId={} Redis sync non-fatal bypass: {}", executionId, e.getMessage());
            }

            // 7. Post-Commit STOMP WebSocket Broadcast
            PricingEngineService.PriceEvaluationCycleResult cycleResult = PricingEngineService.PriceEvaluationCycleResult.builder()
                    .timestamp(now.toString())
                    .nextUpdateAt(nextSettlementTime.toString())
                    .evaluatedProductsCount(txResult.dtos.size())
                    .updatedPrices(txResult.dtos)
                    .marketStatus(marketCrashService != null && marketCrashService.isCrashActive() ? "CRASH" : (PriceAdjustmentService.isMarketPaused() ? "PAUSED" : "OPEN"))
                    .build();

            try {
                if (messagingTemplate != null) {
                    messagingTemplate.convertAndSend("/topic/prices", cycleResult);
                    messagingTemplate.convertAndSend("/topic/settlement", cycleResult);
                    messagingTemplate.convertAndSend("/topic/products", productRepository.findByIsActiveTrueOrderByIdAsc());
                    messagingTemplate.convertAndSend("/topic/led-display", cycleResult);
                    log.info("[WEBSOCKET_BROADCAST] executionId={} Broadcasted updated prices to /topic/prices, /topic/settlement, /topic/products, /topic/led-display.", executionId);
                }
            } catch (Exception e) {
                log.warn("[WEBSOCKET_BROADCAST_WARNING] executionId={} WebSocket broadcast non-fatal error: {}", executionId, e.getMessage());
            }

            log.info("[SETTLEMENT_COMPLETED] executionId={} Cycle finished successfully at {}.", executionId, now);
            return cycleResult;

        } catch (Exception e) {
            log.error("[SETTLEMENT_FAILED] executionId={} Error executing settlement: {}", executionId, e.getMessage(), e);
            throw e;
        } finally {
            if (pgConnection != null && pgLockAcquired) {
                try {
                    releasePgAdvisoryLock(pgConnection, PG_ADVISORY_LOCK_ID);
                } catch (Exception ex) {
                    log.warn("Error releasing PostgreSQL advisory lock: {}", ex.getMessage());
                } finally {
                    try {
                        pgConnection.close();
                    } catch (SQLException ignored) {}
                }
            }
            localMutationLock.unlock();
        }
    }

    private boolean tryAcquirePgAdvisoryLock(Connection conn, long lockId) {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            stmt.setLong(1, lockId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        } catch (SQLException e) {
            log.debug("pg_try_advisory_lock query failed: {}", e.getMessage());
        }
        return false;
    }

    private void releasePgAdvisoryLock(Connection conn, long lockId) {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            stmt.setLong(1, lockId);
            stmt.executeQuery();
        } catch (SQLException e) {
            log.debug("pg_advisory_unlock query failed: {}", e.getMessage());
        }
    }

    private PricingEngineService.PriceEvaluationCycleResult buildCurrentMarketState(String executionId, LocalDateTime now, int intervalSec) {
        List<Product> products = productRepository.findByIsActiveTrueOrderByIdAsc();
        List<PricingEngineService.ProductPriceDTO> dtos = new ArrayList<>();
        for (Product p : products) {
            BigDecimal current = p.getCurrentCupPrice() != null ? p.getCurrentCupPrice() : p.getDefaultCupPrice();
            BigDecimal base = p.getDefaultCupPrice() != null ? p.getDefaultCupPrice() : new BigDecimal("25.00");
            BigDecimal effective = (marketCrashService != null) ? marketCrashService.getEffectivePrice(p) : current;
            double changePct = (base.compareTo(BigDecimal.ZERO) > 0)
                    ? ((current.subtract(base)).doubleValue() / base.doubleValue()) * 100.0
                    : 0.0;

            dtos.add(PricingEngineService.ProductPriceDTO.builder()
                    .beverageId(p.getId())
                    .name(p.getName())
                    .flavour(p.getFlavour())
                    .currentPrice(current)
                    .effectivePrice(effective)
                    .previousPrice(current)
                    .priceDelta(BigDecimal.ZERO)
                    .priceChange(BigDecimal.ZERO)
                    .priceVersion(p.getPriceVersion() != null ? p.getPriceVersion() : 1)
                    .priceChangePct(BigDecimal.valueOf(changePct).setScale(1, RoundingMode.HALF_UP).doubleValue())
                    .trendDirection("FLAT")
                    .demandRatio(1.0)
                    .weightedSales(0.0)
                    .targetSales(0.55)
                    .rawW0(0)
                    .rawW1(0)
                    .rawW2(0)
                    .unconsumedW0(0)
                    .demandLevelCategory("NORMAL")
                    .isCrashed(marketCrashService != null && marketCrashService.isProductCrashed(p.getId()))
                    .minCupPrice(p.getMinCupPrice())
                    .maxCupPrice(p.getMaxCupPrice())
                    .build());
        }

        return PricingEngineService.PriceEvaluationCycleResult.builder()
                .timestamp(now.toString())
                .nextUpdateAt(getNextSettlementTime().toString())
                .evaluatedProductsCount(dtos.size())
                .updatedPrices(dtos)
                .marketStatus(marketCrashService != null && marketCrashService.isCrashActive() ? "CRASH" : (PriceAdjustmentService.isMarketPaused() ? "PAUSED" : "OPEN"))
                .build();
    }

    private static class SettlementTransactionResult {
        final List<PricingEngineService.ProductPriceDTO> dtos;
        final int updatedCount;
        final int unchangedCount;

        SettlementTransactionResult(List<PricingEngineService.ProductPriceDTO> dtos, int updatedCount, int unchangedCount) {
            this.dtos = dtos;
            this.updatedCount = updatedCount;
            this.unchangedCount = unchangedCount;
        }
    }
}
