# POI extraction (issue #1)

Desktop preprocessing script for Freizeit 2.0. Reads the OSM `.pbf` extracts
maintained for the Velometrics/Ride-Graph project and emits `data/pois.json`
(app-importable) plus a per-category coverage report on stdout. This script
IS the data pipeline — no runtime Overpass in the app.

## Run

Any Python 3.10+ with the `osmium` package works; the Ride-Graph venv
already has it:

```powershell
& C:\Users\bob22\PycharmProjects\Ride-Graph\.venv\Scripts\python.exe `
    tools\poi_extraction\extract_pois.py
```

Defaults: reads `C:\Users\bob22\PycharmProjects\Ride-Graph\data\pbf\*.pbf`,
writes `data\pois.json` (git-ignored — regenerate on demand). Override with
`--pbf-dir` / `--out`.

Re-run the same command whenever the Ride-Graph `.pbf` files are refreshed,
then re-import the JSON into the app.

## Which .pbf files

The **full extracts** at the top level of `Ride-Graph\data\pbf\`
(`koeln-regbez-*.osm.pbf`, `limburg-*.osm.pbf`, `wallonia_*_community.pbf`).
The `precut\` subdirectory must NOT be used: those files are Ride-Graph's
routing cache, filtered to highway data — they contain no POIs (see
Ride-Graph `docs/CHANGELOG.md`, AP5 notes). The files stay where they are;
nothing is copied into this repo.

## Categories

The script extracts two kinds of categories (checked in this order, first
match wins) and tags each POI with its `category`. Filtering/toggling
happens **in the app** — enabling another category later is one added line
and a re-run, no app data-format change.

- **Exact** (`EXACT_CATEGORIES` in `extract_pois.py`) — a specific OSM
  `key=value` pair, no `name` tag required. Original 5 (issue #1):
  playground, park, cafe, restaurant, ice_cream. Unchanged since issue #1.
- **Coarse** (`COARSE_CATEGORIES`) — any object carrying the given OSM key,
  *any* value, but only if it also has a non-empty `name` tag. Added by
  issue #14, one per top-level OSM key: shop, tourism, leisure_other,
  office, craft, historic. `leisure_other` is `leisure=*` minus the values
  already claimed by playground/park. The name requirement exists because a
  bare key match on these keys pulls in a lot of unnamed street furniture
  (benches, vending machines, waste baskets tagged `shop=vacant`, etc.) that
  isn't a place worth showing on the map.

To add another exact category: one line in `EXACT_CATEGORIES`. To add
another coarse (name-required) category: one line in `COARSE_CATEGORIES`.
Either way, re-run the script.

## Output format

```json
{
  "generated": "2026-07-05T17:06:13+00:00",
  "sources": ["koeln-regbez-260604.osm.pbf", "..."],
  "categories": ["playground", "park", "cafe", "restaurant", "ice_cream",
                 "shop", "tourism", "leisure_other", "office", "craft",
                 "historic"],
  "pois": [
    {
      "id": "node/286560726",
      "category": "restaurant",
      "lat": 50.4261098,
      "lon": 6.2054792,
      "name": "Bütgenbacher Hof",
      "opening_hours": "Mo-Su 11:00-22:00",
      "street": "Marktplatz",
      "housenumber": "8",
      "postcode": "4750",
      "city": "Bütgenbach"
    }
  ]
}
```

`id` is OSM `type/id` (node/way/relation) — the app's stable place key.
Way/relation coordinates are outer-ring centroids. Optional fields (`name`,
`opening_hours`, address parts) are omitted when untagged. POIs appearing in
overlapping extracts are deduplicated by `id`.
