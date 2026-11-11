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
| `ambient-canvas` | `#000000` | `#000000` | Fondo del modo ambiente (siempre negro, en ambos temas). >= 85% de la pantalla negra; sin áreas rellenas grandes. |
| `ambient-ink` | `#b0b0b0` | `#b0b0b0` | Texto principal y trazo del icono de maniobra en ambiente: gris claro, nunca blanco puro. 9.7:1 sobre ambient-canvas. |
| `ambient-ink-muted` | `#8a8a8a` | `#8a8a8a` | Texto secundario en ambiente (restante, ETA). 6.1:1 sobre ambient-canvas. |
| `ambient-stroke` | `#6e6e6e` | `#6e6e6e` | Trazos finos decorativos en ambiente. 4.1:1; nunca porta texto. |

Rules: `ambient-*` are the only colours of the ambient (always-on) screen, identical in both themes (Kotlin: `WgoPalette.ambient`). `aura` is decorative only (never carries text). Cobalt as *text or stroke on canvas* is `primary-ink` (Night lightens it to #5C8DFF because #0052FF on black is only 3.65:1). Control borders use `ink` 1.5 dp, never `surface-raised`. `surface-raised` is for decorative tracks only.

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

Pill fit rule (pill width `w`, height `h`, center offset `y` from the dial center, R = 113.5): `sqrt((w/2 - h/2)^2 + y^2) + h/2 <= 104`. For the Galaxy Watch5 Pro (about 225 dp) the usable radius is R - 9.5, so the limit is 103. Verified placements (value = `top` in dp): badge 80x18 @16 (VOICE ON badge with equalizer, about 96x18, @19); CANCEL chip 104x32 @176; FINALIZAR 120x48 @158 (@160 fits 104 but measures 103.2 against the Watch5 Pro's 103); RETRY 120x40 @166; navigation HUD (P6-B) EXPANDED card 150x90 @30 r 32 (corner-arc check 99.1), COMPACT pill 112x36 @22 (100.7), zoom 40 dp circles centred (41.5, 150) / (185.5, 150) (100.7), CANCELAR 86x32 @176 (99.0). Shapes that are not pills (the HUD card) are checked with their corner arcs: `sqrt(cx^2 + cy^2) + r <= 103`, where (cx, cy) is a corner-arc centre relative to the dial centre.

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

Runtime verification (P5, 2026-11): the Small Round emulator (192 dp, s = 0.846) runs every screen; uiautomator bounds and the fit unit tests (`PipelineSurfacesFitTest`, `NavigationSurfacesFitTest`) check the placements. P6-A (2026-11-11): the real Galaxy Watch5 Pro (SM-R920, which now runs Wear OS 6 / API 36, s = 0.991) ran the pipeline and a full mocked trip (intro fit report from the device: lockup 50.9, tagline 69.1, all <= 103); its ambient screen could not be triggered over Wi-Fi debugging.

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

Performance and accessibility: animate in the draw phase only (`graphicsLayer {}`, `Canvas`/`drawBehind`, read `State` inside the lambda); never animate layout size, use scale; the rule "max 2 `InfiniteTransition` per screen" is implemented by one shared `WgoClock` per screen (a single frame loop, zero `InfiniteTransition`s; pieces read it only inside draw lambdas); one shared phase for the 40 voice bars; no blur/RenderEffect. Ambient mode: stop all loops (no `WgoClock`, no map), draw outlines and text only in the `ambient-*` greys on `ambient-canvas`, thin strokes (1.5 dp), no gradients, no large fills; in low-bit ambient draw icons without anti-aliasing. The app does NOT shift content for burn-in (P6-A decision, current Wear OS guidance): the system shifts the display when burn-in protection is required, so every element keeps >= 10 dp inside the dial (fit <= 103). Redraw only on the ambient tick (about once a minute) or when the maneuver / guidance state changes. Reduce motion (`Settings.Global.ANIMATOR_DURATION_SCALE == 0f`): static states and 120 ms cuts. Target 60 fps.

## 6. Components

**MicOrb** - 64 dp circle `primary`, `Mic` icon 32 dp `onPrimary`; idle has a 1 dp #0038B8 inner border. Listening: breathing, two 2 dp ring outlines (`primaryInk`, `auraDeep`) pulsing behind it, 40 voice bars (2 dp wide, round caps, radius 38 dp -> 38 + 11 dp * amplitude, amplitude 0.18-1, every 3rd bar `auraDeep`, others `primaryInk`). Processing: 26% arc (4 dp, `primaryInk`) rotating at radius 46 dp, 1.1 s per turn, with label `TRAZANDO RUTA...`.

**MassiveButton** - pill, 52 dp high (`hit-massive`), `button` text uppercase, 22-24 dp icon, 8 dp gap. Variants: `primary` (primary/onPrimary), `inverted` (ink/inkInverse), `secondary` (surface/ink + `edge-control`), `alert`, `go`. Pressed: **instant** swap to `ink`/`inkInverse` (inverted -> `primary`/`onPrimary`), scale 0.96, release with `pop` spring. Disabled: 38% alpha. An optional ETA pill (11 sp bold, 12% currentColor background) may sit inside.

**BezelGauge** - Canvas ring, stroke 4 dp, inset 3 dp, track `surfaceRaised`, fill round cap starting at 12 o'clock clockwise. `progress` is a lambda read inside the draw scope. Uses: distance-to-turn (depletes), listening (two orbiting comets: 30% `primaryInk` and 12% `auraDeep`), arrival (fills to 100% in `go`), GPS search (22% `alert` arc rotating).

**StatusBadge** - 18 dp high pill, `label-sm`, 8 dp horizontal padding. Variants: neutral (surface/ink), soft (surface/primaryInk), primary, go, alert, on-alert (onAlert/alert, for badges on an `alert` background such as "EN PAUSA"). `VOICE ON` adds a 3-bar equalizer (2 dp bars, 10 dp max, 500-900 ms reverse).

**TurnCard** - 140x66 dp, radius 24 dp, `surface` solid, `edge-control`. Row: maneuver icon 26 dp (`primaryInk`, stroke 2.6) + distance in `numeral-lg` with odometer roll (digits slide 30 dp in 240 ms); below, instruction 12/14 sp weight 500, max width 124 dp, 2 lines. Imminent state (<= 50 m): background `primary`, text `onPrimary`, pulse scale 1 -> 1.04 at 700 ms.

**SegmentedToggle** - 156x40 pill, `surface` + `edge-control`, two equal labels (`label-md`-like 12 sp bold uppercase), knob 75x34 `primary` inset 3 dp that slides with a spring; active label `onPrimary`, inactive `ink`. Expose as a selectable group with announced state.

**RadarMinimap** - heading-up circular map (the map rotates, the user chevron never does). Layers bottom to top: `map-ground`; water; parks; buildings; road casings (`map-road-casing`, minor 7 dp, major 12 dp); roads (`map-road` 5 dp, `map-road-major` 9 dp); range rings centered on the user at radius 38/76/114 dp (1.2 dp dashed `map-range-ring`); route (`map-route-halo` 12 dp under `map-route` 6.5 dp, round caps) with moving flow dashes (3 dp on / 12 dp off, 2.4 dp wide, `map-route-halo`); maneuver node (6 dp radius, canvas fill, `map-route` 3 dp stroke, breathing); blips (3.2 dp, `alert` incident, `go` POI) pinging; user chevron at (113.5, 150) (`map-player`, outlined with `map-ground` 2 dp) with a 15 dp `primary` halo at 24% pulsing; conic radar sweep centered on the user, `map-route` at 20% alpha, 3600 ms per turn. North marker: 18 dp circle `ink`/`inkInverse` with `N` at the left edge. Do not copy any video game's map art; the look comes from these tokens.

**MapPicker (dial)** - Settings -> MAPA opens it. A bezel ring of 8 arcs (r 106 dp, one per style, in `ms-<id>-accent`, 38 degrees each, inactive 4 dp stroke at 42% alpha, active 7 dp at 100%) rotates like a dial so the active arc sits under a fixed pointer at 12 o'clock (11 x 9 dp triangle, `ink`, tip at y 13). Centre: live preview disc 104 dp at (61.5, 58) with a 3 dp `ink` ring (the radar crop, with sweep/flow/halo running); neighbours as 32 dp thumbnails at x 14 and x 181, top 94, 60% alpha, tappable. Name `headline-sm` @22, genre `label-sm` `inkMuted` @41, USAR pill 76x28 @170 (`primary`, check icon 16 dp). Gestures: horizontal swipe, rotary crown (one detent = one style), tap a neighbour. USAR commits and returns; edge-swipe-to-dismiss leaves without changing. The explored style is local until USAR. Change choreography: ring rotates 45 degrees with a spring (damping 0.6, ~620 ms, slight overshoot); new map enters through a circular iris from the centre (500 ms, EmphasizedDecel); a flash ring in the new accent scales 1 -> 1.5 and fades (550 ms, alpha .95 -> 0); name and genre rise 14 dp (360 ms); neighbours pop (scale .6 -> 1, 400 ms). Ignore crown clicks faster than 150 ms apart.

**MapRow** - 132x44 pill, `surface` + `edge-control`, 34 dp round map thumbnail (crop of the active style) at 5 dp inset, text column (`MAPA` 9 sp bold tracking .12 em `inkMuted`, style name 12 sp bold uppercase `ink`), chevron icon 16 dp. Thumbnail pops when the style changes.

**LanguageSwitch** - 40 dp circle, 1.5 dp `ink` border (`edge-control`), transparent fill, code `ES`/`EN` 13 sp bold. On tap: invert to `ink`/`inkInverse` in 0 ms with scale .94, haptic `EFFECT_CLICK` at 80 ms, then the code and the screen title roll upward with a spring (420 ms): `ES` -> `EN` and "¿A dónde vamos?" -> "Where to?". Persist immediately; voice (TTS) switches to `Locale("es","CO")` / `Locale.US`. Touch area extended to 48 dp. `contentDescription`: "Idioma: Español. Toca para cambiar a English" (and the reverse).

## 7. Screens (positions are `top` in dp from the canvas top, horizontally centered unless stated)

**ScreenListening** - bezel ring r 108 (idle: one 9% comet rotating 7 s; listening: two comets). Badge `WGO - NAV` neutral @16. Idle: title `headline-md` "¿A dónde vamos?" @38, width 150 (wraps to 2 lines). Preparing (P6-B; from pointer-down on the orb until the recognizer is ready, about 1 s warm and 2-10 s cold on the Galaxy Watch5 Pro): `label-md` "PREPARANDO MICRÓFONO…" / "GETTING THE MIC READY…" in `primaryInk` @38, 130 wide, up to 2 lines (fit 93.5), the MicOrb processing arc and the CANCEL chip; it must be on screen within 150 ms of the press (measured 49-128 ms). Speak-now cue (P6-B): when the recognizer is ready the screen switches to the listening visuals (comets, pulse rings, voice bars) with the title `label-md` "HABLA AHORA" / "SPEAK NOW" in `primaryInk` @38 (one line, about 100 wide, fit 87.9) until the first words of the transcript appear; if the preparing state lasted more than 600 ms, one light Tick marks the moment (people start talking during a slow start and the recognizer does not hear them). Listening: `label-md` "ESCUCHANDO..." in `primaryInk` @38 once words arrive; live transcript `body-md` @54 (one line, typewriter with a blinking caret). MicOrb 64 dp at (81.5, 92), i.e. center y 124. CANCEL chip 104x32 @176 (`chip`: surface + `edge-control`, 12 sp bold uppercase; accepted deviation: shown only while listening/processing, since in idle there is nothing to cancel). Settings button: 40x40 ghost, 22 dp gear icon, 12 dp from the right edge, top 94. LanguageSwitch: 40x40, 12 dp from the left edge, top 94 (mirror of the gear).

**ScreenModeSelection (split dial, no scroll)** - badge `VOICE ON` soft with equalizer @19 (about 96 x 18; fit 102.97 against the 103 limit of the 225 dp Galaxy Watch5 Pro, where the usable radius is R - 9.5); destination `headline-sm` @38 (width 150, 2-line clamp); address `body-md` `inkMuted` @80 (1 line, ellipsis). Two pads start at top 102, height 140, clipped by the circle: left pad x 0-111 `ink` background = WALK; right pad x 116-227 `primary` background = DRIVE; 5 dp `canvas` seam; inner top corners 16 dp (`radius-md`). Pad content (top to bottom, padding-top 16): 36 dp disc (12% currentColor) with 24 dp icon, label `button` at 13/16 sp tracking 0.04 em, ETA pill (11 sp bold). Left pad padding-start 12, right pad padding-end 12 (content sits in the visible part of the circle). Entrance: header rises 14 dp + fades (420 ms EmphasizedDecel), pads follow at 80 ms and 160 ms. Pressed: pad snaps to `ink` (left pad snaps to `primary`) in 0 ms and an `aura` ripple expands from the touch point.

**ScreenNavigation (P6-B: adaptive HUD and explore mode)** - layers: RadarMinimap (full bleed, user chevron at (113.5, 150)) -> HUD (EXPANDED card or COMPACT pill) -> zoom buttons -> recenter button (explore only) -> CANCELAR chip. Out of scope for now: the depleting BezelGauge, the `N` marker and the mute button. Every geometry value below is checked by `NavigationSurfacesFitTest`; none of them changes size at runtime.

- **HUD EXPANDED card** 150x90 @30, corner radius 32 (a rounded rectangle, checked with its corner arcs: 99.1; 101.9 at the imminent pulse scale 1.04), `surface` + `edge-control`, opaque. Content, centred, top to bottom (padding 8 horizontal, 4 vertical): row = maneuver icon 26 dp (`primaryInk`, stroke 2.6) + distance `numeral-lg` with the odometer roll, its line box trimmed to the glyphs (measured on the Galaxy Watch5 Pro: untrimmed, a two-line instruction pushed the totals line out of the card) + unit `label-md`; instruction `body-md` 12/14 sp weight 500, max width 134, 2 lines, ellipsis (or the OFF ROUTE / REROUTING status, `label-md` `primaryInk`, live region); remaining distance · ETA `label-md` `inkMuted`, 1 line. Imminent (<= 50 m): `primary` background, `onPrimary` content, pulse 1 -> 1.04 at 700 ms (draw phase; static with reduce motion). Its bottom (120) stays 15 dp above the chevron halo.
- **HUD COMPACT pill** 112x36 @22 (100.7), `surface` + `edge-control`: maneuver icon 22 dp + distance `headline-md` (tabular) + unit `label-md`; no instruction, no totals. Imminent colours as above, no pulse. With the compact pill the opaque overlays (pill, zoom buttons, CANCELAR) cover about 22% of the dial: the map keeps >= 55% of the dial visible (checked by the fit test).
- **HUD state rule** (a pure function of distance to the next maneuver, the previous state, the manual override, the explore flag, the reroute time and now), in this order: (1) exploring -> COMPACT; (2) a manual override for the current maneuver -> the state the user chose (a tap always wins, so it is never ignored during the reroute window); (3) OFF ROUTE or REROUTING, or less than 4 s since a new route arrived after them -> EXPANDED; (4) automatic with hysteresis: EXPANDED at <= 200 m, COMPACT above 240 m, between 200 and 240 m the previous state stays (a trip starts COMPACT unless the first maneuver is within 200 m). A tap on the HUD (touch area >= 48 REAL dp; the whole card, or the pill grown to 48 real dp high) forces the other state; the override lasts until the next maneuver changes (a new step index or a new route), then the automatic rule resumes. While exploring, a tap on the HUD recenters (same as the recenter button). HUD state changes are silent (no haptic).
- **HUD motion**: the two states swap with a cross-fade (`snap`, 120 ms) and the incoming one scales 0.94 -> 1 (`base`, 240 ms, Standard); never a layout-size animation. Reduce motion: an instant cut, no animation.
- **Zoom buttons** `-` at (41.5, 150) and `+` at (185.5, 150) (centres), 40 dp circles, `surface` + `edge-control`, hand-drawn 20 dp minus / plus icons in `ink`, touch area 48 REAL dp (fit 100.7). Range 15-18, step 1. Fixed size, always visible (both HUD states, explore too).
- **Recenter button** 40 dp circle, `primary` fill with a 22 dp `onPrimary` locate icon, touch 48 REAL dp, shown only while exploring. P7: it never covers the user's chevron or its 15 dp halo. Centre (113.5, 108) (below the attribution pill; its touch box below the compact pill's even at s = 0.846). When the chevron's centre is closer than 39 dp (button radius 20 + halo 15 + 4) the button hides; a tap on the HUD and the 10 s timer still recenter. No second slot fits: a place that clears the zoom buttons' and CANCELAR's hit boxes at s = 0.846 (CANCELAR's 60 real dp driving box starts at 156.5) and the attribution pill lies within 28 dp of the first, too close to escape the halo. Pure function `RecenterRule`, checked by a unit test.
- **CANCELAR chip** 86x32 @176 (fit 99.0; label `chip` 12 sp bold uppercase), `surface` + `edge-control`; touch height 48 REAL dp walking and 60 REAL dp (`hit-drive`) driving; asks END NAVIGATION? first. 86 dp keeps its touch box clear of the zoom buttons' at every scale.
- **Explore mode**: a one-finger drag on the map (beyond touch slop, not starting in the 30 dp left edge strip that belongs to swipe-back) pans it; no pinch, zoom stays on the buttons. While exploring: the map stops following the user (its heading freezes at the value it had), the chevron is drawn at the user's real position, the HUD is COMPACT and the recenter button shows. It returns to following by itself 10 s after the last touch (the timer is paused while a finger is down), at once on recenter (or a tap on the HUD); the return is a 420 ms `EmphasizedDecel` glide (a cut with reduce motion). The pan is clamped to the route's bounding box grown by 400 m on every side, so tile traffic stays bounded. Exploring changes nothing below the UI: engine, foreground service, haptics, ongoing activity and the single location request run untouched. Explore state is dropped when the trip ends and when the screen enters ambient.

