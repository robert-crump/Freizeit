"""README screenshot fixture (issue #70) — cuts a small set of real OSM POIs
around Aachen Markt out of the full ``data/pois.json`` and writes the
androidTest asset the ``readmeScreenshots`` capture test imports.

The output is a normal POI export (``PoiJsonParser`` reads it through the
app's real import path) plus a ``demo`` section the parser ignores: the
places the test gives a verdict, and the days ago each was visited. Demo
places are picked here by name, so fixture and seed data can't drift apart.

    python tools/poi_extraction/make_readme_fixture.py

Re-run after regenerating ``data/pois.json``; it fails if a demo place is
missing or ambiguous.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

# Aachen Markt — the emulator's faked location during the capture.
CENTER_LAT = 50.7753
CENTER_LON = 6.0839
RADIUS_M = 2000.0

# Nearest POIs kept per category, demo places on top.
PER_CATEGORY = 15

# name -> (verdict, category, days-ago of each visit). All of them are open
# (or have no hours, so never "closed") on Saturday 11:00, the faked clock.
DEMO_PLACES: dict[str, tuple[str, str, list[int]]] = {
    "Eiscafé Tasin": ("favorite", "ice_cream", [6, 20, 41, 55]),
    "Café Dom": ("favorite", "cafe", [2, 16, 33]),
    "Hexenhof": ("favorite", "restaurant", [9, 47]),
    "Elisengarten": ("favorite", "park", [13, 27]),
    "Couven-Museum": ("favorite", "tourism", [52]),
    "Centre Charlemagne": ("want_to_go", "tourism", []),
    "Mehlwölkchen": ("want_to_go", "cafe", []),
    "Spielplatz Bergdriesch": ("want_to_go", "playground", []),
}


def distance_m(lat: float, lon: float) -> float:
    dy = (lat - CENTER_LAT) * 111_320
    dx = (lon - CENTER_LON) * 111_320 * math.cos(math.radians(CENTER_LAT))
    return math.hypot(dx, dy)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--pois", type=Path, default=REPO / "data" / "pois.json")
    parser.add_argument(
        "--out", type=Path,
        default=REPO / "app" / "src" / "androidTest" / "assets" / "readme-pois.json",
    )
    args = parser.parse_args()

    source = json.loads(args.pois.read_text(encoding="utf-8"))
    nearby = sorted(
        (p for p in source["pois"] if distance_m(p["lat"], p["lon"]) <= RADIUS_M),
        key=lambda p: (distance_m(p["lat"], p["lon"]), p["id"]),
    )

    demo = []
    for name, (verdict, category, visits) in DEMO_PLACES.items():
        matches = [p for p in nearby if p.get("name") == name and p["category"] == category]
        if len(matches) != 1:
            print(f"Demo place {name!r} ({category}) matched {len(matches)} POIs", file=sys.stderr)
            return 1
        demo.append({"id": matches[0]["id"], "name": name, "verdict": verdict, "visits_days_ago": visits})
    demo_ids = {d["id"] for d in demo}

    picked = [p for p in nearby if p["id"] in demo_ids]
    for category in source["categories"]:
        in_category = [p for p in nearby if p["category"] == category and p["id"] not in demo_ids]
        picked += in_category[:PER_CATEGORY]
    picked.sort(key=lambda p: p["id"])

    fixture = {
        "generated": source["generated"],
        "sources": source.get("sources", []),
        "categories": source["categories"],
        "demo": demo,
        "pois": picked,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(fixture, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"Wrote {len(picked)} POIs ({len(demo)} demo places) to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
