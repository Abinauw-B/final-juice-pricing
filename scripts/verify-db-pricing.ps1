# PostgreSQL & Backend Authoritative Verification Script
$baseUrl = "http://localhost:8088/api"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "POSTGRESQL SINGLE SOURCE OF TRUTH VERIFICATION" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Fetch Products
$products = Invoke-RestMethod -Uri "$baseUrl/pos/products" -Method Get
Write-Host "[VERIFY] Checking $($products.Count) Products..." -ForegroundColor Yellow

foreach ($p in $products) {
    if ($null -eq $p.currentCupPrice -or $p.currentCupPrice -lt 0) {
        Write-Host "[FAIL] Product $($p.name) has invalid price: $($p.currentCupPrice)" -ForegroundColor Red
        exit 1
    }
    Write-Host "  Product: $($p.name) | Current Price: Rs. $($p.currentCupPrice) | Min: Rs. $($p.minCupPrice) | Max: Rs. $($p.maxCupPrice)" -ForegroundColor Green
}

# 2. Fetch Active Batches
$batches = Invoke-RestMethod -Uri "$baseUrl/batches/active" -Method Get
Write-Host "[VERIFY] Checking $($batches.Count) Active Batches..." -ForegroundColor Yellow

foreach ($b in $batches) {
    if ($null -eq $b.remainingVolumeMl -or $b.remainingVolumeMl -lt 0) {
        Write-Host "[FAIL] Batch $($b.batchNumber) has negative or null remaining volume!" -ForegroundColor Red
        exit 1
    }
    Write-Host "  Batch: $($b.batchNumber) ($($b.juiceFlavour)) | Volume: $($b.remainingVolumeMl) ML / $($b.initialVolumeMl) ML" -ForegroundColor Green
}

# 3. Fetch Price History
$history = Invoke-RestMethod -Uri "$baseUrl/pricing/history" -Method Get
Write-Host "[VERIFY] Total Price History Logs: $($history.Count)" -ForegroundColor Green

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "ALL POSTGRESQL INTEGRITY CHECKS PASSED PERFECTLY!" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