**ScreenArrival** - bezel ring closes in `go`; disc 60 dp `go` with a 34 dp check (stroke 3) at top 36 (overshoot 1.12 then 1); two 80 dp ring bursts (3 dp border, `go` and `aura`, scale 0.8 -> 2.3, fade); `headline-lg` "¡Llegamos!" @104; `body-md` `inkMuted` "Centro Histórico · 8 min" @134; FINALIZAR pill 120x48 (`inverted`, 14 sp) @158 (fit 101.4; @160 is 103.2, over the Watch5 Pro limit) which resets the state machine to Listening. Real timeline ~2.5 s: ring 0-0.9, disc 0.5-0.9, check 0.8-1.2, bursts 0.9-1.6, text 1.2-1.6, button 1.4-1.8 s, then hold. Double-click haptic.

**ScreenArrival, P7 celebratory version** (replaces the timeline above; total <= 2.5 s, then hold; scale, alpha and draw-progress only, no layout-size animation; one `WgoClock`-free timeline driven by a single `Animatable` 0 -> 2400 ms read in draw lambdas):
- 0-900 ms: the bezel ring (r 108) closes clockwise in `go` (EmphasizedDecel).
- 0-500 ms: disc 60 dp `go` @26 (fit 87.5) scales 0.85 -> 1 (Standard) showing the destination pin (24 dp, `onGo`).
- 200-900 ms: a 3 dp `go` ring at r 36 around the disc closes clockwise ("the ring closes around the pin").
- 900-1300 ms: the pin cross-fades out and a 34 dp check (stroke 3, `onGo`) draws itself (path trimmed 0 -> 1).
- 1100-1800 ms: ONE soft radial pulse: an 80 dp ring, 2 dp `go` at 40% alpha, scales 1 -> 1.8 while fading to 0 (no confetti, no bursts, no game-like effects).
- 1300-1700 ms: "¡Llegamos!" / "We're here!" `headline-lg` 170 wide @92 rises 8 dp and fades in; 1400-1800 ms the place name `body-md` `ink` 160x18 @122 (1 line, ellipsis) and the trip summary `label-md` `inkMuted` 150x14 @140 ("1,2 KM · 8 MIN": the distance travelled and the time since the trip started, computed from the session and never stored; "< 1 MIN" under a minute).
- 1600-2000 ms: LISTO / DONE MassiveButton 120x48 `inverted` @158 (fit 101.4; touch 48 REAL dp) fades in; it goes Home. Back also goes Home.
- Haptic DOUBLE_CLICK (spec section 8) and, when voice guidance speaks, "Has llegado a tu destino": both from the navigation host. The service stops and the ongoing activity disappears as before.
- Reduce motion: the final frame at once (no ring, pulse or rise). Ambient: a static arrival screen in the `ambient-*` greys: check outline 40 dp @30 (1.5 dp stroke), "¡Llegamos!" `headline-md` @84, the place name `body-md` @116; no fills, no motion.
- Frame budget: the timeline animates draw-phase values only (target: no slow frame on the benchmark build).

