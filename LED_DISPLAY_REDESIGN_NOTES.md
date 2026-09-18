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

---

## Market Crash Takeover Redesign
**Date:** 2026-09-18  
**Components:**
- LED Display Full-Screen Takeover: `#crashOverlay` & `#minimizedCrashBanner` in [`led-display.html`](file:///d:/Juice%20Dynamic%20Price%20Project/customer-web/src/led-display.html)
- Customer POS Takeover Banner: `#marketCrashBanner` in [`index.html`](file:///d:/Juice%20Dynamic%20Price%20Project/customer-web/src/index.html)

### Design Tokens (Fixed Crash Mode Palette)
```css
:root {
  --crash-bg:           #06080f; /* Deep navy / near-black stadium base */
  --crash-accent-red:   #ef4444; /* Emergency alert vibrant red */
  --crash-accent-gold:  #facc15; /* High-contrast countdown & floor price gold */
  --crash-accent-green: #34d399; /* Live discount percentage badge green */
  --crash-text-primary: #f8fafc; /* Ultra-clear text */
  --crash-text-muted:   #94a3b8; /* Secondary labels & subtitles */
  --crash-card-bg:      rgba(15, 23, 42, 0.95); /* Semi-translucent card surface */
  --crash-card-border:  rgba(239, 68, 68, 0.5);  /* Glowing red perimeter border */
}
```

### Motion & Animation Timing Specifications
| Animation | Element | Timing / Easing | Behavior |
|---|---|---|---|
| `crashFadeIn` / `crashBannerSlideIn` | Overlay / Banner | 450ms–500ms `cubic-bezier(0.16, 1, 0.3, 1)` | Smooth pop-in entrance with 0.97 to 1.0 scale |
| `card-entered` Stagger Cascade | Product Cards / Chips | Staggered `idx * 55ms` (LED), `idx * 40ms` (POS) | Cascades cards in sequence rather than abrupt render |
| `crashGlowBreathe` | Background Radial Glow | 4.0s `ease-in-out infinite` (0.7 to 1.0 opacity) | Subtle breathing background atmosphere |
| `crashBarPulse` | Bar Chart SVG / CSS Bars | 1.9s – 3.1s staggered per bar | Micro-motion reinforcing descending market trend |
| `cautionMove` / `hazardShift` | Caution Tape Edge Borders | 1.2s – 18s linear infinite | Moving hazard warning stripes |
| `timerPillGlow` | Normal Timer State | 2.0s `ease-in-out infinite` | Glowing red perimeter box-shadow |
| `timerPillUrgent` (Final 30s) | Urgent Countdown State | 0.8s `ease-in-out infinite` (1.25 Hz) | Flashes between red & amber; well within safe seizure limits (< 3 Hz) |
| `crashFadeOut` / `crashBannerSlideOut` | Exit Transition | 400ms – 420ms `ease-in` | Graceful fade and scale down before returning to live view |

### Audio Architecture
- **Synthesized Alert Tone:** Uses HTML5 Web Audio API oscillator (`sawtooth` sweep from 480Hz/520Hz up to 960Hz/1040Hz and ramping down over 1.5s–1.6s).
- **Single Play Policy:** Plays exactly once on crash trigger. No endless loops.
- **Operator Replay:** "Replay Pub Siren" / "Replay Siren" buttons on both screens wire directly to `playLedCrashSound()` and `playMarketCrashSiren()`.

### Live Data Binding & Bug 8 Compliance
- **No Hardcoded Prices:** All original prices, crash floor prices, and discount percentages are computed live per product from the active `products` array and `minCupPrice`.
- **Dynamic Floor Pill:** Computes the lowest active floor across products (`Math.min(...minCupPrice)`).
- **Interactive POS Quick-Chips:** POS operators can click any quick-deal chip in `#marketCrashBanner` to immediately add it to the cart at floor rate.

### Accessibility (WCAG / Safe Strobe Standards)
- **ARIA Live Regions:** Screen readers receive targeted priority announcements at trigger, at 60s remaining, at 30s remaining, and upon crash completion.
- **Photosensitive Safety:** Strobe overlay fires only 1 single brief pulse (800ms). Timer urgency pulse runs at 1.25Hz (maximum threshold allowed is 3Hz).

