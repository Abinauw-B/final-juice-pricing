package com.retailpos.inventory;

import com.retailpos.domain.JuiceBatch;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/batches")
@CrossOrigin(origins = "*")
public class JuiceBatchController {

    private final JuiceBatchService juiceBatchService;

    public JuiceBatchController(JuiceBatchService juiceBatchService) {
        this.juiceBatchService = juiceBatchService;
    }

    @GetMapping
    public ResponseEntity<List<JuiceBatch>> getAllBatches() {
        return ResponseEntity.ok(juiceBatchService.getAllBatches());
    }

    @GetMapping("/active")
    public ResponseEntity<List<JuiceBatch>> getActiveBatches() {
        return ResponseEntity.ok(juiceBatchService.getActiveBatches());
    }

    public static class CreateBatchRequest {
        private Long productId;
        private Integer containerCapacityMl;

        public CreateBatchRequest() {}
        public CreateBatchRequest(Long productId, Integer containerCapacityMl) {
            this.productId = productId;
            this.containerCapacityMl = containerCapacityMl;
        }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Integer getContainerCapacityMl() { return containerCapacityMl; }
        public void setContainerCapacityMl(Integer containerCapacityMl) { this.containerCapacityMl = containerCapacityMl; }
    }

    public static class UpdateBatchRequest {
        private Integer remainingVolumeMl;
        private JuiceBatch.BatchStatus status;

        public UpdateBatchRequest() {}
        public UpdateBatchRequest(Integer remainingVolumeMl, JuiceBatch.BatchStatus status) {
            this.remainingVolumeMl = remainingVolumeMl;
            this.status = status;
        }

        public Integer getRemainingVolumeMl() { return remainingVolumeMl; }
        public void setRemainingVolumeMl(Integer remainingVolumeMl) { this.remainingVolumeMl = remainingVolumeMl; }
        public JuiceBatch.BatchStatus getStatus() { return status; }
        public void setStatus(JuiceBatch.BatchStatus status) { this.status = status; }
    }

    @PostMapping
    public ResponseEntity<JuiceBatch> registerBatch(@RequestBody CreateBatchRequest request) {
        JuiceBatch newBatch = juiceBatchService.registerNewBatch(request.getProductId(), request.getContainerCapacityMl());
        return ResponseEntity.ok(newBatch);
    }

    @PutMapping("/{identifier}")
    public ResponseEntity<JuiceBatch> updateBatch(@PathVariable String identifier, @RequestBody UpdateBatchRequest request) {
        JuiceBatch updated = juiceBatchService.updateBatch(identifier, request.getRemainingVolumeMl(), request.getStatus());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/restock")
    public ResponseEntity<JuiceBatch> restockBatch(@PathVariable Long id, @RequestParam(required = false, defaultValue = "20000") Integer additionalMl) {
        JuiceBatch restocked = juiceBatchService.restockBatch(id, additionalMl);
        return ResponseEntity.ok(restocked);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBatch(@PathVariable Long id) {
        juiceBatchService.deleteBatch(id);
        return ResponseEntity.ok().build();
    }
}
