# WGO Design System — "Cobalt Radar" (agent spec)

Source of truth for the visual and motion design of the native Wear OS app (Kotlin + Jetpack Compose for Wear OS). Companion file: `tokens.json` (same folder, machine-readable). Both live in `docs/design/`.

- Reference canvas: **Wear OS Large Round, 227 x 227 dp**. Everything is in dp/sp. Also verify on **Small Round, 192 x 192 dp** (express layout as fractions, keep the 48 dp touch minimum).
- Themes: `light` (Daylight, source theme) and `dark` (Night). Same token names in both. Token `x-y` maps to Kotlin `xY` (e.g. `surface-raised` -> `surfaceRaised`).
- Do NOT touch or commit `legacy_web_app/` and `stitch_wgo_smartwatch_ai_navigator/`.
- Hard rules: no sidebars, no hamburger menus, no soft/blurred shadows, no sharp-cornered shapes (pill or circle only, except the inner top corners of the split pads), touch targets >= 48 dp, text/background pairs >= 4.5:1, nothing flashes faster than 3 Hz.

## 1. Color tokens

| token | Daylight (light) | Night (dark) | usage |
|---|---|---|---|
| `canvas` | `#ffffff` | `#000000` | Fondo de toda pantalla. Blanco puro al sol, negro puro en AMOLED nocturno. Nunca grises intermedios como fondo. |
| `surface` | `#f2f4f7` | `#1e1e1e` | Píldoras secundarias, tarjetas HUD, chips, botónes circulares. Texto ink: 17:1 en Daylight, 16:1 en Night. |
| `surface-raised` | `#e1e4ea` | `#2e3138` | Pistas de gauge, divisores y bordes DECORATIVOS. No usar como único borde de un control (3:1 no se cumple); para bordes de control usar ink. |
| `ink` | `#000000` | `#ffffff` | Texto, iconos y bordes de control sobre canvas o surface. También el fondo de los botónes invertidos y del estado pressed. |
| `ink-muted` | `#5a606b` | `#9aa3b2` | Metadatos no críticos: dirección secundaria, unidades, etiquetas inactivas. 5.7:1 sobre surface en Daylight, 6.5:1 en Night. |
| `ink-inverse` | `#ffffff` | `#000000` | Texto e iconos sobre fondos ink (botónes invertidos, estado pressed). |
| `primary` | `#0052ff` | `#0052ff` | Cobalto eléctrico. Relleno del micrófono, botón CONDUCIR, ruta en Daylight. Texto on-primary: 5.75:1. NO usar como texto sobre canvas oscuro (3.65:1). |
| `on-primary` | `#ffffff` | `#ffffff` | Texto e iconos sobre primary. |
| `primary-ink` | `#0052ff` | `#5c8dff` | Cobalto como TEXTO, trazo o icono directo sobre canvas/surface. En Night se aclara a #5C8DFF (6.7:1 sobre negro). |
| `aura` | `#00d4ff` | `#00d4ff` | Cian eléctrico. SOLO decorativo: halos, estelas, barrido de radar, ripple. Nunca porta texto (1.8:1 sobre blanco). |
| `aura-deep` | `#00677e` | `#3cd7ff` | Versión legible del cian para texto o iconos con significado sobre canvas (6.5:1 Daylight, 12:1 Night). |
| `alert` | `#bf3003` | `#ff7a59` | Peligro, pérdida de GPS, cancelar ruta, incidentes en el radar. Texto on-alert: 5.8:1 Daylight, 8.2:1 Night. |
| `on-alert` | `#ffffff` | `#000000` | Texto e iconos sobre alert. |
| `go` | `#007a55` | `#3ddc97` | Éxito y llegada al destino. Contrasta con alert por luminosidad, no solo por tono. |
| `on-go` | `#ffffff` | `#000000` | Texto e iconos sobre go. |
| `map-ground` | `#e9ecf1` | `#0b1220` | Terreno base del minimapa radar. Ruta sobre ground: 4.9:1 Daylight, 10.6:1 Night. |
| `map-building` | `#dde1e8` | `#141d30` | Manzanas y edificios; apenas separados del terreno para no competir con la ruta. |
| `map-park` | `#cbe7cf` | `#10302a` | Parques y zonas verdes. |
| `map-water` | `#bbd9f0` | `#0c2f52` | Ríos y cuerpos de agua. |
| `map-road` | `#ffffff` | `#26324a` | Calles secundarias (trazo 5 dp). |
| `map-road-major` | `#ffffff` | `#3b507d` | Avenidas y la vía activa (trazo 9 dp). |
| `map-road-casing` | `#c9ced8` | `#0f1626` | Contorno de las calles; da el aspecto de plano tipo videojuego. |
| `map-route` | `#0052ff` | `#00d4ff` | Línea de ruta y barrido de radar. Cobalto de día, cian neón de noche. |
| `map-route-halo` | `#ffffff` | `#0052ff` | Contorno de la ruta y puntos de flujo animados. |
| `map-player` | `#000000` | `#ffffff` | Chevron del usuario, siempre apuntando arriba (heading-up). |
| `map-range-ring` | `#00000024` | `#ffffff1f` | Anillos de alcance del radar centrados en el usuario (decorativos). |

