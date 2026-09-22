package com.retailpos.user;

import com.retailpos.domain.SalesOrder;
import com.retailpos.domain.SalesOrderItem;
import com.retailpos.domain.SalesOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/admin/orders", "/api/orders"})
public class AdminOrderController {

    private final SalesOrderRepository salesOrderRepository;

    public AdminOrderController(SalesOrderRepository salesOrderRepository) {
        this.salesOrderRepository = salesOrderRepository;
    }

    private DateRange parseDateRange(String dateFilter, String startDateStr, String endDateStr) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = null;
        LocalDateTime end = null;

        if (dateFilter != null && !dateFilter.isBlank() && !"ALL".equalsIgnoreCase(dateFilter)) {
            switch (dateFilter.toUpperCase()) {
                case "TODAY":
                    start = LocalDate.now().atStartOfDay();
                    end = LocalDate.now().atTime(LocalTime.MAX);
                    break;
                case "YESTERDAY":
                    start = LocalDate.now().minusDays(1).atStartOfDay();
                    end = LocalDate.now().minusDays(1).atTime(LocalTime.MAX);
                    break;
                case "LAST_7_DAYS":
                case "7DAYS":
                    start = LocalDate.now().minusDays(7).atStartOfDay();
                    end = now;
                    break;
                case "LAST_30_DAYS":
                case "30DAYS":
                    start = LocalDate.now().minusDays(30).atStartOfDay();
                    end = now;
                    break;
                case "CUSTOM":
                    break;
            }
        }

        if (start == null && startDateStr != null && !startDateStr.isBlank()) {
            try {
                if (startDateStr.contains("T")) {
                    start = LocalDateTime.parse(startDateStr);
                } else {
                    start = LocalDate.parse(startDateStr).atStartOfDay();
                }
            } catch (Exception ignored) {}
        }

        if (end == null && endDateStr != null && !endDateStr.isBlank()) {
            try {
                if (endDateStr.contains("T")) {
                    end = LocalDateTime.parse(endDateStr);
                } else {
                    end = LocalDate.parse(endDateStr).atTime(LocalTime.MAX);
                }
            } catch (Exception ignored) {}
        }

