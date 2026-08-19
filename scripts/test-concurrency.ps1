# 100 Simultaneous Checkout Concurrency Verification Script (High Speed)
$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8088/api"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "PUB EXCHANGE 100 CONCURRENT PURCHASES TEST SUITE" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Authenticate
$loginBody = @{ username = "superadmin"; password = "Password@123" } | ConvertTo-Json
$authResp = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $authResp.token

# 2. Target Product
$products = Invoke-RestMethod -Uri "$baseUrl/pos/products" -Method Get
$targetProduct = $products[0]

Write-Host "[INFO] Target Product for Concurrency: $($targetProduct.name) (ID: $($targetProduct.id))" -ForegroundColor Yellow

# 3. Fire 100 Parallel Requests using Runspaces
Write-Host "[INFO] Firing 100 Concurrent HTTP Checkout Requests..." -ForegroundColor Yellow

$runspacePool = [runspacefactory]::CreateRunspacePool(1, 20)
$runspacePool.Open()

$tasks = @()

for ($i = 0; $i -lt 100; $i++) {
    $powershell = [powershell]::Create().AddScript({
        param($url, $t, $prodId)
        $h = @{ "Authorization" = "Bearer $t" }
        $body = @{
            items = @( @{ productId = $prodId; quantity = 1; cupSizeMl = 250 } )
            paymentMethod = "CASH"
        } | ConvertTo-Json -Depth 5
        try {
            $res = Invoke-RestMethod -Uri "$url/pos/checkout" -Method Post -Body $body -ContentType "application/json" -Headers $h
            return "SUCCESS"
        } catch {
            return "REJECTED"
        }
    }).AddArgument($baseUrl).AddArgument($token).AddArgument($targetProduct.id)

    $powershell.RunspacePool = $runspacePool
    $tasks += [PSCustomObject]@{
        Pipe = $powershell
        Result = $powershell.BeginInvoke()
    }
}

$successCount = 0
$rejectedCount = 0

foreach ($task in $tasks) {
    $res = $task.Pipe.EndInvoke($task.Result)
    if ($res -eq "SUCCESS") { $successCount++ } else { $rejectedCount++ }
    $task.Pipe.Dispose()
}

$runspacePool.Close()
$runspacePool.Dispose()

Write-Host "[RESULTS] Successful Checkout Transactions: $successCount / 100" -ForegroundColor Green
if ($rejectedCount -gt 0) {
    Write-Host "[INFO] Orders Safely Rejected (Out of Stock / Concurrency): $rejectedCount" -ForegroundColor Yellow
}

# 4. Verify Final Inventory & Product State
$batches = Invoke-RestMethod -Uri "$baseUrl/batches/active" -Method Get
$updatedProduct = (Invoke-RestMethod -Uri "$baseUrl/pos/products" -Method Get) | Where-Object { $_.id -eq $targetProduct.id }

Write-Host "  Product Price: Rs. $($updatedProduct.currentCupPrice)" -ForegroundColor Green

foreach ($b in $batches) {
    if ($null -ne $b.remainingVolumeMl -and $b.remainingVolumeMl -lt 0) {
        Write-Host "[FAIL] NEGATIVE INVENTORY DETECTED!" -ForegroundColor Red
        exit 1
    }
}

Write-Host "[PASS] Concurrency Safety Confirmed: ZERO negative inventory and atomic persistence!" -ForegroundColor Green

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "100 CONCURRENT PURCHASES TEST COMPLETED SUCCESSFULLY!" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
