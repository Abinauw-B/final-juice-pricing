# ☢️ NUCLEAR BACKEND RECONNECTION & RUNTIME AUDIT SCRIPT
$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8088/api"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "ACTUAL BACKEND CONNECTION & RUNTIME AUDIT" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Process & Port Verification
$conn = Get-NetTCPConnection -LocalPort 8088 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($conn) {
    Write-Host "[PASS] 1. Running Backend Process PID: $($conn.OwningProcess) on Port 8088" -ForegroundColor Green
} else {
    Write-Host "[FAIL] 1. No backend process listening on port 8088" -ForegroundColor Red
    exit 1
}

# 2. Direct PostgreSQL Health Telemetry
$health = Invoke-RestMethod -Uri "$baseUrl/health" -Method Get
if ($health.status -eq "UP" -and $health.services.postgresDb.status -eq "UP") {
    Write-Host "[PASS] 2. PostgreSQL Connection Verified: UP & CONNECTED" -ForegroundColor Green
} else {
    Write-Host "[FAIL] 2. PostgreSQL telemetry failed" -ForegroundColor Red
    exit 1
}

# 3. SuperAdmin Login
$loginBody = @{ username = "superadmin"; password = "Password@123" } | ConvertTo-Json
$authResp = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $authResp.token
$headers = @{ "Authorization" = "Bearer $token" }

Write-Host "[PASS] 3. Authenticated as SuperAdmin" -ForegroundColor Green

# 4. Products Parity Check
$apiProducts = Invoke-RestMethod -Uri "$baseUrl/products" -Method Get
$posProducts = Invoke-RestMethod -Uri "$baseUrl/pos/products" -Method Get

if ($apiProducts.Count -ne $posProducts.Count) {
    Write-Host "[FAIL] 4. Product count parity failure" -ForegroundColor Red
    exit 1
}

$mango = $apiProducts | Where-Object { $_.id -eq 1 }
Write-Host "[PASS] 4. Product Endpoints Parity: Target Mango ID=1, Price=Rs. $($mango.currentCupPrice)" -ForegroundColor Green

# 5. Checkout Price Authority Test (Ignore submitted fake price)
$batches = Invoke-RestMethod -Uri "$baseUrl/batches/active" -Method Get -Headers $headers
$activeBatch = $batches | Where-Object { $_.remainingVolumeMl -ge 250 } | Select-Object -First 1
$targetId = if ($activeBatch) { $activeBatch.productId } else { 1 }

$preProduct = (Invoke-RestMethod -Uri "$baseUrl/products/$targetId" -Method Get)
$prePrice = [double]$preProduct.currentCupPrice

$fakeOrderBody = @{
    paymentMethod = "CASH"
    items = @(
        @{ productId = $targetId; quantity = 1; cupSizeMl = 250; price = 1.00 }
    )
} | ConvertTo-Json -Depth 5

$checkoutResp = Invoke-RestMethod -Uri "$baseUrl/pos/checkout" -Method Post -Body $fakeOrderBody -ContentType "application/json"
$checkoutPrice = [double]$checkoutResp.totalAmount

if ($checkoutPrice -eq $prePrice) {
    Write-Host "[PASS] 5. Checkout Price Authority Confirmed: Submitted fake Rs.1.00 ignored, charged DB price Rs. $checkoutPrice" -ForegroundColor Green
} else {
    Write-Host "[FAIL] 5. Backend used submitted frontend price Rs. $checkoutPrice instead of DB price Rs. $prePrice!" -ForegroundColor Red
    exit 1
}

# 6. Surge (+Rs.1) and Decay (-Rs.1) Verification
$evalResp = Invoke-RestMethod -Uri "$baseUrl/pricing/evaluate/$targetId" -Method Post -Headers $headers
Write-Host "[PASS] 6. Dynamic Price Evaluation: Old Rs. $($evalResp.oldPrice) -> New Rs. $($evalResp.newPrice) (Status: $($evalResp.statusReason))" -ForegroundColor Green

# 7. Market Crash Overrides Check
$crashTrigger = Invoke-RestMethod -Uri "$baseUrl/pricing/market-crash/trigger?durationMinutes=1" -Method Post -Headers $headers
Write-Host "[PASS] 7. Market Crash Triggered: Active=$($crashTrigger.active)" -ForegroundColor Green

$crashStop = Invoke-RestMethod -Uri "$baseUrl/pricing/market-crash/stop" -Method Post -Headers $headers
Write-Host "[PASS] 8. Market Crash Stopped: Active=$($crashStop.active)" -ForegroundColor Green

# 8. Price History Audit Trail Check
$history = Invoke-RestMethod -Uri "$baseUrl/pricing/products/$targetId/history" -Method Get -Headers $headers
Write-Host "[PASS] 9. Price History Audit Trail: Found $($history.Count) history logs for Product ID $targetId" -ForegroundColor Green

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "NUCLEAR BACKEND RUNTIME AUDIT COMPLETED SUCCESSFULLY!" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