**ScreenCredits (P7)** - from the Settings credits row (centre 345, the row after the voice row, 57 dp pitch). The same scrolling list: title "CRÉDITOS" / "CREDITS", then centred text blocks 160 wide (`body-md` `ink` headings, `label-md`-sized `inkMuted` details, sentence case): OpenStreetMap contributors (map data, ODbL), CARTO (map tiles), Overture Maps Foundation (places; CDLA-Permissive-2.0; Foursquare rows Apache-2.0; AllThePlaces CC0), osmdroid (Apache-2.0), Nominatim and OSRM / FOSSGIS (search and routes), the app version and build; then three privacy lines: what leaves the watch when online (what you say, as text, to the language model; map tiles; place searches and routes), and that your exact position and your private places never go to the language model. ES + EN, every line inside the dial while scrolled into view.

**ScreenGpsLost (overlay)** - full-screen `alert` background, text `onAlert`. Ring r 108 (track 28% `onAlert`, fill 100%) breathing at 1.6 s alternate. Badge "EN PAUSA" (`onAlert` bg, `alert` text) @18. Satellite-off icon 38 dp in a 40 dp box at (93.5, 64); three 48 dp search rings centered at (113.5, 84), border 3 dp `onAlert`, scale 0.6 -> 2.4, alpha 0.9 -> 0, 2400 ms, staggered 800 ms. `headline-md` "SIN SEÑAL GPS" @112 (width 170); `body-md` "Buscando satélites" + 3 fading dots @138; RETRY pill 120x40 (`onAlert` background, `alert` text, 13 sp) @166. Pause telemetry ticks while visible.

