# NOIDA PUB EXCHANGE — DESIGN SYSTEM REFERENCE

> **Version:** 1.0  
> **Date:** September 2026  
> **Token File:** `pub-exchange-tokens.css` (hosted in both `customer-web/src/` and `admin-panel/src/`)

---

## Architecture

The design system uses a **single shared CSS token file** (`pub-exchange-tokens.css`) duplicated into both serving directories:
- `customer-web/src/pub-exchange-tokens.css` → served on port 8000
- `admin-panel/src/pub-exchange-tokens.css` → served on port 8001

All 4 frontends link this file via `<link rel="stylesheet">` before their local `<style>` blocks. App-specific overrides cascade naturally.

> **Important:** When editing tokens, update BOTH copies. They must stay identical.

---

## Color Palette

### Dark Theme (Default)

| Token | Value | Usage |
|:---|:---|:---|
| `--bg-page` | `#080B11` | Root page background |
| `--bg-surface` | `rgba(13, 18, 29, 0.88)` | Cards, panels, sidebar |
| `--bg-card` | `rgba(15, 22, 35, 0.78)` | Product cards, data cards |
| `--bg-elevated` | `rgba(26, 36, 56, 0.85)` | Modals, dropdowns |
| `--bg-glass` | `rgba(16, 22, 34, 0.72)` | Glassmorphism panels |
| `--text-primary` | `#F8FAFC` | Headings, body text |
| `--text-secondary` | `#CBD5E1` | Subtext, descriptions |
| `--text-muted` | `#8E9EB5` | Labels, hints |
| `--accent-primary` | `#38BDF8` | Primary actions, links |
| `--color-gain` | `#10B981` | **Market gain — ALWAYS green** |
| `--color-loss` | `#F43F5E` | **Market loss — ALWAYS red** |
| `--accent-amber` | `#F59E0B` | Warnings, caution states |
| `--accent-purple` | `#C084FC` | Secondary accent |
| `--accent-orange` | `#FF6B00` | Brand highlight, crash |

### Light Theme

| Token | Dark → Light | Value |
|:---|:---|:---|
| `--bg-page` | `#080B11` → | `#d5e7f0` |
| `--bg-surface` | dark rgba → | `rgba(255, 255, 255, 0.92)` |
| `--text-primary` | `#F8FAFC` → | `#0F172A` |
| `--accent-primary` | `#38BDF8` → | `#0284C7` |
| `--color-gain` | `#10B981` → | `#059669` |
| `--color-loss` | `#F43F5E` → | `#E11D48` |

### Semantic Status

| Token | Dark | Light | Usage |
|:---|:---|:---|:---|
| `--success` | `#10B981` | `#059669` | Success toasts, valid states |
| `--danger` | `#F43F5E` | `#E11D48` | Errors, destructive actions |
| `--warning` | `#F59E0B` | `#D97706` | Caution, low stock |
| `--info` | `#38BDF8` | `#0284C7` | Informational toasts |

---

## Typography

| Token | Size | Usage |
|:---|:---|:---|
| `--text-xs` | 11px | Micro labels, pills, badges |
| `--text-sm` | 13px | Body text, table cells |
| `--text-base` | 14px | Default body |
| `--text-md` | 15px | Emphasized body |
| `--text-lg` | 17px | Section headers |
| `--text-xl` | 20px | Card titles |
| `--text-2xl` | 24px | Page section titles |
| `--text-3xl` | 28px | Display headings |
| `--text-4xl` | 36px | Hero / LED display prices |

**Fonts:**
- **UI:** `Plus Jakarta Sans` (weights 300–900)
- **Monospace / Numbers:** `JetBrains Mono` (weights 400–800, `font-variant-numeric: tabular-nums`)

---

## Spacing Scale

| Token | Value | Usage |
|:---|:---|:---|
| `--space-1` | 4px | Inline gaps |
| `--space-2` | 8px | Tight element spacing |
| `--space-3` | 12px | Card internal padding |
| `--space-4` | 16px | Standard padding |
| `--space-6` | 24px | Section padding |
| `--space-8` | 32px | Large section gaps |
| `--space-12` | 48px | Empty state padding |

---

## Border Radius

| Token | Value | Usage |
|:---|:---|:---|
| `--radius-xs` | 4px | Small inputs, tags |
| `--radius-sm` | 6px | Buttons, badges |
| `--radius-md` | 8px | Standard controls |
| `--radius-lg` | 12px | Cards, toasts |
| `--radius-xl` | 16px | Large cards |
| `--radius-card` | 14px | Product cards |
| `--radius-pill` | 9999px | Status pills |

