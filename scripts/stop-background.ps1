$pidFile = Join-Path $PSScriptRoot ".autopush.pid"
$stoppedAny = $false

if (Test-Path $pidFile) {
    $targetPid = (Get-Content $pidFile -ErrorAction SilentlyContinue).Trim()
    if ($targetPid) {
        try {
            $p = Get-Process -Id $targetPid -ErrorAction SilentlyContinue
            if ($p) {
                Stop-Process -Id $targetPid -Force -ErrorAction SilentlyContinue
                Write-Host "[SUCCESS] Stopped auto-push background process (PID: $targetPid)" -ForegroundColor Green
                $stoppedAny = $true
            }
        } catch {}
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

$allProcs = Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like "*auto-git-push.ps1*" }
foreach ($p in $allProcs) {
    try {
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
        Write-Host "[SUCCESS] Stopped background auto-push process (PID: $($p.ProcessId))" -ForegroundColor Green
        $stoppedAny = $true
    } catch {}
}

if (-not $stoppedAny) {
    Write-Host "[INFO] No active auto-push background service found." -ForegroundColor Yellow
}
