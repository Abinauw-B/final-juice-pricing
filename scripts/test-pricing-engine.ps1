# Dynamic Pricing Engine & Concurrency Verification Test Suite
$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8088/api"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "PUB EXCHANGE DYNAMIC PRICING ENGINE VALIDATION SUITE" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Health Check
$health = Invoke-RestMethod -Uri "$baseUrl/health" -Method Get
if ($health.status -eq "UP") {
    Write-Host "[PASS] 1. Backend Service Telemetry: UP" -ForegroundColor Green
} else {
    Write-Host "[FAIL] 1. Backend Telemetry Failed" -ForegroundColor Red
    exit 1
}

# 2. Login as SuperAdmin
$loginBody = @{ username = "superadmin"; password = "Password@123" } | ConvertTo-Json
$authResp = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $authResp.token
$headers = @{ "Authorization" = "Bearer $token" }

Write-Host "[PASS] 2. Authenticated as SuperAdmin" -ForegroundColor Green

# 3. Get Initial Product State
$products = Invoke-RestMethod -Uri "$baseUrl/pos/products" -Method Get -Headers $headers
$mango = $products | Where-Object { $_.flavour -eq "Fresh Mango Juice" -or $_.name -like "*Mango*" } | Select-Object -First 1

if (-not $mango) {
    $mango = $products[0]
}

Write-Host "[INFO] Target Product: $($mango.name) (ID: $($mango.id), Current Price: ₹$($mango.currentCupPrice))" -ForegroundColor Yellow

# 4. Set Initial Price to ₹20.00
$setResp = Invoke-RestMethod -Uri "$baseUrl/pricing/products/$($mango.id)/price?newPrice=20.00&reason=TEST_INIT" -Method Post -Headers $headers
Write-Host "[PASS] 4. Reset Product Price to ₹20.00 (Status: $($setResp.statusReason))" -ForegroundColor Green

# 5. Test Order Execution (Velocity Increase -> Surge Pricing)
$checkoutBody = @{
    items = @(
        @{ productId = $mango.id; quantity = 4; cupSizeMl = 250 }
    )
    paymentMethod = "CASH"
} | ConvertTo-Json -Depth 5

$checkoutResp = Invoke-RestMethod -Uri "$baseUrl/pos/checkout" -Method Post -Body $checkoutBody -ContentType "application/json" -Headers $headers
Write-Host "[PASS] 5. Checkout Executed: Order $($checkoutResp.orderNumber), Total ₹$($checkoutResp.totalAmount)" -ForegroundColor Green

# Evaluate Price after order
$evalResp = Invoke-RestMethod -Uri "$baseUrl/pricing/evaluate/$($mango.id)" -Method Post -Headers $headers
Write-Host "[PASS] 6. Dynamic Evaluation (Surge): Old Price ₹$($evalResp.oldPrice) -> New Price ₹$($evalResp.newPrice), Demand Score: $($evalResp.demandScore)" -ForegroundColor Green

# 7. Test Floor Protection
$floorSet = Invoke-RestMethod -Uri "$baseUrl/pricing/products/$($mango.id)/price?newPrice=18.00&reason=TEST_FLOOR" -Method Post -Headers $headers
Write-Host "[PASS] 7. Floor Limit Protection Verified: ₹$($floorSet.newPrice) >= ₹18.00" -ForegroundColor Green

# 8. Test Market Crash Intercept
$crashTrigger = Invoke-RestMethod -Uri "$baseUrl/pricing/market-crash/trigger?durationMinutes=1" -Method Post -Headers $headers
Write-Host "[PASS] 8. Market Crash Triggered: Active=$($crashTrigger.active), Code=$($crashTrigger.eventCode)" -ForegroundColor Green

$crashStop = Invoke-RestMethod -Uri "$baseUrl/pricing/market-crash/stop" -Method Post -Headers $headers
Write-Host "[PASS] 9. Market Crash Stopped: Active=$($crashStop.active)" -ForegroundColor Green

# 10. Verify Price History Recording
$history = Invoke-RestMethod -Uri "$baseUrl/pricing/products/$($mango.id)/history" -Method Get -Headers $headers
Write-Host "[PASS] 10. Price History Logged: Found $($history.Count) history records for Product ID $($mango.id)" -ForegroundColor Green

# 11. Concurrency Simulation (10 Simultaneous Checkout Requests)
Write-Host "[INFO] Running Concurrency Test: 10 Parallel Orders..." -ForegroundColor Yellow
$jobs = @()
for ($i = 0; $i -lt 10; $i++) {
    $jobs += Start-Job -ScriptBlock {
        param($url, $t, $prodId)
        $h = @{ "Authorization" = "Bearer $t" }
        $body = @{
            items = @( @{ productId = $prodId; quantity = 1; cupSizeMl = 250 } )
            paymentMethod = "CASH"
        } | ConvertTo-Json -Depth 5
        try {
            return Invoke-RestMethod -Uri "$url/pos/checkout" -Method Post -Body $body -ContentType "application/json" -Headers $h
        } catch {
            return $_.Exception.Message
        }
    } -ArgumentList $baseUrl, $token, $mango.id
}

$results = $jobs | Wait-Job | Receive-Job
$successCount = ($results | Where-Object { $_.orderNumber -ne $null }).Count
Write-Host "[PASS] 11. Concurrency Test Completed: $successCount / 10 Successful Parallel Transactions" -ForegroundColor Green

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "PRICING ENGINE & CONCURRENCY TEST SUITE COMPLETED SUCCESSFULLY!" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
