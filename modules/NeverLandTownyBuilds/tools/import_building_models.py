#!/usr/bin/env python3
"""Convert the reviewed JSON model pack into dependency-free NLTB resources.

The JSON files remain the human-readable source of truth in the release model
archive.  The generated format is deliberately tiny and can be parsed with the
JDK alone at server startup, so the plugin does not acquire a JSON runtime
dependency merely to load block coordinates.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


PALETTE = {
    "stone": "STONE_BRICKS",
    "wood": "SPRUCE_PLANKS",
    "roof": "DEEPSLATE_TILES",
    "brick": "BRICKS",
    "metal": "IRON_BLOCK",
    "plant": "OAK_LEAVES",
    "glass": "CYAN_STAINED_GLASS",
    "light": "SEA_LANTERN",
    "water": "WATER",
    "sand": "SMOOTH_SANDSTONE",
}

ROLE_MATERIALS = {
    "door": "SPRUCE_DOOR",
    "window": "CYAN_STAINED_GLASS_PANE",
    "rail": "SPRUCE_FENCE",
    "light": "LANTERN",
    "plant": "OAK_LEAVES",
    "water": "WATER",
}

# Blocks which cannot sensibly be placed as an ordinary full block are applied
# by the plugin after the resident-built structure for an upgrade is complete.
DECORATION_ROLES = {
    "awning", "detail", "door", "fountain", "light", "marking",
    "pattern", "plant", "rail", "statue", "water", "window",
}


def center(bounds: dict, axis: str) -> int:
    return (int(bounds["min"][axis]) + int(bounds["max"][axis])) // 2


def convert_model(source: Path, destination: Path) -> dict:
    model = json.loads(source.read_text(encoding="utf-8"))
    center_x = center(model["bounds"], "x")
    center_z = center(model["bounds"], "z")
    lines = [
        "# NeverLand Towny Builds imported model v1",
        f"# id={model['id']}",
        f"# name={model['name']}",
        f"# category={model['category']}",
        f"# kind={model['kind']}",
        f"# source-blocks={len(model['blocks'])}",
        "# x,y,z,stage,material,build-role,facing,half,hinge,source-role",
    ]
    coordinates: set[tuple[int, int, int]] = set()
    stages: set[int] = set()
    doors = 0
    roofs = 0
    for block in model["blocks"]:
        key = (int(block["x"]), int(block["y"]), int(block["z"]))
        if key in coordinates:
            raise ValueError(f"{model['id']}: duplicate coordinate {key}")
        coordinates.add(key)
        stage = int(block["stage"])
        stages.add(stage)
        role = str(block["role"])
        material = ROLE_MATERIALS.get(role, PALETTE[str(block["material"])])
        build_role = "DECORATION" if role in DECORATION_ROLES else "RESIDENT"
        data = block.get("block_data", {})
        facing = str(data.get("facing", "-")).upper()
        half = str(data.get("half", "-")).upper()
        hinge = str(data.get("hinge", "-")).upper()
        if role == "door":
            doors += 1
        if role == "roof":
            roofs += 1
        lines.append(",".join((
            str(key[0] - center_x), str(key[1]), str(key[2] - center_z),
            str(stage), material, build_role, facing, half, hinge, role,
        )))
    if stages != {1, 2, 3, 4, 5}:
        raise ValueError(f"{model['id']}: expected stages 1..5, got {sorted(stages)}")
    destination.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return {
        "id": model["id"],
        "name": model["name"],
        "category": model["category"],
        "kind": model["kind"],
        "blocks": len(model["blocks"]),
        "doors": doors,
        "roofs": roofs,
        "dimensions": model["dimensions"],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("model_root", type=Path, help="Extracted model-pack root")
    parser.add_argument("output", type=Path, help="Generated resource directory")
    args = parser.parse_args()
    manifest = json.loads((args.model_root / "manifest.json").read_text(encoding="utf-8"))
    args.output.mkdir(parents=True, exist_ok=True)
    catalog = []
    for entry in manifest["models"]:
        source = args.model_root / entry["file"]
        catalog.append(convert_model(source, args.output / f"{entry['id']}.nltb"))
    result = {
        "schema": 1,
        "source_version": manifest["version"],
        "models": catalog,
        "model_count": len(catalog),
        "total_blocks": sum(entry["blocks"] for entry in catalog),
    }
    (args.output / "catalog.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Imported {result['model_count']} models and {result['total_blocks']} blocks")


if __name__ == "__main__":
    main()