**ScreenSettings, P7 update** - P7 adds three rows, so Settings becomes the standard Wear scrolling list (`ScalingLazyColumn` with the position indicator shown while scrolling, WO-V8; touch scroll, no crown needed; still no close button, edge-swipe-to-dismiss leaves). Rows, in order, 57 dp apart so every 48 REAL dp hit box stays apart down to s = 0.846: title "AJUSTES"; theme toggle 156x40; language toggle 156x40; MapRow 132x44; **voice guidance row** 156x44 (`surface` + `edge-control`, small label "GUÍA POR VOZ" / "VOICE GUIDANCE" 9 sp bold `inkMuted` tracking .12 em, value "AUTO" / "SIEMPRE" / "NO" 12 sp bold `ink`; a tap cycles OFF -> AUTO -> ALWAYS, `stateDescription` announces the value); **voice row** 156x44 (label "VOZ" / "VOICE", value = the chosen voice's name, or "Sin voz instalada" / "No voice installed"; opens the voice picker); **credits row** 156x44 ("CRÉDITOS" / "CREDITS"; opens Credits). The original three rows keep their look; the list starts with them on screen.

**ScreenVoicePicker (P7)** - the same scrolling list: title "VOZ" / "VOICE"; up to 4 installed voices of the UI language as selectable pills 156x44 (name "Español (Colombia) 1" built from the locale and an index, plus "sin conexión" / "offline" when the voice needs no network; the selected one is `primary` / `onPrimary`, the others `surface` + `edge-control`); a "PROBAR" / "TRY" pill 120x40 that speaks "Así sonará tu guía" / "This is how your guidance will sound" with the selected voice, ALWAYS rules (the speaker is fine: the user asked). The engine lives only while this screen is open. Choosing a voice persists it (DataStore) and is silent.

**ScreenSettings (P4-P6 layout, replaced by the P7 scrolling list above; no scroll, no close button; leave with edge-swipe-to-dismiss)** - `label-md` "AJUSTES" @18; SegmentedToggle Claro/Oscuro 156x40 @40; SegmentedToggle Español/English 156x40 @97 (labels uppercase 12 sp; they fit in 75 dp halves); MapRow 132x44 @152. The three controls sit on a 57 dp centre-to-centre pitch so their 48 REAL dp hit boxes (56.7 dp at s = 0.846) never overlap; below s = 0.846 the hit boxes are capped at the 57 dp pitch. Fit values: title 99.8, theme toggle 98.9, language toggle 78.1, MapRow 96.8 (all <= 103). The previous layout (@24/@52/@104/@156) overlapped the toggle hit boxes by 4.7 dp and the language toggle with the MapRow by 2.7 dp at s = 0.846. Theme and language apply live and persist; MapRow opens ScreenMapPicker.

**ScreenMapPicker** - the MapPicker component full screen (see section 6). Background `canvas`. Anything not listed there is not on this screen.

### 7.1 Live pipeline and navigation surfaces (P5)

Fit values are against the Galaxy Watch5 Pro limit of 103 (section 4) and are checked by `PipelineSurfacesFitTest` and `NavigationSurfacesFitTest`. Every action has a 48 REAL dp hit area (`wgoMinTouch`); titles are polite live regions; reduce motion makes every loop static.

- **Intro (cold start)** - WHERE / WE / GO? -> WGO lockup in `display-xl`, tagline "¿A dónde vamos?"; one Click haptic at the lockup; tap or Back skips it.
- **Permission denied (microphone / location)** - title, why, RETRY, and SETTINGS after "don't ask again".
- **Working** (interpreting / resolving / routing) - badge 80x18 @16 (102.8); stage `label-md` 130x14 @38 (96.8); detail `body-md` 150x18 @54 (92.1); processing orb 64 @92; CANCEL 104x32 @176 (102.4); bezel shows the stage statically (1/3, 2/3, 9/10).
- **Confirm place** (P6-B: the subtitle never truncates the neighbourhood) - name `headline-sm` 130x40 @28 (99.5, 2 lines, ellipsis, live region announced as "Destino: <name>"); subtitle `body-md` `inkMuted` 150x36 @70 (80.4): line 1 the distance ("a 500 m"), line 2 the neighbourhood / address ("Comuna 7, Pasto"), the block ellipsizes at 2 lines (without a distance the address may use both lines); IR MassiveButton 120 centred at 134; source credit `label-sm` @164 ("© OVERTURE MAPS" for a place from the local index, "© OPENSTREETMAP" for a Nominatim place, none for my places; P7); OTRO 1/2 104x32 @176 (102.4) only with two results. The previous soft badge "DESTINO" @22 was removed to make room for the second subtitle line (its meaning moves into the name's accessibility label); the GO and OTRO hit boxes keep their non-overlapping positions.
- **Error** - icon disc 40 @30, title `headline-sm` 150x40 @80, hint `body-md` 160x36 @124, RETRY 120x32 centred at 186 (100.8). Messages: not understood, voice unavailable, no internet, taking too long (a read timeout: "Tardó demasiado", retry repeats the failed step), place not found, route unavailable, AI unavailable, mic could not start ("No pude iniciar el micrófono" / "Reintenta."). Recognizer errors (P6-B): only network codes 1 and 2 show "no internet", and only when they repeat after one silent retry; a missing language model (12, 13) goes online and through the tags es-CO, es-419, es (en-US, en); a busy, disconnected or unbinding recognizer (5, 8, 10, 11) is recreated once after 400 ms, then "mic could not start". Transcribed words always end the session with them (no restart drops them). A restart after the recognizer said ready puts screen 01 back to PREPARANDO and the speak-now cue fires again at the next ready (measured on the Galaxy Watch5 Pro: the offline engine reports ready then fails with 12; a cold online engine reports ready then fails with 2 about 5 s later). Each attempt gets 20 s to get ready; "no offline model" is remembered across app restarts.
- **Place not found (P7)** - replaces the generic "place not found" error when the place search (rules: `docs/PLACE_SEARCH.md`) has no candidate. Title `headline-sm` "No encontré «X»" / "I couldn't find «X»" 130x40 @26 (101.1, 2 lines, ellipsis inside the quotes, polite live region). With partial matches: hint `body-md` `inkMuted` "¿Quizá este?" / "Maybe this one?" 150x18 @68 (84.4) and a suggestion pill 150x36 centred at 108 (75.3; `surface` + `edge-control`, `body-md` bold `ink`, the nearest partial match's name, "+1" when there is a second one): it opens Confirm place with those one or two matches (OTRO 1/2). Without partial matches the hint "Dime el barrio o la calle" / "Tell me the neighbourhood or street" `body-md` 160x36 @90 (2 lines) takes that row. Microphone retry pill 150x36 centred at 165 (94.8; `primary`, mic icon 20 dp + "DIME LA CALLE" / "SAY THE STREET" `button` 13 sp): it listens again and uses the words as the hint for the same name. The two rows sit 57 dp apart so their 48 REAL dp hit boxes never overlap down to s = 0.846. Silent like every error surface (section 8 has no error row).
- **Far result (P7)** - on Confirm place, a candidate more than 25 km from the user (or the Pasto centre) has its distance line drawn in `alert` colour; it is never listed before a nearer candidate.
- **Mode selection** - the pad ETA pills are estimates from the straight-line distance (walk 1.15x at 1.25 m/s, drive 1.43x at 9.5 m/s): shown as "~12 min", spoken "unos 12 minutos".
- **GPS SEARCHING** (no usable fix yet) - same grid as Working: bezel GPS-search arc (ambience rate), badge @16, "BUSCANDO GPS…" `label-md` `primaryInk` @38, "Busca cielo abierto" `body-md` @54, location disc 64 @92, CANCEL @176 (ends the trip).
- **GPS LOST** - the ScreenGpsLost overlay of section 7 over the navigation screen, with one constraint found by the fit rule: the "EN PAUSA" badge must stay at most 80 dp wide (@18: 100.9; a 96 dp badge would be 103.9). RETRY reopens the location request. The engine keeps running underneath and the overlay goes away by itself when fixes return.
- **OFF ROUTE / REROUTING** - not a screen: the HUD instruction line becomes "FUERA DE RUTA" / "RECALCULANDO…" (`label-md` `primaryInk`, live region); the HUD is EXPANDED while they last and for 4 s after the new route arrives (unless exploring).
- **LOST ROUTE** (3 reroutes in a row failed) - alert badge "SIN RUTA" @22 (100.3); title 150x40 @44 (94.0); hint @86 (82.4); REINTENTAR MassiveButton 150 centred at 134 (79.1); TERMINAR chip @176 (102.4).
- **END NAVIGATION?** (Back or CANCEL while navigating) - "¿Terminar la navegación?" 150x40 @44; destination @86; TERMINAR MassiveButton `alert` with close icon, 150 centred at 134 (79.1; 120 dp clipped the label); SEGUIR chip @176. Back on this surface = SEGUIR.
- **ARRIVED (minimal, P5)** - section 7's ScreenArrival without its timeline (the celebration is P6): bezel closed in `go`, disc 60 `go` with check @36, "¡Llegamos!" @104, destination @134, FINALIZAR 120x48 inverted **@158** (101.4; the @160 above measures 103.2, over the Watch5 Pro limit).

- **AMBIENT NAVIGATION (P6-A)** - only during a trip, when the system enters ambient: `ambient-canvas`; next-maneuver outline icon 40 @30 (83.5, 1.5 dp `ambient-ink` stroke); distance to it `numeral-lg` 150x30 @76 (79.1); instruction or GPS / reroute status `body-md` 160x36 @110 (81.7, 2 lines, live region); remaining · ETA `label-md` uppercase `ambient-ink-muted` 140x14 @158 (88.4). No radar, map, frame loop, animation or gradient. Leaving ambient rebuilds the live navigation screen with no transition. On any other screen ambient draws nothing (black) and the system returns to the watch face after its timeout. Checked by `AmbientNavigationTest`.

Under GPS LOST, LOST ROUTE and END NAVIGATION? the radar stays composed (no map rebuild on SEGUIR) with its loops stopped. (A P6-A experiment also froze its data below the surface; P6-B measured no gain and dropped it.)

**State machine** - Intro -> Listening -> Working (interpreting -> resolving) -> ConfirmPlace -> (ModeSelection) -> Working (routing) -> Navigation -> Arrived -> Listening; Error from any pipeline step (retry repeats it); Settings <-> MapPicker from Listening. Inside Navigation the engine state picks the surface: WaitingForGps (GPS SEARCHING), Navigating, OffRoute and Rerouting (HUD status), LostRoute, GpsLost, plus the END NAVIGATION? overlay. Back: Intro skips; pipeline goes one step back; Navigation asks END NAVIGATION? (Back again keeps); Arrived goes home. Ending or arriving stops the location request and clears the saved destination; a relaunch never resumes a trip by itself.

## 8. Haptics and voice (each visual event has a pair)

| event | haptic | voice |
|---|---|---|
| starts listening | `EFFECT_TICK` | silence |
| mic ready after a slow start (preparing > 600 ms) | `EFFECT_TICK` | silence |
| destination understood | `EFFECT_CLICK` | destination name |
| mode chosen | `EFFECT_HEAVY_CLICK` | "Route by car, 8 minutes" |
| turn at 200 m | `EFFECT_TICK` | "In 200 meters, turn right" |
| turn at 50 m | `EFFECT_DOUBLE_CLICK` | "Turn right now" |
| arrival | `EFFECT_DOUBLE_CLICK` | "You have arrived" |
| GPS lost | `EFFECT_HEAVY_CLICK` | "Signal lost" |
| map picker: crown click / swipe step | `EFFECT_TICK` | "Neon, cyber arcade" (announce the style name and genre, `liveRegion`) |
| map picker: USAR | `EFFECT_CLICK` | "Map neon" |
| language switch | `EFFECT_CLICK` | next utterance already in the new language |

**Spoken guidance (P7).** The voice column above is spoken by the navigation host (inside the foreground service, so it works with the screen off) through `android.speech.tts.TextToSpeech`; haptics are unchanged and speech never replaces them.

- **Setting** "Guía por voz" / "Voice guidance": OFF / AUTO / ALWAYS (ES "NO" / "AUTO" / "SIEMPRE"), default **AUTO**. AUTO speaks only while a headset or Bluetooth audio output is connected (wired, USB or Bluetooth A2DP / LE headset), otherwise haptics only; ALWAYS also uses the watch speaker; OFF never speaks.
- **Phrases** (`Strings.kt`, ES / EN, numbers written as words so every engine reads them naturally): turn at 200 m "En doscientos metros, gira a la derecha por Carrera 35" / "In two hundred meters, turn right onto Carrera 35" (the street only when the route has one; the distance is the real one rounded to 10 m when the maneuver is first announced under 200 m); turn at 50 m "Gira a la derecha" / "Turn right"; reroute "Recalculando" / "Recalculating"; arrival "Has llegado a tu destino" / "You have arrived"; GPS lost "Perdí la señal del GPS" / "GPS signal lost" (once per loss); lost route "No encuentro una ruta" / "I can't find a route". Roundabouts: "toma la salida número dos" / "take the second exit".
- **Queue rules**: speech never overlaps. The 50 m turn and the arrival are urgent: they flush the queue; the others are added. A phrase is spoken at most once per maneuver (200 m and 50 m each), GPS lost once until fixes return, reroute once per reroute.
- **Engine and audio**: initialised lazily when a trip starts (never at app start or on Home) and shut down when the trip ends; audio attributes `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` + `CONTENT_TYPE_SPEECH`; transient audio focus with ducking (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`), abandoned after each phrase. No engine or no voice data: no crash, haptics only, and the Settings voice row says "Sin voz instalada" / "No voice installed".
- **Voice choice**: the installed voice of the UI language, preferring es-CO, then es-419, es-US, es-MX, es-ES (EN: en-US, en-GB), offline voices before network ones; the user's choice in Settings wins and is persisted.

Fire haptics from the navigation ViewModel on state change, never from composables. All user-facing strings go through `Strings.kt` (ES default, EN).

Implementation notes (P6-B): "starts listening" fires on the mic press (pointer-down), together with the preparing state, not when the recognizer reports ready: a cold recognizer takes seconds and the press needs immediate feedback. No second Tick at ready when the recognizer was ready within 600 ms of the press; after a slower start the "mic ready after a slow start" Tick tells the user to speak now (the speak-now cue, section 7).

Implementation notes (P6-A): the trip rows (turns, GPS lost, arrival) are played by the navigation host inside the foreground navigation service, so they fire once each with the screen off, in ambient or with the app in the background (verified on the emulator and on the Galaxy Watch5 Pro: vibrator records `finished`, usage TOUCH); the pipeline rows stay with the ViewModel and the Activity.

Implementation notes (P5): the turn rows fire once per maneuver and only for real turns (not for "continue", the departure or the arrival), with the same 200 m / 50 m in both modes (this table does not distinguish walking). Events with no row here are silent on purpose: off route, rerouted, GPS recovered, lost route. Voice (TTS) is P6. The same mapping is machine-readable in `tokens.json` (`haptic`).

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
- **Map legibility (P6-B)**. Targets, WCAG non-text contrast on the rendered radar, Daylight and Night, every style: OSM streets against the land around them >= 3:1; the route line against that land >= 4.5:1 (and against its own halo >= 3:1). Measured from the real Pasto tiles cached on the Galaxy Watch5 Pro (land #F2EFE9 28% of the pixels, white minor streets 15%, raw 1.15:1): before P6-B every style rendered streets at 1.00-1.07:1 (the old filters mapped land and white streets to the same paper colour, then a 25-80% `ground` wash and the Night dim flattened what was left) and BARRIO, ISLA, PIXEL Night and PERGAMINO Night had the route under 4.5:1.
  - Tile filter (one for every style): an affine street score on R, G, B with weights (1.4, 0.3, -0.15), anchored so OSM land maps to `ground` and the white street fill to the new `street` role (`map-street`, `ms-<style>-street`); yellow / orange main roads score about 0.85 of a white street; buildings, parks, water, casings and labels fall past `ground`, away from `street`. No OSM fill scores more than 1.2 (a first version with weights (1.3, 0.7, -0.9) let school and retail areas overshoot to black blobs on the watch). Known side effect: school areas take the street colour, as blocks. No ground wash (removed); the Night dim of PERGAMINO, PIXEL and ISLA stays.
  - Street colours (each a shade of the style's own street or casing hue) and the four route tweaks, with the contrast they reach (street:land / route:land, Daylight; Night where it differs; main roads >= 3.2 everywhere): COBALTO #3b507d 6.7 / 4.9, Night #5a78b8 4.3 / 10.6; BARRIO #d2dbc6 (light, from its casing hue: a dark street on its mid-dark ground left the main roads at 2.7) 4.0 / 4.8 (route #ffd23f -> #ffea9a); PERGAMINO #7a5c30 4.4 / 5.7, Night 4.1 / 5.2 (route #b3261e -> #9e1f18); NEON #ff2bd6 6.2 / 14.1; PIXEL #204220 5.2 / 7.3, Night 3.9 / 4.9 (route #0f380f -> #082808); CIRCUITO #e8ecf2 15.1 / 12.5; SONAR #1fb5a2 7.6 / 15.9; ISLA #5e5535 5.2 / 5.8, Night 4.6 / 5.0 (route #b83a00 -> #8f2c00). Checked by `MapContrastTest`.
  - Tile scale: osmdroid scales tiles to the screen density, and on top of that by `TILE_SCALE` = 2.0, taking the tiles one zoom level lower (radar zoom 17 draws z16 tiles at twice their size), so the ground on screen is unchanged while street lines and labels are twice as large. Chosen on the Galaxy Watch5 Pro from 1.0 / 1.5 / 2.0 screenshots (1.0 turned labels into speckle under the street filter; 1.5 and 2.0 look alike, 2.0 is an exact 2x of whole-zoom tiles). Tile bytes per km must not grow: 2.6 km Udenar drive at 60 km/h with a cold cache, 147 KB/km before, 51.9 KB/km after.
  - Route: 6.5 dp `map-route` over a 12 dp `map-route-halo`, so the halo is a 2.75 dp casing on each side. The route must stand out from the streets it runs on: route:street >= 3:1, or else halo:street >= 3:1 and route:halo >= 3:1 (the casing does it). Measured: every style passes through its casing (halo:street 3.3-16.2:1); COBALTO Night needed a darker halo (#0052ff -> #00226e: 1.32 -> 3.31). Checked by `MapContrastTest`. Default radar zoom 17 in both travel modes, buttons 15-18 (unchanged).
- **Tiles still loading (P6-B)**: osmdroid's loading placeholder is drawn in the OSM land colour (#F2EFE9) with no grid lines, so the tile filter turns it into the style's `ground`: a map whose tiles have not arrived yet (cold cache, slow link, a pan into an area never loaded) shows the style's empty ground, never a plain white / grey page. Root cause of the "walking showed a basic map" report: reproduced only as this loading state (offline pan on the emulator: plain white before the fix); on home Wi-Fi the cold cache loads during routing, so walking and driving looked identical on the watch.
- **Map layers (P7: legible labels)**. The P6-B filter recoloured the OSM label pixels too (dark glyphs pushed past `ground`, white halos to `street`): names became street-coloured smudges. Layer stack, bottom to top:
  1. **Base**: CARTO `dark_nolabels` (Dark Matter without labels) at 1x for **every** style, Day and Night, recoloured by the WGO tile filter. Measured over Pasto (P7 STEP 0): Dark Matter is the only CARTO base whose roads stand apart from land (land #0e0e0e 43% of pixels, roads #414758 19%, major roads #535666, buildings #2b2b2b, water / dark areas #0a0a0a); in Positron (land #fafaf8, roads #ffffff) and Voyager (land #fbf8f3, roads #ffffff / #fefdd7) roads differ from land by 4-12 units of 255, too little to recolour. Street score weights (-2, 0, 3) on R, G, B (the roads are the only blue-tinted feature) anchored land -> `ground`, road -> `street`: major roads score 1.05, buildings 0.24 (faint blocks), water -0.04.
  2. **Labels**: CARTO `light_only_labels` at 2x (512 px, so the text drawn at `TILE_SCALE` 2 stays sharp), **not recoloured by the style**. CARTO's own label colours reach only 1.2-2.8:1 against their halo over the WGO grounds and streets (text #5c666f at 90% on a #f8f8f8 halo at <= 60%), so the labels get a neutral, hue-free stretch: grey -> black text on a white halo when the style's ground has luminance >= 0.18, inverted (white text on a black halo) on darker grounds; alpha x1.35. Text vs halo, worst over the style's ground and street (Night dim included): COBALTO 15.5 / Night 18.0, BARRIO 14.6, PERGAMINO 16.0 / 12.8, NEON 17.6, PIXEL 14.9 / 9.0, CIRCUITO 13.8, SONAR 16.7, ISLA 15.7 / 11.5 (all >= 4.5; checked by `MapContrastTest`). While a labels tile loads nothing is drawn (transparent placeholder, never grey).
  3. Compose layers (range rings, route halo + route, maneuver node, blips, chevron, sweep, FX), then the HUD and the controls. The route is drawn above the labels: a street name under the route line is hidden, and the HUD names the street.
  - Tile scale unchanged (`TILE_SCALE` 2.0, z-1 tiles at twice their size) for both layers. Traffic target: base + labels <= 100 KB per km (P6-B OSM: 51.9 KB/km).
  - **Key and fallback**: `MAP_TILE_KEY` from `local.properties` through `BuildConfig` (the URL gets `?key=`; the key and the full URL are never logged). Without a key, or when CARTO refuses it, the whole map uses the P6-B OpenStreetMap pipeline (OSM tiles + the OSM filter weights) for the rest of the process, logged once. CARTO answers a missing or wrong key with HTTP 200 and a small "API KEY REQUIRED" image (2005-2513 bytes, cached for 30 days), so the fallback recognises those bytes (and 401 / 403); the tile cache namespace includes a hash of the key, so a new key never shows old placeholders. Debug metric: `tiles=carto|osm` in WGO_METRICS (no key).
  - **Attribution** (P7): "© OpenStreetMap contributors" / "© CARTO" (OSM pipeline: only the first line; the legal wording stays in English in both languages) in `label-sm` 10 sp, sentence case, no tracking, `ink` on a `surface` pill 164x28 @58 (two lines, fit 93.7; the first line measures about 155 dp), between the COMPACT HUD and the recenter button. It fades in (`base` 240 ms; a cut with reduce motion) for the first 4 s of a trip while the HUD is COMPACT, and stays visible while exploring. Ambient shows no map, so no attribution. The map picker draws the schematic radar (no tiles): nothing to attribute there. Confirm place credits the source of the shown place: "© OVERTURE MAPS" (local index), "© OPENSTREETMAP" (Nominatim), none for my places. The Credits screen (Settings) lists every source.
- The map style depends only on the chosen style and the theme, never on the travel mode: walking and driving routes render the same WGO style in every style the picker offers (P6-B; checked by a unit test).

