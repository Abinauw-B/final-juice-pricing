# 🚀 Automatic Git Sync & Push Guide

This project is configured with automated Git synchronization to push your code regularly to GitHub:
**Repository**: [https://github.com/Abinauw-B/final-juice-pricing.git](https://github.com/Abinauw-B/final-juice-pricing.git)

---

## ⚡ Quick Options Summary

You have 4 easy ways to sync your code regularly:

| Method | Script | Description |
|---|---|---|
| **1. Visual Watcher** | [`auto-push-watch.bat`](file:///d:/Juice%20Dynamic%20Price%20Project/auto-push-watch.bat) | Opens a terminal window showing countdown timer, sync status, and commit log. |
| **2. Background Service** | [`start-autopush-background.bat`](file:///d:/Juice%20Dynamic%20Price%20Project/start-autopush-background.bat) | Runs silently in the background without keeping a CMD window open. |
| **3. Windows Auto-Start** | [`enable-startup-autopush.bat`](file:///d:/Juice%20Dynamic%20Price%20Project/enable-startup-autopush.bat) | Automatically starts background sync whenever your PC turns on or you log into Windows. |
| **4. On Every Commit** | `.githooks/post-commit` | Automatically pushes to GitHub immediately whenever you make a commit in VS Code or CLI. |
| **5. Immediate 1-Click Push** | [`push-to-github.bat`](file:///d:/Juice%20Dynamic%20Price%20Project/push-to-github.bat) | Manually stages, commits, and pushes right now. |

---

## 🖥️ Detailed Usage

### Option 1: Live Visual Watcher
- **How to run**: Double-click [`auto-push-watch.bat`](file:///d:/Juice%20Dynamic%20Price%20Project/auto-push-watch.bat) or run in terminal:
  ```cmd
  auto-push-watch.bat
  ```
- **Custom Interval**: Pass minutes as an argument (e.g., check every 5 minutes):
  ```cmd
  auto-push-watch.bat 5
  ```
- **How to stop**: Press `Ctrl + C` in the console window.

---

### Option 2: Silent Background Service
- **Start**: Double-click [`start-autopush-background.bat`](file:///d:/Juice%20Dynamic%20Price%20Project/start-autopush-background.bat)
  - Runs completely hidden without any popup windows.
  - Automatically syncs every 10 minutes (or custom: `start-autopush-background.bat 15`).
- **Stop**: Double-click [`stop-autopush.bat`](file:///d:/Juice%20Dynamic%20Price%20Project/stop-autopush.bat)
- **Check Log**: Open or view [`scripts/.autopush.log`](file:///d:/Juice%20Dynamic%20Price%20Project/scripts/.autopush.log) to see recent sync activity.

---

### Option 3: Enable Auto-Sync on Windows Startup
To ensure you never have to remember to start it:
- **Enable**: Double-click [`enable-startup-autopush.bat`](file:///d:/Juice%20Dynamic%20Price%20Project/enable-startup-autopush.bat)
  - Installs a lightweight launcher shortcut in your Windows user Startup folder.
  - Starts silently whenever you log in.
- **Disable**: Double-click [`disable-startup-autopush.bat`](file:///d:/Juice%20Dynamic%20Price%20Project/disable-startup-autopush.bat)

---

### Option 4: Instant Push on Commit (Git Hook)
- The repository is configured with a Git post-commit hook ([`.githooks/post-commit`](file:///d:/Juice%20Dynamic%20Price%20Project/.githooks/post-commit)).
- Whenever you make a commit in VS Code (Source Control panel) or via `git commit -m "..."`, Git will immediately push that commit to GitHub automatically.

---

## 🛡️ Reliability & Safety Features
- **Conflict Prevention**: Runs `git pull --rebase origin main` before pushing to avoid branch divergences.
- **Auto-Recovery**: Automatically detects and clears any stale Git `.lock` files from previously interrupted processes.
- **Single Instance Guard**: Uses an OS-level Named Mutex (`Local\JuiceDynamicPricingAutoPushMutex`) to prevent duplicate sync loops.
- **Offline Resilient**: If your internet connection drops, the engine logs a notice and safely retries on the next cycle without crashing.
- **Log Management**: [`scripts/.autopush.log`](file:///d:/Juice%20Dynamic%20Price%20Project/scripts/.autopush.log) automatically rotates and trims to prevent excessive file size.
