$json = '{"username":"superadmin","password":"admin123"}'
$auth = Invoke-RestMethod -Uri "http://localhost:8088/api/auth/login" -Method Post -ContentType "application/json" -Body $json
$token = $auth.token

$headers = @{
    "Authorization" = "Bearer $token"
    "X-User-Role" = "SUPER_ADMIN"
}

$resetRes = Invoke-RestMethod -Uri "http://localhost:8088/api/pricing/reset-all" -Method Post -Headers $headers -ContentType "application/json"
Write-Host "Reset result:" ($resetRes | ConvertTo-Json -Depth 3)
