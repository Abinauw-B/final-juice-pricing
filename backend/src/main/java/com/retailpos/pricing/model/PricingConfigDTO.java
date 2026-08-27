package com.retailpos.pricing.model;

import java.math.BigDecimal;
import java.util.List;

public class PricingConfigDTO {

    private Long version;
    private String updatedAt;
    private GlobalConfig global;
    private List<ProductConfig> products;

    public PricingConfigDTO() {}

    public PricingConfigDTO(Long version, String updatedAt, GlobalConfig global, List<ProductConfig> products) {
        this.version = version;
        this.updatedAt = updatedAt;
        this.global = global;
        this.products = products;
    }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public GlobalConfig getGlobal() { return global; }
    public void setGlobal(GlobalConfig global) { this.global = global; }

    public List<ProductConfig> getProducts() { return products; }
    public void setProducts(List<ProductConfig> products) { this.products = products; }

    public static class GlobalConfig {
        private Integer settlementIntervalSeconds;
        private BigDecimal weightW0;
        private BigDecimal weightW1;
        private BigDecimal weightW2;
        private BigDecimal highDemandThreshold;
        private BigDecimal stableDemandLowerThreshold;
        private BigDecimal stableDemandUpperThreshold;
        private BigDecimal lowDemandThreshold;
        private BigDecimal increaseStep;
        private BigDecimal decreaseStep1;
        private BigDecimal decreaseStep2;
        private BigDecimal priceDecreaseStep;
        private Integer marketCrashDurationSeconds;
        private BigDecimal marketCrashPrice;
        private BigDecimal defaultCupPrice;
        private BigDecimal minCupPrice;
        private BigDecimal maxCupPrice;

        public GlobalConfig() {}

        public Integer getSettlementIntervalSeconds() { return settlementIntervalSeconds; }
        public void setSettlementIntervalSeconds(Integer settlementIntervalSeconds) { this.settlementIntervalSeconds = settlementIntervalSeconds; }

        public BigDecimal getWeightW0() { return weightW0; }
        public void setWeightW0(BigDecimal weightW0) { this.weightW0 = weightW0; }

        public BigDecimal getWeightW1() { return weightW1; }
        public void setWeightW1(BigDecimal weightW1) { this.weightW1 = weightW1; }

        public BigDecimal getWeightW2() { return weightW2; }
        public void setWeightW2(BigDecimal weightW2) { this.weightW2 = weightW2; }

        public BigDecimal getHighDemandThreshold() { return highDemandThreshold; }
        public void setHighDemandThreshold(BigDecimal highDemandThreshold) { this.highDemandThreshold = highDemandThreshold; }

        public BigDecimal getStableDemandLowerThreshold() { return stableDemandLowerThreshold; }
        public void setStableDemandLowerThreshold(BigDecimal stableDemandLowerThreshold) { this.stableDemandLowerThreshold = stableDemandLowerThreshold; }

        public BigDecimal getStableDemandUpperThreshold() { return stableDemandUpperThreshold; }
        public void setStableDemandUpperThreshold(BigDecimal stableDemandUpperThreshold) { this.stableDemandUpperThreshold = stableDemandUpperThreshold; }

        public BigDecimal getLowDemandThreshold() { return lowDemandThreshold; }
        public void setLowDemandThreshold(BigDecimal lowDemandThreshold) { this.lowDemandThreshold = lowDemandThreshold; }

        public BigDecimal getIncreaseStep() { return increaseStep; }
        public void setIncreaseStep(BigDecimal increaseStep) { this.increaseStep = increaseStep; }

        public BigDecimal getDecreaseStep1() { return decreaseStep1; }
        public void setDecreaseStep1(BigDecimal decreaseStep1) { this.decreaseStep1 = decreaseStep1; }

        public BigDecimal getDecreaseStep2() { return decreaseStep2; }
        public void setDecreaseStep2(BigDecimal decreaseStep2) { this.decreaseStep2 = decreaseStep2; }

        public BigDecimal getPriceDecreaseStep() { return priceDecreaseStep != null ? priceDecreaseStep : (decreaseStep1 != null ? decreaseStep1 : new BigDecimal("4.00")); }
        public void setPriceDecreaseStep(BigDecimal priceDecreaseStep) { this.priceDecreaseStep = priceDecreaseStep; }