        return new DateRange(start, end);
    }

    private static class DateRange {
        final LocalDateTime start;
        final LocalDateTime end;
        DateRange(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String dateFilter,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        String cleanedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String searchPattern = (cleanedSearch != null) ? "%" + cleanedSearch.toLowerCase() + "%" : null;
        String searchExact = cleanedSearch;
        String cleanedStatus = (paymentStatus != null && !paymentStatus.isBlank() && !"ALL".equalsIgnoreCase(paymentStatus)) ? paymentStatus.trim().toLowerCase() : null;

        DateRange dr = parseDateRange(dateFilter, startDate, endDate);

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String field = "id";
        if ("createdAt".equalsIgnoreCase(sortBy) || "date".equalsIgnoreCase(sortBy)) {
            field = "createdAt";
        } else if ("totalAmount".equalsIgnoreCase(sortBy) || "total".equalsIgnoreCase(sortBy) || "amount".equalsIgnoreCase(sortBy)) {
            field = "totalAmount";
        } else if ("orderNumber".equalsIgnoreCase(sortBy)) {
            field = "orderNumber";
        }

        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(direction, field));
        Page<SalesOrder> orderPage = salesOrderRepository.findWithFilters(cleanedStatus, dr.start, dr.end, searchPattern, searchExact, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", orderPage.getContent());
        response.put("pageNumber", orderPage.getNumber());
        response.put("pageSize", orderPage.getSize());
        response.put("totalElements", orderPage.getTotalElements());
        response.put("totalPages", orderPage.getTotalPages());
        response.put("last", orderPage.isLast());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{idOrNumber}")
    public ResponseEntity<?> getOrderByIdOrNumber(@PathVariable String idOrNumber) {
        Optional<SalesOrder> order = Optional.empty();
        try {
            Long id = Long.parseLong(idOrNumber);
            order = salesOrderRepository.findByIdWithItems(id);
        } catch (NumberFormatException ignored) {}

        if (order.isEmpty()) {
            order = salesOrderRepository.findByOrderNumberWithItems(idOrNumber);
        }

        return order.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(null));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getOrderSummary(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String dateFilter,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        String cleanedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String searchPattern = (cleanedSearch != null) ? "%" + cleanedSearch.toLowerCase() + "%" : null;
        String searchExact = cleanedSearch;
        String cleanedStatus = (paymentStatus != null && !paymentStatus.isBlank() && !"ALL".equalsIgnoreCase(paymentStatus)) ? paymentStatus.trim().toLowerCase() : null;
        DateRange dr = parseDateRange(dateFilter, startDate, endDate);

        Long totalOrders = salesOrderRepository.countWithFilters(cleanedStatus, dr.start, dr.end, searchPattern, searchExact);
        BigDecimal totalSales = salesOrderRepository.sumTotalAmountWithFilters(cleanedStatus, dr.start, dr.end, searchPattern, searchExact);

        Long completedOrders = salesOrderRepository.countWithFilters("completed", dr.start, dr.end, searchPattern, searchExact);
        Long pendingOrders = salesOrderRepository.countWithFilters("pending", dr.start, dr.end, searchPattern, searchExact);
        Long failedOrders = salesOrderRepository.countWithFilters("failed", dr.start, dr.end, searchPattern, searchExact);
        Long cancelledOrders = salesOrderRepository.countWithFilters("cancelled", dr.start, dr.end, searchPattern, searchExact);

        BigDecimal avgOrderValue = (totalOrders != null && totalOrders > 0 && totalSales != null)
                ? totalSales.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalOrders", totalOrders != null ? totalOrders : 0L);
        summary.put("completedOrders", completedOrders != null ? completedOrders : 0L);
        summary.put("pendingOrders", pendingOrders != null ? pendingOrders : 0L);
        summary.put("failedOrders", failedOrders != null ? failedOrders : 0L);
        summary.put("cancelledOrders", cancelledOrders != null ? cancelledOrders : 0L);
        summary.put("totalSales", totalSales != null ? totalSales : BigDecimal.ZERO);
        summary.put("averageOrderValue", avgOrderValue);

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportOrdersCsv(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String dateFilter,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        String cleanedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String searchPattern = (cleanedSearch != null) ? "%" + cleanedSearch.toLowerCase() + "%" : null;
        String searchExact = cleanedSearch;
        String cleanedStatus = (paymentStatus != null && !paymentStatus.isBlank() && !"ALL".equalsIgnoreCase(paymentStatus)) ? paymentStatus.trim().toLowerCase() : null;
        DateRange dr = parseDateRange(dateFilter, startDate, endDate);

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String field = "id";
        if ("createdAt".equalsIgnoreCase(sortBy) || "date".equalsIgnoreCase(sortBy)) {
            field = "createdAt";
        } else if ("totalAmount".equalsIgnoreCase(sortBy)) {
            field = "totalAmount";
        }

        Pageable pageable = PageRequest.of(0, 5000, Sort.by(direction, field));
        Page<SalesOrder> orderPage = salesOrderRepository.findWithFilters(cleanedStatus, dr.start, dr.end, searchPattern, searchExact, pageable);

        StringBuilder csv = new StringBuilder();
        csv.append("Order ID,Order Number,Date,Time,Total Amount (INR),Payment Method,Payment Status,Items Count,Items Purchased\n");

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        for (SalesOrder order : orderPage.getContent()) {
            String dateStr = order.getCreatedAt() != null ? order.getCreatedAt().format(dateFormatter) : "";
            String timeStr = order.getCreatedAt() != null ? order.getCreatedAt().format(timeFormatter) : "";

            String itemsSummary = "";
            int itemCount = 0;
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                itemCount = order.getItems().size();
                itemsSummary = order.getItems().stream()
                        .map(i -> i.getProductName() + " x" + i.getQuantity())
                        .collect(Collectors.joining("; "));
            }

            csv.append(String.format("%d,\"%s\",\"%s\",\"%s\",%.2f,\"%s\",\"%s\",%d,\"%s\"\n",
                    order.getId(),
                    order.getOrderNumber(),
                    dateStr,
                    timeStr,
                    order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO,
                    order.getPaymentMethod() != null ? order.getPaymentMethod() : "CASH",
                    order.getPaymentStatus() != null ? order.getPaymentStatus() : "COMPLETED",
                    itemCount,
                    itemsSummary.replace("\"", "\"\"")));
        }

        byte[] body = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "purchasing_history.csv");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
