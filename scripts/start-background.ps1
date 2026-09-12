param(
    [int]$IntervalMinutes = 10
)

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$pidFile = Join-Path $PSScriptRoot ".autopush.pid"
$scriptPath = Join-Path $PSScriptRoot "auto-git-push.ps1"

# Check if already running
if (Test-Path $pidFile) {
    $existingPid = (Get-Content $pidFile -ErrorAction SilentlyContinue).Trim()
    if ($existingPid) {
        $proc = Get-Process -Id $existingPid -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "================================================================" -ForegroundColor Yellow
            Write-Host "ℹ️ Auto-push is ALREADY running in background (PID: $existingPid)" -ForegroundColor Yellow
            Write-Host "   Interval      : Every $IntervalMinutes minute(s)" -ForegroundColor Yellow
            Write-Host "   Log File      : scripts\.autopush.log" -ForegroundColor Yellow
            Write-Host "   To stop it run: stop-autopush.bat" -ForegroundColor Yellow
            Write-Host "================================================================" -ForegroundColor Yellow
            return
        }
    }
}

$argString = "-NoLogo -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$scriptPath`" -IntervalMinutes $IntervalMinutes -Quiet"
$proc = Start-Process powershell.exe -ArgumentList $argString -WorkingDirectory $RepoRoot -PassThru

# Give process a moment to initialize
Start-Sleep -Milliseconds 700

if ($proc -and -not $proc.HasExited) {
    $proc.Id | Out-File -FilePath $pidFile -Encoding ascii -Force
    Write-Host "================================================================" -ForegroundColor Cyan
    Write-Host "✅ Auto-push background service started successfully!" -ForegroundColor Green
    Write-Host "   Process ID (PID)  : $($proc.Id)" -ForegroundColor Cyan
    Write-Host "   Sync Interval     : Every $IntervalMinutes minute(s)" -ForegroundColor Cyan
    Write-Host "   Repository        : $RepoRoot" -ForegroundColor Cyan
    Write-Host "   Live Log File     : scripts\.autopush.log" -ForegroundColor Cyan
    Write-Host "   To stop it run    : stop-autopush.bat" -ForegroundColor Yellow
    Write-Host "================================================================" -ForegroundColor Cyan
} else {
    Write-Host "❌ Failed to start background auto-push service or process exited immediately." -ForegroundColor Red
}
