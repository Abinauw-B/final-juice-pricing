$json = '{"username":"superadmin","password":"admin123"}'
$res = Invoke-RestMethod -Uri "http://localhost:8088/api/auth/login" -Method Post -ContentType "application/json" -Body $json
Write-Host "SUCCESS! Token is:" $res.token
