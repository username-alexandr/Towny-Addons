#!/usr/bin/env python3
"""Render exact block-coordinate previews of the 17 proposed buildings.

The top-level model calls are read from ExpansionBlueprintGenerator.java, so the
preview and the reviewed Java design cannot silently drift apart.
"""

from __future__ import annotations

import json
import math
import re
import sys
import zipfile
from collections import OrderedDict
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


PROJECTS = OrderedDict([
    ("infirmary", ("Лазарет", "infirmary")),
    ("fire_station", ("Пожарная часть", "fireStation")),
    ("tavern_inn", ("Таверна и постоялый двор", "tavernInn")),
    ("city_bank", ("Городской банк", "cityBank")),
    ("embassy", ("Посольство", "embassy")),
    ("museum", ("Музей", "museum")),
    ("shipyard", ("Верфь", "shipyard")),
    ("university", ("Университет", "university")),
    ("courthouse", ("Суд", "courthouse")),
    ("aqueduct", ("Акведук", "aqueduct")),
    ("workshop", ("Мастерская", "workshop")),
    ("watch_fortress", ("Сторожевая крепость", "watchFortress")),
    ("hunting_lodge", ("Охотничий дом", "huntingLodge")),
    ("fishing_harbor", ("Рыбацкая гавань", "fishingHarbor")),
    ("post_station", ("Почтовая станция", "postStation")),
    ("observatory", ("Обсерватория", "observatory")),
    ("prison", ("Тюрьма", "prison")),
])

STAGE_NAMES = {
    "infirmary": ["Приёмный покой", "Палатное крыло", "Аптекарский дом", "Башня лекаря", "Лечебный сад"],
    "fire_station": ["Дежурный дом", "Пожарное депо", "Водяная башня", "Конюшня команды", "Учебный двор"],
    "tavern_inn": ["Таверна", "Гостевое крыло", "Кухня и погреб", "Конюшня", "Постоялый двор"],
    "city_bank": ["Расчётная палата", "Кассовый зал", "Хранилище", "Счётная башня", "Банковская площадь"],
    "embassy": ["Дом посланника", "Приёмный зал", "Гостевой флигель", "Дипломатическая башня", "Парадный двор"],
    "museum": ["Выставочный дом", "Западная галерея", "Восточная галерея", "Купольный зал", "Музейный двор"],
    "shipyard": ["Корабельный сарай", "Стапель", "Кран", "Склад снастей", "Корабельная гавань"],
    "university": ["Учебный корпус", "Библиотечное крыло", "Алхимическое крыло", "Башня знаний", "Университетский двор"],
    "courthouse": ["Судебная палата", "Зал заседаний", "Архив суда", "Башня закона", "Площадь правосудия"],
    "aqueduct": ["Водосборник", "Первая аркада", "Главная аркада", "Напорная башня", "Городской водовод"],
    "workshop": ["Дом мастера", "Производственный зал", "Склад материалов", "Подъёмный кран", "Ремесленный двор"],
    "watch_fortress": ["Воротный дом", "Крепостные стены", "Угловые башни", "Дозорная башня", "Укреплённый двор"],
    "hunting_lodge": ["Охотничья изба", "Трофейный зал", "Псарня", "Наблюдательная вышка", "Стрелковый двор"],
    "fishing_harbor": ["Дом рыбака", "Рыбный причал", "Рынок улова", "Портовый кран", "Малая гавань"],
    "post_station": ["Почтовый дом", "Сортировочное крыло", "Конюшня", "Часовая башня", "Станционный двор"],
    "observatory": ["Дом астронома", "Наблюдательная башня", "Купол", "Большой телескоп", "Звёздный двор"],
    "prison": ["Караульный корпус", "Камерный блок", "Внешняя стена", "Сторожевые башни", "Тюремный двор"],
}


