package com.retailpos.pricing.model;

import com.retailpos.pricing.PricingEngineService.ProductPriceDTO;
import java.util.List;

public class MarketPriceUpdate {

    private String type = "PRICE_UPDATE";
    private int marketVersion;
    private String timestamp;
    private List<ProductPriceDTO> changes;

    public MarketPriceUpdate() {}

    public MarketPriceUpdate(String type, int marketVersion, String timestamp, List<ProductPriceDTO> changes) {
        this.type = type != null ? type : "PRICE_UPDATE";
        this.marketVersion = marketVersion;
        this.timestamp = timestamp;
        this.changes = changes;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getMarketVersion() { return marketVersion; }
    public void setMarketVersion(int marketVersion) { this.marketVersion = marketVersion; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public List<ProductPriceDTO> getChanges() { return changes; }
    public void setChanges(List<ProductPriceDTO> changes) { this.changes = changes; }

    public static MarketPriceUpdateBuilder builder() { return new MarketPriceUpdateBuilder(); }

    public static class MarketPriceUpdateBuilder {
        private String type = "PRICE_UPDATE";
        private int marketVersion;
        private String timestamp;
        private List<ProductPriceDTO> changes;

        public MarketPriceUpdateBuilder type(String type) { this.type = type; return this; }
        public MarketPriceUpdateBuilder marketVersion(int marketVersion) { this.marketVersion = marketVersion; return this; }
        public MarketPriceUpdateBuilder timestamp(String timestamp) { this.timestamp = timestamp; return this; }
        public MarketPriceUpdateBuilder changes(List<ProductPriceDTO> changes) { this.changes = changes; return this; }

        public MarketPriceUpdate build() {
            return new MarketPriceUpdate(type, marketVersion, timestamp, changes);
        }
    }
}
