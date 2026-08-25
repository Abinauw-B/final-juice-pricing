package com.retailpos.pricing;

import com.retailpos.domain.Product;
import com.retailpos.domain.ProductRepository;
import com.retailpos.pricing.model.PriceQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class PriceLockService {

    private static final Logger log = LoggerFactory.getLogger(PriceLockService.class);

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, PriceQuote> quoteRegistry = new ConcurrentHashMap<>();

    public PriceLockService(ProductRepository productRepository, RedisTemplate<String, Object> redisTemplate) {
        this.productRepository = productRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public PriceQuote createQuote(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));

        String quoteId = "QUOTE-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(10); // Strict 10-second price guarantee

        BigDecimal lockedPrice = product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : product.getDefaultCupPrice();
        int version = product.getPriceVersion() != null ? product.getPriceVersion() : 1;

        PriceQuote quote = PriceQuote.builder()
                .quoteId(quoteId)
                .productId(product.getId())
                .productName(product.getName())
                .quantity(quantity > 0 ? quantity : 1)
                .lockedPrice(lockedPrice)
                .priceVersion(version)
                .createdAt(now)
                .expiresAt(expiresAt)
                .redeemed(false)
                .build();

        quoteRegistry.put(quoteId, quote);
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set("quote:" + quoteId, quote, 15, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.debug("Redis quote storage bypass: {}", e.getMessage());
        }

        log.info("🔒 Price Quote Lock Created: QuoteId={} Product='{}' LockedPrice=₹{} Version={} ExpiresAt={}",
                quoteId, product.getName(), lockedPrice, version, expiresAt);

        return quote;
    }

    public PriceQuote validateAndRedeemQuote(String quoteId, Long productId) {
        PriceQuote quote = quoteRegistry.get(quoteId);

        if (quote == null) {
            try {
                if (redisTemplate != null) {
                    Object val = redisTemplate.opsForValue().get("quote:" + quoteId);
                    if (val instanceof PriceQuote) {
                        quote = (PriceQuote) val;
                    }
                }
            } catch (Exception e) {
                log.debug("Redis quote fetch bypass: {}", e.getMessage());
            }
        }

        if (quote == null) {
            throw new IllegalArgumentException("Invalid price quote token: " + quoteId);
        }

        if (productId != null && !quote.getProductId().equals(productId)) {
            throw new IllegalArgumentException("Quote ID " + quoteId + " belongs to product ID " + quote.getProductId() + ", not " + productId);
        }

        if (quote.isRedeemed()) {
            throw new IllegalStateException("Price quote token " + quoteId + " has already been redeemed.");
        }

        if (LocalDateTime.now().isAfter(quote.getExpiresAt())) {
            quoteRegistry.remove(quoteId);
            try {
                if (redisTemplate != null) redisTemplate.delete("quote:" + quoteId);
            } catch (Exception ignored) {}
            throw new IllegalStateException("QUOTE_EXPIRED: Price quote " + quoteId + " expired at " + quote.getExpiresAt() + ". Please request a fresh quote.");
        }

        quote.setRedeemed(true);
        quoteRegistry.remove(quoteId);
        try {
            if (redisTemplate != null) redisTemplate.delete("quote:" + quoteId);
        } catch (Exception ignored) {}

        log.info("🔓 Price Quote Token Successfully Redeemed: QuoteId={} Product='{}' Price=₹{}", quoteId, quote.getProductName(), quote.getLockedPrice());
        return quote;
    }

    // --- Legacy Backwards-Compatibility Methods ---
    @Transactional(readOnly = true)
    public LockedPriceVersion lockPriceForCartItem(Long productId, int quantity) {
        PriceQuote q = createQuote(productId, quantity);
        return LockedPriceVersion.builder()
                .lockToken(q.getQuoteId())
                .productId(q.getProductId())
                .productName(q.getProductName())
                .lockedPrice(q.getLockedPrice())
                .priceVersion(q.getPriceVersion())
                .lockedAt(q.getCreatedAt())
                .expiresAt(q.getExpiresAt())
                .redeemed(false)
                .build();
    }

    public LockedPriceVersion validateAndRedeemLock(String lockToken) {
        PriceQuote q = validateAndRedeemQuote(lockToken, null);
        return LockedPriceVersion.builder()
                .lockToken(q.getQuoteId())
                .productId(q.getProductId())
                .productName(q.getProductName())
                .lockedPrice(q.getLockedPrice())
                .priceVersion(q.getPriceVersion())
                .lockedAt(q.getCreatedAt())
                .expiresAt(q.getExpiresAt())
                .redeemed(true)
                .build();
    }

    public static class LockedPriceVersion {
        private String lockToken;
        private Long productId;
        private String productName;
        private BigDecimal lockedPrice;
        private int priceVersion;
        private LocalDateTime lockedAt;
        private LocalDateTime expiresAt;
        private boolean redeemed;

        public LockedPriceVersion() {}
        public LockedPriceVersion(String lockToken, Long productId, String productName, BigDecimal lockedPrice, int priceVersion, LocalDateTime lockedAt, LocalDateTime expiresAt, boolean redeemed) {
            this.lockToken = lockToken;
            this.productId = productId;
            this.productName = productName;
            this.lockedPrice = lockedPrice;
            this.priceVersion = priceVersion;
            this.lockedAt = lockedAt;
            this.expiresAt = expiresAt;
            this.redeemed = redeemed;
        }

        public String getLockToken() { return lockToken; }
        public Long getProductId() { return productId; }
        public String getProductName() { return productName; }
        public BigDecimal getLockedPrice() { return lockedPrice; }
        public int getPriceVersion() { return priceVersion; }
        public LocalDateTime getLockedAt() { return lockedAt; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public boolean isRedeemed() { return redeemed; }

        public static LockedPriceVersionBuilder builder() { return new LockedPriceVersionBuilder(); }
        public static class LockedPriceVersionBuilder {
            private String lockToken;
            private Long productId;
            private String productName;
            private BigDecimal lockedPrice;
            private int priceVersion;
            private LocalDateTime lockedAt;
            private LocalDateTime expiresAt;
            private boolean redeemed;

            public LockedPriceVersionBuilder lockToken(String lockToken) { this.lockToken = lockToken; return this; }
            public LockedPriceVersionBuilder productId(Long productId) { this.productId = productId; return this; }
            public LockedPriceVersionBuilder productName(String productName) { this.productName = productName; return this; }
            public LockedPriceVersionBuilder lockedPrice(BigDecimal lockedPrice) { this.lockedPrice = lockedPrice; return this; }
            public LockedPriceVersionBuilder priceVersion(int priceVersion) { this.priceVersion = priceVersion; return this; }
            public LockedPriceVersionBuilder lockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; return this; }
            public LockedPriceVersionBuilder expiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; return this; }
            public LockedPriceVersionBuilder redeemed(boolean redeemed) { this.redeemed = redeemed; return this; }
            public LockedPriceVersion build() { return new LockedPriceVersion(lockToken, productId, productName, lockedPrice, priceVersion, lockedAt, expiresAt, redeemed); }
        }
    }
}
