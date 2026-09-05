package com.retailpos.pricing;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PricingSimulationService {

    public static class SimulationRequest {
        private String flavourName = "Fresh Mango Juice";
        private Integer initialVolumeMl = 20000;
        private BigDecimal initialPrice = new BigDecimal("25.00");
        private BigDecimal minPrice = new BigDecimal("20.00");
        private BigDecimal maxPrice = new BigDecimal("30.00");
        private Integer totalSimulatedPurchases = 40;
        private Integer cupsPerInterval = 4;
        private Integer intervalMinutes = 1;
        private Double targetSales = 0.55;
        private String startTimeStr = "12:00";
        private Boolean includeCrash = false;
        private Double weightVelocity = 0.40;
        private Double weightStockPressure = 0.40;
        private Double weightTimeFactor = 0.20;

        public SimulationRequest() {}

        public String getFlavourName() { return flavourName; }
        public void setFlavourName(String flavourName) { this.flavourName = flavourName; }
        public Integer getInitialVolumeMl() { return initialVolumeMl; }
        public void setInitialVolumeMl(Integer initialVolumeMl) { this.initialVolumeMl = initialVolumeMl; }
        public BigDecimal getInitialPrice() { return initialPrice; }
        public void setInitialPrice(BigDecimal initialPrice) { this.initialPrice = initialPrice; }
        public BigDecimal getMinPrice() { return minPrice; }
        public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }
        public BigDecimal getMaxPrice() { return maxPrice; }
        public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }
        public Integer getTotalSimulatedPurchases() { return totalSimulatedPurchases; }
        public void setTotalSimulatedPurchases(Integer totalSimulatedPurchases) { this.totalSimulatedPurchases = totalSimulatedPurchases; }
        public Integer getCupsPerInterval() { return cupsPerInterval; }
        public void setCupsPerInterval(Integer cupsPerInterval) { this.cupsPerInterval = cupsPerInterval; }
        public Integer getIntervalMinutes() { return intervalMinutes; }
        public void setIntervalMinutes(Integer intervalMinutes) { this.intervalMinutes = intervalMinutes; }
        public Double getTargetSales() { return targetSales; }
        public void setTargetSales(Double targetSales) { this.targetSales = targetSales; }
        public String getStartTimeStr() { return startTimeStr; }
        public void setStartTimeStr(String startTimeStr) { this.startTimeStr = startTimeStr; }
        public Boolean getIncludeCrash() { return includeCrash; }
        public void setIncludeCrash(Boolean includeCrash) { this.includeCrash = includeCrash; }
        public boolean isIncludeCrash() { return includeCrash != null && includeCrash; }
        public Double getWeightVelocity() { return weightVelocity; }
        public void setWeightVelocity(Double weightVelocity) { this.weightVelocity = weightVelocity; }
        public Double getWeightStockPressure() { return weightStockPressure; }
        public void setWeightStockPressure(Double weightStockPressure) { this.weightStockPressure = weightStockPressure; }
        public Double getWeightTimeFactor() { return weightTimeFactor; }
        public void setWeightTimeFactor(Double weightTimeFactor) { this.weightTimeFactor = weightTimeFactor; }
    }

    public static class SimulationStep {
        private int stepIndex;
        private String timeStr;
        private int remainingVolumeMl;
        private int estimatedRemainingCups;
        private int cupsSoldThisStep;
        private int cumulativeCupsSold;
        private int w0;
        private int w1;
        private int w2;
        private double weightedSales;
        private double targetSales;
        private double demandRatio;
        private double demandScore;
        private BigDecimal price;
        private String priceMovement;
        private String explanation;

        public SimulationStep() {}
        public SimulationStep(int stepIndex, String timeStr, int remainingVolumeMl, int estimatedRemainingCups, int cupsSoldThisStep, int cumulativeCupsSold, int w0, int w1, int w2, double weightedSales, double targetSales, double demandRatio, double demandScore, BigDecimal price, String priceMovement, String explanation) {
            this.stepIndex = stepIndex;
            this.timeStr = timeStr;
            this.remainingVolumeMl = remainingVolumeMl;
            this.estimatedRemainingCups = estimatedRemainingCups;
            this.cupsSoldThisStep = cupsSoldThisStep;
            this.cumulativeCupsSold = cumulativeCupsSold;
            this.w0 = w0;
            this.w1 = w1;
            this.w2 = w2;
            this.weightedSales = weightedSales;
            this.targetSales = targetSales;
            this.demandRatio = demandRatio;
            this.demandScore = demandScore;
            this.price = price;
            this.priceMovement = priceMovement;
            this.explanation = explanation;
        }

        public int getStepIndex() { return stepIndex; }
        public void setStepIndex(int stepIndex) { this.stepIndex = stepIndex; }
        public String getTimeStr() { return timeStr; }
        public void setTimeStr(String timeStr) { this.timeStr = timeStr; }
        public int getRemainingVolumeMl() { return remainingVolumeMl; }
        public void setRemainingVolumeMl(int remainingVolumeMl) { this.remainingVolumeMl = remainingVolumeMl; }
        public int getEstimatedRemainingCups() { return estimatedRemainingCups; }
        public void setEstimatedRemainingCups(int estimatedRemainingCups) { this.estimatedRemainingCups = estimatedRemainingCups; }
        public int getCupsSoldThisStep() { return cupsSoldThisStep; }
        public void setCupsSoldThisStep(int cupsSoldThisStep) { this.cupsSoldThisStep = cupsSoldThisStep; }
        public int getCumulativeCupsSold() { return cumulativeCupsSold; }
        public void setCumulativeCupsSold(int cumulativeCupsSold) { this.cumulativeCupsSold = cumulativeCupsSold; }
        public int getW0() { return w0; }
        public void setW0(int w0) { this.w0 = w0; }
        public int getW1() { return w1; }
        public void setW1(int w1) { this.w1 = w1; }
        public int getW2() { return w2; }
        public void setW2(int w2) { this.w2 = w2; }
        public double getWeightedSales() { return weightedSales; }
        public void setWeightedSales(double weightedSales) { this.weightedSales = weightedSales; }
        public double getTargetSales() { return targetSales; }
        public void setTargetSales(double targetSales) { this.targetSales = targetSales; }
        public double getDemandRatio() { return demandRatio; }
        public void setDemandRatio(double demandRatio) { this.demandRatio = demandRatio; }
        public double getDemandScore() { return demandScore; }
        public void setDemandScore(double demandScore) { this.demandScore = demandScore; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public String getPriceMovement() { return priceMovement; }
        public void setPriceMovement(String priceMovement) { this.priceMovement = priceMovement; }
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }

        public static SimulationStepBuilder builder() { return new SimulationStepBuilder(); }
        public static class SimulationStepBuilder {
            private int stepIndex;
            private String timeStr;
            private int remainingVolumeMl;
            private int estimatedRemainingCups;
            private int cupsSoldThisStep;
            private int cumulativeCupsSold;
            private int w0;
            private int w1;
            private int w2;
            private double weightedSales;
            private double targetSales;
            private double demandRatio;
            private double demandScore;
            private BigDecimal price;
            private String priceMovement;
            private String explanation;

            public SimulationStepBuilder stepIndex(int stepIndex) { this.stepIndex = stepIndex; return this; }
            public SimulationStepBuilder timeStr(String timeStr) { this.timeStr = timeStr; return this; }
            public SimulationStepBuilder remainingVolumeMl(int remainingVolumeMl) { this.remainingVolumeMl = remainingVolumeMl; return this; }
            public SimulationStepBuilder estimatedRemainingCups(int estimatedRemainingCups) { this.estimatedRemainingCups = estimatedRemainingCups; return this; }
            public SimulationStepBuilder cupsSoldThisStep(int cupsSoldThisStep) { this.cupsSoldThisStep = cupsSoldThisStep; return this; }
            public SimulationStepBuilder cumulativeCupsSold(int cumulativeCupsSold) { this.cumulativeCupsSold = cumulativeCupsSold; return this; }
            public SimulationStepBuilder w0(int w0) { this.w0 = w0; return this; }
            public SimulationStepBuilder w1(int w1) { this.w1 = w1; return this; }
            public SimulationStepBuilder w2(int w2) { this.w2 = w2; return this; }
            public SimulationStepBuilder weightedSales(double weightedSales) { this.weightedSales = weightedSales; return this; }
            public SimulationStepBuilder targetSales(double targetSales) { this.targetSales = targetSales; return this; }
            public SimulationStepBuilder demandRatio(double demandRatio) { this.demandRatio = demandRatio; return this; }
            public SimulationStepBuilder demandScore(double demandScore) { this.demandScore = demandScore; return this; }
            public SimulationStepBuilder price(BigDecimal price) { this.price = price; return this; }
            public SimulationStepBuilder priceMovement(String priceMovement) { this.priceMovement = priceMovement; return this; }
            public SimulationStepBuilder explanation(String explanation) { this.explanation = explanation; return this; }
            public SimulationStep build() { return new SimulationStep(stepIndex, timeStr, remainingVolumeMl, estimatedRemainingCups, cupsSoldThisStep, cumulativeCupsSold, w0, w1, w2, weightedSales, targetSales, demandRatio, demandScore, price, priceMovement, explanation); }
        }
    }

    public static class SimulationResponse {
        private String flavourName;
        private int initialVolumeMl;
        private int finalVolumeMl;
        private BigDecimal initialPrice;
        private BigDecimal finalPrice;
        private int totalCupsSold;
        private List<SimulationStep> steps;

        public SimulationResponse() {}
        public SimulationResponse(String flavourName, int initialVolumeMl, int finalVolumeMl, BigDecimal initialPrice, BigDecimal finalPrice, int totalCupsSold, List<SimulationStep> steps) {
            this.flavourName = flavourName;
            this.initialVolumeMl = initialVolumeMl;
            this.finalVolumeMl = finalVolumeMl;
            this.initialPrice = initialPrice;
            this.finalPrice = finalPrice;
            this.totalCupsSold = totalCupsSold;
            this.steps = steps;
        }

        public String getFlavourName() { return flavourName; }
        public void setFlavourName(String flavourName) { this.flavourName = flavourName; }
        public int getInitialVolumeMl() { return initialVolumeMl; }
        public void setInitialVolumeMl(int initialVolumeMl) { this.initialVolumeMl = initialVolumeMl; }
        public int getFinalVolumeMl() { return finalVolumeMl; }
        public void setFinalVolumeMl(int finalVolumeMl) { this.finalVolumeMl = finalVolumeMl; }
        public BigDecimal getInitialPrice() { return initialPrice; }
        public void setInitialPrice(BigDecimal initialPrice) { this.initialPrice = initialPrice; }
        public BigDecimal getFinalPrice() { return finalPrice; }
        public void setFinalPrice(BigDecimal finalPrice) { this.finalPrice = finalPrice; }
        public int getTotalCupsSold() { return totalCupsSold; }
        public void setTotalCupsSold(int totalCupsSold) { this.totalCupsSold = totalCupsSold; }
        public List<SimulationStep> getSteps() { return steps; }
        public void setSteps(List<SimulationStep> steps) { this.steps = steps; }

        public static SimulationResponseBuilder builder() { return new SimulationResponseBuilder(); }
        public static class SimulationResponseBuilder {
            private String flavourName;
            private int initialVolumeMl;
            private int finalVolumeMl;
            private BigDecimal initialPrice;
            private BigDecimal finalPrice;
            private int totalCupsSold;
            private List<SimulationStep> steps;

            public SimulationResponseBuilder flavourName(String flavourName) { this.flavourName = flavourName; return this; }
            public SimulationResponseBuilder initialVolumeMl(int initialVolumeMl) { this.initialVolumeMl = initialVolumeMl; return this; }
            public SimulationResponseBuilder finalVolumeMl(int finalVolumeMl) { this.finalVolumeMl = finalVolumeMl; return this; }
            public SimulationResponseBuilder initialPrice(BigDecimal initialPrice) { this.initialPrice = initialPrice; return this; }
            public SimulationResponseBuilder finalPrice(BigDecimal finalPrice) { this.finalPrice = finalPrice; return this; }
            public SimulationResponseBuilder totalCupsSold(int totalCupsSold) { this.totalCupsSold = totalCupsSold; return this; }
            public SimulationResponseBuilder steps(List<SimulationStep> steps) { this.steps = steps; return this; }
            public SimulationResponse build() { return new SimulationResponse(flavourName, initialVolumeMl, finalVolumeMl, initialPrice, finalPrice, totalCupsSold, steps); }
        }
    }

    public SimulationResponse runSimulation(SimulationRequest request) {
        int volume = (request.getInitialVolumeMl() != null) ? request.getInitialVolumeMl() : 20000;
        BigDecimal currentPrice = (request.getInitialPrice() != null) ? request.getInitialPrice().setScale(2, RoundingMode.HALF_UP) : new BigDecimal("25.00");
        BigDecimal minPrice = (request.getMinPrice() != null) ? request.getMinPrice().setScale(2, RoundingMode.HALF_UP) : new BigDecimal("20.00");
        BigDecimal maxPrice = (request.getMaxPrice() != null) ? request.getMaxPrice().setScale(2, RoundingMode.HALF_UP) : new BigDecimal("30.00");

        int cupsPerStep = (request.getCupsPerInterval() != null) ? request.getCupsPerInterval() : 4;
        int intervalMins = (request.getIntervalMinutes() != null && request.getIntervalMinutes() > 0) ? request.getIntervalMinutes() : 1;
        int totalPurchases = (request.getTotalSimulatedPurchases() != null) ? request.getTotalSimulatedPurchases() : 40;
        int maxSteps = Math.min(30, (int) Math.ceil((double) totalPurchases / Math.max(1, cupsPerStep)));
        if (maxSteps <= 0) maxSteps = 10;

        double targetVal = (request.getTargetSales() != null && request.getTargetSales() > 0) ? request.getTargetSales() : 0.55;
        BigDecimal targetSales = BigDecimal.valueOf(targetVal).setScale(2, RoundingMode.HALF_UP);

        LocalTime currentTime = LocalTime.parse(request.getStartTimeStr() != null ? request.getStartTimeStr() : "12:00");
        int cumulativeCups = 0;
        List<SimulationStep> steps = new ArrayList<>();
        List<Integer> salesHistory = new ArrayList<>();

        boolean crashInjected = false;
        BigDecimal preCrashSnapshotPrice = null;

        for (int i = 1; i <= maxSteps && volume > 0; i++) {
            int cupsToDeduct = Math.min(cupsPerStep, volume / 250);
            int mlDeducted = cupsToDeduct * 250;
            volume -= mlDeducted;
            cumulativeCups += cupsToDeduct;
            salesHistory.add(cupsToDeduct);

            int w0 = cupsToDeduct;
            int w1 = (salesHistory.size() >= 2) ? salesHistory.get(salesHistory.size() - 2) : 0;
            int w2 = (salesHistory.size() >= 3) ? salesHistory.get(salesHistory.size() - 3) : 0;

            // Authoritative DWMA: S_w = 1.00 * W0 + 0.50 * W1 + 0.25 * W2
            BigDecimal sw = BigDecimal.valueOf(w0).multiply(new BigDecimal("1.00"))
                    .add(BigDecimal.valueOf(w1).multiply(new BigDecimal("0.50")))
                    .add(BigDecimal.valueOf(w2).multiply(new BigDecimal("0.25")))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal rd = (targetSales.compareTo(BigDecimal.ZERO) > 0)
                    ? sw.divide(targetSales, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal oldPrice = currentPrice;
            String movement = "₹0";
            String explanation;

            // Check Market Crash Event at Step 5
            if (i == 5 && request.isIncludeCrash() && !crashInjected) {
                crashInjected = true;
                preCrashSnapshotPrice = currentPrice;
                currentPrice = minPrice;
                movement = "CRASH";
                explanation = String.format("Step %d (%s): 🚨 Market Crash Injected! Price dropped to floor limit ₹%s (Snapshot saved: ₹%s).",
                        i, currentTime, currentPrice, preCrashSnapshotPrice);
            } else if (i == 6 && preCrashSnapshotPrice != null) {
                currentPrice = preCrashSnapshotPrice;
                preCrashSnapshotPrice = null;
                movement = "RESTORED";
                explanation = String.format("Step %d (%s): Market Crash expired. Exact pre-crash snapshot restored to ₹%s.",
                        i, currentTime, currentPrice);
            } else {
                // Authoritative Movement Rules:
                // R_d >= 1.10 AND W0 > 0 -> +₹1
                // R_d >= 1.10 AND W0 == 0 -> ₹0
                // 0.90 <= R_d < 1.10 -> ₹0
                // 0.50 <= R_d < 0.90 -> -₹1
                // R_d < 0.50 -> -₹2
                BigDecimal deltaP;
                if (rd.compareTo(new BigDecimal("1.10")) >= 0) {
                    if (w0 > 0) {
                        deltaP = BigDecimal.ONE;
                        movement = "+₹1";
                    } else {
                        deltaP = BigDecimal.ZERO;
                        movement = "₹0";
                    }
                } else if (rd.compareTo(new BigDecimal("0.90")) >= 0) {
                    deltaP = BigDecimal.ZERO;
                    movement = "₹0";
                } else if (rd.compareTo(new BigDecimal("0.50")) >= 0) {
                    deltaP = new BigDecimal("-1.00");
                    movement = "-₹1";
                } else {
                    deltaP = new BigDecimal("-2.00");
                    movement = "-₹2";
                }

                BigDecimal uncapped = currentPrice.add(deltaP);
                currentPrice = uncapped.max(minPrice).min(maxPrice).setScale(2, RoundingMode.HALF_UP);

                explanation = String.format("Step %d (%s): W0=%d, W1=%d, W2=%d | S_w=%.2f, Target=%.2f cups/min, R_d=%.2f => Movement %s (₹%s -> ₹%s)",
                        i, currentTime, w0, w1, w2, sw.doubleValue(), targetSales.doubleValue(), rd.doubleValue(), movement, oldPrice, currentPrice);
            }

            steps.add(SimulationStep.builder()
                    .stepIndex(i)
                    .timeStr(currentTime.toString())
                    .remainingVolumeMl(volume)
                    .estimatedRemainingCups(volume / 250)
                    .cupsSoldThisStep(cupsToDeduct)
                    .cumulativeCupsSold(cumulativeCups)
                    .w0(w0)
                    .w1(w1)
                    .w2(w2)
                    .weightedSales(sw.doubleValue())
                    .targetSales(targetSales.doubleValue())
                    .demandRatio(rd.doubleValue())
                    .demandScore(rd.multiply(BigDecimal.valueOf(100)).doubleValue())
                    .price(currentPrice)
                    .priceMovement(movement)
                    .explanation(explanation)
                    .build());

            currentTime = currentTime.plusMinutes(intervalMins);
        }

        return SimulationResponse.builder()
                .flavourName(request.getFlavourName())
                .initialVolumeMl(request.getInitialVolumeMl())
                .finalVolumeMl(volume)
                .initialPrice(request.getInitialPrice() != null ? request.getInitialPrice().setScale(2, RoundingMode.HALF_UP) : new BigDecimal("25.00"))
                .finalPrice(currentPrice)
                .totalCupsSold(cumulativeCups)
                .steps(steps)
                .build();
    }
}

