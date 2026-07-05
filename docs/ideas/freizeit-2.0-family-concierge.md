# Freizeit 2.0 — The Family Concierge

*Refined and stress-tested 2026-07-05. Supersedes the v1 concept (manual activity database with distance-sorted lists).*

## Problem Statement

How might we get a family with kids from "we have free time right now" to out the door — without decision fatigue and without anyone maintaining a database?

## What v1 taught us

v1 is a hand-curated address book with a distance sorter. Its two fatal flaws feed each other: it doesn't help decide (it's a list, not an answer), and it stays sparse because every entry is manual data entry. Any redo that keeps manual entry as the primary data source dies the same death.

## Recommended Direction

Rebuild Freizeit as a **context-aware concierge**. The home screen answers one question with **three ranked suggestion cards**. The inventory comes from OpenStreetMap automatically; the family's only data duty is a one-tap verdict after visits.

### Decisions (grilled and locked)

**Data foundation**
- Inventory extracted from the **precut OSM .pbf files maintained for the Velometrics/Ride-Graph project** (re-downloaded periodically there anyway) via a desktop preprocessing step that emits an app-importable POI file. No runtime Overpass dependency — Overpass queries are too slow (known from Velometrics). Suggestions always serve from the Room cache — stale beats spinner.
- Refresh cycle: rerun the extraction whenever the Velometrics .pbf files are refreshed; import the new POI file into the app (replaces the old favorites-anchored Overpass refresh model).
- Places keyed by OSM type+id. Refresh is an upsert. **Verdicted places survive upstream disappearance** (kept + flagged "no longer in OSM"; verdict rows snapshot name/coords/category). Unverdicted vanished places are dropped.
- **POIs only**: playground, park, café, restaurant, ice cream. Walk/jogging/cycling routes are cut (v1's own data contained zero of them); reconsidered in v2 only if missed.
- **Start clean** — no v1 data importer; Overpass re-discovers everything that matters.

**Suggestion engine**
- **Zero-input default**: cards appear immediately assuming *now / ~3 hours / kids along*. Two override chips (time, who) re-rank on demand. Never a question before an answer.
- **Hard filters** remove embarrassments: closed now (where hours known), outdoor in bad weather, unreachable in the time budget.
- **Transparent soft score** ranks survivors: distance decay, weather-fit bonus (Open-Meteo), ❤️ big boost, 👍 small boost, 👎 excluded, small novelty bonus. At least two categories among the three cards.
- **Reason line on every card** ("Open · 12 min by bike · sunny 3 more hours · you ❤️ this") — turns a bad suggestion into a bug report instead of an uninstall.
- **Verdict-aware cooldowns**: recently visited rests ~2 weeks, but ❤️ places only ~2 days — kids' rituals (same playground every Saturday) are a feature, not a rut. Rerolled cards get a mild penalty until tomorrow.

**Verdict capture**
- "Go" on a card opens details/navigation and records an intent. Next app open (≥2 h later) shows a one-tap banner: "Were you at X? 👍 👎 ❤️ · didn't go."
- No geofencing, no background location, no accounts. Rating also possible anytime from a place's detail sheet.

**UX surface — three screens, no more**
1. **Home** — weather strip, pending-verdict banner, three cards, reroll.
2. **Explore** — map + filterable list of the cached inventory (category chips, ❤️ filter). Absorbs v1's map view, category browsing, and the "places we love" memory.
3. **Settings** — favorite locations / import areas, data export, about.
- Place detail is a bottom sheet over either surface, verdict buttons always on it. v1's random swiper dies — the cards are the random.

**Engineering**
- In-place rewrite on a `v2` branch: delete old sources, fresh scaffold. Git history preserves v1.
- Kotlin + Jetpack Compose + Material 3, Room, single module, plain ViewModels with manual DI (no Hilt at this size), Retrofit/Ktor for the two JSON APIs (Overpass, Open-Meteo — both free and keyless), **osmdroid retained** via Compose interop for the Explore map and offline tiles. minSdk 26+.

## Key Assumptions to Validate (before writing Kotlin)

- [ ] **OSM coverage near Aachen / South Limburg is good enough** for the 5 POI categories — measure directly while building the .pbf extraction script (count per category from the Velometrics extracts).
- [ ] **Opening-hours coverage is usable** — same extraction run; if <50 % of POIs have hours, demote "open now" from hard filter to badge.
- [ ] **The heuristic produces acceptable suggestions** — run the scoring against the extracted POI data and eyeball ~20 scenarios (rainy Tuesday 16:00, sunny Saturday 10:00, …) before building UI.
- [ ] **Verdicts actually get tapped** — observable within weeks of family use; if not, the app still works, just stays generic.

## MVP Scope

One screen done well: weather strip + three suggestion cards with reason lines + reroll + detail bottom sheet with "Go" and verdict buttons + next-open verdict banner. Overpass import for 5 categories around one favorite location. Explore and Settings can follow the first working Home screen. The MVP tests the riskiest assumption: *do we accept the suggestions and leave the house?*

## Not Doing (and Why)

- **Ausflug Composer (multi-stop itineraries)** — most over-engineerable idea on the table; earns consideration only after weekly usage is real.
- **Kid-Picks Mode (wheel / picture cards)** — promising v2 feature, but it needs a trusted suggestion engine underneath first.
- **Full logbook (notes, photos)** — habit-dependent; the 👍/👎/❤️ atom captures the value without the journaling graveyard.
- **Real routing / transit times** — Haversine estimates survive; 4-minute errors don't change family decisions.
- **Geofencing / background location** — heavy machinery against the app's no-surveillance ethos; the next-open prompt does the job.
- **Manual entry as a primary flow** — it's what killed v1. Entry stays possible but buried.
- **Accounts, sync, sharing, Play Store** — no backend by constraint; household = one device for now.

## Open Questions (deferred, not blocking)

- Category set beyond the initial five (museums? indoor play halls? swimming?) — add per Overpass tag once the engine works.
- Multi-device household sync — only if single-device proves limiting; would need to stay backendless (e.g., file-based export/import already covers 80 %).