Rules: `aura` is decorative only (never carries text). Cobalt as *text or stroke on canvas* is `primary-ink` (Night lightens it to #5C8DFF because #0052FF on black is only 3.65:1). Control borders use `ink` 1.5 dp, never `surface-raised`. `surface-raised` is for decorative tracks only.

### Map styles (8, original, genre-inspired)

Each style redefines the ten map roles. `cobalto` is the default and uses the theme-dependent `map-*` tokens; the other seven use `ms-<id>-<role>` (identical in Daylight and Night) and each style also has `ms-<id>-accent`, the colour of its arc in the map-picker dial (decorative, >= 3:1 on both white and black). Do NOT use any game's name, logo, icon or map art: these are generic looks, not copies.

| id | ES name | EN name | genre | accent | ground | building | park | water | road | road-major | road-casing | route | route-halo | player |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| cobalto | COBALTO | COBALT | WGO original | #1a63ff | `map-*` (theme-dependent) |  |  |  |  |  |  |  |  |  |
| barrio | BARRIO | DISTRICT | Open city | #c47700 | #5d6a55 | #53604b | #6f8a5b | #3f6f8f | #3a3f3a | #23262a | #8d9a82 | #ffd23f | #23262a | #ffffff |
| pergamino | PERGAMINO | PARCHMENT | Classic quest | #b5651d | #ead9b0 | #dcc590 | #bccb8c | #8fb7b0 | #f6ecd2 | #fff6dd | #8a6a3b | #b3261e | #fff6dd | #2b1d0e |
| neon | NEÓN | NEON | Cyber arcade | #e020c0 | #12002a | #1d0840 | #1a0a4a | #0a1f6b | #4a1a7a | #ff2bd6 | #12002a | #00f0ff | #12002a | #ffffff |
| pixel | PÍXEL | PIXEL | Retro 8-bit | #5f8f0a | #9bbc0f | #8bac0f | #8bac0f | #306230 | #8bac0f | #306230 | #306230 | #0f380f | #9bbc0f | #0f380f |
| circuito | CIRCUITO | CIRCUIT | Racing | #e03a3a | #15171c | #1d2027 | #1a2420 | #12263a | #4a5163 | #e8ecf2 | #0d0f13 | #ffd400 | #0d0f13 | #ff6b6b |
| sonar | SONAR | SONAR | Stealth ops | #00a58f | #02100e | #04201c | #04201c | #031a1f | #0e4b43 | #19a08f | #02100e | #7dffe0 | #02100e | #ffffff |
| isla | ISLA | ISLAND | Tropical quest | #1b9ad6 | #c9dfa0 | #bcd392 | #8fc16b | #5fb4e6 | #f3e9c6 | #fff7da | #a89a6a | #b83a00 | #fff7da | #10240a |

Per-style effects drawn over the radar (static, cheap): barrio = dark vignette + thin building outlines + dashed centre line; pergamino = paper hatch + burnt-edge vignette + building outlines + dashed centre line; neon = scanlines + glow on route and major roads; pixel = hard edges (butt caps, no rounded corners), 3 dp grid, stepped animations (flow/halo/sweep use stepped timing); circuito = diagonal hatch + dashed centre line + wider major roads; sonar = outline-only buildings/parks, thin roads, strong sweep (55% alpha), route glow; isla = rounded buildings, a soft cloud shadow drifting across (9 s loop) and dashed centre line. Contrast: route vs ground >= 4:1 and player chevron vs ground >= 5.7:1 in all eight. In Night the light styles are dimmed by a black overlay: pixel to 78% brightness, pergamino to 90% and isla to 86% (their paper effects already darken the tile around the route), so the route stays >= 3.4:1 over the rendered tile. Measured route-vs-tile contrast (route core vs the tile 13-20 dp from it, sweep off, median of 3 frames, Small Round emulator) Daylight / Night: cobalto 4.91 / 10.01, barrio 3.98 / 3.98, pergamino 4.68 / 4.36, neon 14.08 / 14.08, pixel 5.82 / 4.50, circuito 12.52 / 12.52, sonar 16.02 / 16.02, isla 3.99 / 3.67.

## 2. Typography

Font: **Space Grotesk**, file `res/font/space_grotesk.ttf` is a **variable font (wght 300-700, default instance 300 = Light)**. A static `Font(R.font.space_grotesk, FontWeight.Bold)` renders Light. Use `FontVariation.Settings(FontVariation.weight(w))` for 500 and 700. Numerals use tabular figures (`fontFeatureSettings = "tnum"`).

| style | size / line | weight | tracking | usage |
|---|---|---|---|---|
| `display-xl` | 40px / 44px | 700 | -0.03em | Brand moment only: the cold-start intro (WHERE / WE / GO? -> WGO lockup). Never in running UI. |
| `numeral-xl` | 34px / 34px | 700 | -0.03em | Distancia héroe en llegada y giros inminentes. Cifras tabulares (tnum). |
| `numeral-lg` | 28px / 30px | 700 | -0.03em | Contador de distancia del HUD con efecto odómetro. Cifras tabulares (tnum). |
| `headline-lg` | 24px / 28px | 700 | -0.02em | Mensaje de estado principal (llegada, sin señal). |
| `headline-md` | 20px / 24px | 700 | -0.02em | Pregunta de bienvenida y títulos de pantalla. |
| `headline-sm` | 16px / 20px | 700 | -0.01em | Nombre del destino (máximo 2 lineas con elipsis). |
| `body-lg` | 16px / 20px | 500 | 0em | Instruccion de giro en el HUD. |
| `body-md` | 14px / 18px | 500 | 0em | Dirección secundaria y transcripcion de voz en vivo. |
| `button` | 15px / 18px | 700 | 0.07em | Texto de botónes masivos. Siempre en mayusculas. |
| `label-md` | 12px / 14px | 700 | 0.08em | Estados del sistema, chips, ETA. |
| `label-sm` | 10px / 12px | 700 | 0.12em | Badges y micro-métricas. Mínimo absoluto 10 sp en Wear OS. |

Uppercase: `button`, `label-md`, `label-sm`. Minimum text size 10 sp. Destination names: max 2 lines with ellipsis.

## 3. Spacing, radius, shadow

| token | value | usage |
|---|---|---|
| `space-1` | 4px | Separación entre icono y texto pequeno, gap interno de badges. |
| `space-2` | 8px | Gutter estándar entre controles hermanos. |
| `space-3` | 12px | Distancia minima entre objetivos táctiles (anti-error con guante). |
| `space-4` | 16px | Margen lateral de contenido. |
| `space-5` | 24px | Separación entre zonas (cabecera / foco / accion). |
| `hit-min` | 48px | Objetivo táctil minimo absoluto. |
| `hit-massive` | 52px | Alto de los botónes masivos en píldora. |
| `hit-drive` | 60px | Zona táctil extendida para acciones críticas en modo conduccion (cancelar ruta). |
| `bezel-stroke` | 4px | Grosor del gauge de bisel, inset de 3 dp respecto al borde físico. |
| `radius-sm` | 8px | Marcas y detalles internos pequeños. |
| `radius-md` | 16px | Esquinas internas de las pastillas del selector partido. |
| `radius-lg` | 24px | Tarjeta HUD de giro. |
| `radius-pill` | 9999px | Botónes, chips y badges. Las esquinas rectas están prohibidas: chocan con el dial. |
| `edge-control` | light `0 0 0 1.5px #000000` / dark `0 0 0 1.5px #ffffff` | Borde nitido de controles secundarios (equivale a ink 1.5 dp). |
| `ring-listening` | 0 0 0 3px #0052ff, 0 0 0 11px #0052ff4d, 0 0 0 21px #00d4ff2e | Resonancia del micrófono: borde 3 dp + anillo 8 dp + anillo cian 18 dp (Stitch Level 2). |
| `ring-alert` | light `0 0 0 3px #bf3003` / dark `0 0 0 3px #ff7a59` | Anillo de foco en alertas. |

Elevation is made of crisp rings, never blur: `edge-control` for secondary controls, `ring-listening` for the mic resonance.

## 4. Round geometry

| zone | size (227 dp) | content |
|---|---|---|
| bezel | outer 9% (~10 dp) | gauges and tracks only (`bezel-stroke` 4 dp, ring radius 108 dp) |
| safe circle | 82% (~186 dp) | text and controls |
| meridian | vertical center | header on top, focus in the middle, action at the bottom |

Pill fit rule (pill width `w`, height `h`, center offset `y` from the dial center, R = 113.5): `sqrt((w/2 - h/2)^2 + y^2) + h/2 <= 104`. For the Galaxy Watch5 Pro (about 225 dp) the usable radius is R - 9.5, so the limit is 103. Verified placements (value = `top` in dp): badge 80x18 @16 (VOICE ON badge with equalizer, about 96x18, @19); CANCEL chip 104x32 @176; FINALIZAR 120x48 @160; RETRY 120x40 @166; HUD card 140x66 @22 (24 dp corners); bottom nav row (mute 34 + gap 8 + END 86x34) @172.

### 4.1 Responsive scale (all round watches)

Every geometry value in this document is written in **reference dp** on the 227 dp canvas. At runtime the whole UI is scaled uniformly:

- `s = clamp(min(screenWidthDp, screenHeightDp) / 227, 0.8, 1.25)`, implemented by overriding `LocalDensity` once around the whole tree (`WgoResponsive`, called by `WgoTheme`): `density = base * s`, `fontScale = base * textS / s`. Every `dp`, `sp` and `toPx()` scales with no per-file changes; `LocalWgoScale` exposes `s`. `LocalConfiguration` is overridden with the matching dp sizes.
- Exceptions: (1) hit areas are at least **48 REAL dp**: use `Modifier.wgoMinTouch()`, which grows the invisible box to `48 / s` reference dp without changing the visual; (2) text scales with `textS = clamp(s, 0.9, 1.1)` combined with the user's system font scale; (3) hairlines never go under 1 real dp (`Dp.atLeastRealDp()`).
- The fit rule `sqrt((w/2 - h/2)^2 + y^2) + h/2 <= 104` is scale-invariant (w, h and y all scale by `s`), so a placement that fits at s = 1 fits at every size.

| Device | Screen (dp) | s | Verified |
|---|---|---|---|
| Wear OS Small Round | 192 | 0.846 | previews and unit tests |
| Samsung Galaxy Watch5 Pro (450 px, ~321 ppi) | about 225 | 0.991 | previews and unit tests |
| Wear OS Large Round (reference) | 227 | 1.000 | previews and unit tests |
| Large round sanity case | 240 | 1.057 | previews and unit tests |

Runtime verification on devices is pending (no emulator was connected when this was written).

## 5. Motion

| token | ms | use | Compose |
|---|---|---|---|
| `instant` | 0 | pressed color inversion | no animation |
| `snap` | 120 | icon/badge/text swaps | `tween(120, Standard)` |
| `base` | 240 | toggle knob, control states | `spring(0.8f, StiffnessMedium)` |
| `enter` | 420 | screen element entrance | `tween(420, EmphasizedDecel)` |
| `exit` | 200 | screen exit | `tween(200, EmphasizedAccel)` |
| `stagger` | 40 | delay between sibling entrances | `delayMillis = i * 40` |
| `pop` | spring | button release | `spring(0.6f, StiffnessMediumLow)` |

Easings: `Standard` cubic-bezier(0.2, 0, 0, 1); `EmphasizedDecel` (0.05, 0.7, 0.1, 1); `EmphasizedAccel` (0.3, 0, 0.8, 0.15).
Ambient loops (linear): mic breathing 1100 ms reverse (scale 1 -> 1.06); pulse rings 2400 ms (scale 1 -> 2.1, alpha 0.5 -> 0, second ring offset 1200 ms); bezel comets 3200 ms (second one reversed, 4600 ms); radar sweep 3600 ms; route flow 1000 ms; player halo 1800 ms; blip ping 2400 ms; voice bars 0.8-1.4 s each.

Performance and accessibility: animate in the draw phase only (`graphicsLayer {}`, `Canvas`/`drawBehind`, read `State` inside the lambda); never animate layout size, use scale; the rule "max 2 `InfiniteTransition` per screen" is implemented by one shared `WgoClock` per screen (a single frame loop, zero `InfiniteTransition`s; pieces read it only inside draw lambdas); one shared phase for the 40 voice bars; no blur/RenderEffect. Ambient mode: stop all loops, draw outlines only in `ink`, shift content 1-2 dp per minute. Reduce motion (`Settings.Global.ANIMATOR_DURATION_SCALE == 0f`): static states and 120 ms cuts. Target 60 fps.

## 6. Components

**MicOrb** - 64 dp circle `primary`, `Mic` icon 32 dp `onPrimary`; idle has a 1 dp #0038B8 inner border. Listening: breathing, two 2 dp ring outlines (`primaryInk`, `auraDeep`) pulsing behind it, 40 voice bars (2 dp wide, round caps, radius 38 dp -> 38 + 11 dp * amplitude, amplitude 0.18-1, every 3rd bar `auraDeep`, others `primaryInk`). Processing: 26% arc (4 dp, `primaryInk`) rotating at radius 46 dp, 1.1 s per turn, with label `TRAZANDO RUTA...`.

**MassiveButton** - pill, 52 dp high (`hit-massive`), `button` text uppercase, 22-24 dp icon, 8 dp gap. Variants: `primary` (primary/onPrimary), `inverted` (ink/inkInverse), `secondary` (surface/ink + `edge-control`), `alert`, `go`. Pressed: **instant** swap to `ink`/`inkInverse` (inverted -> `primary`/`onPrimary`), scale 0.96, release with `pop` spring. Disabled: 38% alpha. An optional ETA pill (11 sp bold, 12% currentColor background) may sit inside.

**BezelGauge** - Canvas ring, stroke 4 dp, inset 3 dp, track `surfaceRaised`, fill round cap starting at 12 o'clock clockwise. `progress` is a lambda read inside the draw scope. Uses: distance-to-turn (depletes), listening (two orbiting comets: 30% `primaryInk` and 12% `auraDeep`), arrival (fills to 100% in `go`), GPS search (22% `alert` arc rotating).

**StatusBadge** - 18 dp high pill, `label-sm`, 8 dp horizontal padding. Variants: neutral (surface/ink), soft (surface/primaryInk), primary, go, alert. `VOICE ON` adds a 3-bar equalizer (2 dp bars, 10 dp max, 500-900 ms reverse).

**TurnCard** - 140x66 dp, radius 24 dp, `surface` solid, `edge-control`. Row: maneuver icon 26 dp (`primaryInk`, stroke 2.6) + distance in `numeral-lg` with odometer roll (digits slide 30 dp in 240 ms); below, instruction 12/14 sp weight 500, max width 124 dp, 2 lines. Imminent state (<= 50 m): background `primary`, text `onPrimary`, pulse scale 1 -> 1.04 at 700 ms.

**SegmentedToggle** - 156x40 pill, `surface` + `edge-control`, two equal labels (`label-md`-like 12 sp bold uppercase), knob 75x34 `primary` inset 3 dp that slides with a spring; active label `onPrimary`, inactive `ink`. Expose as a selectable group with announced state.

**RadarMinimap** - heading-up circular map (the map rotates, the user chevron never does). Layers bottom to top: `map-ground`; water; parks; buildings; road casings (`map-road-casing`, minor 7 dp, major 12 dp); roads (`map-road` 5 dp, `map-road-major` 9 dp); range rings centered on the user at radius 38/76/114 dp (1.2 dp dashed `map-range-ring`); route (`map-route-halo` 12 dp under `map-route` 6.5 dp, round caps) with moving flow dashes (3 dp on / 12 dp off, 2.4 dp wide, `map-route-halo`); maneuver node (6 dp radius, canvas fill, `map-route` 3 dp stroke, breathing); blips (3.2 dp, `alert` incident, `go` POI) pinging; user chevron at (113.5, 150) (`map-player`, outlined with `map-ground` 2 dp) with a 15 dp `primary` halo at 24% pulsing; conic radar sweep centered on the user, `map-route` at 20% alpha, 3600 ms per turn. North marker: 18 dp circle `ink`/`inkInverse` with `N` at the left edge. Do not copy any video game's map art; the look comes from these tokens.

**MapPicker (dial)** - Settings -> MAPA opens it. A bezel ring of 8 arcs (r 106 dp, one per style, in `ms-<id>-accent`, 38 degrees each, inactive 4 dp stroke at 42% alpha, active 7 dp at 100%) rotates like a dial so the active arc sits under a fixed pointer at 12 o'clock (11 x 9 dp triangle, `ink`, tip at y 13). Centre: live preview disc 104 dp at (61.5, 58) with a 3 dp `ink` ring (the radar crop, with sweep/flow/halo running); neighbours as 32 dp thumbnails at x 14 and x 181, top 94, 60% alpha, tappable. Name `headline-sm` @22, genre `label-sm` `inkMuted` @41, USAR pill 76x28 @170 (`primary`, check icon 16 dp). Gestures: horizontal swipe, rotary crown (one detent = one style), tap a neighbour. USAR commits and returns; edge-swipe-to-dismiss leaves without changing. The explored style is local until USAR. Change choreography: ring rotates 45 degrees with a spring (damping 0.6, ~620 ms, slight overshoot); new map enters through a circular iris from the centre (500 ms, EmphasizedDecel); a flash ring in the new accent scales 1 -> 1.5 and fades (550 ms, alpha .95 -> 0); name and genre rise 14 dp (360 ms); neighbours pop (scale .6 -> 1, 400 ms). Ignore crown clicks faster than 150 ms apart.

**MapRow** - 132x44 pill, `surface` + `edge-control`, 34 dp round map thumbnail (crop of the active style) at 5 dp inset, text column (`MAPA` 9 sp bold tracking .12 em `inkMuted`, style name 12 sp bold uppercase `ink`), chevron icon 16 dp. Thumbnail pops when the style changes.

**LanguageSwitch** - 40 dp circle, 1.5 dp `ink` border (`edge-control`), transparent fill, code `ES`/`EN` 13 sp bold. On tap: invert to `ink`/`inkInverse` in 0 ms with scale .94, haptic `EFFECT_CLICK` at 80 ms, then the code and the screen title roll upward with a spring (420 ms): `ES` -> `EN` and "¿A dónde vamos?" -> "Where to?". Persist immediately; voice (TTS) switches to `Locale("es","CO")` / `Locale.US`. Touch area extended to 48 dp. `contentDescription`: "Idioma: Español. Toca para cambiar a English" (and the reverse).

## 7. Screens (positions are `top` in dp from the canvas top, horizontally centered unless stated)

**ScreenListening** - bezel ring r 108 (idle: one 9% comet rotating 7 s; listening: two comets). Badge `WGO - NAV` neutral @16. Idle: title `headline-md` "¿A dónde vamos?" @38, width 150 (wraps to 2 lines). Listening: `label-md` "ESCUCHANDO..." in `primaryInk` @38; live transcript `body-md` @54 (one line, typewriter with a blinking caret). MicOrb 64 dp at (81.5, 92), i.e. center y 124. CANCEL chip 104x32 @176 (`chip`: surface + `edge-control`, 12 sp bold uppercase; accepted deviation: shown only while listening/processing, since in idle there is nothing to cancel). Settings button: 40x40 ghost, 22 dp gear icon, 12 dp from the right edge, top 94. LanguageSwitch: 40x40, 12 dp from the left edge, top 94 (mirror of the gear).

**ScreenModeSelection (split dial, no scroll)** - badge `VOICE ON` soft with equalizer @19 (about 96 x 18; fit 102.97 against the 103 limit of the 225 dp Galaxy Watch5 Pro, where the usable radius is R - 9.5); destination `headline-sm` @38 (width 150, 2-line clamp); address `body-md` `inkMuted` @80 (1 line, ellipsis). Two pads start at top 102, height 140, clipped by the circle: left pad x 0-111 `ink` background = WALK; right pad x 116-227 `primary` background = DRIVE; 5 dp `canvas` seam; inner top corners 16 dp (`radius-md`). Pad content (top to bottom, padding-top 16): 36 dp disc (12% currentColor) with 24 dp icon, label `button` at 13/16 sp tracking 0.04 em, ETA pill (11 sp bold). Left pad padding-start 12, right pad padding-end 12 (content sits in the visible part of the circle). Entrance: header rises 14 dp + fades (420 ms EmphasizedDecel), pads follow at 80 ms and 160 ms. Pressed: pad snaps to `ink` (left pad snaps to `primary`) in 0 ms and an `aura` ripple expands from the touch point.

**ScreenNavigation** - layers: RadarMinimap (full bleed) -> BezelGauge r 109, `primaryInk`, depleting toward the next maneuver -> `N` marker (left 12, top 104) -> TurnCard 140x66 @22 -> zoom buttons 34 dp (`+` top 98, `-` top 138, 13 dp from the right, touch area extended to 48 dp) -> bottom row @172: mute 34 dp circle at x 49.5 and END pill 86x34 (`alert`, close icon 16 dp, 12 sp bold) at x 91.5. In driving mode the END action must have a 60 dp (`hit-drive`) touch height.

**ScreenArrival** - bezel ring closes in `go`; disc 60 dp `go` with a 34 dp check (stroke 3) at top 36 (overshoot 1.12 then 1); two 80 dp ring bursts (3 dp border, `go` and `aura`, scale 0.8 -> 2.3, fade); `headline-lg` "¡Llegamos!" @104; `body-md` `inkMuted` "Centro Histórico · 8 min" @134; FINALIZAR pill 120x48 (`inverted`, 14 sp) @160 which resets the state machine to Listening. Real timeline ~2.5 s: ring 0-0.9, disc 0.5-0.9, check 0.8-1.2, bursts 0.9-1.6, text 1.2-1.6, button 1.4-1.8 s, then hold. Double-click haptic.

**ScreenGpsLost (overlay)** - full-screen `alert` background, text `onAlert`. Ring r 108 (track 28% `onAlert`, fill 100%) breathing at 1.6 s alternate. Badge "EN PAUSA" (`onAlert` bg, `alert` text) @18. Satellite-off icon 38 dp in a 40 dp box at (93.5, 64); three 48 dp search rings centered at (113.5, 84), border 3 dp `onAlert`, scale 0.6 -> 2.4, alpha 0.9 -> 0, 2400 ms, staggered 800 ms. `headline-md` "SIN SEÑAL GPS" @112 (width 170); `body-md` "Buscando satélites" + 3 fading dots @138; RETRY pill 120x40 (`onAlert` background, `alert` text, 13 sp) @166. Pause telemetry ticks while visible.

**ScreenSettings (no scroll, no close button; leave with edge-swipe-to-dismiss)** - `label-md` "AJUSTES" @18; SegmentedToggle Claro/Oscuro 156x40 @40; SegmentedToggle Español/English 156x40 @97 (labels uppercase 12 sp; they fit in 75 dp halves); MapRow 132x44 @152. The three controls sit on a 57 dp centre-to-centre pitch so their 48 REAL dp hit boxes (56.7 dp at s = 0.846) never overlap; below s = 0.846 the hit boxes are capped at the 57 dp pitch. Fit values: title 99.8, theme toggle 98.9, language toggle 78.1, MapRow 96.8 (all <= 103). The previous layout (@24/@52/@104/@156) overlapped the toggle hit boxes by 4.7 dp and the language toggle with the MapRow by 2.7 dp at s = 0.846. Theme and language apply live and persist; MapRow opens ScreenMapPicker.

**ScreenMapPicker** - the MapPicker component full screen (see section 6). Background `canvas`. Anything not listed there is not on this screen.

## 8. Haptics and voice (each visual event has a pair)

| event | haptic | voice |
|---|---|---|
| starts listening | `EFFECT_TICK` | silence |
| destination understood | `EFFECT_CLICK` | destination name |
| mode chosen | `EFFECT_HEAVY_CLICK` | "Route by car, 8 minutes" |
| turn at 200 m | `EFFECT_TICK` | "In 200 meters, turn right" |
| turn at 50 m | `EFFECT_DOUBLE_CLICK` | "Turn right now" |
| arrival | `EFFECT_DOUBLE_CLICK` | "You have arrived" |
| GPS lost | `EFFECT_HEAVY_CLICK` | "Signal lost" |
| map picker: crown click / swipe step | `EFFECT_TICK` | "Neon, cyber arcade" (announce the style name and genre, `liveRegion`) |
| map picker: USAR | `EFFECT_CLICK` | "Map neon" |
| language switch | `EFFECT_CLICK` | next utterance already in the new language |

Fire haptics from the navigation ViewModel on state change, never from composables. All user-facing strings go through `Strings.kt` (ES default, EN).

## 9. Compose mapping (current stack: Kotlin 2.1.20, Compose BOM 2024.02.01, `androidx.wear.compose:compose-material:1.3.0` (Material 2), minSdk 30; do not change the stack inside the design steps)

```kotlin
@Immutable data class WgoMapPalette(val ground: Color, val building: Color, val park: Color, val water: Color,
    val road: Color, val roadMajor: Color, val roadCasing: Color, val route: Color, val routeHalo: Color,
    val player: Color, val rangeRing: Color)

@Immutable data class WgoPalette(val canvas: Color, val surface: Color, val surfaceRaised: Color,
    val ink: Color, val inkMuted: Color, val inkInverse: Color, val primary: Color, val onPrimary: Color,
    val primaryInk: Color, val aura: Color, val auraDeep: Color, val alert: Color, val onAlert: Color,
    val go: Color, val onGo: Color, val map: WgoMapPalette)

val LocalWgoPalette = staticCompositionLocalOf<WgoPalette> { error("WgoTheme missing") }
object Wgo { val palette: WgoPalette @Composable @ReadOnlyComposable get() = LocalWgoPalette.current }

@OptIn(ExperimentalTextApi::class)
private fun grotesk(w: Int) = Font(R.font.space_grotesk, FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val SpaceGrotesk = FontFamily(grotesk(500), grotesk(700))

object WgoMotion {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    fun <T> pop() = spring<T>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
}

@Composable
fun BezelGauge(progress: () -> Float, color: Color, track: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val stroke = 4.dp.toPx(); val inset = 3.dp.toPx() + stroke / 2
        val d = size.minDimension - inset * 2; val tl = Offset(inset, inset); val sz = Size(d, d)
        drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(stroke))
        drawArc(color, -90f, 360f * progress().coerceIn(0f, 1f), false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
```

`WgoTheme(isDarkMode)` provides `LocalWgoPalette` and builds the Wear `Colors(...)` from `canvas`, `ink`, `primary`, `alert`. Set the activity window background from the theme (the current `themes.xml` hard-codes white).

Map: `osmdroid` `MapView` in `AndroidView`; heading-up via `mapOrientation`; radar look = a color filter over the tile overlay (`TilesOverlay.setColorFilter` with a `ColorMatrix`) or a styled tile source, plus Compose overlay layers (sweep, rings, chevron, halo). The implementing step must audit both options, report which was chosen and why, and set a proper User-Agent and a tile provider suitable for production (OSM tile usage policy).

### 9.1 Map styles, picker, language, persistence

```kotlin
enum class MapStyleId { COBALTO, BARRIO, PERGAMINO, NEON, PIXEL, CIRCUITO, SONAR, ISLA }
enum class MapFx { NONE, VIGNETTE, PAPER, SCANLINES, PIXEL_GRID, HATCH, SONAR, CLOUDS }
@Immutable data class WgoMapStyle(val id: MapStyleId, val palette: WgoMapPalette, val accent: Color, val fx: MapFx)
```

- Palettes come from `ms-<id>-<role>` in `tokens.json`; COBALTO uses `WgoPalette.map` for the active theme. Visible names/genres (ES/EN) live in `Strings.kt`. `SettingsViewModel` exposes `mapStyle: StateFlow<MapStyleId>`, `language`, `isDarkMode`, all persisted (DataStore Preferences recommended; SharedPreferences acceptable; read before first frame to avoid a theme flash; report which was chosen). Provide the active style through a `CompositionLocal` so the radar only reads colours.
- Applying a style to the real map: raster osmdroid tiles cannot be recoloured per feature class. Audit and report. Recommended starting point: per-style `ColorMatrixColorFilter` on the tile overlay (tint/invert/green channel/high contrast) + Compose `Canvas` layers for route, chevron, halo and FX using the style palette. Alternative: own vector radar drawn in `Canvas` (most faithful, most expensive). Do not change the stack inside design steps.
- Dial: continuous index in an `Animatable<Float>`; ring `graphicsLayer { rotationZ = -k * 45f }` with `spring(0.6f, Spring.StiffnessMediumLow)`; active = `k.roundToInt().mod(8)`. Gestures with `draggable`/`HorizontalPager` plus `edgeSwipeToDismiss`; crown with `onRotaryScrollEvent` + `focusRequester` + `focusable()` (prefer `rotaryScrollable`/`rotaryWithSnap` if the foundation version has them), one style per ~40 px of accumulated delta. Iris: `drawWithContent { clipPath(Path().apply { addOval(Rect(center, radius * progress)) }) { drawContent() } }` with `progress` 0 -> 1 in 500 ms. Haptics/voice are emitted by the ViewModel as events, not by composables.
- Language: `SettingsViewModel.toggleLanguage()`; pill with `AnimatedContent { slideInVertically { it } togetherWith slideOutVertically { -it } }` and `spring(0.7f)`.
- Reduce motion / ambient: maps render without sweep, flow, cloud or halo; the dial changes style with a 120 ms cut.

