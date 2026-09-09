package com.retailpos.report;

import com.retailpos.domain.JuiceBatch;
import com.retailpos.domain.JuiceBatchRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/reports", "/api"})
public class ReportController {

    private final JuiceBatchRepository juiceBatchRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public ReportController(JuiceBatchRepository juiceBatchRepository, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.juiceBatchRepository = juiceBatchRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping({"/summary", "/dashboard"})
    public ResponseEntity<Map<String, Object>> getSummaryReport() {
        Map<String, Object> report = new HashMap<>();

        Long totalOrdersObj = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sales_orders", Long.class);
        long totalOrders = totalOrdersObj != null ? totalOrdersObj : 0L;

        BigDecimal totalRevenueObj = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(total_amount), 0) FROM sales_orders", BigDecimal.class);
        BigDecimal totalRevenue = totalRevenueObj != null ? totalRevenueObj : BigDecimal.ZERO;

        Integer cupsSoldObj = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(quantity), 0) FROM sales_order_items", Integer.class);
        int cupsSold = cupsSoldObj != null ? cupsSoldObj : 0;

        if (cupsSold == 0 && totalOrders > 0) {
            cupsSold = (int) Math.min(totalOrders, Integer.MAX_VALUE);
        }

        List<JuiceBatch> batches = juiceBatchRepository.findAll();
        long activeBatches = batches.stream().filter(b -> b.getStatus() == JuiceBatch.BatchStatus.ACTIVE).count();
        double liquidVolumeLitres = batches.stream()
                .mapToDouble(b -> (b.getRemainingVolumeMl() != null ? b.getRemainingVolumeMl() : 0) / 1000.0)
                .sum();

        BigDecimal avgOrderValue = (totalOrders > 0 && totalRevenue.compareTo(BigDecimal.ZERO) > 0)
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        report.put("totalOrders", totalOrders);
        report.put("activeBatches", activeBatches);
        report.put("totalBatches", batches.size());
        report.put("totalRevenue", totalRevenue);
        report.put("cupsSold", cupsSold);
        report.put("liquidVolumeLitres", Math.round(liquidVolumeLitres * 10.0) / 10.0);
        report.put("averageOrderValue", avgOrderValue);

        return ResponseEntity.ok(report);
    }

    @GetMapping("/products/sales")
    public ResponseEntity<List<Map<String, Object>>> getPerProductSalesBreakdown() {
        String sql = """
                SELECT
                    soi.product_id,
                    soi.product_name,
                    COUNT(DISTINCT soi.sales_order_id) AS order_count,
                    COALESCE(SUM(soi.quantity), 0) AS total_cups_sold,
                    COALESCE(SUM(soi.total_price), 0) AS total_revenue,
                    COALESCE(AVG(soi.unit_price), 0) AS avg_unit_price,
                    COALESCE(SUM(soi.volume_deducted_ml), 0) AS total_volume_ml
                FROM sales_order_items soi
                GROUP BY soi.product_id, soi.product_name
                ORDER BY total_revenue DESC
                """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/revenue/hourly")
    public ResponseEntity<List<Map<String, Object>>> getHourlyRevenue(
            @RequestParam(required = false) String date) {
        String sql = """
                SELECT
                    EXTRACT(HOUR FROM so.created_at) AS hour,
                    COUNT(*) AS order_count,
                    COALESCE(SUM(so.total_amount), 0) AS revenue
                FROM sales_orders so
                WHERE so.created_at::date = COALESCE(CAST(? AS DATE), CURRENT_DATE)
                GROUP BY EXTRACT(HOUR FROM so.created_at)
                ORDER BY hour
                """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, date);
        return ResponseEntity.ok(results);
    }
}
