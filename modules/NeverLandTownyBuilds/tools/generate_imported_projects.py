#!/usr/bin/env python3
"""Generate balanced projects.yml entries for the reviewed model expansion."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path

from import_building_models import PALETTE, ROLE_MATERIALS


ICONS = {
    "sawmill": "IRON_AXE", "quarry": "STONE_PICKAXE", "foundry": "BLAST_FURNACE",
    "mill": "WHEAT", "bakery": "BREAD", "apiary": "HONEYCOMB", "warehouse": "CHEST",
    "caravanserai": "LEAD", "auction": "EMERALD", "merchant_guild": "EMERALD_BLOCK",
    "cargo_terminal": "MINECART", "water_tower": "WATER_BUCKET", "sewer": "CAULDRON",
    "baths": "HEART_OF_THE_SEA", "roads": "STONE_BRICKS", "bridge_service": "OAK_FENCE",
    "reservoir": "BLUE_CONCRETE", "guard": "SHIELD", "arsenal": "CROSSBOW",
    "armory": "SMITHING_TABLE", "watchtower": "SPYGLASS", "fortress_gate": "IRON_DOOR",
    "counterintel": "ENDER_EYE", "alchemy": "BREWING_STAND", "archive": "BOOK",
    "cartography": "FILLED_MAP", "research": "SCULK_SENSOR", "botanical": "FLOWERING_AZALEA",
    "theater": "RED_BANNER", "square": "BELL", "arena": "IRON_SWORD", "gallery": "PAINTING",
    "park": "OAK_SAPLING", "guild_house": "NAME_TAG", "memorial": "CHISELED_STONE_BRICKS",
    "shelter": "WHITE_BED", "cathedral": "TOTEM_OF_UNDYING",
}

DESCRIPTIONS = {
    "sawmill": "Лесной производственный двор и склад древесины.",
    "quarry": "Городская добыча и сортировка каменных пород.",
    "foundry": "Переработка руды и металлических ресурсов.",
    "mill": "Мельничный комплекс для зерна и фермерских задач.",
    "bakery": "Городское производство и хранение продовольствия.",
    "apiary": "Производство мёда, сот и лечебных запасов.",
    "warehouse": "Центральный узел хранения городских ресурсов.",
    "caravanserai": "Опорная точка сухопутных торговых маршрутов.",
    "auction": "Площадка городских торгов и крупных сделок.",
    "merchant_guild": "Торговые договоры, экспорт и купеческие связи.",
    "cargo_terminal": "Логистический узел склада, рынка и маршрутов.",
    "water_tower": "Сбор и передача воды в городскую систему.",
    "sewer": "Подземная санитарная инфраструктура города.",
    "baths": "Общественные бани для здоровья жителей.",
    "roads": "Линейный дорожный участок по направлению взгляда.",
    "bridge_service": "Линейный мостовой участок через преграды.",
    "reservoir": "Накопление воды для служб и хозяйства города.",
    "guard": "Патрули, караульные помещения и защита жителей.",
    "arsenal": "Защищённый городской запас оружия и щитов.",
    "armory": "Изготовление и обслуживание экипировки стражи.",
    "watchtower": "Раннее обнаружение набегов и опасных событий.",
    "fortress_gate": "Контролируемый укреплённый вход в город.",
    "counterintel": "Защита города от шпионов и разведчиков.",
    "alchemy": "Лекарства, реагенты и алхимические исследования.",
    "archive": "Хранилище законов, договоров и городской истории.",
    "cartography": "Карты экспедиций, событий и дальних маршрутов.",
    "research": "Общие научные исследования и развитие города.",
    "botanical": "Редкие растения для лечения и алхимии.",
    "theater": "Культурные представления и городские фестивали.",
    "square": "Место собраний, объявлений и праздников.",
    "arena": "Турниры игроков и состязания между городами.",
    "gallery": "Художественные коллекции и престиж города.",
    "park": "Общественный сад и пространство для отдыха.",
    "guild_house": "Общий дом городских профессий и гильдий.",
    "memorial": "Память о мэрах, войнах и важных событиях.",
    "shelter": "Помощь новичкам и пострадавшим жителям.",
    "cathedral": "Развитый духовный и общественный центр.",
}

COLORS = {
    "production": "&#E89A4A", "trade": "&#FFD45B", "infrastructure": "&#58CFE8",
    "security": "&#C0C7D1", "science": "&#8C8CFF", "culture": "&#DFA7FF",
}

EFFECTS = {
    "production": [[], ["HASTE:1"], ["HASTE:1"], ["HASTE:2"], ["HASTE:2", "SPEED:1"]],
    "trade": [[], ["LUCK:1"], ["LUCK:1", "SPEED:1"], ["LUCK:2", "SPEED:1"], ["LUCK:2", "SPEED:2"]],
    "infrastructure": [[], ["SPEED:1"], ["SPEED:1", "RESISTANCE:1"], ["SPEED:2", "RESISTANCE:1"], ["SPEED:2", "RESISTANCE:2"]],
    "security": [[], ["RESISTANCE:1"], ["RESISTANCE:1", "STRENGTH:1"], ["RESISTANCE:2", "STRENGTH:1"], ["RESISTANCE:2", "STRENGTH:2"]],
    "science": [[], ["NIGHT_VISION:1"], ["NIGHT_VISION:1", "LUCK:1"], ["NIGHT_VISION:1", "LUCK:2"], ["NIGHT_VISION:1", "LUCK:2", "HASTE:1"]],
    "culture": [[], ["LUCK:1"], ["LUCK:1", "REGENERATION:1"], ["LUCK:2", "REGENERATION:1"], ["LUCK:2", "REGENERATION:2"]],
}


def yaml_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def resource_lines(model: dict, stage: int) -> list[str]:
    materials = Counter()
    for block in model["blocks"]:
        if int(block["stage"]) != stage:
            continue
        role = str(block["role"])
        material = ROLE_MATERIALS.get(role, PALETTE[str(block["material"])])
        if material == "WATER":
            material = "WATER_BUCKET"
        materials[material] += 1
    chosen = []
    for material, amount in materials.most_common(3):
        if material == "WATER_BUCKET":
            amount = max(1, min(16, (amount + 15) // 16))
        chosen.append(f"{material}:{amount}")
    return chosen


def render(root: Path) -> str:
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    lines = ["  # BEGIN GENERATED 37-MODEL EXPANSION"]
    content_slots = [row * 9 + column for row in range(1, 5) for column in range(1, 8)]
    for index, entry in enumerate(manifest["models"]):
        model = json.loads((root / entry["file"]).read_text(encoding="utf-8"))
        project_id = entry["id"]
        base = max(3500, min(10000, round(entry["blocks"] * 10 / 500) * 500))
        lines.extend([
            f"  {project_id}:",
            f"    name: {yaml_string(COLORS[entry['category']] + '&l' + entry['name'])}",
            f"    icon: {ICONS[project_id]}",
            "    itemsadder-icon: \"\"",
            f"    slot: {content_slots[(25 + index) % len(content_slots)]}",
            f"    order: {100 + index}",
            f"    description: [{yaml_string(DESCRIPTIONS[project_id])}, {yaml_string('Пять физических этапов строительства.')}]",
            "    levels:",
        ])
        for stage in range(1, 6):
            resources = ", ".join(yaml_string(value) for value in resource_lines(model, stage))
            effects = ", ".join(yaml_string(value) for value in EFFECTS[entry["category"]][stage - 1])
            lines.append(
                f"      {stage}: {{money: {base * (2 ** (stage - 1))}, bonus-blocks: {[1, 2, 3, 4, 6][stage - 1]}, "
                f"resources: [{resources}], effects: [{effects}]}}"
            )
    lines.append("  # END GENERATED 37-MODEL EXPANSION")
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("model_root", type=Path)
    parser.add_argument("projects_yml", type=Path)
    args = parser.parse_args()
    text = args.projects_yml.read_text(encoding="utf-8")
    begin = "  # BEGIN GENERATED 37-MODEL EXPANSION"
    end = "  # END GENERATED 37-MODEL EXPANSION"
    if begin in text:
        prefix, remainder = text.split(begin, 1)
        _, suffix = remainder.split(end, 1)
        text = prefix + render(args.model_root) + suffix
    else:
        text = text.replace("\nwonders:\n", "\n" + render(args.model_root) + "\n\nwonders:\n", 1)
    args.projects_yml.write_text(text, encoding="utf-8")
    print("Generated 37 project definitions")


if __name__ == "__main__":
    main()
