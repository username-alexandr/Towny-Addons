#!/usr/bin/env python3
"""Install the curated city progression in the Builds catalog without reformatting prices."""
from pathlib import Path
import re
import sys

REQUIRES = {
    "forge": {"town_hall": 1}, "barracks": {"town_hall": 1},
    "market": {"town_hall": 1}, "great_library": {"town_hall": 1},
    "agrarian_complex": {"town_hall": 1}, "roads": {"town_hall": 1},
    "warehouse": {"town_hall": 1},
    "miners_guild": {"forge": 1}, "temple": {"town_hall": 2},
    "infirmary": {"agrarian_complex": 1}, "fire_station": {"roads": 1, "warehouse": 1},
    "tavern_inn": {"market": 1}, "city_bank": {"market": 2, "warehouse": 1},
    "embassy": {"town_hall": 2, "roads": 1}, "museum": {"great_library": 1},
    "shipyard": {"roads": 2, "market": 2}, "university": {"great_library": 2},
    "courthouse": {"town_hall": 2, "great_library": 1},
    "aqueduct": {"roads": 2, "agrarian_complex": 1},
    "workshop": {"forge": 1, "warehouse": 1},
    "watch_fortress": {"barracks": 2}, "hunting_lodge": {"agrarian_complex": 1},
    "fishing_harbor": {"market": 1, "roads": 1},
    "post_station": {"roads": 2}, "observatory": {"great_library": 2},
    "prison": {"courthouse": 1, "barracks": 1},
    "sawmill": {"warehouse": 1}, "quarry": {"miners_guild": 1},
    "foundry": {"forge": 2, "miners_guild": 1},
    "mill": {"agrarian_complex": 1}, "bakery": {"mill": 1},
    "apiary": {"agrarian_complex": 1},
    "caravanserai": {"post_station": 1, "market": 2},
    "auction": {"market": 2, "city_bank": 1},
    "merchant_guild": {"market": 2, "caravanserai": 1},
    "cargo_terminal": {"warehouse": 2, "shipyard": 1},
    "water_tower": {"aqueduct": 1}, "sewer": {"aqueduct": 1},
    "baths": {"water_tower": 1, "sewer": 1},
    "bridge_service": {"roads": 2, "quarry": 1},
    "reservoir": {"aqueduct": 2}, "guard": {"barracks": 1},
    "arsenal": {"barracks": 2, "forge": 1},
    "armory": {"arsenal": 1, "foundry": 1},
    "watchtower": {"guard": 1},
    "fortress_gate": {"watchtower": 1, "quarry": 1},
    "counterintel": {"guard": 2, "courthouse": 1},
    "alchemy": {"great_library": 2, "forge": 1},
    "archive": {"great_library": 1},
    "cartography": {"archive": 1, "post_station": 1},
    "research": {"university": 2, "alchemy": 1},
    "botanical": {"university": 1, "agrarian_complex": 2},
    "theater": {"tavern_inn": 1}, "square": {"roads": 1},
    "arena": {"square": 1, "barracks": 2},
    "gallery": {"museum": 1, "theater": 1},
    "park": {"square": 1, "agrarian_complex": 1},
    "guild_house": {"workshop": 1, "market": 1},
    "memorial": {"square": 1, "temple": 1},
    "shelter": {"infirmary": 1, "residential_quarter": 1},
    "cathedral": {"temple": 2},
    "printing_house": {"archive": 1, "workshop": 1},
    "forestry": {"sawmill": 1},
    "customs": {"market": 2, "post_station": 1},
    "trade_port": {"shipyard": 2, "customs": 1},
    "mint": {"city_bank": 2, "foundry": 1},
    "stables": {"caravanserai": 1},
    "fortress_wall": {"fortress_gate": 1, "quarry": 1},
    "city_moat": {"fortress_wall": 1, "aqueduct": 1},
    "archery_range": {"barracks": 2, "hunting_lodge": 1},
    "port_fort": {"trade_port": 1, "watch_fortress": 1},
    "census_bureau": {"town_hall": 2, "archive": 1},
    "insurance_chamber": {"city_bank": 2, "courthouse": 1},
    "pumping_station": {"water_tower": 1},
    "irrigation_station": {"aqueduct": 1, "agrarian_complex": 2},
    "recycling_yard": {"workshop": 1, "sewer": 1},
    "dam": {"reservoir": 2, "pumping_station": 1},
    "army": {"barracks": 2, "arsenal": 1},
    "residential_quarter": {"town_hall": 2, "roads": 2},
}

CATEGORIES = {
    "ADMINISTRATION": "town_hall embassy courthouse prison archive census_bureau",
    "PRODUCTION": "forge miners_guild agrarian_complex workshop hunting_lodge fishing_harbor sawmill quarry foundry mill bakery apiary alchemy forestry recycling_yard seed_vault industrial_works",
    "TRADE": "market city_bank post_station warehouse caravanserai auction merchant_guild cargo_terminal customs trade_port mint insurance_chamber trade_exchange admiralty",
    "INFRASTRUCTURE": "residential_quarter shipyard aqueduct water_tower sewer baths roads bridge_service reservoir pumping_station irrigation_station dam",
    "DEFENSE": "army barracks fire_station watch_fortress guard arsenal armory watchtower fortress_gate counterintel stables fortress_wall city_moat archery_range port_fort citadel",
    "SCIENCE": "great_library museum university observatory cartography research botanical printing_house academy_of_sciences",
    "CULTURE": "temple infirmary tavern_inn theater square arena gallery park guild_house memorial shelter cathedral pilgrimage_center",
    "ENERGY": "water_wheel generator power_station",
}
CATEGORY_BY_ID = {id: category for category, names in CATEGORIES.items() for id in names.split()}


def patch(source: str) -> str:
    blocks = list(re.finditer(r"(?m)^  ([a-z0-9_]+):\n(?=    )", source))
    assert blocks and blocks[0].group(1) == "town_hall"
    # Stop at the next top-level section (wonders); retain all original comments and prices.
    boundary = source.find("\nwonders:")
    if boundary < 0:
        boundary = len(source)
    blocks = [b for b in blocks if b.start() < boundary]
    ids = {b.group(1) for b in blocks}
    assert set(CATEGORY_BY_ID) == ids
    assert set(REQUIRES).issubset(ids)
    assert set(REQUIRES) | {"town_hall"} | {"water_wheel", "generator", "power_station", "trade_exchange", "citadel", "seed_vault", "industrial_works", "academy_of_sciences", "admiralty", "pilgrimage_center"} == ids
    for name, deps in REQUIRES.items():
        assert name not in deps and set(deps).issubset(ids)
        assert all(1 <= n <= 5 for n in deps.values())
    for match in reversed(blocks):
        name = match.group(1)
        end = next((item.start() for item in blocks if item.start() > match.start()), boundary)
        part = source[match.start():end]
        level = part.find("\n    levels:")
        assert level > 0, name
        addition = ""
        if "\n    category:" not in part:
            addition += f"\n    category: {CATEGORY_BY_ID[name]}"
        if name in REQUIRES:
            assert "\n    requires:" not in part, f"Existing requirements for {name}"
            addition += "\n    requires:\n" + "".join(f"      {key}: {value}\n" for key, value in REQUIRES[name].items()).rstrip("\n")
        source = source[:match.start()+level] + addition + source[match.start()+level:]
    return source


if __name__ == "__main__":
    original = Path(sys.argv[1]).read_text(encoding="utf-8")
    output = Path(sys.argv[2])
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(patch(original), encoding="utf-8")
