"""POI extraction for Freizeit 2.0 — reads the Velometrics/Ride-Graph OSM
extracts and emits an app-importable POI JSON plus a coverage report.

Reads every top-level ``*.pbf`` in the Ride-Graph ``data/pbf/`` directory.
IMPORTANT: it must read the FULL extracts, not ``data/pbf/precut/`` — the
precut cache is filtered to highway data and contains no POIs.

All categories in ``CATEGORIES`` are extracted and each POI carries its
``category`` field; the Freizeit app decides at runtime which categories to
show. To add a category later, add one line to ``CATEGORIES`` and re-run.

Requires the ``osmium`` package (available in the Ride-Graph venv):

    C:/Users/bob22/PycharmProjects/Ride-Graph/.venv/Scripts/python.exe \
        tools/poi_extraction/extract_pois.py

Re-running after a .pbf refresh regenerates the output file in place.
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

# category name -> (OSM key, OSM value). Order is priority when an object
# matches more than one category.
CATEGORIES: dict[str, tuple[str, str]] = {
    "playground": ("leisure", "playground"),
    "park": ("leisure", "park"),
    "cafe": ("amenity", "cafe"),
    "restaurant": ("amenity", "restaurant"),
    "ice_cream": ("amenity", "ice_cream"),
}

ADDRESS_TAGS = {
    "addr:street": "street",
    "addr:housenumber": "housenumber",
    "addr:postcode": "postcode",
    "addr:city": "city",
}

DEFAULT_PBF_DIR = Path(r"C:\Users\bob22\PycharmProjects\Ride-Graph\data\pbf")
DEFAULT_OUT = Path(__file__).resolve().parents[2] / "data" / "pois.json"


def categorize(tags: dict) -> str | None:
    for category, (key, value) in CATEGORIES.items():
        if tags.get(key) == value:
            return category
    return None


def build_record(osm_id: str, category: str, tags: dict,
                 lat: float, lon: float) -> dict:
    record = {
        "id": osm_id,
        "category": category,
        "lat": round(lat, 7),
        "lon": round(lon, 7),
    }
    if tags.get("name"):
        record["name"] = tags["name"]
    if tags.get("opening_hours"):
        record["opening_hours"] = tags["opening_hours"]
    for osm_key, field in ADDRESS_TAGS.items():
        if tags.get(osm_key):
            record[field] = tags[osm_key]
    return record


def area_centroid(area) -> tuple[float, float] | None:
    """Mean of all outer-ring node coordinates (good enough for POI pins)."""
    lat_sum = lon_sum = 0.0
    count = 0
    for ring in area.outer_rings():
        nodes = [n for n in ring if n.location.valid()]
        # closed rings repeat the first node at the end; drop the duplicate
        if len(nodes) > 1 and (nodes[0].ref == nodes[-1].ref):
            nodes = nodes[:-1]
        for n in nodes:
            lat_sum += n.location.lat
            lon_sum += n.location.lon
            count += 1
    if count == 0:
        return None
    return lat_sum / count, lon_sum / count


def extract_file(pbf_path: Path) -> list[dict]:
    """One streaming pass: POI nodes directly, ways/relations via assembled
    areas (closed ways and multipolygon relations both surface as Areas)."""
    import osmium
    from osmium.filter import KeyFilter

    filter_keys = sorted({key for key, _ in CATEGORIES.values()})
    # Ways/relations must be in the read mask to feed the area assembler.
    # Raw Ways are skipped below: a closed POI way arrives twice (as Way and
    # as Area), and only the Area has resolved geometry.
    processor = (
        osmium.FileProcessor(str(pbf_path))
        .with_areas(KeyFilter(*filter_keys))
        .with_filter(KeyFilter(*filter_keys))
    )

    records: list[dict] = []
    for obj in processor:
        if not isinstance(obj, (osmium.osm.Node, osmium.osm.Area)):
            continue
        tags = dict(obj.tags)
        category = categorize(tags)
        if category is None:
            continue

        if isinstance(obj, osmium.osm.Node):
            if not obj.location.valid():
                continue
            records.append(build_record(
                f"node/{obj.id}", category, tags,
                obj.location.lat, obj.location.lon))
        elif isinstance(obj, osmium.osm.Area):
            centroid = area_centroid(obj)
            if centroid is None:
                continue
            osm_type = "way" if obj.from_way() else "relation"
            records.append(build_record(
                f"{osm_type}/{obj.orig_id()}", category, tags,
                centroid[0], centroid[1]))
    return records


def print_report(pois: list[dict], duplicates: int) -> None:
    print()
    print("Coverage report")
    print(f"{'category':<12} {'total':>7} {'% named':>9} {'% hours':>9}")
    print("-" * 40)
    for category in CATEGORIES:
        subset = [p for p in pois if p["category"] == category]
        total = len(subset)
        named = sum(1 for p in subset if p.get("name"))
        hours = sum(1 for p in subset if p.get("opening_hours"))
        pct = lambda n: f"{100 * n / total:8.1f}%" if total else "       -"
        print(f"{category:<12} {total:>7} {pct(named)} {pct(hours)}")
    print("-" * 40)
    print(f"{'all':<12} {len(pois):>7}   ({duplicates} cross-extract duplicates dropped)")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--pbf-dir", type=Path, default=DEFAULT_PBF_DIR,
                        help="Directory with the full OSM .pbf extracts "
                             "(NOT the precut/ cache)")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT,
                        help="Output POI JSON path")
    args = parser.parse_args()

    pbf_paths = sorted(args.pbf_dir.glob("*.pbf"))
    if not pbf_paths:
        print(f"No .pbf files found in {args.pbf_dir}", file=sys.stderr)
        return 1

    by_id: dict[str, dict] = {}
    duplicates = 0
    for pbf_path in pbf_paths:
        print(f"Extracting {pbf_path.name} …", flush=True)
        records = extract_file(pbf_path)
        new = 0
        for record in records:
            if record["id"] in by_id:
                duplicates += 1
            else:
                by_id[record["id"]] = record
                new += 1
        print(f"  {len(records)} POIs ({new} new)")

    pois = list(by_id.values())
    output = {
        "generated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "sources": [p.name for p in pbf_paths],
        "categories": list(CATEGORIES),
        "pois": pois,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, separators=(",", ":"))
    print(f"\nWrote {len(pois)} POIs -> {args.out}")

    print_report(pois, duplicates)
    return 0


if __name__ == "__main__":
    sys.exit(main())
