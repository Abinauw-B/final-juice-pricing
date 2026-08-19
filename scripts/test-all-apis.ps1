# Pub Exchange (Juice Bar Stock Exchange) - Comprehensive API End-to-End Test Suite
# Runs against backend: http://localhost:8088/api

$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8088/api"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "STARTING PUB EXCHANGE AUTOMATED API TEST SUITE" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

$passedCount = 0
$failedCount = 0

function Assert-Test {
    param(
        [string]$TestName,
        [scriptblock]$TestBlock
    )
    Write-Host "[TEST] $TestName ... " -NoNewline
    try {
        & $TestBlock
        Write-Host "PASS [OK]" -ForegroundColor Green
        $script:passedCount++
    } catch {
        Write-Host "FAIL [ERR] ($($_.Exception.Message))" -ForegroundColor Red
        $script:failedCount++
    }
}

# 1. Health & Telemetry Test
Assert-Test "1. Service Health Telemetry (GET /api/health)" {
    $res = Invoke-RestMethod -Uri "$baseUrl/health" -Method GET
    if ($res.status -ne "UP") { throw "Status is not UP" }
    if ($res.onlineServicesCount -ne 5) { throw "Expected 5 online services" }
}

# 2. Authentication Login Test
$jwtToken = ""
Assert-Test "2. SuperAdmin Login (POST /api/auth/login)" {
    $body = @{ username = "superadmin"; password = "Password123!" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method POST -ContentType "application/json" -Body $body
    if ([string]::IsNullOrWhiteSpace($res.token)) { throw "JWT Token missing in response" }
    $script:jwtToken = $res.token
}

$headers = @{ "Authorization" = "Bearer $jwtToken" }

# 3. User Profile Test
Assert-Test "3. Fetch User Profile (GET /api/auth/profile)" {
    $res = Invoke-RestMethod -Uri "$baseUrl/auth/profile?username=superadmin" -Method GET -Headers $headers
    if ($res.username -ne "superadmin") { throw "Username mismatch" }
}

# 4. Product Catalog Listing Test
$firstProductId = 1
Assert-Test "4. Fetch Available Juice Products (GET /api/pos/products)" {
    $res = Invoke-RestMethod -Uri "$baseUrl/pos/products" -Method GET
    if ($res.Count -lt 1) { throw "No products returned" }
    $script:firstProductId = $res[0].id
}

# 5. Juice Batch Listing Test
Assert-Test "5. Fetch Active 20L Juice Batches (GET /api/batches/active)" {
    $res = Invoke-RestMethod -Uri "$baseUrl/batches/active" -Method GET -Headers $headers
    if ($res.Count -lt 1) { throw "No active batches found" }
}

# 6. Dynamic Pricing Engine Evaluation Test
Assert-Test "6. Evaluate Dynamic Pricing Engine (GET /api/pricing/evaluate)" {
    $res = Invoke-RestMethod -Uri "$baseUrl/pricing/evaluate" -Method GET -Headers $headers
    if ($res.Count -lt 1) { throw "Pricing evaluation empty" }
}

# 7. Market Crash Trigger Test
Assert-Test "7. Trigger Market Crash Routine (POST /api/pricing/market-crash/trigger)" {
    $res = Invoke-RestMethod -Uri "$baseUrl/pricing/market-crash/trigger?durationMinutes=3" -Method POST -Headers $headers
    if ($res.active -ne $true) { throw "Market crash failed to activate" }
}

# 8. Market Crash Status Verification Test
Assert-Test "8. Verify Market Crash Status (GET /api/pricing/market-crash/status)" {
    $res = Invoke-RestMethod -Uri "$baseUrl/pricing/market-crash/status" -Method GET
    if ($res.active -ne $true) { throw "Market crash status reports inactive" }
}

# 9. Market Crash Stop Test
Assert-Test "9. Stop Market Crash Routine (POST /api/pricing/market-crash/stop)" {
    $res = Invoke-RestMethod -Uri "$baseUrl/pricing/market-crash/stop" -Method POST -Headers $headers
    if ($res.active -ne $false) { throw "Market crash failed to stop" }
}

# 10. Customer POS Order Checkout Test
Assert-Test "10. Execute POS Cart Order Checkout (POST /api/pos/checkout)" {
    $orderBody = @{
        paymentMethod = "CASH"
        items = @(
            @{ productId = $script:firstProductId; quantity = 1; cupSizeMl = 250 }
        )
    } | ConvertTo-Json -Depth 3

    $res = Invoke-RestMethod -Uri "$baseUrl/pos/checkout" -Method POST -ContentType "application/json" -Body $orderBody
    if ([string]::IsNullOrWhiteSpace($res.orderNumber)) { throw "Order number missing" }
    if ($res.paymentStatus -ne "COMPLETED") { throw "Payment status incomplete" }
}

# 11. Dashboard Reports Summary Test
Assert-Test "11. Fetch Dashboard Revenue Summary (GET /api/reports/summary)" {
    $res = Invoke-RestMethod -Uri "$baseUrl/reports/summary" -Method GET -Headers $headers
    if ($res.cupsSold -lt 1) { throw "Cups sold aggregate zero" }
}

# 12. Notification Unread Count Test
Assert-Test "12. Fetch Notification Unread Count (GET /api/notifications/unread-count)" {
    $res = Invoke-RestMethod -Uri "$baseUrl/notifications/unread-count" -Method GET -Headers $headers
    if ($res.unreadCount -eq $null) { throw "Unread count missing" }
}

# 13. System Audit Logs Test
Assert-Test "13. Record & Retrieve Audit Log Entries (POST & GET /api/audit-logs)" {
    $auditPayload = @{
        userId = 1
        username = "superadmin"
        action = "API_TEST_SUITE_RUN"
        module = "SYSTEM_TEST"
        details = "Automated integration test execution"
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/audit-logs" -Method POST -ContentType "application/json" -Body $auditPayload -Headers $headers | Out-Null
    
    $res = Invoke-RestMethod -Uri "$baseUrl/audit-logs" -Method GET -Headers $headers
    if ($res.Count -lt 1) { throw "Audit logs empty" }
}

# 14. System Configuration Test
Assert-Test "14. Fetch System Config Parameters (GET /api/pricing/config)" {
    $res = Invoke-RestMethod -Uri "$baseUrl/pricing/config" -Method GET -Headers $headers
    if ($res.Count -lt 1) { throw "System config empty" }
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "FINAL API TEST SUMMARY" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Total Tests Run : $($passedCount + $failedCount)"
Write-Host "Passed          : $passedCount [OK]" -ForegroundColor Green
Write-Host "Failed          : $failedCount [ERR]" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan

if ($failedCount -gt 0) {
    exit 1
}
