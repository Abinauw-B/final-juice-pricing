$json = '{"intervalSeconds":30}'
$res = Invoke-RestMethod -Uri "http://localhost:8088/api/pricing/timing" -Method Put -ContentType "application/json" -Body $json
Write-Host "Timing result:" ($res | ConvertTo-Json)
