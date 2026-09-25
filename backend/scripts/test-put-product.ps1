# 1. Test WITHOUT token
try {
    $body = '{"name":"Fresh Mango Juice","currentCupPrice":26.00,"defaultCupPrice":25.00,"minCupPrice":20.00,"maxCupPrice":30.00}'
    $res = Invoke-RestMethod -Uri "http://localhost:8088/api/pos/products/1" -Method Put -ContentType "application/json" -Body $body
    Write-Host "Without token: SUCCESS!"
} catch {
    Write-Host "Without token: FAILED - " $_.Exception.Message
}

# 2. Test WITH token
try {
    $loginBody = '{"username":"superadmin","password":"admin123"}'
    $auth = Invoke-RestMethod -Uri "http://localhost:8088/api/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
    $token = $auth.token

    $headers = @{ "Authorization" = "Bearer $token" }
    $res2 = Invoke-RestMethod -Uri "http://localhost:8088/api/pos/products/1" -Method Put -Headers $headers -ContentType "application/json" -Body $body
    Write-Host "With token: SUCCESS! New price:" $res2.currentCupPrice
} catch {
    Write-Host "With token: FAILED - " $_.Exception.Message
}
