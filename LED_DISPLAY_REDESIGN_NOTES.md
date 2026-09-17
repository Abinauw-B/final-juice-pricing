# LED TV Billboard Display — Visual Redesign Notes
**Target File**: [`customer-web/src/led-display.html`](file:///d:/Juice%20Dynamic%20Price%20Project/customer-web/src/led-display.html)  
**System**: Noida Pub Exchange & Juice Bar (Wall LED Display / Beverage Ticker)  
**Design Standard**: Bloomberg Terminal / NASDAQ Billboard Style (Light & Dark Modes)

---

## 1. Executive Summary & Goal
The Wall LED TV Billboard Display is displayed on overhead 55"+ venue screens and must be effortlessly legible from 10–15+ feet away in both high-ambient daytime daylight and dimly lit evening pub/lounge lighting.

The objective of this upgrade was a **pure visual, CSS, and motion design overhaul** that elevates the display into a sharp, high-contrast, confident electronic trading board while:
- Preserving 100% of existing JavaScript functions, calculation pipelines, and WebSocket STOMP subscriptions.
- Retaining all dynamic DOM element IDs (`card_price_${id}`, `card_base_${id}`, `card_floor_${id}`, `card_ceiling_${id}`, `card_target_${id}`, `card_demand_label_${id}`, `card_demand_dots_${id}`, `chart_canvas_${id}`, `sidebarAggregatePct`, `qrPosUrlText`, etc.).
- Ensuring seamless runtime stability with zero layout shifts or jitter during real-time 1-minute settlement bursts.

---

## 2. Design Token System Architecture

### Light Mode ("Financial Times / Bloomberg White")
Designed for daytime brightness with maximum contrast against venue glare:
- **Background (`--bg-page`)**: `#EBF2F7` (crisp paper tint, eliminates pure-white glare).
- **Surface (`--bg-surface`)**: `#FFFFFF` (elevated card containers with subtle 1.5px technical borders).
- **Secondary (`--bg-secondary`)**: `#F1F5F9` (high-contrast cockpit chambers and telemetry strips).
- **Primary Text (`--text-primary`)**: `#0A1120` (near-black, WCAG AAA 16.8:1 contrast).
- **Secondary Text (`--text-secondary`)**: `#334155` (slate 700, 9.4:1 contrast).
- **Muted Text (`--text-muted`)**: `#64748B` (slate 500, 5.2:1 contrast).
- **Surge / Bullish (`--accent-emerald`)**: `#008744` (bold London/NASDAQ financial green).
- **Decay / Bearish (`--accent-rose`)**: `#D50000` (saturated deep trading red).
- **Stable / Ticker (`--accent-cyan`)**: `#0284C7` (deep cerulean blue).
- **Market Crash (`--accent-amber`)**: `#D97706` (warning amber).
- **Chart Background (`--chart-bg`)**: `#070C18` (dark cockpit chamber for maximum vector line contrast).

### Dark Mode ("NASDAQ Tower Midnight")
Designed for high-impact lounge and evening visibility with vibrant neon luminescence:
- **Background (`--bg-page`)**: `#030712` (deep obsidian black).
- **Surface (`--bg-surface`)**: `#0B1120` (slate-navy glass container with 1px border highlight).
- **Secondary (`--bg-secondary`)**: `#0F172A` (technical chamber fill).
- **Primary Text (`--text-primary`)**: `#F8FAFC` (pure luminescent white, 19.5:1 contrast).
- **Secondary Text (`--text-secondary`)**: `#CBD5E1` (slate 300).
- **Muted Text (`--text-muted`)**: `#94A3B8` (slate 400).
- **Surge / Bullish (`--accent-emerald`)**: `#00E676` (vibrant neon emerald + `text-shadow: 0 0 16px currentColor`).
- **Decay / Bearish (`--accent-rose`)**: `#FF1744` (electric scarlet rose + `text-shadow: 0 0 16px currentColor`).
- **Stable / Ticker (`--accent-cyan`)**: `#00E5FF` (cyan laser blue).
- **Market Crash (`--accent-amber`)**: `#FFD600` (electric gold).
- **Chart Background (`--chart-bg`)**: `#070C18` (dark technical cockpit chamber).

---

## 3. Visual & Structural Upgrades

### A. Product Card & Price Hierarchy
1. **Monumental Price Metric**:
   - Price bumped to `38px`, `font-weight: 900`, `font-variant-numeric: tabular-nums`, and `letter-spacing: -0.04em`.
   - In Dark Mode, prices glow with `text-shadow: 0 0 16px currentColor`.
   - In Light Mode, crisp micro-drop shadow prevents washing out.
2. **Directional Delta Badges**:
   - Replaced weak, washed-out pills with high-contrast, bordered capsules (`11px font-weight: 900`).
   - Saturated background tint + matching solid border + glowing aura.
3. **Real-Time WebSocket Flash Pulse**:
   - Added keyframe animations `@keyframes pxSurgePulse` and `@keyframes pxDecayPulse`.
   - When WebSocket updates arrive, the target product card pulses with a 1.2-second glowing strobe border and gentle micro-scale lift, giving patrons instant visual feedback on price shifts.

### B. Canvas Price History Chart Chamber
1. **Dedicated Technical Cockpit Chamber**:
   - Both Light and Dark modes now render the chart inside a dedicated dark chamber (`--chart-bg: #070C18`). Vector lines and fluorescent curves pop with maximum clarity.
   - Added an ambient 8-second animated scanline sheen (`::after` with `@keyframes chartScanline`).
2. **Removal of Muddy Canvas Washes**:
   - Removed the heavy 22% radial gradient canvas wash (`ctx.fillRect`) and dynamic inline border recoloring that previously made charts appear cloudy.
   - Refined the area gradient fill to a subtle, professional Bloomberg terminal slope (`0.24 -> 0.08 -> 0.02 -> 0.00`).
   - Floor, Base, and Ceiling reference lines now render with crisp dashed vector strokes and bold price tags (`MIN`, `BASE`, `MAX`).

### C. Unified Single-Line Telemetry Spec Strip
1. **Replaced Bulky 4-Box Spec Grid**:
   - Old 4-box specs and separate demand intensity rows consumed over 50px of vertical space, crowding the price and chart.
   - Replaced with a single-line horizontal telemetry strip:
     `BASE ₹X | FLR ₹Y | CEIL ₹Z | TGT W`
   - Reallocated recovered vertical space directly into the price display and the interactive chart.
2. **Compact Tank & Demand Flow**:
   - Unified the 20L tank level gauge and real-time demand dots into a consolidated row preserving both `card_demand_label_${id}` and `card_demand_dots_${id}`.

### D. Right Sidebar: Market Telemetry & Elevated Hierarchy
1. **Card Elevation**:
   - Upgraded `.sidebar-card` with `1.5px solid var(--border-color)` and deep layered drop shadows (`--shadow-card`).
   - Added hover elevation transitions (`var(--shadow-hover)`).
2. **Market Sentiment Progress Bar**:
   - Bullish, Stable, and Bearish bars now feature glowing neon edges and crisp percentage legends.
3. **Mascot & QR Ordering**:
   - Retained QR box with clean padding and high-contrast styling.
   - Framed mascot card with smooth amber accents.

### E. Electronic Ticker Tape Upgrade
1. **Instrument Badges**:
   - Upgraded `.ticker-tape-badge` from pale pastel colors to the unified design tokens (`--accent-emerald-bg`, `--accent-rose-bg`).
   - Dark mode items glow with neon text shadows.
2. **Smooth Right-to-Left Continuous Tape**:
   - 30-second linear continuous ticker animation with hover-to-pause functionality.

---

## 4. Before vs. After Comparison Matrix

| Component | Before Upgrade | After Upgrade (Bloomberg/NASDAQ) |
| :--- | :--- | :--- |
| **Product Price** | 34px, standard shadow, muted contrast | **38px**, ultra-bold tabular-nums, neon luminescence in dark mode, crisp contrast in light mode |
| **Delta Badges** | Low-saturation pastel pills, small borders | High-visibility directional badges with 1.5px border and matching glow |
| **Price Change Feedback** | Silent (CSS classes were missing) | **High-intensity WebSocket strobe pulse** (`pxSurgePulse` / `pxDecayPulse`) |
| **Chart Container** | Washed out by radial canvas aura and light borders | **Technical dark cockpit chamber** (`#070C18`) with 8s ambient scanline sheen |
| **Specs & Demand** | Bulky 4-box grid + separate demand row taking 50px+ | **Streamlined single-line telemetry strip** + integrated tank/flow gauge |
| **Sidebar Cards** | Flat borders, generic contrast | Elevated 1.5px cards with layered drop shadows and glowing sentiment indicators |
| **Ticker Badges** | Low-contrast pastel green/red | Unified design tokens with high-contrast typography and glowing dark mode auras |
| **Theme Switching** | Abrupt color change | Smooth `0.3s cubic-bezier` crossfade transition on background and text |

---

## 5. DOM & Logic Integrity Verification
All 10 dynamic DOM element IDs and bindings have been verified as fully intact:
- `card_price_${id}`: Confirmed.
- `card_base_${id}`: Confirmed.
- `card_floor_${id}`: Confirmed.
- `card_ceiling_${id}`: Confirmed.
- `card_target_${id}`: Confirmed.
- `card_demand_label_${id}`: Confirmed.
- `card_demand_dots_${id}`: Confirmed.
- `chart_canvas_${id}`: Confirmed.
- `sidebarAggregatePct`: Confirmed.
- `qrPosUrlText`: Confirmed.
All embedded JavaScript scripts passed strict ECMAScript syntax and execution validation with zero errors.
