package com.retailpos.pricing.redis;

import com.retailpos.domain.Product;
import com.retailpos.domain.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
@SuppressWarnings("null")
public class PricingRedisRepository {

    private static final Logger log = LoggerFactory.getLogger(PricingRedisRepository.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductRepository productRepository;

    public PricingRedisRepository(RedisTemplate<String, Object> redisTemplate, ProductRepository productRepository) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
    }

    public void setProductPrice(Long productId, BigDecimal price) {
        try {
            if (redisTemplate != null && productId != null && price != null) {
                redisTemplate.opsForValue().set("price:juice:" + productId, price.toString());
            }
        } catch (Exception e) {
            log.debug("Redis setProductPrice bypass: {}", e.getMessage());
        }
    }

    public BigDecimal getProductPrice(Long productId) {
        try {
            if (redisTemplate != null && productId != null) {
                Object val = redisTemplate.opsForValue().get("price:juice:" + productId);
                if (val != null) {
                    return new BigDecimal(val.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Redis getProductPrice bypass: {}", e.getMessage());
        }
        return null;
    }

    public void setProductStock(Long productId, long stockVolumeMl) {
        try {
            if (redisTemplate != null && productId != null) {
                redisTemplate.opsForValue().set("stock:juice:" + productId, stockVolumeMl);
            }
        } catch (Exception e) {
            log.debug("Redis setProductStock bypass: {}", e.getMessage());
        }
    }

    public int getMarketVersion() {
        try {
            if (redisTemplate != null) {
                Object val = redisTemplate.opsForValue().get("market:version");
                if (val != null) {
                    return Integer.parseInt(val.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Redis getMarketVersion bypass: {}", e.getMessage());
        }
        return 1;
    }

    public int incrementMarketVersion() {
        try {
            if (redisTemplate != null) {
                Long newVersion = redisTemplate.opsForValue().increment("market:version");
                if (newVersion != null) {
                    return newVersion.intValue();
                }
            }
        } catch (Exception e) {
            log.debug("Redis incrementMarketVersion bypass: {}", e.getMessage());
        }
        return 1;
    }

    public void setMarketVersion(int version) {
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set("market:version", version);
            }
        } catch (Exception e) {
            log.debug("Redis setMarketVersion bypass: {}", e.getMessage());
        }
    }

    public long incrementMarketVolume(int qty) {
        try {
            if (redisTemplate != null) {
                Long newVolume = redisTemplate.opsForValue().increment("market:volume", qty);
                if (newVolume != null) {
                    return newVolume;
                }
            }
        } catch (Exception e) {
            log.debug("Redis incrementMarketVolume bypass: {}", e.getMessage());
        }
        return 0L;
    }

    public long getMarketVolume() {
        try {
            if (redisTemplate != null) {
                Object val = redisTemplate.opsForValue().get("market:volume");
                if (val != null) {
                    return Long.parseLong(val.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Redis getMarketVolume bypass: {}", e.getMessage());
        }
        return 0L;
    }

    public void setLastTradeTimestamp(Long productId, LocalDateTime timestamp) {
        try {
            if (redisTemplate != null && productId != null && timestamp != null) {
                redisTemplate.opsForValue().set("market:lastTrade:" + productId, timestamp.toString());
            }
        } catch (Exception e) {
            log.debug("Redis setLastTradeTimestamp bypass: {}", e.getMessage());
        }
    }

    public void setCrashState(boolean active, String crashCode, String endTimeIso) {
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set("market:crash:active", String.valueOf(active));
                if (crashCode != null) redisTemplate.opsForValue().set("market:crash:code", crashCode);
                if (endTimeIso != null) redisTemplate.opsForValue().set("market:crash:endTime", endTimeIso);
            }
        } catch (Exception e) {
            log.debug("Redis setCrashState bypass: {}", e.getMessage());
        }
    }

    public boolean isCrashActiveInRedis() {
        try {
            if (redisTemplate != null) {
                Object val = redisTemplate.opsForValue().get("market:crash:active");
                return val != null && "true".equalsIgnoreCase(val.toString());
            }
        } catch (Exception e) {
            log.debug("Redis isCrashActiveInRedis bypass: {}", e.getMessage());
        }
        return false;
    }

    public String getCrashEndTimeFromRedis() {
        try {
            if (redisTemplate != null) {
                Object val = redisTemplate.opsForValue().get("market:crash:endTime");
                return val != null ? val.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Redis getCrashEndTimeFromRedis bypass: {}", e.getMessage());
        }
        return null;
    }

    public String getCrashCodeFromRedis() {
        try {
            if (redisTemplate != null) {
                Object val = redisTemplate.opsForValue().get("market:crash:code");
                return val != null ? val.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Redis getCrashCodeFromRedis bypass: {}", e.getMessage());
        }
        return null;
    }

    public void setCrashSnapshot(Long productId, BigDecimal price) {
        try {
            if (redisTemplate != null && productId != null && price != null) {
                redisTemplate.opsForHash().put("market:crash:snapshot", productId.toString(), price.toString());
            }
        } catch (Exception e) {
            log.debug("Redis setCrashSnapshot bypass: {}", e.getMessage());
        }
    }

    public BigDecimal getCrashSnapshot(Long productId) {
        try {
            if (redisTemplate != null && productId != null) {
                Object val = redisTemplate.opsForHash().get("market:crash:snapshot", productId.toString());
                if (val != null) {
                    return new BigDecimal(val.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Redis getCrashSnapshot bypass: {}", e.getMessage());
        }
        return null;
    }

    public void clearCrashStateInRedis() {
        try {
            if (redisTemplate != null) {
                redisTemplate.delete("market:crash:active");
                redisTemplate.delete("market:crash:code");
                redisTemplate.delete("market:crash:endTime");
                redisTemplate.delete("market:crash:snapshot");
            }
        } catch (Exception e) {
            log.debug("Redis clearCrashStateInRedis bypass: {}", e.getMessage());
        }
    }

    public void reconcileWithDatabase() {
        try {
            log.info("🔄 Reconciling Redis Market State with Authoritative PostgreSQL Database...");
            for (Product p : productRepository.findAll()) {
                if (p.getCurrentCupPrice() != null) {
                    setProductPrice(p.getId(), p.getCurrentCupPrice());
                }
            }
            log.info("✅ Redis Market State Reconciliation Complete.");
        } catch (Exception e) {
            log.warn("Redis reconciliation failed: {}", e.getMessage());
        }
    }
}
