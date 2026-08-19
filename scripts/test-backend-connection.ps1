# Backend Connection Audit & Telemetry Validation Script
$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8088/api"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "PUB EXCHANGE BACKEND & POSTGRESQL CONNECTION AUDIT" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Health & Database Connection Telemetry
$health = Invoke-RestMethod -Uri "$baseUrl/health" -Method Get
if ($health.status -eq "UP" -and $health.services.postgresDb.status -eq "UP") {
    Write-Host "[PASS] 1. Backend Health & PostgreSQL Ping: UP & CONNECTED" -ForegroundColor Green
} else {
    Write-Host "[FAIL] 1. Database Disconnected or Health Status DOWN" -ForegroundColor Red
    exit 1
}

# 2. SuperAdmin Authentication
$loginBody = @{ username = "superadmin"; password = "Password@123" } | ConvertTo-Json
$authResp = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
if ($authResp.token) {
    Write-Host "[PASS] 2. JWT Authentication: SUCCESS" -ForegroundColor Green
} else {
    Write-Host "[FAIL] 2. Authentication Failed" -ForegroundColor Red
    exit 1
}

$headers = @{ "Authorization" = "Bearer $($authResp.token)" }

# 3. Products Endpoint Consistency
$products1 = Invoke-RestMethod -Uri "$baseUrl/products" -Method Get
$products2 = Invoke-RestMethod -Uri "$baseUrl/pos/products" -Method Get

if ($products1.Count -eq $products2.Count -and $products1.Count -ge 7) {
    Write-Host "[PASS] 3. Product Endpoints Parity: $($products1.Count) Products Available" -ForegroundColor Green
} else {
    Write-Host "[FAIL] 3. Product count mismatch between /api/products and /api/pos/products" -ForegroundColor Red
    exit 1
}

# 4. Active Batches Count
$batches = Invoke-RestMethod -Uri "$baseUrl/batches/active" -Method Get
if ($batches.Count -ge 1) {
    Write-Host "[PASS] 4. Container Batches Parity: $($batches.Count) Active 20L Batches" -ForegroundColor Green
} else {
    Write-Host "[FAIL] 4. No Active Batches Found" -ForegroundColor Red
    exit 1
}

# 5. System Configurations
$config = Invoke-RestMethod -Uri "$baseUrl/pricing/config" -Method Get -Headers $headers
if ($config.Count -ge 1) {
    Write-Host "[PASS] 5. System Config Parameters Loaded: $($config.Count) Config Entries" -ForegroundColor Green
} else {
    Write-Host "[FAIL] 5. System Configurations empty" -ForegroundColor Red
    exit 1
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "BACKEND CONNECTION AUDIT PASSED WITH 100% SUCCESS!" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
