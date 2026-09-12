<#
.SYNOPSIS
    Automated Git Sync Engine for Juice Dynamic Pricing System.
.DESCRIPTION
    Periodically checks for modified, added, or deleted files, stages them,
    commits them with a timestamp, pulls remote updates with rebase, and pushes to GitHub.
.PARAMETER IntervalMinutes
    Number of minutes between sync cycles (default: 10).
.PARAMETER SingleRun
    If set, executes a single sync check and exits immediately.
.PARAMETER Branch
    Branch to push to (default: auto-detected or 'main').
.PARAMETER Remote
    Git remote name (default: 'origin').
.PARAMETER Quiet
    Suppress console messages (useful for background runners).
#>
param(
    [int]$IntervalMinutes = 10,
    [switch]$SingleRun,
    [string]$Branch = "",
    [string]$Remote = "origin",
    [switch]$Quiet
)

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $RepoRoot

$LogFile = Join-Path $PSScriptRoot ".autopush.log"

function Write-Log {
    param(
        [string]$Message,
        [string]$Level = "INFO"
    )
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $formattedMsg = "[$timestamp] [$Level] $Message"

    if (-not $Quiet) {
        $color = switch ($Level) {
            "SUCCESS" { "Green" }
            "WARN"    { "Yellow" }
            "ERROR"   { "Red" }
            "SYNC"    { "Cyan" }
            default   { "White" }
        }
        Write-Host $formattedMsg -ForegroundColor $color
    }

    # Append to log file and keep log trimmed
    try {
        Add-Content -Path $LogFile -Value $formattedMsg -ErrorAction SilentlyContinue
        if ((Test-Path $LogFile) -and ((Get-Item $LogFile).Length -gt 200KB)) {
            $recent = Get-Content -Path $LogFile -Tail 300 -ErrorAction SilentlyContinue
            Set-Content -Path $LogFile -Value $recent -ErrorAction SilentlyContinue
        }
    } catch {}
}

function Clear-StaleLocks {
    try {
        $gitDir = Join-Path $RepoRoot ".git"
        if (Test-Path $gitDir) {
            $locks = Get-ChildItem -Path $gitDir -Filter "*.lock" -Recurse -Force -ErrorAction SilentlyContinue
            foreach ($l in $locks) {
                if ((Get-Date) - $l.LastWriteTime -gt (New-TimeSpan -Seconds 45)) {
                    Remove-Item -Path $l.FullName -Force -ErrorAction SilentlyContinue
                    Write-Log "Cleared stale git lock file: $($l.Name)" "WARN"
                }
            }
        }
    } catch {}
}

