# Merge plan (prepared in P7, refreshed in P8, P9, P10 and P11)

State on 2026-11-13 after P11 (read-only analysis: `git rev-list`, `git merge-tree --write-tree`): `feat/live-engine`
is 408+ commits ahead of `origin/master` and 4 behind (the PR merge commits of `main`), 0 behind `origin/main`.
**P11 decision (owner's prompt): one pull request `feat/live-engine` -> `master`, merged with a merge commit** (every
atomic commit kept; no squash, no rebase; the branch is kept).

## Branch graph

```
origin/master  ── PR #1..#4 merge commits (73ce1e0) ──┐   (4 commits not in feat/live-engine, all merge commits)
                                                      │
origin/main    ── 0c5bcff feat: integrate real OSM ───┤   main is an ancestor of feat/live-engine (0 behind)
                                                      │
design/foundations (255 behind HEAD, 0 ahead) ─┐      │
design/pieces      (242 behind, 0 ahead)       │      │
design/screens-01-02 (231 behind, 0 ahead)     ├──> feat/live-engine (HEAD, 375 commits ahead of master, 4 behind)
design/responsive  (214 behind, 0 ahead)       │      P5 live engine, P6-A service/ambient, P6-B HUD/explore/
design/radar       (194 behind, 0 ahead)       │      legibility, P7 places/labels/voice/arrival/release prep
design/map-styles  (162 behind, 0 ahead) ──────┘      P8 (40 commits): first-press mic fixes, AI place choice,
                                                      attribution arc, marquee, voices, orientation, @2x labels,
                                                      targetSdk 35; P9: instant mic (own capture, cloud
                                                      transcription, recognizer fallback, notice, setting); P10: long
                                                      phrases, strict choice, pick list, no-network surface, FLAC;
                                                      P11: index v2 + chains + Photon, alternatives and live
                                                      reroute, watch compass
```

Every `design/*` branch is already fully contained in `feat/live-engine` (0 commits ahead): merging them separately
adds nothing. `main` is an ancestor of `feat/live-engine`. `master` holds only the four PR merge commits of `main`.

## Recommended order

1. `design/*` -> nothing to do (already in `feat/live-engine`); delete them after the final merge, or keep them as
   history tags (`git tag design/foundations-final origin/design/foundations`).
2. P11: `feat/live-engine` -> `master` through one pull request, merged with a merge commit:
   `git merge-tree --write-tree origin/master HEAD` reports **no conflicts** (exit 0); 308 files changed
   (+32,492 / -9,351), most of it the native app replacing the old web prototype (`legacy_web_app/` stays local and
   git-ignored).
3. `main` (an ancestor of `feat/live-engine`) can then be fast-forwarded to `master` or left as history; nothing
   depends on it.

Before step 2: `assembleDebug`, `assembleRelease`, `testDebugUnitTest` and `lintDebug` green; the P7 report reviewed;
the owner's decision on publishing (`docs/RELEASE_CHECKLIST.md`; target API 35 is done since P8, the key proxy and a
persistent map attribution are not).

## Predicted conflicts

None (`git merge-tree` against `origin/master`, 2026-11-13 after P11: exit 0, no conflicted files). P9 to P11 touch only
files that `master` does not have or did not change (the app sources and `docs/`), so no new conflict is expected. Re-run it right before merging:
`git fetch origin; git merge-tree --write-tree --name-only origin/master origin/feat/live-engine`.

## Draft pull request (feat/live-engine -> main)

**Title**: WGO native Wear OS app: live navigation, service, adaptive HUD, real places, legible map, voice

**Summary**
- Native Kotlin + Compose for Wear OS app replacing the web prototype; design system and 8 original map styles.
- Live pipeline: instant mic (own capture + cloud transcription, the system recognizer as fallback), local + remote interpretation (text only), place search (private places, offline
  Overture index of Pasto, Nominatim with ranking and far-result rules), OSRM routes.
- Navigation engine in a foreground service: haptics and optional spoken guidance with the screen off, ambient
  screen, ongoing activity, adaptive HUD, explore mode, celebratory arrival, credits.
- Map: CARTO base recoloured per style with an unfiltered-hue label layer (label contrast 9-18:1), OSM fallback.
- Release prep: launcher icon, R8 release build, release checklist, README.

**Verification**: unit tests (places, voice, map contrast, fit rules, HUD, engine), on a Galaxy Watch5 Pro and the
Wear OS Small Round emulator; budgets in `docs/ARCHITECTURE.md`.

**Not in this PR**: publishing (the key proxy, now mandatory because every press uploads with the key; a persistent
map attribution; own or paid router and geocoders), vector tiles, the ambient staleness fix (backlog in
`docs/ARCHITECTURE.md`). The final PR body (P11) is written at PR time and summarises P5-P11.

## Commit messages that do not match their content

History is pushed and is not rewritten; for reviewers, two P7 commits whose subjects do not describe what they
contain (flagged by the owner):

- `6e23dd5` "feat(geocode): rank candidates by match and distance and never auto-select far results": the commit adds
  the whole `PlaceResolver` (225 lines: my places, the offline index, Nominatim with its request budget, the hint
  re-rank, dedupe) and wires it into `AppViewModel`, `MainActivity`, the geocoder and the confirm surface; the ranking is
  only part of it.
- `5fef4bc` "perf(places): load the index while the user speaks": besides the prewarm call it rewrites the index parser
  (`PlaceIndex`, 63 lines) and adds the my-places prewarm; the prewarm was later moved from the mic press to the
  recognizer's ready (`9b9a874`).

P8 adds one more to know about: `ac05c4e` .. `fa8c940` build but crash at launch on the device (ART rejected the
place resolver's coroutine state machine); `b670258` fixes it. Bisecting across that range needs the fix applied.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