        public Integer getMarketCrashDurationSeconds() { return marketCrashDurationSeconds; }
        public void setMarketCrashDurationSeconds(Integer marketCrashDurationSeconds) { this.marketCrashDurationSeconds = marketCrashDurationSeconds; }

        public BigDecimal getMarketCrashPrice() { return marketCrashPrice; }
        public void setMarketCrashPrice(BigDecimal marketCrashPrice) { this.marketCrashPrice = marketCrashPrice; }

        public BigDecimal getDefaultCupPrice() { return defaultCupPrice; }
        public void setDefaultCupPrice(BigDecimal defaultCupPrice) { this.defaultCupPrice = defaultCupPrice; }

        public BigDecimal getMinCupPrice() { return minCupPrice; }
        public void setMinCupPrice(BigDecimal minCupPrice) { this.minCupPrice = minCupPrice; }

        public BigDecimal getMaxCupPrice() { return maxCupPrice; }
        public void setMaxCupPrice(BigDecimal maxCupPrice) { this.maxCupPrice = maxCupPrice; }
    }

    public static class ProductConfig {
        private Long productId;
        private String productName;
        private String flavour;
        private Double targetSales;
        private Double targetSalesPer1Minute;
        private BigDecimal defaultCupPrice;
        private BigDecimal currentCupPrice;
        private BigDecimal minCupPrice;
        private BigDecimal maxCupPrice;
        private String pricingMode;

        public ProductConfig() {}

        public ProductConfig(Long productId, String productName, String flavour, Double targetSales, BigDecimal defaultCupPrice, BigDecimal currentCupPrice, BigDecimal minCupPrice, BigDecimal maxCupPrice) {
            this(productId, productName, flavour, targetSales, defaultCupPrice, currentCupPrice, minCupPrice, maxCupPrice, "DYNAMIC");
        }

        public ProductConfig(Long productId, String productName, String flavour, Double targetSales, BigDecimal defaultCupPrice, BigDecimal currentCupPrice, BigDecimal minCupPrice, BigDecimal maxCupPrice, String pricingMode) {
            this.productId = productId;
            this.productName = productName;
            this.flavour = flavour;
            this.targetSales = targetSales;
            this.targetSalesPer1Minute = targetSales;
            this.defaultCupPrice = defaultCupPrice;
            this.currentCupPrice = currentCupPrice;
            this.minCupPrice = minCupPrice;
            this.maxCupPrice = maxCupPrice;
            this.pricingMode = pricingMode != null ? pricingMode : "DYNAMIC";
        }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public String getFlavour() { return flavour; }
        public void setFlavour(String flavour) { this.flavour = flavour; }

        public Double getTargetSales() { return targetSales != null ? targetSales : targetSalesPer1Minute; }
        public void setTargetSales(Double targetSales) {
            this.targetSales = targetSales;
            this.targetSalesPer1Minute = targetSales;
        }

        public Double getTargetSalesPer1Minute() { return targetSalesPer1Minute != null ? targetSalesPer1Minute : targetSales; }
        public void setTargetSalesPer1Minute(Double targetSalesPer1Minute) {
            this.targetSalesPer1Minute = targetSalesPer1Minute;
            this.targetSales = targetSalesPer1Minute;
        }

        public BigDecimal getDefaultCupPrice() { return defaultCupPrice; }
        public void setDefaultCupPrice(BigDecimal defaultCupPrice) { this.defaultCupPrice = defaultCupPrice; }

        public BigDecimal getCurrentCupPrice() { return currentCupPrice; }
        public void setCurrentCupPrice(BigDecimal currentCupPrice) { this.currentCupPrice = currentCupPrice; }

        public BigDecimal getMinCupPrice() { return minCupPrice; }
        public void setMinCupPrice(BigDecimal minCupPrice) { this.minCupPrice = minCupPrice; }

        public BigDecimal getMaxCupPrice() { return maxCupPrice; }
        public void setMaxCupPrice(BigDecimal maxCupPrice) { this.maxCupPrice = maxCupPrice; }

        public String getPricingMode() { return pricingMode != null ? pricingMode : "DYNAMIC"; }
        public void setPricingMode(String pricingMode) { this.pricingMode = pricingMode != null ? pricingMode : "DYNAMIC"; }
    }
}