function Invoke-GitSync {
    Clear-StaleLocks

    # 1. Determine active branch
    $currentBranch = (git rev-parse --abbrev-ref HEAD 2>$null).Trim()
    if (-not $currentBranch -or $currentBranch -eq "HEAD") {
        $currentBranch = if ($Branch) { $Branch } else { "main" }
    }

    # 2. Check for local modifications (untracked, modified, deleted)
    $statusOutput = (git status --porcelain 2>$null)
    $hasUncommitted = ($statusOutput | Where-Object { $_ -match '\S' } | Measure-Object).Count -gt 0

    # 3. Check for unpushed commits against remote
    $unpushed = (git log "$Remote/$currentBranch..HEAD" --oneline 2>$null)
    $hasUnpushed = ($unpushed | Where-Object { $_ -match '\S' } | Measure-Object).Count -gt 0

    if (-not $hasUncommitted -and -not $hasUnpushed) {
        Write-Log "Working tree clean & fully synchronized with $Remote/$currentBranch. No push needed." "INFO"
        return $true
    }

    $changeSummary = @()
    if ($hasUncommitted) { $changeSummary += "$((($statusOutput | Where-Object { $_ -match '\S' }).Count)) uncommitted changes" }
    if ($hasUnpushed) { $changeSummary += "$((($unpushed | Where-Object { $_ -match '\S' }).Count)) unpushed commits" }
    Write-Log "Changes detected ($($changeSummary -join ', ')). Starting auto-push sequence..." "SYNC"

    # 4. Stage and commit uncommitted changes first so working tree is clean
    if ($hasUncommitted) {
        Write-Log "Staging modified files (git add .)..." "INFO"
        git add .

        $nowStr = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        $commitMessage = "auto: periodic sync $nowStr"

        # Verify there are staged changes
        $stagedChanges = (git status --porcelain 2>$null) | Where-Object { $_ -match '^[MADRCU]' }
        if ($stagedChanges) {
            Write-Log "Creating commit: '$commitMessage'..." "INFO"
            $commitOutput = git commit -m "$commitMessage" 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-Log "Commit failed: $commitOutput" "ERROR"
                return $false
            }
        }
    }

    # 5. Safe pull with rebase
    Write-Log "Checking for remote updates from $Remote/$currentBranch (git pull --rebase)..." "INFO"
    $pullOutput = git pull --rebase $Remote $currentBranch 2>&1
    $pullExit = $LASTEXITCODE

    if ($pullExit -ne 0) {
        # Check if a rebase conflict stopped midway
        if ((Test-Path "$RepoRoot\.git\rebase-merge") -or (Test-Path "$RepoRoot\.git\rebase-apply")) {
            git rebase --abort 2>$null
            Write-Log "Rebase conflict detected! Aborted rebase to safeguard your files. Manual merge may be required." "ERROR"
            return $false
        }
        Write-Log "Pull encountered notice: $pullOutput" "WARN"
    }

    # 6. Push to remote
    Write-Log "Pushing commits to $Remote/$currentBranch..." "INFO"
    $pushOutput = git push $Remote $currentBranch 2>&1
    if ($LASTEXITCODE -eq 0) {
        $latest = (git log -1 --oneline 2>$null).Trim()
        Write-Log "SUCCESS: Code pushed to $Remote/$currentBranch! ($latest)" "SUCCESS"
        return $true
    } else {
        Write-Log "Push failed (network or credentials issue). Will retry next cycle: $pushOutput" "ERROR"
        return $false
    }
}

# Main Execution Loop / Single Run
Write-Log "================================================================" "INFO"
Write-Log ">>> JUICE DYNAMIC PRICING - AUTOMATED GIT SYNC ENGINE" "INFO"
Write-Log "Repository: $RepoRoot" "INFO"
Write-Log "Interval: Every $IntervalMinutes minute(s) | Target: $Remote" "INFO"
Write-Log "================================================================" "INFO"

if ($SingleRun) {
    $success = Invoke-GitSync
    if ($success) { exit 0 } else { exit 1 }
}

# Ensure only ONE background/loop watcher runs at a time using a named Mutex
$mutexName = "Local\JuiceDynamicPricingAutoPushMutex"
$createdNew = $false
try {
    $script:AppMutex = New-Object System.Threading.Mutex($true, $mutexName, [ref]$createdNew)
    if (-not $createdNew) {
        Write-Log "Another auto-push watcher instance is already running. Exiting." "WARN"
        exit 0
    }
} catch {
    # Fallback if mutex cannot be created
}

# Continuous Loop Mode
$intervalSeconds = [Math]::Max(30, $IntervalMinutes * 60)

while ($true) {
    try {
        $null = Invoke-GitSync
    } catch {
        Write-Log "Unexpected error during sync cycle: $_" "ERROR"
    }

    $nextRun = (Get-Date).AddSeconds($intervalSeconds).ToString("HH:mm:ss")
    Write-Log "Sleeping for $IntervalMinutes min. Next check at $nextRun (Press Ctrl+C to stop)..." "INFO"

    # Sleep in small increments of 1 second so interruption is instant
    $remaining = $intervalSeconds
    while ($remaining -gt 0) {
        Start-Sleep -Seconds 1
        $remaining--
    }
}
