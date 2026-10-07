---
name: WGO Wearable Navigator
colors:
  surface: '#f9f9f9'
  surface-dim: '#dadada'
  surface-bright: '#f9f9f9'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3f3'
  surface-container: '#eeeeee'
  surface-container-high: '#e8e8e8'
  surface-container-highest: '#e2e2e2'
  on-surface: '#1b1b1b'
  on-surface-variant: '#434656'
  inverse-surface: '#303030'
  inverse-on-surface: '#f1f1f1'
  outline: '#737688'
  outline-variant: '#c3c5d9'
  surface-tint: '#004ced'
  primary: '#003ec7'
  on-primary: '#ffffff'
  primary-container: '#0052ff'
  on-primary-container: '#dfe3ff'
  inverse-primary: '#b7c4ff'
  secondary: '#00677e'
  on-secondary: '#ffffff'
  secondary-container: '#00d2fd'
  on-secondary-container: '#005669'
  tertiary: '#952200'
  on-tertiary: '#ffffff'
  tertiary-container: '#bf3003'
  on-tertiary-container: '#ffddd5'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dde1ff'
  primary-fixed-dim: '#b7c4ff'
  on-primary-fixed: '#001452'
  on-primary-fixed-variant: '#0038b6'
  secondary-fixed: '#b4ebff'
  secondary-fixed-dim: '#3cd7ff'
  on-secondary-fixed: '#001f27'
  on-secondary-fixed-variant: '#004e5f'
  tertiary-fixed: '#ffdbd2'
  tertiary-fixed-dim: '#ffb4a1'
  on-tertiary-fixed: '#3c0800'
  on-tertiary-fixed-variant: '#891e00'
  background: '#f9f9f9'
  on-background: '#1b1b1b'
  surface-variant: '#e2e2e2'
typography:
  headline-lg:
    fontFamily: Space Grotesk
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 30px
    letterSpacing: -0.03em
  headline-md:
    fontFamily: Space Grotesk
    fontSize: 20px
    fontWeight: '700'
    lineHeight: 24px
    letterSpacing: -0.02em
  headline-sm:
    fontFamily: Space Grotesk
    fontSize: 17px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Space Grotesk
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0em
  body-md:
    fontFamily: Space Grotesk
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 18px
    letterSpacing: 0em
  label-lg:
    fontFamily: Space Grotesk
    fontSize: 13px
    fontWeight: '700'
    lineHeight: 16px
    letterSpacing: 0.04em
  label-md:
    fontFamily: Space Grotesk
    fontSize: 11px
    fontWeight: '700'
    lineHeight: 14px
    letterSpacing: 0.08em
  label-sm:
    fontFamily: Space Grotesk
    fontSize: 9px
    fontWeight: '700'
    lineHeight: 12px
    letterSpacing: 0.12em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 0.5rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
---

## Brand & Style

This design system delivers a high-urgency, glanceable ambient navigation interface optimized explicitly for circular smartwatch hardware (40mm to 46mm casings). Built for dynamic outdoor sunlight and immediate physical motion, the visual language marries high-contrast utilitarian minimalism with kinetic, futuristic kinetic signals.

The aesthetic strips away decorative layers in favor of instantaneous cognitive acquisition:
- **Pure Polar Contrast:** An unapologetic stark white canvas with pure obsidian type and iconography ensures reflection-free readability under direct equatorial sunlight.
- **Electric Precision:** Active AI listening states and navigational feedback pierce the monochrome baseline through electric cobalt-blue luminescent pulses.
- **Physical Affordance on Glass:** High target-scale ratios that eliminate missed touches while in motion (walking, running, cycling).

## Colors

The color palette is calibrated strictly for optical penetration through scratch-resistant sapphire crystal and high-nits OLED screens under raw ambient daylight.

- **Primary Canvas (`#FFFFFF`):** Pure non-dimmed white. Acts as a reflector plate, prioritizing daylight visibility over power-saving dark modes.
- **Absolute Text & Glyphs (`#000000`):** 100% optical density black. No subtle charcoal or low-contrast grays on primary text; achieves a 21:1 contrast ratio against the canvas.
- **Primary Signal (`#0052FF`):** Electric Cobalt. Communicates machine readiness, listening states, and navigational vector locks.
- **Secondary Aura (`#00D4FF`):** Electric Cyan. Used exclusively as an inner radiant ring or audio frequency oscillation layer atop the cobalt field.
- **Subordinate Neutral (`#5A606B`):** Cool slate used strictly for non-critical metadata, distance meters, and inactive cancel outlines.
- **Surface Elevation Tint (`#F2F4F7`):** Soft, structural off-white for secondary buttons and inset pill backgrounds to isolate them from the base canvas without reducing glanceability.

## Typography

Typography relies on **Space Grotesk** across all roles to ensure geometric structure, wide apertures, and unmistakable glyph recognition at wrist distance (30-40cm). 

- **Display Scales:** Never exceed 26px to account for the physical curvature and bezels of 40-46mm screens. Words must not truncate abruptly; prompt copy like "¿A dónde vamos?" spans at most two controlled lines.
- **Typographic Geometry:** Uppercase transformations are applied strictly to utility tags, system confirmations, and micro-metrics (`label-sm`, `label-md`) with boosted letter spacing to avoid ink bleed on small pixel matrices.

## Layout & Spacing

Layout adheres to a **Concentric Radial Constraint Model** tailored for round viewports with a 1:1 aspect ratio (e.g., 390x390 to 454x454 physical pixels).

