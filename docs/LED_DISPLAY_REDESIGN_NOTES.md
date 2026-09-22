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

---

## Crash Panel — Bug Fix & Consistency Pass
**Date:** 2026-09-19

### 1. Bug Fix: Top & Bottom Hazard-Stripe Marquee Bars (Zero Overlap, 100% Readable)
- **Root Cause Diagnosis:**
  1. *Stripe Invisibility / Dropout:* Text was originally rendered directly atop repeating yellow-and-black diagonal hazard stripes (`repeating-linear-gradient(-45deg, #EAB308 0px, #EAB308 16px, #000000 16px, #000000 32px)`). Characters landing on black stripes became completely swallowed/invisible, leaving only fragmented letters visible on yellow stripes (producing the garbled "ENU ARK CRA IN GRE A BEV AGE CES ABS UTE OR UT 00 ORD T P NOV" appearance).
  2. *Stream Collision:* Marquee items lacked a structured chassis and seamless cloning, resulting in clipping at different viewport widths.
- **Resolution Architecture:**
  - **Chassis Isolation Pattern:** Maintained the industrial hazard stripes on the outer container (`.crash-hazard-bar.crash-hazard-top` and `.crash-hazard-bottom`, 40px height) to reinforce emergency visual theme, while embedding an inner chassis (`.crash-hazard-chassis`) with deep obsidian background (`rgba(6, 10, 20, 0.96)`) and crisp gold borders (`1.5px solid #FACC15`). This leaves the caution stripes clearly visible along the top and bottom 4px rims while guaranteeing 100% solid, high-contrast, razor-sharp backing for all text.
  - **Dual-Stream Infinite Looping:** Built the track (`.hazard-marquee-track`) with two cloned, identically formatted message streams (`.hazard-marquee-stream`), scrolling seamlessly left-to-right via `transform: translateX(-50%)` over 42 seconds.
  - **Clean Message Separators:** Each message is separated by distinct category badges (`.hp-red`, `.hp-amber`, `.hp-emerald`, `.hp-cyan`) with inline SVG icons and gold star separators (`✦`), preventing any possibility of word overlap or collisions across 1080p, 1440p, and 4K displays.

### 2. Bug Fix: Product Cards Overflow & Text Truncation Resolved
- **Root Cause Diagnosis:**
  1. *Right-Edge Clipping:* The product container lacked flexible minimum widths, causing the 8-card grid to exceed screen boundaries on certain 16:9 viewports.
  2. *Ellipsis Name Truncation:* Single-line `white-space: nowrap; text-overflow: ellipsis;` forced beverage names ("VALENCIA ORANGE SPECIAL", "COOL MINT COOLER", "FRESH MANGO JUICE") to cut off unreadably.
- **Resolution Architecture:**
  - **Responsive Grid:** Configured `.crash-juice-matrix` with `grid-template-columns: repeat(4, minmax(0, 1fr))` and a responsive gap (`clamp(8px, 0.85vw, 12px)`). Pinned the central container `.crash-box` to `max-width: min(1680px, 97vw); width: 97vw;`, ensuring all 8 cards fit within the viewport without horizontal overflow.
  - **Two-Line Graceful Wrapping:** Updated `.cjc-name` to `display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; min-height: 2.4em; white-space: normal; line-height: 1.2;`. Longer beverage titles now wrap smoothly across 2 lines with complete, untruncated text at all standard resolutions (1080p, 1440p, 4K).

