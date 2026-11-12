# Merge plan (prepared in P7; nothing merged)

State on 2026-11-11 (read-only analysis: `git rev-list`, `git merge-tree --write-tree`).

## Branch graph

```
origin/master  ── PR #1..#4 merge commits (73ce1e0) ──┐   (4 commits not in feat/live-engine, all merge commits)
                                                      │
origin/main    ── 0c5bcff feat: integrate real OSM ───┤   main is an ancestor of feat/live-engine (0 behind)
                                                      │
design/foundations (255 behind HEAD, 0 ahead) ─┐      │
design/pieces      (242 behind, 0 ahead)       │      │
design/screens-01-02 (231 behind, 0 ahead)     ├──> feat/live-engine (HEAD, 280 commits ahead of master)
design/responsive  (214 behind, 0 ahead)       │      P5 live engine, P6-A service/ambient, P6-B HUD/explore/
design/radar       (194 behind, 0 ahead)       │      legibility, P7 places/labels/voice/arrival/release prep
design/map-styles  (162 behind, 0 ahead) ──────┘
```

Every `design/*` branch is already fully contained in `feat/live-engine` (0 commits ahead): merging them separately
adds nothing. `main` is an ancestor of `feat/live-engine`. `master` holds only the four PR merge commits of `main`.

## Recommended order

1. `design/*` -> nothing to do (already in `feat/live-engine`); delete them after the final merge, or keep them as
   history tags (`git tag design/foundations-final origin/design/foundations`).
2. `feat/live-engine` -> `main`: a fast-forward (main is an ancestor), through a pull request so CI and review run.
3. `main` -> `master`: `git merge-tree --write-tree origin/master HEAD` reports **no conflicts** (exit 0); the merge
   commit brings 256 files changed (+23,260 / -9,352), most of it the native app replacing the old web prototype
   (`legacy_web_app/` stays local and git-ignored).

Before step 2: `assembleDebug`, `assembleRelease`, `testDebugUnitTest` and `lintDebug` green; the P7 report reviewed;
the owner's decision on publishing (see `docs/RELEASE_CHECKLIST.md`: target API 35 is required for Play).

## Predicted conflicts

None (`git merge-tree` against `origin/master`, 2026-11-11). Re-run it right before merging:
`git fetch origin; git merge-tree --write-tree --name-only origin/master origin/feat/live-engine`.

## Draft pull request (feat/live-engine -> main)

**Title**: WGO native Wear OS app: live navigation, service, adaptive HUD, real places, legible map, voice

**Summary**
- Native Kotlin + Compose for Wear OS app replacing the web prototype; design system and 8 original map styles.
- Live pipeline: on-device speech, local + remote interpretation (text only), place search (private places, offline
  Overture index of Pasto, Nominatim with ranking and far-result rules), OSRM routes.
- Navigation engine in a foreground service: haptics and optional spoken guidance with the screen off, ambient
  screen, ongoing activity, adaptive HUD, explore mode, celebratory arrival, credits.
- Map: CARTO base recoloured per style with an unfiltered-hue label layer (label contrast 9-18:1), OSM fallback.
- Release prep: launcher icon, R8 release build, release checklist, README.

**Verification**: unit tests (places, voice, map contrast, fit rules, HUD, engine), on a Galaxy Watch5 Pro and the
Wear OS Small Round emulator; budgets in `docs/ARCHITECTURE.md`.

**Not in this PR**: publishing (target API 35 needed), the key proxy, vector tiles.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
