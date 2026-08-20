# Issue #1: Enterprise UI Default Light Theme Configuration

## Description
The enterprise dashboard and customer POS previously initialized in Dark Mode by default. The objective is to configure Light Mode as the primary system default theme across all portals while retaining full toggle capability for Dark Mode.

## Requirements
- Update `admin-panel/src/index.html` initialization script to default to `light` mode.
- Update `customer-web/src/index.html` initialization script to default to `light` mode.
- Update root `index.html` to default to `light` mode.
- Synchronize theme button UI state (`☀️ Theme: Light`).
- Strip legacy contrast storage keys on mode toggle.

## Status
- State: Closed
- Resolution: Resolved in Pull Request #1
- Assigned To: @Abinauw-B