### 3. Tone & Quality Realignment (Circuit Breaker Design Standard)
- **SVG Icon Uniformity:** Replaced all decorative OS-dependent emojis (`🚨`, `⚡`, `💥`, `🛡️`, `🍹`, `👁️`, `📱`, `🔥`) with bespoke inline SVG vector icons (emergency beacon sirens, algorithmic lightning bolts, precision shields, tap gauges, terminal eye, and POS QR glyphs).
- **Countdown Focal Dominance:** Scaled countdown digits to `clamp(56px, 6.2vw, 84px)`—more than double the size of the headline title—ensuring the time-critical countdown is the undisputed single largest element on screen.
- **Telemetry & Live Data Strip:** Preserved 4 high-contrast live metric tiles (Max Venue Discount, Floor Lock Price, Pricing Mode, and Floor Availability with real-time active tap count `products.filter(p => !p.soldOut && (p.remainingVolumeMl === undefined || p.remainingVolumeMl > 0)).length`).
- **Full Card Detail Model:** Maintained per-card tap indicators, avatar boxes with fruit theme gradients, strikethrough base prices with green savings tags (`SAVE ₹X / CUP`), floor lock tags, live tap stock liters & percentage bars with liquid shimmer, and hover-reactive claim buttons.

---

## Radiant Crash Theme (Full Visual Reimagining)
**Date:** 2026-09-22

### Overview
A comprehensive visual and motion reimagining of the **Market Crash Takeover Screen** on the Wall LED TV Display (`customer-web/src/led-display.html`). Transforms the previous monochrome dark red panel into a radiant, multi-color gradient theme blending a high-stakes Bloomberg terminal with a neon juice bar at night.

### Color & Motion Architecture
1. **Shifting Multi-Color Radial Mesh Background (`.crash-overlay`)**
   - **Deep Base Gradient:** `radial-gradient(ellipse at 50% 15%, rgba(225, 29, 72, 0.38) 0%, rgba(124, 58, 237, 0.28) 45%, rgba(11, 15, 25, 0.97) 85%)`
   - **Living Atmosphere:** Shifting multi-layer mesh (`@keyframes meshShift`) animating scale and rotation over 20s with `backdrop-filter: blur(20px)`.

2. **Per-Beverage Fruit Identity Tokens (8 Distinct Themes)**
   - **Fresh Mango Juice (🥭):** Gold-Orange gradient (`#FF6B00` → `#FFD600`), amber border, orange glow.
   - **Zesty Lemon Juice (🍋):** Yellow-Lime gradient (`#EAB308` → `#84CC16`), yellow border, lime glow.
   - **Cool Mint Cooler (🌿):** Emerald-Teal gradient (`#059669` → `#06B6D4`), mint border, emerald glow.
   - **Valencia Orange Juice (🍊):** Deep Orange gradient (`#EA580C` → `#FB923C`), citrus border, orange glow.
   - **Strawberry Delight (🍓):** Crimson-Pink gradient (`#E11D48` → `#FB7185`), strawberry border, rose glow.
   - **Royal Grape Juice (🍇):** Violet-Magenta gradient (`#7C3AED` → `#C084FC`), purple border, violet glow.
   - **Lychee Mist (🌸):** Soft Pink gradient (`#DB2777` → `#F472B6`), rose border, pink glow.
   - **Thunder Power (⚡):** Electric Blue gradient (`#0284C7` → `#38BDF8`), cyan border, electric blue glow.

3. **Countdown Halo Ring Anchor (`#crashTimerChamber`)**
   - **Circular SVG Depletion Ring:** `stroke: url(#ringGrad)` with `stroke-dashoffset` dynamically depleting in real-time synced to `crashRemainingSeconds / crashTotalDuration`.
   - **3-Stage Urgency Glow:**
     - Calm Gold (>60s)
     - Warning Orange (30s-60s)
     - Critical Pulse Red/Magenta (<30s)

4. **Zero-Regression Data & Event Bindings**
   - Preserved 100% of DOM IDs (`crashOverlay`, `crashTimerChamber`, `crashTimerText`, `crashTimerMins`, `crashTimerSecs`, `crashProgressBar`, `crashMaxDiscountTelem`, `crashFloorLockVal`, `crashActiveTapsVal`, `crashJuiceMatrix`, `pubSirenBtn`).
   - Retained all WebSocket subscriptions (`/topic/market-crash`, `/topic/orders`), audio siren debouncing, and POS QR claim handlers.
