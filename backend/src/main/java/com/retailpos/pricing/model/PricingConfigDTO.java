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
        private Integer settlementIntervalSeconds = 120;
        private BigDecimal weightW0 = new BigDecimal("1.00");
        private BigDecimal weightW1 = new BigDecimal("0.50");
        private BigDecimal weightW2 = new BigDecimal("0.25");
        private BigDecimal highDemandThreshold = new BigDecimal("1.10");
        private BigDecimal stableDemandLowerThreshold = new BigDecimal("0.90");
        private BigDecimal stableDemandUpperThreshold = new BigDecimal("1.10");
        private BigDecimal lowDemandThreshold = new BigDecimal("0.50");
        private BigDecimal increaseStep = new BigDecimal("1.00");
        private BigDecimal decreaseStep1 = new BigDecimal("1.00");
        private BigDecimal decreaseStep2 = new BigDecimal("2.00");
        private Integer marketCrashDurationSeconds = 180;
        private BigDecimal marketCrashPrice = new BigDecimal("18.00");
        private BigDecimal defaultCupPrice = new BigDecimal("25.00");
        private BigDecimal minCupPrice = new BigDecimal("18.00");
        private BigDecimal maxCupPrice = new BigDecimal("35.00");

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
        private BigDecimal defaultCupPrice;
        private BigDecimal currentCupPrice;
        private BigDecimal minCupPrice;
        private BigDecimal maxCupPrice;

        public ProductConfig() {}

        public ProductConfig(Long productId, String productName, String flavour, Double targetSales, BigDecimal defaultCupPrice, BigDecimal currentCupPrice, BigDecimal minCupPrice, BigDecimal maxCupPrice) {
            this.productId = productId;
            this.productName = productName;
            this.flavour = flavour;
            this.targetSales = targetSales;
            this.defaultCupPrice = defaultCupPrice;
            this.currentCupPrice = currentCupPrice;
            this.minCupPrice = minCupPrice;
            this.maxCupPrice = maxCupPrice;
        }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public String getFlavour() { return flavour; }
        public void setFlavour(String flavour) { this.flavour = flavour; }

        public Double getTargetSales() { return targetSales; }
        public void setTargetSales(Double targetSales) { this.targetSales = targetSales; }

        public BigDecimal getDefaultCupPrice() { return defaultCupPrice; }
        public void setDefaultCupPrice(BigDecimal defaultCupPrice) { this.defaultCupPrice = defaultCupPrice; }

        public BigDecimal getCurrentCupPrice() { return currentCupPrice; }
        public void setCurrentCupPrice(BigDecimal currentCupPrice) { this.currentCupPrice = currentCupPrice; }

        public BigDecimal getMinCupPrice() { return minCupPrice; }
        public void setMinCupPrice(BigDecimal minCupPrice) { this.minCupPrice = minCupPrice; }

        public BigDecimal getMaxCupPrice() { return maxCupPrice; }
        public void setMaxCupPrice(BigDecimal maxCupPrice) { this.maxCupPrice = maxCupPrice; }
    }
}
