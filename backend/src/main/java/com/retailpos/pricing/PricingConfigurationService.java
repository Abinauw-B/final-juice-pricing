package com.retailpos.pricing;

import com.retailpos.domain.*;
import com.retailpos.pricing.model.PricingConfigDTO;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@SuppressWarnings("null")
public class PricingConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(PricingConfigurationService.class);

    private final PricingConfigurationRepository configRepository;
    private final PricingConfigAuditLogRepository auditLogRepository;
    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    // Fast in-memory cache for low-latency DWMA pricing calculations
    private final Map<String, String> globalConfigCache = new ConcurrentHashMap<>();
    private final AtomicLong currentConfigVersion = new AtomicLong(1L);
    private volatile LocalDateTime lastConfigUpdate = LocalDateTime.now();

    public PricingConfigurationService(PricingConfigurationRepository configRepository,
                                       PricingConfigAuditLogRepository auditLogRepository,
                                       ProductRepository productRepository,
                                       RedisTemplate<String, Object> redisTemplate,
                                       SimpMessagingTemplate messagingTemplate) {
        this.configRepository = configRepository;
        this.auditLogRepository = auditLogRepository;
        this.productRepository = productRepository;
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void init() {
        reloadFromDatabase();
    }

    public synchronized void reloadFromDatabase() {
        try {
            List<PricingConfiguration> allConfigs = configRepository.findByProductIdIsNull();
            for (PricingConfiguration cfg : allConfigs) {
                globalConfigCache.put(cfg.getSettingKey(), cfg.getSettingValue());
                if ("GLOBAL_CONFIG_VERSION".equals(cfg.getSettingKey())) {
                    try {
                        currentConfigVersion.set(Long.parseLong(cfg.getSettingValue()));
                    } catch (Exception ignored) {}
                }
            }
            lastConfigUpdate = LocalDateTime.now();
            log.info("Loaded {} global pricing configuration keys from PostgreSQL. Active version: {}",
                    globalConfigCache.size(), currentConfigVersion.get());
        } catch (Exception e) {
            log.warn("Failed to load pricing configurations from DB on startup (tables may be initializing): {}", e.getMessage());
        }
    }

    // --- GETTERS FOR DWMA ENGINE ---

    public long getConfigurationVersion() {
        return currentConfigVersion.get();
    }

    public int getSettlementIntervalSeconds() {
        String val = globalConfigCache.getOrDefault("SETTLEMENT_INTERVAL_SECONDS", "60");
        try {
            int interval = Integer.parseInt(val);
            return interval > 0 ? interval : 60;
        } catch (Exception e) {
            return 60;
        }
    }

    public BigDecimal getWeightW0() {
        return parseDecimal(globalConfigCache.getOrDefault("WEIGHT_W0", "1.0000"), new BigDecimal("1.0000"));
    }

    public BigDecimal getWeightW1() {
        return parseDecimal(globalConfigCache.getOrDefault("WEIGHT_W1", "0.5000"), new BigDecimal("0.5000"));
    }

    public BigDecimal getWeightW2() {
        return parseDecimal(globalConfigCache.getOrDefault("WEIGHT_W2", "0.2500"), new BigDecimal("0.2500"));
    }

    public BigDecimal getHighDemandThreshold() {
        return parseDecimal(globalConfigCache.getOrDefault("HIGH_DEMAND_THRESHOLD", "1.1000"), new BigDecimal("1.1000"));
    }

    public BigDecimal getStableDemandLowerThreshold() {
        return parseDecimal(globalConfigCache.getOrDefault("STABLE_DEMAND_LOWER_THRESHOLD", "0.9000"), new BigDecimal("0.9000"));
    }

    public BigDecimal getStableDemandUpperThreshold() {
        return parseDecimal(globalConfigCache.getOrDefault("STABLE_DEMAND_UPPER_THRESHOLD", "1.1000"), new BigDecimal("1.1000"));
    }

    public BigDecimal getLowDemandThreshold() {
        return parseDecimal(globalConfigCache.getOrDefault("LOW_DEMAND_THRESHOLD", "0.5000"), new BigDecimal("0.5000"));
    }

    public BigDecimal getIncreaseStep() {
        BigDecimal val = parseDecimal(globalConfigCache.getOrDefault("INCREASE_STEP", "1.00"), new BigDecimal("1.00"));
        return (val.compareTo(BigDecimal.ONE) > 0) ? new BigDecimal("1.00") : val;
    }

    public BigDecimal getDecreaseStep1() {
        BigDecimal val = parseDecimal(globalConfigCache.getOrDefault("DECREASE_STEP_1", "1.00"), new BigDecimal("1.00"));
        return (val.compareTo(BigDecimal.ONE) > 0) ? new BigDecimal("1.00") : val;
    }

    public BigDecimal getDecreaseStep2() {
        BigDecimal val = parseDecimal(globalConfigCache.getOrDefault("DECREASE_STEP_2", "1.00"), new BigDecimal("1.00"));
        return (val.compareTo(BigDecimal.ONE) > 0) ? new BigDecimal("1.00") : val;
    }

    public BigDecimal getPriceDecreaseStep() {
        BigDecimal val = parseDecimal(globalConfigCache.getOrDefault("PRICE_DECREASE_STEP", "1.00"), new BigDecimal("1.00"));
        return (val.compareTo(BigDecimal.ONE) > 0) ? new BigDecimal("1.00") : val;
    }

    public static final java.util.Set<BigDecimal> ALLOWED_DELTAS = java.util.Set.of(
            new BigDecimal("1.00"),
            BigDecimal.ZERO,
            new BigDecimal("-1.00")
    );

    public static void validatePriceMovement(BigDecimal delta) {
        if (delta == null) return;
        boolean isAllowed = ALLOWED_DELTAS.stream().anyMatch(allowed -> allowed.compareTo(delta) == 0);
        if (!isAllowed) {
            log.error("[PRICE_MOVEMENT_VALIDATION] Dynamic price movement {} is not in allowed set {+1.00, 0.00, -1.00}!", delta);
            throw new IllegalStateException("Dynamic price movement must strictly be +1.00, 0.00, or -1.00. Got: " + delta);
        }
    }

    public int getMarketCrashDurationSeconds() {
        String val = globalConfigCache.getOrDefault("MARKET_CRASH_DURATION_SECONDS", "180");
        try {
            int dur = Integer.parseInt(val);
            return dur > 0 ? dur : 180;
        } catch (Exception e) {
            return 180;
        }
    }

    public BigDecimal getMarketCrashPrice() {
        return parseDecimal(globalConfigCache.getOrDefault("MARKET_CRASH_PRICE", "20.00"), new BigDecimal("20.00"));
    }

    public BigDecimal getDefaultCupPrice() {
        return parseDecimal(globalConfigCache.getOrDefault("DEFAULT_CUP_PRICE", "25.00"), new BigDecimal("25.00"));
    }

    public BigDecimal getMinCupPrice() {
        return parseDecimal(globalConfigCache.getOrDefault("MIN_CUP_PRICE", "20.00"), new BigDecimal("20.00"));
    }

    public BigDecimal getMaxCupPrice() {
        return parseDecimal(globalConfigCache.getOrDefault("MAX_CUP_PRICE", "30.00"), new BigDecimal("30.00"));
    }

    public double getTargetSalesForProduct(Product product) {
        if (product == null) return 0.55;
        if (product.getTargetSalesPer1Minute() != null && product.getTargetSalesPer1Minute() > 0) {
            return product.getTargetSalesPer1Minute();
        }
        if (product.getTargetSalesPer2Minute() != null && product.getTargetSalesPer2Minute() > 0) {
            return product.getTargetSalesPer2Minute() / 2.0;
        }
        return 0.55;
    }

    private BigDecimal parseDecimal(String str, BigDecimal def) {
        if (str == null || str.isBlank()) return def;
        try {
            return new BigDecimal(str).setScale(4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return def;
        }
    }

    // --- FULL DTO SERIALIZATION ---

    public PricingConfigDTO getFullConfiguration() {
        PricingConfigDTO.GlobalConfig global = new PricingConfigDTO.GlobalConfig();
        global.setSettlementIntervalSeconds(getSettlementIntervalSeconds());
        global.setWeightW0(getWeightW0().setScale(2, RoundingMode.HALF_UP));
        global.setWeightW1(getWeightW1().setScale(2, RoundingMode.HALF_UP));
        global.setWeightW2(getWeightW2().setScale(2, RoundingMode.HALF_UP));
        global.setHighDemandThreshold(getHighDemandThreshold().setScale(2, RoundingMode.HALF_UP));
        global.setStableDemandLowerThreshold(getStableDemandLowerThreshold().setScale(2, RoundingMode.HALF_UP));
        global.setStableDemandUpperThreshold(getStableDemandUpperThreshold().setScale(2, RoundingMode.HALF_UP));
        global.setLowDemandThreshold(getLowDemandThreshold().setScale(2, RoundingMode.HALF_UP));
        global.setIncreaseStep(getIncreaseStep().setScale(2, RoundingMode.HALF_UP));
        global.setDecreaseStep1(getDecreaseStep1().setScale(2, RoundingMode.HALF_UP));
        global.setDecreaseStep2(getDecreaseStep2().setScale(2, RoundingMode.HALF_UP));
        global.setPriceDecreaseStep(getPriceDecreaseStep().setScale(2, RoundingMode.HALF_UP));
        global.setMarketCrashDurationSeconds(getMarketCrashDurationSeconds());
        global.setMarketCrashPrice(getMarketCrashPrice().setScale(2, RoundingMode.HALF_UP));
        global.setDefaultCupPrice(getDefaultCupPrice().setScale(2, RoundingMode.HALF_UP));
        global.setMinCupPrice(getMinCupPrice().setScale(2, RoundingMode.HALF_UP));
        global.setMaxCupPrice(getMaxCupPrice().setScale(2, RoundingMode.HALF_UP));

        List<Product> products = productRepository.findByIsActiveTrueOrderByIdAsc();
        List<PricingConfigDTO.ProductConfig> productConfigs = new ArrayList<>();
        for (Product p : products) {
            double target = p.getTargetSalesPer1Minute() != null ? p.getTargetSalesPer1Minute() : (p.getTargetSalesPer2Minute() != null ? p.getTargetSalesPer2Minute() / 2.0 : 0.55);
            productConfigs.add(new PricingConfigDTO.ProductConfig(
                    p.getId(),
                    p.getName(),
                    p.getFlavour(),
                    target,
                    p.getDefaultCupPrice() != null ? p.getDefaultCupPrice().setScale(2, RoundingMode.HALF_UP) : new BigDecimal("25.00"),
                    p.getCurrentCupPrice() != null ? p.getCurrentCupPrice().setScale(2, RoundingMode.HALF_UP) : new BigDecimal("25.00"),
                    p.getMinCupPrice() != null ? p.getMinCupPrice().setScale(2, RoundingMode.HALF_UP) : new BigDecimal("20.00"),
                    p.getMaxCupPrice() != null ? p.getMaxCupPrice().setScale(2, RoundingMode.HALF_UP) : new BigDecimal("30.00"),
                    p.getPricingMode()
            ));
        }

        return new PricingConfigDTO(currentConfigVersion.get(), lastConfigUpdate.toString(), global, productConfigs);
    }

    // --- TRANSACTIONAL UPDATES ---

    @Transactional
    public PricingConfigDTO updateGlobalConfiguration(PricingConfigDTO.GlobalConfig update, String adminUser, String reason) {
        if (update == null) {
            throw new IllegalArgumentException("Configuration payload cannot be null");
        }

        // 1. Strict Validation
        validateGlobalConfig(update);

        String user = (adminUser != null && !adminUser.isBlank()) ? adminUser : "ADMIN";
        long oldVersion = currentConfigVersion.get();
        long newVersion = oldVersion + 1;

        Map<String, String> newSettings = new LinkedHashMap<>();
        if (update.getSettlementIntervalSeconds() != null) newSettings.put("SETTLEMENT_INTERVAL_SECONDS", String.valueOf(update.getSettlementIntervalSeconds()));
        if (update.getWeightW0() != null) newSettings.put("WEIGHT_W0", update.getWeightW0().setScale(4, RoundingMode.HALF_UP).toString());
        if (update.getWeightW1() != null) newSettings.put("WEIGHT_W1", update.getWeightW1().setScale(4, RoundingMode.HALF_UP).toString());
        if (update.getWeightW2() != null) newSettings.put("WEIGHT_W2", update.getWeightW2().setScale(4, RoundingMode.HALF_UP).toString());
        if (update.getHighDemandThreshold() != null) newSettings.put("HIGH_DEMAND_THRESHOLD", update.getHighDemandThreshold().setScale(4, RoundingMode.HALF_UP).toString());
        if (update.getStableDemandLowerThreshold() != null) newSettings.put("STABLE_DEMAND_LOWER_THRESHOLD", update.getStableDemandLowerThreshold().setScale(4, RoundingMode.HALF_UP).toString());
        if (update.getStableDemandUpperThreshold() != null) newSettings.put("STABLE_DEMAND_UPPER_THRESHOLD", update.getStableDemandUpperThreshold().setScale(4, RoundingMode.HALF_UP).toString());
        if (update.getLowDemandThreshold() != null) newSettings.put("LOW_DEMAND_THRESHOLD", update.getLowDemandThreshold().setScale(4, RoundingMode.HALF_UP).toString());
        if (update.getIncreaseStep() != null) newSettings.put("INCREASE_STEP", update.getIncreaseStep().setScale(2, RoundingMode.HALF_UP).toString());
        if (update.getDecreaseStep1() != null) newSettings.put("DECREASE_STEP_1", update.getDecreaseStep1().setScale(2, RoundingMode.HALF_UP).toString());
        if (update.getDecreaseStep2() != null) newSettings.put("DECREASE_STEP_2", update.getDecreaseStep2().setScale(2, RoundingMode.HALF_UP).toString());
        if (update.getPriceDecreaseStep() != null) {
            newSettings.put("PRICE_DECREASE_STEP", update.getPriceDecreaseStep().setScale(2, RoundingMode.HALF_UP).toString());
            newSettings.put("DECREASE_STEP_1", update.getPriceDecreaseStep().setScale(2, RoundingMode.HALF_UP).toString());
            newSettings.put("DECREASE_STEP_2", update.getPriceDecreaseStep().setScale(2, RoundingMode.HALF_UP).toString());
        }
        if (update.getMarketCrashDurationSeconds() != null) newSettings.put("MARKET_CRASH_DURATION_SECONDS", String.valueOf(update.getMarketCrashDurationSeconds()));
        if (update.getMarketCrashPrice() != null) newSettings.put("MARKET_CRASH_PRICE", update.getMarketCrashPrice().setScale(2, RoundingMode.HALF_UP).toString());
        if (update.getDefaultCupPrice() != null) newSettings.put("DEFAULT_CUP_PRICE", update.getDefaultCupPrice().setScale(2, RoundingMode.HALF_UP).toString());
        if (update.getMinCupPrice() != null) newSettings.put("MIN_CUP_PRICE", update.getMinCupPrice().setScale(2, RoundingMode.HALF_UP).toString());
        if (update.getMaxCupPrice() != null) newSettings.put("MAX_CUP_PRICE", update.getMaxCupPrice().setScale(2, RoundingMode.HALF_UP).toString());
        newSettings.put("GLOBAL_CONFIG_VERSION", String.valueOf(newVersion));

        // 2. Persist to PostgreSQL
        for (Map.Entry<String, String> entry : newSettings.entrySet()) {
            String key = entry.getKey();
            String newVal = entry.getValue();
            String oldVal = globalConfigCache.get(key);

            PricingConfiguration cfg = configRepository.findBySettingKeyAndProductIdIsNull(key)
                    .orElse(new PricingConfiguration(key, newVal, "STRING", "GLOBAL", null, key, user));

            cfg.setSettingValue(newVal);
            cfg.setUpdatedBy(user);
            cfg.setVersion(newVersion);
            configRepository.save(cfg);

            // Audit record
            if (oldVal != null && !oldVal.equals(newVal)) {
                auditLogRepository.save(new PricingConfigAuditLog(
                        user, key, null, oldVal, newVal, oldVersion, newVersion, reason
                ));
            }
        }

        configRepository.flush();

        // 3. Update in-memory snapshot
        globalConfigCache.putAll(newSettings);
        if (update.getMinCupPrice() != null || update.getMaxCupPrice() != null || update.getDefaultCupPrice() != null) {
            List<Product> products = productRepository.findAll();
            for (Product p : products) {
                if (update.getMinCupPrice() != null) p.setMinCupPrice(update.getMinCupPrice());
                if (update.getMaxCupPrice() != null) p.setMaxCupPrice(update.getMaxCupPrice());
                if (update.getDefaultCupPrice() != null) p.setDefaultCupPrice(update.getDefaultCupPrice());
            }
            productRepository.saveAllAndFlush(products);
        }

        currentConfigVersion.set(newVersion);
        lastConfigUpdate = LocalDateTime.now();

        log.info("[ADMIN PRICING CONFIG] Global pricing settings updated to version {} by user {}", newVersion, user);

        // 4. Update Redis Cache
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set("pricing:config:global:version", String.valueOf(newVersion));
                redisTemplate.opsForValue().set("pricing:config:global", globalConfigCache);
            }
        } catch (Exception e) {
            log.warn("Failed to sync pricing config to Redis: {}", e.getMessage());
        }

        // 5. Broadcast to WebSocket
        PricingConfigDTO fullDto = getFullConfiguration();
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/pricing-config", fullDto);
                List<Product> activeProducts = productRepository.findByIsActiveTrueOrderByIdAsc();
                messagingTemplate.convertAndSend("/topic/prices", activeProducts);
                messagingTemplate.convertAndSend("/topic/products", activeProducts);
                Map<String, Object> settlementMsg = new HashMap<>();
                settlementMsg.put("type", "PRICING_CONFIG_UPDATED");
                settlementMsg.put("version", newVersion);
                settlementMsg.put("intervalSeconds", getSettlementIntervalSeconds());
                messagingTemplate.convertAndSend("/topic/settlement", settlementMsg);
            }
        } catch (Exception e) {
            log.warn("Failed to broadcast pricing config over WebSocket: {}", e.getMessage());
        }

        return fullDto;
    }

    @Transactional
    public PricingConfigDTO.ProductConfig updateProductConfiguration(Long productId, PricingConfigDTO.ProductConfig update, String adminUser, String reason) {
        if (productId == null || update == null) {
            throw new IllegalArgumentException("Product ID and configuration payload cannot be null");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));

        // Validation
        if (update.getTargetSales() != null && update.getTargetSales() <= 0) {
            throw new IllegalArgumentException("Target sales must be greater than 0");
        }
        if (update.getMinCupPrice() != null && update.getMinCupPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Minimum cup price cannot be negative");
        }
        if (update.getMaxCupPrice() != null && update.getMinCupPrice() != null && update.getMaxCupPrice().compareTo(update.getMinCupPrice()) <= 0) {
            throw new IllegalArgumentException("Maximum price must be strictly greater than minimum price");
        }
        if (update.getDefaultCupPrice() != null && update.getMinCupPrice() != null && update.getDefaultCupPrice().compareTo(update.getMinCupPrice()) < 0) {
            throw new IllegalArgumentException("Default price cannot be below minimum floor price");
        }
        if (update.getDefaultCupPrice() != null && update.getMaxCupPrice() != null && update.getDefaultCupPrice().compareTo(update.getMaxCupPrice()) > 0) {
            throw new IllegalArgumentException("Default price cannot exceed maximum ceiling price");
        }

        String user = (adminUser != null && !adminUser.isBlank()) ? adminUser : "ADMIN";
        long oldVersion = currentConfigVersion.get();
        long newVersion = oldVersion + 1;

        Double oldTarget = product.getTargetSalesPer1Minute();

        if (update.getTargetSales() != null) {
            product.setTargetSalesPer1Minute(update.getTargetSales());
            product.setTargetSalesPer2Minute(update.getTargetSales() * 2.0);
        }
        if (update.getPricingMode() != null && !update.getPricingMode().isBlank()) {
            product.setPricingMode(update.getPricingMode());
        }
        if (update.getDefaultCupPrice() != null) product.setDefaultCupPrice(update.getDefaultCupPrice());
        if (update.getMinCupPrice() != null) product.setMinCupPrice(update.getMinCupPrice());
        if (update.getMaxCupPrice() != null) product.setMaxCupPrice(update.getMaxCupPrice());
        if (update.getCurrentCupPrice() != null) {
            BigDecimal clamped = update.getCurrentCupPrice()
                    .max(product.getMinCupPrice() != null ? product.getMinCupPrice() : BigDecimal.ZERO)
                    .min(product.getMaxCupPrice() != null ? product.getMaxCupPrice() : new BigDecimal("999.00"));
            product.setCurrentCupPrice(clamped);
        }

        productRepository.saveAndFlush(product);

        // Audit logs
        if (update.getTargetSales() != null && !Objects.equals(oldTarget, update.getTargetSales())) {
            auditLogRepository.save(new PricingConfigAuditLog(
                    user, "PRODUCT_TARGET_SALES", productId, String.valueOf(oldTarget), String.valueOf(update.getTargetSales()), oldVersion, newVersion, reason
            ));
        }

        currentConfigVersion.set(newVersion);
        lastConfigUpdate = LocalDateTime.now();

        // Sync Redis & STOMP
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set("pricing:product:" + productId + ":target", String.valueOf(product.getTargetSalesPer1Minute()));
            }
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/pricing-config", getFullConfiguration());
                List<Product> activeProds = productRepository.findByIsActiveTrueOrderByIdAsc();
                messagingTemplate.convertAndSend("/topic/prices", activeProds);
                messagingTemplate.convertAndSend("/topic/products", activeProds);
            }
        } catch (Exception e) {}

        return new PricingConfigDTO.ProductConfig(
                product.getId(),
                product.getName(),
                product.getFlavour(),
                product.getTargetSalesPer1Minute(),
                product.getDefaultCupPrice(),
                product.getCurrentCupPrice(),
                product.getMinCupPrice(),
                product.getMaxCupPrice(),
                product.getPricingMode()
        );
    }

    private void validateGlobalConfig(PricingConfigDTO.GlobalConfig config) {
        BigDecimal effectiveMin = config.getMinCupPrice() != null ? config.getMinCupPrice() : getMinCupPrice();
        BigDecimal effectiveMax = config.getMaxCupPrice() != null ? config.getMaxCupPrice() : getMaxCupPrice();
        BigDecimal effectiveDefault = config.getDefaultCupPrice() != null ? config.getDefaultCupPrice() : getDefaultCupPrice();
        BigDecimal effectiveCrashPrice = config.getMarketCrashPrice() != null ? config.getMarketCrashPrice() : getMarketCrashPrice();

        if (effectiveMin != null && effectiveMin.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Minimum price cannot be negative");
        }
        if (effectiveMax != null && effectiveMin != null && effectiveMax.compareTo(effectiveMin) <= 0) {
            throw new IllegalArgumentException("Maximum price must be strictly greater than minimum price");
        }
        if (effectiveDefault != null && effectiveMin != null && effectiveDefault.compareTo(effectiveMin) < 0) {
            throw new IllegalArgumentException("Default price cannot be less than minimum price");
        }
        if (effectiveDefault != null && effectiveMax != null && effectiveDefault.compareTo(effectiveMax) > 0) {
            throw new IllegalArgumentException("Default price cannot exceed maximum price");
        }
        if (config.getWeightW0() != null && config.getWeightW0().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Weight W0 cannot be negative");
        }
        if (config.getWeightW1() != null && config.getWeightW1().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Weight W1 cannot be negative");
        }
        if (config.getWeightW2() != null && config.getWeightW2().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Weight W2 cannot be negative");
        }
        if (config.getSettlementIntervalSeconds() != null) {
            int interval = config.getSettlementIntervalSeconds();
            if (!ALLOWED_INTERVALS.contains(interval)) {
                throw new IllegalArgumentException("Invalid settlement interval: " + interval + "s. Allowed values are: 10s (10), 30s (30), 1 min (60), 2 min (120), 5 min (300), 10 min (600), 15 min (900).");
            }
        }
        if (config.getMarketCrashDurationSeconds() != null && config.getMarketCrashDurationSeconds() <= 0) {
            throw new IllegalArgumentException("Market crash duration must be strictly positive (> 0 seconds)");
        }
        if (effectiveCrashPrice != null && effectiveMin != null && effectiveCrashPrice.compareTo(effectiveMin) < 0) {
            // Auto-adjust or validate crash price
            if (config.getMarketCrashPrice() != null) {
                throw new IllegalArgumentException("Market crash price cannot be below minimum floor price");
            }
        }
        if (config.getIncreaseStep() != null && config.getIncreaseStep().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Increase step cannot be negative");
        }
        if (config.getDecreaseStep1() != null && config.getDecreaseStep1().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Decrease step 1 cannot be negative");
        }
        if (config.getDecreaseStep2() != null && config.getDecreaseStep2().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Decrease step 2 cannot be negative");
        }
        if (config.getPriceDecreaseStep() != null && config.getPriceDecreaseStep().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price decrease step cannot be negative");
        }
        if (config.getLowDemandThreshold() != null && config.getStableDemandLowerThreshold() != null
                && config.getLowDemandThreshold().compareTo(config.getStableDemandLowerThreshold()) >= 0) {
            throw new IllegalArgumentException("Low demand threshold must be strictly less than stable demand lower bound");
        }
        if (config.getStableDemandLowerThreshold() != null && config.getStableDemandUpperThreshold() != null
                && config.getStableDemandLowerThreshold().compareTo(config.getStableDemandUpperThreshold()) > 0) {
            throw new IllegalArgumentException("Stable demand lower bound cannot exceed upper bound");
        }
    }

    public static final Set<Integer> ALLOWED_INTERVALS = Set.of(10, 30, 60, 120, 300, 600, 900);

    public String getSettlementIntervalLabel() {
        int sec = getSettlementIntervalSeconds();
        return getIntervalLabel(sec);
    }

    public double getNormalizedTargetSales(Product product, int intervalSeconds) {
        double storedTarget = getTargetSalesForProduct(product);
        int storedTargetPeriodSeconds = 60; // Authoritative stored period is 60s
        return (storedTarget * intervalSeconds) / (double) storedTargetPeriodSeconds;
    }

    public static String getIntervalLabel(int seconds) {
        return switch (seconds) {
            case 10 -> "10 Seconds";
            case 30 -> "30 Seconds";
            case 60 -> "1 Minute";
            case 120 -> "2 Minutes";
            case 300 -> "5 Minutes";
            case 600 -> "10 Minutes";
            case 900 -> "15 Minutes";
            default -> (seconds % 60 == 0) ? (seconds / 60) + " Minutes" : seconds + " Seconds";
        };
    }
}