class Builder:
    def __init__(self, target: int):
        self.target = target
        self.blocks: OrderedDict[tuple[int, int, int], dict] = OrderedDict()

    def _data(self, material, role, stage, **extra):
        result = {"material": material, "role": role, "stage": stage,
                  "facing": "NORTH", "axis": "Y", "half": None, "hinge": None}
        result.update(extra)
        return result

    def block(self, x, y, z, material, role, stage):
        if stage <= self.target and material != "AIR":
            self.blocks.setdefault((x, y, z), self._data(material, role, stage))

    def replace(self, x, y, z, material, role, stage):
        if stage > self.target or material == "AIR": return
        key = (x, y, z)
        old = self.blocks.get(key)
        if old is None or old["stage"] == stage:
            self.blocks[key] = self._data(material, role, stage)

    def axis(self, x, y, z, material, role, stage, axis):
        if stage <= self.target:
            self.blocks.setdefault((x, y, z), self._data(material, role, stage, axis=axis))

    def stair(self, x, y, z, material, face, stage):
        if stage <= self.target:
            self.blocks.setdefault((x, y, z), self._data(material, "DECORATION", stage,
                                                           facing=face, half="BOTTOM"))

    def door(self, x, y, z, material, face, hinge, stage):
        if stage > self.target: return
        self.blocks.setdefault((x, y, z), self._data(material, "DECORATION", stage,
                                                       facing=face, half="BOTTOM", hinge=hinge))
        self.blocks.setdefault((x, y + 1, z), self._data(material, "DECORATION", stage,
                                                           facing=face, half="TOP", hinge=hinge))

    def floor(self, x1, x2, z1, z2, y, material, stage):
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1): self.block(x, y, z, material, "RESIDENT", stage)

    def path(self, x1, x2, z1, z2, material, stage):
        self.floor(min(x1, x2), max(x1, x2), min(z1, z2), max(z1, z2), 0, material, stage)

    def ring(self, x1, x2, z1, z2, y, material, role, stage):
        for x in range(x1, x2 + 1):
            self.block(x, y, z1, material, role, stage); self.block(x, y, z2, material, role, stage)
        for z in range(z1 + 1, z2):
            self.block(x1, y, z, material, role, stage); self.block(x2, y, z, material, role, stage)

    def pillar(self, x, z, y1, y2, material, stage):
        for y in range(y1, y2 + 1): self.axis(x, y, z, material, "RESIDENT", stage, "Y")

    def house(self, x1, x2, z1, z2, wall_top, foundation, wall, frame, roof, door, stage, double_door):
        self.floor(x1, x2, z1, z2, 0, foundation, stage)
        center = (x1 + x2) // 2
        first, second = (center - 1 if double_door else center), center
        for y in range(1, wall_top + 1):
            for x in range(x1, x2 + 1):
                entrance = y <= 2 and (x == first or (double_door and x == second))
                if not entrance: self.block(x, y, z1, wall, "RESIDENT", stage)
                self.block(x, y, z2, wall, "RESIDENT", stage)
            for z in range(z1 + 1, z2):
                self.block(x1, y, z, wall, "RESIDENT", stage); self.block(x2, y, z, wall, "RESIDENT", stage)
        for x in (x1, x2):
            for z in (z1, z2): self.pillar(x, z, 1, wall_top, frame, stage)
        for x, y, z in ((x1 + 2, min(2, wall_top), z1), (x2 - 2, min(2, wall_top), z1),
                        (x1 + 2, min(2, wall_top), z2), (x2 - 2, min(2, wall_top), z2),
                        (x1, min(2, wall_top), (z1 + z2) // 2),
                        (x2, min(2, wall_top), (z1 + z2) // 2)):
            self.window(x, y, z, stage)
        if wall_top >= 5:
            for x, z in ((x1 + 2, z1), (x2 - 2, z1), (x1 + 2, z2), (x2 - 2, z2)):
                self.window(x, 4, z, stage)
        self.door(first, 1, z1, door, "NORTH", "LEFT", stage)
        if double_door: self.door(second, 1, z1, door, "NORTH", "RIGHT", stage)
        for x in range(first, second + 1): self.block(x, 0, z1 - 1, foundation, "RESIDENT", stage)
        self.gableRoof(x1, x2, z1, z2, wall_top + 1, roof, stage)
        self.gableWalls(x1, x2, z1, z2, wall_top, wall, stage)
        self.replace((x1 + x2) // 2, wall_top + 2, z1, "GLASS_PANE", "DECORATION", stage)
        self.replace((x1 + x2) // 2, wall_top + 2, z2, "GLASS_PANE", "DECORATION", stage)

    def window(self, x, y, z, stage):
        self.replace(x, y, z, "GLASS_PANE", "DECORATION", stage)
        if y >= 3: self.replace(x, y - 1, z, "GLASS_PANE", "DECORATION", stage)

    def gableRoof(self, x1, x2, z1, z2, y, roof, stage):
        left, right, layer = x1 - 1, x2 + 1, 0
        while left + layer < right - layer:
            roof_y = y + layer
            for z in range(z1 - 1, z2 + 2):
                self.stair(left + layer, roof_y, z, roof, "EAST", stage)
                self.stair(right - layer, roof_y, z, roof, "WEST", stage)
            layer += 1
        ridge = (left + right) // 2
        mapping = {"OAK_STAIRS": "OAK_PLANKS", "BIRCH_STAIRS": "BIRCH_PLANKS",
                   "SPRUCE_STAIRS": "SPRUCE_PLANKS", "DARK_OAK_STAIRS": "DARK_OAK_PLANKS",
                   "BRICK_STAIRS": "BRICKS", "STONE_BRICK_STAIRS": "STONE_BRICKS",
                   "DEEPSLATE_TILE_STAIRS": "DEEPSLATE_TILES",
                   "RED_NETHER_BRICK_STAIRS": "RED_NETHER_BRICKS",
                   "DARK_PRISMARINE_STAIRS": "DARK_PRISMARINE",
                   "CUT_COPPER_STAIRS": "COPPER_BLOCK"}
        for z in range(z1, z2 + 1): self.axis(ridge, y + layer - 1, z, mapping.get(roof, "STONE_BRICKS"), "DECORATION", stage, "Z")

    def gableWalls(self, x1, x2, z1, z2, wall_top, wall, stage):
        left_edge, right_edge = x1 - 1, x2 + 1
        for x in range(x1, x2 + 1):
            rise = min(x - left_edge, right_edge - x)
            for y in range(wall_top + 1, wall_top + rise + 1):
                self.block(x, y, z1, wall, "RESIDENT", stage)
                self.block(x, y, z2, wall, "RESIDENT", stage)

    def flatRoof(self, x1, x2, z1, z2, y, material, stage): self.floor(x1, x2, z1, z2, y, material, stage)

    def hipRoof(self, x1, x2, z1, z2, y, material, stage):
        layer = 0
        while x1 + layer <= x2 - layer and z1 + layer <= z2 - layer:
            self.ring(x1 + layer, x2 - layer, z1 + layer, z2 - layer, y + layer, material, "DECORATION", stage)
            layer += 1

    def tower(self, cx, cz, radius, height, wall, frame, stage):
        self.floor(cx - radius, cx + radius, cz - radius, cz + radius, 0, wall, stage)
        for y in range(1, height + 1): self.ring(cx - radius, cx + radius, cz - radius, cz + radius, y, wall, "RESIDENT", stage)
        for x in (cx - radius, cx + radius):
            for z in (cz - radius, cz + radius): self.pillar(x, z, 1, height, frame, stage)
        for y in range(3, height, 3):
            self.replace(cx, y, cz - radius, "GLASS_PANE", "DECORATION", stage)
            self.replace(cx, y, cz + radius, "GLASS_PANE", "DECORATION", stage)

    def classicalHall(self, x1, x2, z1, z2, height, foundation, wall, frame, roof, stage):
        self.house(x1, x2, z1, z2, height, foundation, wall, frame, roof, "IRON_DOOR", stage, True)

    def portico(self, x1, x2, z1, z2, height, columns, roof, stage):
        self.path(x1, x2, z1, z2, "SMOOTH_STONE", stage)
        for x in range(x1, x2 + 1, 3): self.pillar(x, z1, 1, height, columns, stage)
        self.pillar(x2, z1, 1, height, columns, stage)
        self.flatRoof(x1 - 1, x2 + 1, z1 - 1, z2, height + 1, roof, stage)
        self.stairway(max(x1, -4), min(x2, 4), z1 - 2, z1, "QUARTZ_STAIRS", stage)

    def stairway(self, x1, x2, z1, z2, material, stage):
        y = 0
        for z in range(z1, z2 + 1):
            for x in range(x1, x2 + 1): self.stair(x, y, z, material, "SOUTH", stage)
            if (z - z1) % 2 == 1: y += 1

    def gallery(self, x1, x2, z1, z2, foundation, wall, stage):
        self.house(x1, x2, z1, z2, 4, foundation, wall, "QUARTZ_PILLAR", "CUT_COPPER_STAIRS", "BIRCH_DOOR", stage, False)

    def dome(self, cx, cz, base_y, shell, glass, stage):
        radii = [5, 5, 4, 4, 3, 2, 1]
        for dy, radius in enumerate(radii):
            for x in range(-radius, radius + 1):
                for z in range(-radius, radius + 1):
                    distance = x * x + z * z
                    if distance <= radius * radius and distance >= (radius - 1) * (radius - 1):
                        material = glass if (x == 0 or z == 0) and dy > 0 else shell
                        self.block(cx + x, base_y + dy, cz + z, material, "DECORATION", stage)
        self.block(cx, base_y + len(radii), cz, "LIGHTNING_ROD", "DECORATION", stage)

    def domeSupports(self, cx, cz, base_y, material, stage):
        for x in (cx - 3, cx + 3):
            for z in (cz - 3, cz + 3):
                self.pillar(x, z, base_y, base_y + 2, material, stage)

    def cross(self, cx, cy, z, material, stage):
        for x in range(cx - 2, cx + 3): self.replace(x, cy, z, material, "DECORATION", stage)
        for y in range(cy - 2, cy + 3): self.replace(cx, y, z, material, "DECORATION", stage)

    def largeGate(self, x1, x2, y, z, material, stage):
        for x in range(x1, x2 + 1): self.door(x, y, z, material, "NORTH", "LEFT" if x == x1 else "RIGHT", stage)

    def chimney(self, x, z, y1, y2, material, stage):
        for y in range(y1, y2 + 1): self.block(x, y, z, material, "RESIDENT", stage)
        self.block(x, y2 + 1, z, "CAMPFIRE", "DECORATION", stage)

    def canopy(self, x1, x2, z1, z2, frame, roof, stage):
        self.floor(x1, x2, z1, z2, 0, "COBBLESTONE", stage)
        for x in (x1, x2):
            for z in (z1, z2): self.pillar(x, z, 1, 3, frame, stage)
        self.flatRoof(x1 - 1, x2 + 1, z1 - 1, z2 + 1, 4, roof, stage)

    def fencedYard(self, x1, x2, z1, z2, fence, ground, stage):
        self.floor(x1 + 1, x2 - 1, z1 + 1, z2 - 1, 0, ground, stage)
        self.ring(x1, x2, z1, z2, 1, fence, "DECORATION", stage)
        self.replace((x1 + x2) // 2, 1, z1, "OAK_FENCE_GATE", "DECORATION", stage)

    def fencedGarden(self, x1, x2, z1, z2, fence, ground, stage):
        self.fencedYard(x1, x2, z1, z2, fence, ground, stage)
        for x in range(x1 + 2, x2 - 1, 4): self.flowerBed(x, (z1 + z2) // 2, stage)

    def flowerBed(self, x, z, stage):
        self.block(x, 1, z, "FLOWERING_AZALEA", "DECORATION", stage)
        self.block(x + 1, 1, z, "AZALEA", "DECORATION", stage)

    def cistern(self, cx, cz, radius, stage):
        for y in range(1, 4): self.ring(cx - radius, cx + radius, cz - radius, cz + radius, y, "STONE_BRICKS", "RESIDENT", stage)
        self.floor(cx - radius + 1, cx + radius - 1, cz - radius + 1, cz + radius - 1, 1, "WATER", stage)
        self.pillar(cx - radius, cz - radius, 1, 6, "STRIPPED_OAK_LOG", stage)
        self.pillar(cx + radius, cz - radius, 1, 6, "STRIPPED_OAK_LOG", stage)

    def stable(self, x1, x2, z1, z2, stage):
        self.canopy(x1, x2, z1, z2, "STRIPPED_OAK_LOG", "OAK_PLANKS", stage)
        for x in range(x1 + 2, x2, 3):
            self.pillar(x, z2, 1, 2, "OAK_FENCE", stage)
            self.block(x, 1, (z1 + z2) // 2, "HAY_BLOCK", "DECORATION", stage)

    def signPost(self, x, z, stage):
        self.pillar(x, z, 1, 3, "OAK_FENCE", stage)
        self.block(x, 4, z, "OAK_SIGN", "DECORATION", stage)
        self.block(x, 3, z + 1, "LANTERN", "DECORATION", stage)

    def vault(self, x1, x2, z1, z2, stage):
        self.floor(x1, x2, z1, z2, 0, "DEEPSLATE_BRICKS", stage)
        for y in range(1, 5): self.ring(x1, x2, z1, z2, y, "REINFORCED_DEEPSLATE", "RESIDENT", stage)
        self.door((x1 + x2) // 2, 1, z1, "IRON_DOOR", "NORTH", "LEFT", stage)
        self.flatRoof(x1, x2, z1, z2, 5, "DEEPSLATE_TILES", stage)
        self.block((x1 + x2) // 2, 1, (z1 + z2) // 2, "GOLD_BLOCK", "DECORATION", stage)

    def plaza(self, x1, x2, z1, z2, material, stage): self.floor(x1, x2, z1, z2, 0, material, stage)

    def flag(self, x, z, color, stage):
        self.pillar(x, z, 1, 8, "OAK_FENCE", stage)
        for y in range(6, 9):
            for dx in range(1, 4): self.block(x + dx, y, z, color, "DECORATION", stage)

    def statue(self, x, z, stage):
        self.floor(x - 1, x + 1, z - 1, z + 1, 0, "POLISHED_ANDESITE", stage)
        self.pillar(x, z, 1, 3, "QUARTZ_PILLAR", stage)
        self.block(x, 4, z, "CARVED_PUMPKIN", "DECORATION", stage)

    def slipway(self, x1, x2, z1, z2, stage):
        for z in range(z1, z2 + 1, 2):
            for x in range(x1, x2 + 1): self.block(x, 0, z, "OAK_PLANKS", "RESIDENT", stage)
        for x in (x1, x2):
            for z in range(z1, z2 + 1): self.block(x, 1, z, "SPRUCE_FENCE", "DECORATION", stage)

    def shipFrame(self, cx, z1, z2, stage):
        for z in range(z1, z2 + 1):
            half = max(1, min(4, (z - z1 + 2) // 2))
            self.block(cx - half, 1, z, "SPRUCE_PLANKS", "RESIDENT", stage)
            self.block(cx + half, 1, z, "SPRUCE_PLANKS", "RESIDENT", stage)
            if (z - z1) % 3 == 0:
                for x in range(cx - half, cx + half + 1): self.block(x, 0, z, "OAK_LOG", "RESIDENT", stage)
        self.pillar(cx, (z1 + z2) // 2, 1, 8, "STRIPPED_SPRUCE_LOG", stage)

    def crane(self, cx, cz, stage):
        for x in (cx - 2, cx + 2): self.pillar(x, cz, 1, 8, "STRIPPED_DARK_OAK_LOG", stage)
        for x in range(cx - 3, cx + 8): self.axis(x, 9, cz, "DARK_OAK_LOG", "RESIDENT", stage, "X")
        for y in range(5, 9): self.block(cx + 6, y, cz, "CHAIN", "DECORATION", stage)
        self.block(cx + 6, 4, cz, "IRON_BLOCK", "DECORATION", stage)

    def waterPlane(self, x1, x2, z1, z2, y, stage):
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1): self.block(x, y, z, "WATER", "DECORATION", stage)

    def pier(self, x1, x2, z1, z2, stage):
        self.floor(x1, x2, z1, z2, 1, "SPRUCE_PLANKS", stage)
        for x in (x1, x2):
            for z in range(z1, z2 + 1, 4): self.pillar(x, z, 0, 2, "STRIPPED_SPRUCE_LOG", stage)

    def fountain(self, cx, cz, stage):
        self.ring(cx - 3, cx + 3, cz - 3, cz + 3, 0, "STONE_BRICKS", "RESIDENT", stage)
        self.floor(cx - 2, cx + 2, cz - 2, cz + 2, 0, "WATER", stage)
        self.pillar(cx, cz, 1, 3, "QUARTZ_PILLAR", stage)
        self.block(cx, 4, cz, "WATER", "DECORATION", stage)

    def reservoir(self, cx, cz, radius, stage):
        for y in range(1, 13): self.ring(cx - radius, cx + radius, cz - radius, cz + radius, y, "STONE_BRICKS", "RESIDENT", stage)
        self.floor(cx - radius + 1, cx + radius - 1, cz - radius + 1, cz + radius - 1, 12, "WATER", stage)
        self.ring(cx - radius - 1, cx + radius + 1, cz - radius - 1, cz + radius + 1, 13, "SMOOTH_STONE", "DECORATION", stage)

    def aqueductSpan(self, x1, x2, cz, top, stage):
        for x in range(x1, x2 + 1):
            local = (x - x1) % 6
            for y in range(1, top + 1):
                if local <= 1 or local >= 5 or y >= top - 1:
                    self.block(x, y, cz, "STONE_BRICKS", "RESIDENT", stage)
        for x in range(x1, x2 + 1): self.block(x, top + 1, cz, "SMOOTH_STONE", "DECORATION", stage)

    def waterChannel(self, x1, x2, z, y, stage):
        for x in range(x1, x2 + 1):
            self.block(x, y, z - 1, "STONE_BRICKS", "RESIDENT", stage)
            self.block(x, y, z + 1, "STONE_BRICKS", "RESIDENT", stage)
            self.block(x, y, z, "WATER", "DECORATION", stage)

    def lamp(self, x, z, stage):
        self.pillar(x, z, 1, 3, "IRON_BARS", stage)
        self.block(x, 4, z, "LANTERN", "DECORATION", stage)

    def anvilRow(self, x1, x2, z, stage):
        for x in range(x1, x2 + 1, 3): self.block(x, 1, z, "ANVIL", "DECORATION", stage)

    def gatehouse(self, x1, x2, z1, z2, height, stage):
        self.floor(x1, x2, z1, z2, 0, "DEEPSLATE_BRICKS", stage)
        for y in range(1, height + 1):
            for x in range(x1, x2 + 1):
                if not (-2 <= x <= 2 and y <= 4): self.block(x, y, z1, "STONE_BRICKS", "RESIDENT", stage)
                self.block(x, y, z2, "STONE_BRICKS", "RESIDENT", stage)
            for z in range(z1 + 1, z2):
                self.block(x1, y, z, "STONE_BRICKS", "RESIDENT", stage); self.block(x2, y, z, "STONE_BRICKS", "RESIDENT", stage)
        for x in range(-2, 3): self.block(x, 5, z1, "DEEPSLATE_BRICKS", "DECORATION", stage)
        self.door(-1, 1, z1, "IRON_DOOR", "NORTH", "LEFT", stage)
        self.door(0, 1, z1, "IRON_DOOR", "NORTH", "RIGHT", stage)
        self.flatRoof(x1, x2, z1, z2, height + 1, "DEEPSLATE_TILES", stage)
        self.crenellations(x1, x2, z1, z2, height + 2, "DEEPSLATE_BRICK_WALL", stage)

    def fortressWall(self, x1, x2, z1, z2, height, stage):
        for y in range(1, height + 1): self.ring(x1, x2, z1, z2, y, "STONE_BRICKS", "RESIDENT", stage)
        for x in range(-2, 3):
            for y in range(1, 5): self.replace(x, y, z1, "IRON_BARS", "DECORATION", stage)
        self.crenellations(x1, x2, z1, z2, height + 1, "STONE_BRICK_WALL", stage)

    def crenellations(self, x1, x2, z1, z2, y, material, stage):
        for x in range(x1, x2 + 1, 2):
            self.block(x, y, z1, material, "DECORATION", stage); self.block(x, y, z2, material, "DECORATION", stage)
        for z in range(z1, z2 + 1, 2):
            self.block(x1, y, z, material, "DECORATION", stage); self.block(x2, y, z, material, "DECORATION", stage)

    def roundTower(self, cx, cz, radius, height, wall, stage):
        for y in range(0, height + 1):
            for x in range(-radius, radius + 1):
                for z in range(-radius, radius + 1):
                    d = x * x + z * z
                    if d <= radius * radius and d >= (radius - 1) * (radius - 1):
                        self.block(cx + x, y, cz + z, wall, "RESIDENT", stage)
        for a in range(8):
            angle = a * math.pi / 4
            self.block(cx + round(math.cos(angle) * radius), height + 1,
                       cz + round(math.sin(angle) * radius), "STONE_BRICK_WALL", "DECORATION", stage)

    def fortressYard(self, x1, x2, z1, z2, stage):
        self.floor(x1, x2, z1, z2, 0, "COBBLESTONE", stage)
        self.canopy(x1, x1 + 5, z2 - 4, z2, "STRIPPED_DARK_OAK_LOG", "DARK_OAK_PLANKS", stage)
        self.canopy(x2 - 5, x2, z2 - 4, z2, "STRIPPED_DARK_OAK_LOG", "DARK_OAK_PLANKS", stage)

    def kennel(self, x1, x2, z1, z2, stage):
        self.fencedYard(x1, x2, z1, z2, "SPRUCE_FENCE", "PODZOL", stage)
        self.canopy(x1, x2, z2 - 2, z2, "STRIPPED_SPRUCE_LOG", "SPRUCE_PLANKS", stage)

    def watchPlatform(self, cx, cz, stage):
        for x in (cx - 2, cx + 2):
            for z in (cz - 2, cz + 2): self.pillar(x, z, 1, 7, "STRIPPED_SPRUCE_LOG", stage)
        self.floor(cx - 3, cx + 3, cz - 3, cz + 3, 8, "SPRUCE_PLANKS", stage)
        self.ring(cx - 3, cx + 3, cz - 3, cz + 3, 9, "SPRUCE_FENCE", "DECORATION", stage)
        self.hipRoof(cx - 4, cx + 4, cz - 4, cz + 4, 10, "DARK_OAK_PLANKS", stage)

    def archeryRange(self, x1, x2, z, stage):
        for x in range(x1, x2 + 1, 4):
            self.block(x, 1, z, "HAY_BLOCK", "RESIDENT", stage)
            self.block(x, 2, z, "TARGET", "DECORATION", stage)

    def smallBoat(self, cx, cz, stage):
        for z in range(cz - 3, cz + 4):
            half = 0 if abs(z - cz) == 3 else (1 if abs(z - cz) >= 2 else 2)
            for x in range(cx - half, cx + half + 1): self.block(x, 1, z, "SPRUCE_PLANKS", "DECORATION", stage)
        self.pillar(cx, cz, 2, 6, "OAK_FENCE", stage)
        for y in range(3, 6):
            for x in range(cx + 1, cx + 4): self.block(x, y, cz, "WHITE_WOOL", "DECORATION", stage)

    def telescope(self, cx, y, cz, stage):
        self.pillar(cx, cz, y - 3, y - 1, "IRON_BLOCK", stage)
        for i in range(7): self.block(cx + i, y + i // 3, cz, "AMETHYST_BLOCK" if i == 6 else "COPPER_BLOCK", "DECORATION", stage)

    def starMarker(self, x, z, stage):
        self.block(x, 1, z, "AMETHYST_BLOCK", "DECORATION", stage)
        self.block(x, 2, z, "END_ROD", "DECORATION", stage)

    def cellBlock(self, x1, x2, z1, z2, height, stage):
        self.floor(x1, x2, z1, z2, 0, "DEEPSLATE_BRICKS", stage)
        for y in range(1, height + 1): self.ring(x1, x2, z1, z2, y, "STONE_BRICKS", "RESIDENT", stage)
        self.flatRoof(x1, x2, z1, z2, height + 1, "DEEPSLATE_TILES", stage)
        for x in range(x1 + 2, x2, 3):
            self.replace(x, 2, z1, "IRON_BARS", "DECORATION", stage); self.replace(x, 2, z2, "IRON_BARS", "DECORATION", stage)
        self.door(0, 1, z1, "IRON_DOOR", "NORTH", "LEFT", stage)

    def prisonWall(self, x1, x2, z1, z2, height, stage):
        for y in range(1, height + 1): self.ring(x1, x2, z1, z2, y, "STONE_BRICKS", "RESIDENT", stage)
        for x in range(-2, 3):
            for y in range(1, 5): self.replace(x, y, z1, "IRON_BARS", "DECORATION", stage)
        self.crenellations(x1, x2, z1, z2, height + 1, "IRON_BARS", stage)

    def prisonYard(self, x1, x2, z1, z2, stage):
        self.floor(x1, x2, z1, z2, 0, "SMOOTH_STONE", stage)
        for z in range(z1 + 3, z2 - 2, 5):
            for x in range(x1 + 3, x2 - 2, 6): self.block(x, 1, z, "IRON_BARS", "DECORATION", stage)


def java_method_body(source: str, method: str) -> str:
    marker = re.search(r"private void\s+" + re.escape(method) + r"\(Builder b\)\s*\{", source)
    if not marker: raise RuntimeError(f"Не найден метод {method}")
    start = marker.end(); depth = 1; index = start
    while index < len(source) and depth:
        if source[index] == "{": depth += 1
        elif source[index] == "}": depth -= 1
        index += 1
    return source[start:index - 1]


def parse_arg(raw: str):
    value = raw.strip()
    for prefix in ("Material.", "BlockRole.", "BlockFace.", "Door.Hinge."):
        if value.startswith(prefix): return value[len(prefix):]
    if value == "true": return True
    if value == "false": return False
    if re.fullmatch(r"-?\d+", value): return int(value)
    raise ValueError(value)


def build_from_java(java_file: Path, project_id: str, level: int) -> Builder:
    source = java_file.read_text(encoding="utf-8")
    method = PROJECTS[project_id][1]
    body = re.sub(r"//.*", "", java_method_body(source, method))
    builder = Builder(level)
    for match in re.finditer(r"b\.(\w+)\((.*?)\);", body, re.S):
        name, raw_args = match.group(1), match.group(2)
        try:
            args = [parse_arg(part) for part in raw_args.split(",")]
        except ValueError:
            # Четыре компактных Java-цикла раскрываются на том же месте, чтобы
            # сохранить putIfAbsent-порядок точь-в-точь.
            if project_id == "infirmary" and name == "flowerBed":
                for x in range(-10, 11, 5): builder.flowerBed(x, -9, 5)
            elif project_id == "watch_fortress" and name == "roundTower":
                for x in (-14, 14):
                    for z in (-8, 12): builder.roundTower(x, z, 3, 9, "DEEPSLATE_BRICKS", 3)
            elif project_id == "observatory" and name == "starMarker":
                for x in range(-9, 10, 6): builder.starMarker(x, -10, 5)
            elif project_id == "prison" and name == "roundTower":
                for x in (-14, 14):
                    for z in (-11, 16): builder.roundTower(x, z, 2, 10, "DEEPSLATE_BRICKS", 4)
            continue
        getattr(builder, name)(*args)
    return builder


def validate(java_file: Path):
    results = {}
    signatures = set()
    no_door = {"aqueduct"}
    for project_id in PROJECTS:
        previous = None
        levels = []
        for level in range(1, 6):
            builder = build_from_java(java_file, project_id, level)
            if not builder.blocks: raise AssertionError(f"{project_id}: пустой этап {level}")
            if previous:
                for key, value in previous.items():
                    if builder.blocks.get(key) != value:
                        raise AssertionError(f"{project_id}: этап {level} изменил блок {key}")
            added = [block for key, block in builder.blocks.items() if not previous or key not in previous]
            if not added: raise AssertionError(f"{project_id}: этап {level} ничего не добавляет")
            levels.append(len(builder.blocks)); previous = OrderedDict(builder.blocks)
        doors = sum(1 for block in previous.values() if block["material"].endswith("_DOOR") and block["half"] == "BOTTOM")
        if project_id not in no_door and doors == 0: raise AssertionError(f"{project_id}: нет настоящей двери")
        signature = tuple(sorted((key, value["material"]) for key, value in previous.items()))
        if signature in signatures: raise AssertionError(f"{project_id}: модель дублируется")
        signatures.add(signature)
        xs = [key[0] for key in previous]; ys = [key[1] for key in previous]; zs = [key[2] for key in previous]
        results[project_id] = {"levels": levels, "doors": doors,
                               "dimensions": [max(xs) - min(xs) + 1, max(zs) - min(zs) + 1, max(ys) - min(ys) + 1]}
    return results


COLORS = {
    "WATER": (62, 158, 214, 135), "GLASS": (191, 232, 239, 150), "GLASS_PANE": (191, 232, 239, 165),
    "BLUE_STAINED_GLASS": (67, 133, 194, 170), "GOLD_BLOCK": (237, 190, 45, 255),
    "RED_WOOL": (176, 45, 48, 255), "BLUE_WOOL": (48, 79, 173, 255), "YELLOW_WOOL": (238, 190, 43, 255),
    "WHITE_WOOL": (226, 228, 228, 255), "BRICKS": (145, 75, 61, 255), "RED_NETHER_BRICKS": (75, 24, 32, 255),
    "STONE_BRICKS": (117, 120, 118, 255), "STONE_BRICK_WALL": (112, 115, 113, 255), "SMOOTH_STONE": (157, 160, 158, 255),
    "COBBLESTONE": (105, 107, 104, 255), "MOSSY_COBBLESTONE": (89, 112, 78, 255), "POLISHED_ANDESITE": (131, 135, 134, 255),
    "POLISHED_DIORITE": (191, 190, 185, 255), "CALCITE": (221, 217, 204, 255), "SMOOTH_QUARTZ": (232, 226, 214, 255),
    "QUARTZ_PILLAR": (220, 214, 201, 255), "SANDSTONE": (213, 195, 142, 255), "SMOOTH_SANDSTONE": (223, 207, 159, 255),
    "CUT_SANDSTONE": (213, 194, 139, 255), "DEEPSLATE_BRICKS": (60, 61, 66, 255), "DEEPSLATE_TILES": (48, 50, 55, 255),
    "REINFORCED_DEEPSLATE": (62, 68, 67, 255), "DARK_PRISMARINE": (52, 99, 91, 255), "COPPER_BLOCK": (179, 91, 63, 255),
    "OAK_PLANKS": (166, 132, 78, 255), "SPRUCE_PLANKS": (112, 81, 48, 255), "DARK_OAK_PLANKS": (68, 47, 29, 255),
    "BIRCH_PLANKS": (199, 182, 126, 255), "OAK_LOG": (108, 87, 52, 255), "DARK_OAK_LOG": (58, 43, 27, 255),
    "STRIPPED_OAK_LOG": (174, 142, 91, 255), "STRIPPED_SPRUCE_LOG": (111, 82, 52, 255),
    "STRIPPED_DARK_OAK_LOG": (82, 64, 46, 255), "STRIPPED_BIRCH_LOG": (197, 178, 127, 255),
    "WHITE_TERRACOTTA": (210, 178, 161, 255), "LIGHT_BLUE_TERRACOTTA": (112, 137, 145, 255),
    "LIME_TERRACOTTA": (102, 118, 69, 255), "PACKED_MUD": (139, 107, 77, 255), "PODZOL": (106, 78, 48, 255),
    "COARSE_DIRT": (119, 83, 52, 255), "MOSS_BLOCK": (90, 126, 46, 255), "FLOWERING_AZALEA": (136, 105, 126, 255),
    "AZALEA": (79, 126, 61, 255), "HAY_BLOCK": (190, 163, 49, 255), "IRON_BLOCK": (188, 190, 187, 255),
    "IRON_BARS": (103, 109, 111, 255), "CHAIN": (73, 79, 80, 255), "LANTERN": (230, 146, 56, 255),
    "CAMPFIRE": (216, 101, 35, 255), "AMETHYST_BLOCK": (132, 83, 174, 255), "END_ROD": (235, 226, 211, 255),
    "TARGET": (213, 197, 166, 255), "ANVIL": (73, 73, 75, 255), "YELLOW_GLAZED_TERRACOTTA": (235, 191, 47, 255),
}


def material_color(name):
    for suffix in ("_STAIRS", "_DOOR", "_FENCE", "_FENCE_GATE", "_WALL", "_SIGN"):
        if name.endswith(suffix):
            base = name[:-len(suffix)]
            for key in (base + "_PLANKS", base + "_LOG", base + "_BRICKS", base):
                if key in COLORS: return COLORS[key]
    return COLORS.get(name, (137, 133, 122, 255))


def shade(color, factor):
    return tuple(min(255, int(channel * factor)) for channel in color[:3]) + (color[3],)


class Projector:
    def __init__(self, scale, ox, oy): self.scale, self.ox, self.oy = scale, ox, oy
    def p(self, x, y, z): return (round(self.ox + (x + z) * self.scale), round(self.oy + (x - z) * self.scale * .5 - y * self.scale))


def draw_cuboid(draw, projector, xyz, bounds, color, visible=(True, True, True)):
    x1, y1, z1 = xyz; x2, y2, z2 = bounds
    top, east, north = visible
    if east:
        draw.polygon([projector.p(x2,y1,z1), projector.p(x2,y1,z2), projector.p(x2,y2,z2), projector.p(x2,y2,z1)], fill=shade(color,.72), outline=(30,36,40,90))
    if north:
        draw.polygon([projector.p(x1,y1,z1), projector.p(x2,y1,z1), projector.p(x2,y2,z1), projector.p(x1,y2,z1)], fill=shade(color,.87), outline=(30,36,40,90))
    if top:
        draw.polygon([projector.p(x1,y2,z1), projector.p(x2,y2,z1), projector.p(x2,y2,z2), projector.p(x1,y2,z2)], fill=shade(color,1.12), outline=(30,36,40,90))


def bounds_for(blocks):
    xs=[k[0] for k in blocks]; ys=[k[1] for k in blocks]; zs=[k[2] for k in blocks]
    return min(xs),max(xs),min(ys),max(ys),min(zs),max(zs)


def render(project_id, title, blocks, output):
    width,height=1600,1200
    image=Image.new("RGBA",(width,height),(181,226,250,255)); draw=ImageDraw.Draw(image,"RGBA")
    minx,maxx,miny,maxy,minz,maxz=bounds_for(blocks)
    corners=[(x,y,z) for x in (minx,maxx+1) for y in (miny,maxy+1) for z in (minz,maxz+1)]
    px=[x+z for x,y,z in corners]; py=[(x-z)*.5-y for x,y,z in corners]
    scale=min(25,1020/max(1,max(px)-min(px)),810/max(1,max(py)-min(py)))
    projector=Projector(scale,width/2-(min(px)+max(px))*scale/2,175-min(py)*scale)
    ground=[projector.p(minx-3,-.08,minz-3),projector.p(maxx+4,-.08,minz-3),projector.p(maxx+4,-.08,maxz+4),projector.p(minx-3,-.08,maxz+4)]
    draw.polygon(ground,fill=(91,155,73,255),outline=(49,102,48,255))
    occupied=set(blocks)
    thin_names=("_FENCE","_WALL","IRON_BARS","CHAIN","END_ROD","LIGHTNING_ROD","GLASS_PANE")
    entries=sorted(blocks.items(),key=lambda item:(item[0][0]-item[0][2],item[0][1],item[0][0]))
    for (x,y,z),block in entries:
        material=block["material"]; color=material_color(material)
        top=(x,y+1,z) not in occupied; east=(x+1,y,z) not in occupied; north=(x,y,z-1) not in occupied
        if material=="WATER":
            draw_cuboid(draw,projector,(x,y,z),(x+1,y+.84,z+1),color,(True,east,north))
        elif material.endswith("_DOOR"):
            if block["facing"] in ("NORTH","SOUTH"):
                draw_cuboid(draw,projector,(x+.08,y,z+.05),(x+.92,y+1,z+.17),color,(True,True,True))
            else:
                draw_cuboid(draw,projector,(x+.05,y,z+.08),(x+.17,y+1,z+.92),color,(True,True,True))
        elif material.endswith("_STAIRS"):
            draw_cuboid(draw,projector,(x,y,z),(x+1,y+.5,z+1),color,(True,True,True))
            x1,x2,z1,z2=x,x+1,z,z+1
            if block["facing"]=="NORTH": z2=z+.5
            elif block["facing"]=="SOUTH": z1=z+.5
            elif block["facing"]=="EAST": x1=x+.5
            elif block["facing"]=="WEST": x2=x+.5
            draw_cuboid(draw,projector,(x1,y+.5,z1),(x2,y+1,z2),color,(True,True,True))
        elif any(material.endswith(name) or material==name for name in thin_names):
            if material=="GLASS_PANE": draw_cuboid(draw,projector,(x+.12,y,z+.44),(x+.88,y+1,z+.56),color,(True,True,True))
            else: draw_cuboid(draw,projector,(x+.32,y,z+.32),(x+.68,y+1,z+.68),color,(True,True,True))
        else:
            draw_cuboid(draw,projector,(x,y,z),(x+1,y+1,z+1),color,(top,east,north))
    bold=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",44)
    regular=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",24)
    draw.text((70,45),title,font=bold,fill=(21,31,44,255))
    dims=f"{maxx-minx+1} × {maxz-minz+1} × {maxy-miny+1} блоков"
    draw.text((72,100),"Точная блочная модель • уровень V • "+dims,font=regular,fill=(55,70,86,255))
    residents=sum(1 for block in blocks.values() if block["role"]=="RESIDENT")
    decoration=len(blocks)-residents
    draw.rounded_rectangle((64,height-94,910,height-36),18,fill=(28,39,51,225))
    draw.text((88,height-81),f"Жители: {residents}   •   Автоотделка: {decoration}   •   Всего: {len(blocks)}",font=regular,fill=(255,255,255,255))
    image.save(output)
    return image


def write_json(project_id, builder, output):
    data={"format":"NeverLandTownyBuilds exact block model v1","project":project_id,
          "name":PROJECTS[project_id][0],"level":5,"stages":STAGE_NAMES[project_id],
          "blocks":[{"x":x,"y":y,"z":z,**block} for (x,y,z),block in builder.blocks.items()]}
    output.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding="utf-8")


def contact_sheet(images, ids, output):
    cell=(800,600); sheet=Image.new("RGBA",(cell[0]*3,cell[1]*2),(233,241,247,255))
    for index,project_id in enumerate(ids):
        preview=images[project_id].resize(cell,Image.Resampling.LANCZOS)
        sheet.alpha_composite(preview,((index%3)*cell[0],(index//3)*cell[1]))
    sheet.save(output)


def main():
    root=Path(__file__).resolve().parents[1]
    java_file=root/"src/main/java/ru/neverland/townybuilds/construction/ExpansionBlueprintGenerator.java"
    output=Path(sys.argv[1] if len(sys.argv)>1 else root/"build/expansion-previews")
    output.mkdir(parents=True,exist_ok=True); blueprint_dir=output/"blueprints"; blueprint_dir.mkdir(exist_ok=True)
    report=validate(java_file); images={}
    for project_id,(title,_) in PROJECTS.items():
        builder=build_from_java(java_file,project_id,5)
        images[project_id]=render(project_id,title,builder.blocks,output/f"{project_id}.png")
        write_json(project_id,builder,blueprint_dir/f"{project_id}.json")
    groups=OrderedDict([
        ("01_общественные_здания",["infirmary","fire_station","tavern_inn","city_bank","embassy"]),
        ("02_знания_и_право",["museum","university","courthouse","observatory"]),
        ("03_инфраструктура",["shipyard","aqueduct","workshop","fishing_harbor","post_station"]),
        ("04_оборона_и_промысел",["watch_fortress","hunting_lodge","prison"]),
    ])
    for name,ids in groups.items(): contact_sheet(images,ids,output/f"{name}.png")
    (output/"validation-report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
    archive=output/"NeverLandTownyBuilds-17-exact-3d-models.zip"
    with zipfile.ZipFile(archive,"w",zipfile.ZIP_DEFLATED) as bundle:
        for project_id in PROJECTS:
            bundle.write(output/f"{project_id}.png",f"previews/{project_id}.png")
            bundle.write(blueprint_dir/f"{project_id}.json",f"blueprints/{project_id}.json")
        bundle.write(output/"validation-report.json","validation-report.json")
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=="__main__": main()