- **Circular Safe Boundary:** All critical touch anchors and legible type sit within a bounded inner concentric circle of 82% of total diameter. The outer 9% ring along the perimeter serves solely for kinetic aura animations, distance gauge arcs, or passive status markers.
- **Vertical Linear Axis:** Stack components strictly along the central vertical meridian:
  - Top Zone: System status & brand beacon (safe zone offset: `space-xl` from crown edge).
  - Center Zone: Interactive focal anchor (Microphone action & listening ripples).
  - Bottom Zone: Immediate escape/cancel action (clearance: `space-lg` from bottom arc).
- **Minimum Tap Distance:** Spacing between the central target and edge triggers never drops below `space-md` (12px) to prevent accidental mis-taps with thumb or glove contact.

## Elevation & Depth

To avoid optical wash-out under high-lux outdoor environments, depth is communicated through **Chromatic Luminescence and High-Contrast Edge Rings** rather than soft ambient drop shadows.

- **Level 0 (Base Canvas):** Pure matte `#FFFFFF`.
- **Level 1 (Structural Action Plinths):** Flat `#F2F4F7` background bounded by crisp 1.5px `#000000` or `#E1E4EA` structural stroke dividers. No blur; boundaries are binary.
- **Level 2 (Active AI Emission):** Concentric resonant rings. Rather than drop shadows, the Primary Mic Button projects a multi-stage radial energy ring:
  - Immediate Edge: 3px solid `#0052FF`.
  - Outer Resonance Ring 1: 2px stroke `#0052FF` with 45% alpha, offset by 8px.
  - Outer Resonance Ring 2: 1.5px stroke `#00D4FF` with 20% alpha, offset by 18px.
- **Pressed Feedback:** Instant inversion—action surfaces snap to pure `#000000` with `#FFFFFF` glyphs at 0ms duration, giving direct haptic-visual feedback.

## Shapes

The design system enforces a **Concentric Circular & Pill Architecture** (`roundedness: 3`):
- Circular shapes mimic the physical circular dial, creating aesthetic harmony with watch cases.
- Primary interactive nodes (Mic, status orbs) use complete circles (`border-radius: 9999px` or `50%`).
- Secondary controls (buttons, system chips, banners) are pill-shaped (fully rounded ends) to match wrist curvature without clipping into viewport edges.
- Sharp rectangular elements are prohibited; sharp corners collide visually with round watch screens.

## Components

### 1. PrimaryMicButton
- **Dimensions:** Fixed diameter of 104dp (minimum 96dp, maximum 112dp on larger 46mm displays).
- **Styling:** Circular `#0052FF` background fill. Centered vector microphone icon rendered in `#FFFFFF` (size: 40dp).
- **Resting State:** Solid fill with subtle 1px border (`#0038B8`).
- **Active / Listening State:** Expands slightly by 4dp, background pulses between `#0052FF` and `#0D59F2`. Coordinates directly with the `ListeningRing`.
- **Interaction Target:** Center-aligned, serving as the dominant, unmistakable action zone on the screen.

### 2. ListeningRing
- **Geometry:** 2 to 3 concentric vectorized vector arcs enclosing the `PrimaryMicButton`.
- **Animation Behavior:** 
  - Ring 1 (Inner): 116dp diameter, `#0052FF` at 60% opacity, pulsing smoothly to audio input amplitudes (scale 1.0 to 1.12).
  - Ring 2 (Outer): 134dp diameter, `#00D4FF` at 30% opacity, oscillating with a 120ms phase delay.
- **Silent Mode:** Collapses cleanly into the outer edge of the `PrimaryMicButton`.

### 3. StatusText
- **Position:** Directly above the center action cluster, spanning the safe-width horizontal bounds.
- **Default Message:** `"¿A dónde vamos?"`
- **Typographic Spec:** `headline-md` (20px / 700 weight), text-align centered, color `#000000`.
- **Processing States:** Transits into `label-lg` uppercase tracking (`"ESCUCHANDO..."`, `"TRAZANDO RUTA..."`) dyed in `#0052FF` during query resolution.

### 4. SecondaryCancelButton
- **Dimensions:** Pill shape, width 140dp, height 52dp.
- **Styling:** Neutral surface `#F2F4F7`, text `#000000`, 1.5px stroke `#000000`.
- **Content:** Icon (Close "✕" in 16dp) + Text (`label-lg`: `"CANCELAR"`).
- **Position:** Anchored in the bottom quadrant with 14dp clearance from the bottom viewport curve.
- **Target Touch Envelope:** The bounding touch-box expands invisible hit areas to 60dp vertical to guarantee responsive cancellation while running or driving.

### 5. BrandBadge
- **Dimensions:** Pill container, height 24dp, auto width with 10dp horizontal padding.
- **Position:** Placed at the top meridian, immediately below the safe sensor cutout/hardware rim.
- **Styling:** Pure `#000000` text (`label-sm`: `"WGO • NAV"`), tracking `0.12em`. Background transparent or `#F2F4F7`. Serves as both subtle orientation anchor and brand beacon.

### 6. DestinationCard (Navigation Result State)
- **Dimensions:** Max safe width (approx. 280dp wide on 454px displays), height auto, pill-rounded corners (`24dp`).
- **Styling:** Crisp `#000000` fill with `#FFFFFF` text for maximum contrast shift when transitioning from prompt to navigation state. Contains eta icon, bold distance (`headline-md`), and immediate confirmation CTA.