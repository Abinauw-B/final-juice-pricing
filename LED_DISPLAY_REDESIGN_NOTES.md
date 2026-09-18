# LED Display Redesign Notes
`customer-web/src/led-display.html`

---

## Card Layout Fix - Dead Whitespace Below Action Buttons
**Date:** 2026-09-17

### Root Cause Diagnosis

Every product card showed dead white/dark space beneath the DETAILS / BUY POS buttons.

**Three-rule interaction:**

| Rule | Element | Effect |
|---|---|---|
| height: 100% | .product-card | Forces card to stretch to full grid row track height |
| grid-template-rows: auto | .product-grid | Row track = tallest card in the row |
| flex-shrink: 0 on ALL sections | .card-top-section, .card-bottom-section | No child fills remaining space |

Result: Raw empty flex space between stats strip and card border with no occupant.

### Fix - Option (a): Mini Recent Settlements Feed

Filled with a mini settlement activity log (last 4 DWMA settlements per product).
Reads from priceHistoryMap - no backend changes.

**New DOM structure per card:**
.card-mid-section (flex:1 1 auto) absorbs all leftover height.
Contains .card-activity-feed showing timestamp, event type (SURGE/DECAY/STABLE), price.

**JS added:** buildActivityFeedHtml(productId) - reads priceHistoryMap, renders last 4 entries.
In-place updater patches activity rows on every WebSocket price push.

### Prior Changes
- Light theme canvas, 22px juice names, 46px prices, 78px chart height
- QR code box: 145x145px (was 70px), API res: 300x300
- Mascot card: flex:1 1 auto fills remaining sidebar height
- Product image wrapper: 82px (was 74px)
- Auto git push daemon: scripts/auto-git-push.ps1, every 5 minutes
