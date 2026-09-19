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

## Circuit Breaker Crash Panel — Polish Pass
**Date:** 2026-09-19

### Overview
Visual, motion, layout, and live-data refinement pass on the full-screen "Circuit Breaker" Market Crash Takeover UI (`#crashOverlay`) on the Wall LED TV Display (`customer-web/src/led-display.html`). Zero breaking changes to WebSocket topics, REST endpoints, DOM IDs, or backend services.

### Key Changes & Polish Enhancements

1. **Fixed Ticker Overlap Bug (Bottom Liquidation Stream)**
   - **Root Cause:** Pinned red badge and scrolling text were competing within the same unmasked horizontal flow container.
   - **Fix:** Architected `.crash-marquee-wrap` with `.crash-marquee-badge` pinned on the left (`flex-shrink: 0; z-index: 10;`) and `.crash-marquee-track` taking the remaining flex width with `overflow: hidden; mask-image: linear-gradient(...)`.
   - **Visual Typography:** Categorized message pills (`.cm-pill-red`, `.cm-pill-amber`, `.cm-pill-emerald`, `.cm-pill-cyan`) for "CIRCUIT BREAKER", "DWMA HALT", "ZERO SLIPPAGE", and "AUTO RESUME" with 24px consistent spacing. Set slow, readable 36s marquee scroll.

2. **Commanding Focal Countdown Chamber**
   - **Enlarged Digits:** Scaled up to `clamp(46px, 4.6vw, 64px)` in monospace tabular-nums with glowing crimson colon blink.
   - **3-Tier Intelligent Atmosphere Glow:**
     - Calm Amber Glow (>60s remaining) via `chamberCalmPulse` (4s smooth cycle).
     - Warning Pulse (30s–60s) via `chamberWarningPulse` (1.2s alternating amber/red aura).
     - Urgent Alarm (<30s) via `chamberUrgentPulse` (0.5s intense 75px red bloom).
   - **Seconds Digit Tick Pulse:** Triggered `.timer-sec-pulse` with `@keyframes secPulse` on every decrement for immediate tactile feedback.
   - **Synchronized Depleting Progress Bar:** Thicker 7px track with rounded gradient fill and glowing right-edge bead (`.timer-progress-fill::after`) driven by real `crashRemainingSeconds / crashTotalDuration`.

3. **High-Contrast 4-Stat Telemetry Bar**
   - **Contrast Hierarchy:** Increased font size and contrast between muted small-caps labels (`#94A3B8`) and glowing bold metric values (`#F8FAFC`).
   - **Tailored Accent Tokens:**
     - Max Venue Discount: Emerald `#10B981` (up to `-43%` computed dynamically).
     - Floor Lock Price: Gold `#F59E0B` (`₹20.00 Flat Cap` or payload `crashPrice`).
     - Pricing Mode: Cyan `#06B6D4` (`DWMA Freeze Active`).
     - Floor Availability: Purple `#A855F7` with continuous ambient shimmer (`.telem-shimmer`).
   - **Dynamic Tap Counter:** Wired to real active batch volume `products.filter(p => !p.soldOut && (p.remainingVolumeMl === undefined || p.remainingVolumeMl > 0)).length`.

4. **Dynamic Product Showcase Matrix**
   - **Dynamically Computed Discount Badges:** Discount percentage is calculated strictly per-product using `Math.round(((baseP - floorP) / baseP) * 100)`. Displays accurate `-20%`, `-33%`, or `-43%` depending on each beverage's true base and floor prices.
   - **Continuous Tap Stock Liquid Shimmer:** Added `@keyframes liquidShimmer` horizontal light sweep over `.cjc-tank-fill` bars so stock readings convey real-time active telemetry.
   - **Cascading Entrance Stagger:** Each card animates via `cardEntranceStagger` with `animation-delay: calc(var(--card-index) * 50ms)`.
   - **Tactile Claim Button:** Upgraded `.cjc-claim-btn` with micro-interactions: scale `1.05`, brightness `1.15` on hover, and active compression scale `0.96`.
   - **Real-Time Order Claim Ring:** Subscribed to `/topic/orders` WebSocket broadcast and local `BroadcastChannel`. Matching ordered beverage cards immediately pulse with `.card-order-claimed-pulse` gold shockwave ring and display an order claim toast.

5. **Footer Actions & Siren Debounce**
   - Cohesive 3-column grid (`.crash-actions-row`) aligned with the cards above.
   - **Siren Debounce:** Guarded `playLedCrashSound()` with `_sirenPlaying` boolean and `pointer-events: none` for 1600ms to eliminate overlapping audio distortion from rapid clicks.

6. **Trading Floor Atmosphere & Background Grid**
   - **Drifting Candlestick / HUD Grid:** Added subtle 32x32px isometric technical grid background drift via `@keyframes gridDrift` with low opacity.
   - **Outer Border Aura:** Replaced erratic flickers with deliberate, majestic 5-second amber/red breathing glow (`@keyframes crashAuraSlow`).