---

## Shadows & Elevation

| Token | Usage |
|:---|:---|
| `--shadow-xs` | Subtle depth for small elements |
| `--shadow-sm` | Inputs, small buttons |
| `--shadow-md` | Dropdowns, hover cards |
| `--shadow-lg` | Toasts, popovers |
| `--shadow-xl` | Modals |
| `--shadow-glow-gain` | Green glow for price surges |
| `--shadow-glow-loss` | Red glow for price decays |
| `--elevation-1` | Glass panel with inset highlight |
| `--elevation-2` | Floating panel |

---

## Motion & Easing

| Token | Value | Usage |
|:---|:---|:---|
| `--ease-out` | `cubic-bezier(0.16, 1, 0.3, 1)` | Standard transitions |
| `--ease-spring` | `cubic-bezier(0.34, 1.56, 0.64, 1)` | Bouncy micro-interactions |
| `--ease-bounce` | `cubic-bezier(0.68, -0.55, 0.265, 1.55)` | Playful button press |
| `--duration-fast` | 150ms | Hover states, small changes |
| `--duration-normal` | 250ms | Modal transitions |
| `--duration-slow` | 400ms | Staggered entrance |
| `--duration-slower` | 600ms | Complex transitions |

---

## Shared Keyframe Animations

| Animation | Duration | Usage |
|:---|:---|:---|
| `pxModalFadeIn/Out` | 250ms | Modal backdrop fade |
| `pxModalScaleIn/Out` | 400ms (spring) | Modal content entrance |
| `pxPriceSurge` | 1.2s | Green flash on price increase |
| `pxPriceDecay` | 1.2s | Red flash on price decrease |
| `pxSkeletonShimmer` | 1.8s (infinite) | Loading skeleton shimmer |
| `pxFadeInUp` | 400ms | Staggered grid entrance |
| `pxSlideInUp` | 250ms | Toast entrance |
| `pxSlideOutRight` | 250ms | Toast exit |
| `pxCrashStrobe` | varies (infinite) | Market crash strobe effect |
| `pxCrashPulse` | varies (infinite) | Crash banner glow pulse |
| `pxLiveDotPulse` | varies (infinite) | Live status dot |
| `pxCountBounce` | 300ms | Number change bounce |
| `pxBtnPress` | varies | Button press feedback |
| `pxRowFlash` | varies | Table row highlight flash |

---

## Shared Component Classes

### Skeleton Loaders
```css
.px-skeleton          /* Base shimmer gradient */
.px-skeleton-text     /* 14px text line */
.px-skeleton-card     /* 180px card placeholder */
.px-skeleton-circle   /* 40px avatar circle */
.px-skeleton-row      /* 48px table row */
```

### Empty States
```css
.px-empty-state       /* Centered container */
.px-empty-state-icon  /* 48px emoji/icon */
.px-empty-state-title /* Bold heading */
.px-empty-state-desc  /* Muted description */
.px-empty-state-cta   /* Action button */
```

### Toast System
```css
.px-toast-container   /* Fixed bottom-right stack */
.px-toast             /* Individual toast */
.px-toast--info/success/warning/error/critical
.px-toast-icon        /* Left icon */
.px-toast-body        /* Title + message */
.px-toast-close       /* Close button (hover-reveal) */
.px-toast-undo        /* Undo action button */
.px-toast-progress    /* Auto-dismiss progress bar */
```

### Buttons
```css
.px-btn               /* Base: flex, padding, radius, transitions */
.px-btn-primary       /* Accent primary background */
.px-btn-danger        /* Red danger background */
.px-btn-success       /* Green success background */
.px-btn-ghost         /* Transparent with border */
```

### Tables
```css
.px-table-sticky      /* Sticky thead on scroll */
.px-table-hover       /* Row hover highlight */
.px-sortable          /* Sortable column headers */
.px-sortable.asc/desc /* Sort direction indicators */
```

### Accessibility
```css
.px-sr-only           /* Screen reader only content */
.px-live-region       /* ARIA live region (hidden) */
.px-focus-ring        /* Focus-visible outline */
.px-network-banner    /* Network disconnect indicator */
```

---

## Z-Index Layer Map

| Layer | Z-Index | Elements |
|:---|---:|:---|
| Base | 1 | Normal content |
| Dropdown | 100 | Select menus, popovers |
| Sticky | 200 | Sticky headers, sidebar |
| Overlay | 500 | Modal backdrop |
| Modal | 1000 | Modal content |
| Toast | 9999 | Toast notifications |
| Crash | 99999 | Market crash takeover |
