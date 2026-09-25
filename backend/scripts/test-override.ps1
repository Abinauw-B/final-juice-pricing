$loginBody = '{"username":"superadmin","password":"admin123"}'
$auth = Invoke-RestMethod -Uri "http://localhost:8088/api/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
$token = $auth.token
$headers = @{ "Authorization" = "Bearer $token" }

# Override product 1 price to 28.00
$res = Invoke-RestMethod -Uri "http://localhost:8088/api/pricing/products/1/price?newPrice=28.00&reason=MANUAL_ADMIN_OVERRIDE" -Method Post -Headers $headers
Write-Host "Override result:" ($res | ConvertTo-Json)

# Check POS products
$prods = Invoke-RestMethod -Uri "http://localhost:8088/api/pos/products"
$p1 = $prods | Where-Object { $_.id -eq 1 }
Write-Host "Product 1 after override:" ($p1 | ConvertTo-Json)
