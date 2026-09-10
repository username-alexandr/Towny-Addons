#!/usr/bin/env python3
"""Validate the NeverLand Towny release matrix and bundled artifacts."""

from __future__ import annotations

import argparse
import re
import sys
import zipfile
from pathlib import Path

try:
    import yaml
except ImportError as exc:  # pragma: no cover - explicit environment guidance
    raise SystemExit("PyYAML is required: python3 -m pip install PyYAML") from exc


ROOT = Path(__file__).resolve().parents[1]
LOCALIZATION_MODULES = {
    "NeverLandTownyBuilds", "NeverLandTownyCamps", "NeverLandTownyContracts",
    "NeverLandTownyEvents", "NeverLandTownyExpeditions", "NeverLandTownyTrade", "NeverLandTownyLogistics",
}
LOCALIZATION_RESOURCE = "neverland-localization/materials-ru.properties"


def load_yaml(path: Path):
    with path.open("r", encoding="utf-8") as stream:
        return yaml.safe_load(stream)


def project_version(module: Path) -> str | None:
    gradle = module / "build.gradle.kts"
    if gradle.exists():
        match = re.search(r'^version\s*=\s*"([^"]+)"', gradle.read_text(encoding="utf-8"), re.MULTILINE)
        if match:
            return match.group(1)

    pom = module / "pom.xml"
    if pom.exists():
        match = re.search(r"<artifactId>[^<]+</artifactId>\s*<version>([^<]+)</version>", pom.read_text(encoding="utf-8"))
        if match:
            return match.group(1)
    return None


def plugin_load_order_errors(metadata: dict[str, dict]) -> list[str]:
    """Mirror Paper's plugin ordering rules and reject internal dependency cycles."""
    errors: list[str] = []
    edges: dict[str, set[str]] = {name: set() for name in metadata}

    for name, plugin in metadata.items():
        for field in ("depend", "softdepend"):
            values = plugin.get(field, []) or []
            if not isinstance(values, list):
                errors.append(f"{name}: {field} must be a YAML list")
                continue
            for dependency in values:
                dependency = str(dependency)
                if dependency in metadata:
                    edges[dependency].add(name)
                elif field == "depend" and dependency.startswith("NeverLandTowny"):
                    errors.append(f"{name}: required internal dependency {dependency} is absent")

        values = plugin.get("loadbefore", []) or []
        if not isinstance(values, list):
            errors.append(f"{name}: loadbefore must be a YAML list")
            continue
        for target in values:
            target = str(target)
            if target in metadata:
                edges[name].add(target)

    indegree = {name: 0 for name in metadata}
    for targets in edges.values():
        for target in targets:
            indegree[target] += 1

    ready = sorted(name for name, degree in indegree.items() if degree == 0)
    visited = 0
    while ready:
        name = ready.pop(0)
        visited += 1
        for target in sorted(edges[name]):
            indegree[target] -= 1
            if indegree[target] == 0:
                ready.append(target)
                ready.sort()

    if visited != len(metadata):
        cycle_members = sorted(name for name, degree in indegree.items() if degree > 0)
        errors.append("circular plugin loading order: " + " -> ".join(cycle_members))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-only", action="store_true",
                        help="validate sources and metadata without requiring freshly built JARs")
    parser.add_argument("--plugins-dir", type=Path, default=ROOT / "dist" / "plugins",
                        help="directory containing the JARs to validate")
    args = parser.parse_args()
    matrix = load_yaml(ROOT / "versions.yml")
    addons: dict[str, object] = matrix["addons"]
    modules_dir = ROOT / "modules"
    plugins_dir = args.plugins_dir
    errors: list[str] = []
    dictionary = ROOT / "shared/localization/src/main/resources" / LOCALIZATION_RESOURCE
    dictionary_bytes = dictionary.read_bytes() if dictionary.exists() else None
    if dictionary_bytes is None:
        errors.append("missing shared Russian material dictionary")

    module_names = {path.name for path in modules_dir.iterdir() if path.is_dir()}
    if module_names != set(addons):
        errors.append(f"module set differs: expected={sorted(addons)}, actual={sorted(module_names)}")

    expected_jars = {f"{name}-{version}.jar" for name, version in addons.items()}
    actual_jars = {path.name for path in plugins_dir.glob("*.jar")}
    if not args.source_only and expected_jars != actual_jars:
        errors.append(f"JAR set differs: expected={sorted(expected_jars)}, actual={sorted(actual_jars)}")

    yaml_count = 0
    for yaml_path in sorted(modules_dir.rglob("*.yml")) + sorted(modules_dir.rglob("*.yaml")):
        try:
            load_yaml(yaml_path)
            yaml_count += 1
        except Exception as exc:  # noqa: BLE001
            errors.append(f"invalid YAML {yaml_path.relative_to(ROOT)}: {exc}")

    source_metadata: dict[str, dict] = {}
    for name, raw_version in addons.items():
        version = str(raw_version)
        module = modules_dir / name
        source_plugin = module / "src" / "main" / "resources" / "plugin.yml"
        if not source_plugin.exists():
            errors.append(f"missing {source_plugin.relative_to(ROOT)}")
            continue

        source_meta = load_yaml(source_plugin)
        source_metadata[name] = source_meta
        if source_meta.get("name") != name:
            errors.append(f"{name}: source plugin name is {source_meta.get('name')!r}")
        source_version = str(source_meta.get("version"))
        if source_version not in {version, "${version}"}:
            errors.append(f"{name}: source plugin version is {source_meta.get('version')!r}, expected {version}")

        declared = project_version(module)
        if declared != version:
            errors.append(f"{name}: build version is {declared!r}, expected {version}")

        if args.source_only:
            continue
        jar = plugins_dir / f"{name}-{version}.jar"
        if not jar.exists():
            continue
        try:
            with zipfile.ZipFile(jar) as archive:
                bad = archive.testzip()
                if bad:
                    errors.append(f"{jar.name}: corrupt entry {bad}")
                jar_meta = yaml.safe_load(archive.read("plugin.yml").decode("utf-8"))
                if jar_meta.get("name") != name or str(jar_meta.get("version")) != version:
                    errors.append(f"{jar.name}: plugin.yml identity/version mismatch")
                if name in LOCALIZATION_MODULES:
                    if archive.read(LOCALIZATION_RESOURCE) != dictionary_bytes:
                        errors.append(f"{jar.name}: bundled Russian dictionary differs from sources")
                    for helper in ("MaterialLabels", "MaterialNameConfig"):
                        if f"ru/neverland/localization/{helper}.class" not in archive.namelist():
                            errors.append(f"{jar.name}: missing localization helper {helper}")
                if name in {"NeverLandTownyBuilds", "NeverLandTownyPopulation", "NeverLandTownyResources"}:
                    if "ru/neverland/integration/DistrictBonuses.class" not in archive.namelist():
                        errors.append(f"{jar.name}: missing district integration")
                if name == "NeverLandTownyBuilds":
                    for helper in ("api/BuildingStorageApi", "api/CargoShipment", "storage/ShipmentTransactions", "storage/StorageSessions", "storage/ProductionService", "util/AtomicYamlFile"):
                        if f"ru/neverland/townybuilds/{helper}.class" not in archive.namelist():
                            errors.append(f"{jar.name}: missing storage/production helper {helper}")
                    if archive.read("production.yml") != (module / "src/main/resources/production.yml").read_bytes():
                        errors.append(f"{jar.name}: production.yml differs from sources")
                if name == "NeverLandTownyLogistics":
                    for helper in ("NeverLandTownyLogistics", "service/CourierNpcs", "service/LogisticsService", "model/NavigationPolicy", "gui/LogisticsMenu"):
                        if f"ru/neverland/townylogistics/{helper}.class" not in archive.namelist():
                            errors.append(f"{jar.name}: missing logistics class {helper}")
                    if archive.read("config.yml") != (module / "src/main/resources/config.yml").read_bytes():
                        errors.append(f"{jar.name}: config.yml differs from sources")
                if name == "NeverLandTownyPopulation":
                    for helper in ("integration/ResourcesBridge", "model/StrategicSupply"):
                        if f"ru/neverland/townypopulation/{helper}.class" not in archive.namelist():
                            errors.append(f"{jar.name}: missing resource supply integration {helper}")
                if name == "NeverLandTownyResources":
                    for resource in ("config.yml", "buildings.yml"):
                        if archive.read(resource) != (module / "src/main/resources" / resource).read_bytes():
                            errors.append(f"{jar.name}: {resource} differs from sources")
                    for helper in ("NeverLandTownyResources", "api/TownyResourcesApi", "model/ResourceEngine", "data/ResourcesRepository", "gui/ResourcesMenu", "integration/ResourcesExpansion"):
                        if f"ru/neverland/townyresources/{helper}.class" not in archive.namelist():
                            errors.append(f"{jar.name}: missing strategic resource class {helper}")
                if name == "NeverLandTownyDistricts":
                    for resource in ("config.yml", "projects.yml"):
                        if archive.read(resource) != (module / "src/main/resources" / resource).read_bytes():
                            errors.append(f"{jar.name}: {resource} differs from sources")
                    if "ru/neverland/townydistricts/api/TownyDistrictsApi.class" not in archive.namelist():
                        errors.append(f"{jar.name}: missing public district API")
        except Exception as exc:  # noqa: BLE001
            errors.append(f"cannot inspect {jar.name}: {exc}")

    errors.extend(plugin_load_order_errors(source_metadata))

    models = ROOT / "assets" / "models" / "NeverLandTownyBuilds-Models-0.1.0.zip"
    try:
        with zipfile.ZipFile(models) as archive:
            bad = archive.testzip()
            if bad:
                errors.append(f"model pack has corrupt entry {bad}")
    except Exception as exc:  # noqa: BLE001
        errors.append(f"cannot inspect model pack: {exc}")

    builds = modules_dir / "NeverLandTownyBuilds"
    projects = load_yaml(builds / "src/main/resources/projects.yml")
    imported_dir = builds / "src/main/resources/blueprints/imported"
    catalog = load_yaml(imported_dir / "catalog.json")
    imported_ids = {entry["id"] for entry in catalog["models"]}
    configured_ids = set(projects["buildings"])
    expected_wonders = {
        "sun_pyramid", "great_colosseum", "alexandria_lighthouse", "hanging_gardens",
        "archmage_spire", "rhodes_colossus", "world_tree", "celestial_orrery",
        "terracotta_army", "crystal_palace", "great_canal",
    }
    expected_prerequisites = {
        "sun_pyramid": {"quarry": 5, "foundry": 4, "mint": 3},
        "great_colosseum": {"barracks": 5, "arsenal": 4, "archery_range": 3},
        "alexandria_lighthouse": {"trade_port": 5, "shipyard": 4, "fishing_harbor": 3},
        "hanging_gardens": {"aqueduct": 5, "botanical": 4, "irrigation_station": 4},
        "archmage_spire": {"university": 5, "alchemy": 5, "observatory": 4},
        "rhodes_colossus": {"trade_port": 5, "port_fort": 3, "shipyard": 3},
        "world_tree": {"forestry": 5, "botanical": 4, "irrigation_station": 3},
        "celestial_orrery": {"observatory": 5, "research": 4, "cartography": 3},
        "terracotta_army": {"arsenal": 4, "archery_range": 4, "census_bureau": 3},
        "crystal_palace": {"merchant_guild": 5, "gallery": 4, "printing_house": 3},
        "great_canal": {"dam": 5, "pumping_station": 5, "reservoir": 4},
    }
    if len(configured_ids) != 80 or set(projects["wonders"]) != expected_wonders:
        errors.append("NeverLandTownyBuilds must contain 80 buildings and the 11 expected wonders")
    resource_ids = {"wood", "stone", "metal", "food", "water", "materials", "knowledge", "influence"}
    strategic = load_yaml(modules_dir / "NeverLandTownyResources/src/main/resources/config.yml")
    if set(strategic.get("resources", {})) != resource_ids:
        errors.append("Resources must configure the eight strategic resources")
    resource_profiles = load_yaml(modules_dir / "NeverLandTownyResources/src/main/resources/buildings.yml").get("buildings", {})
    if set(resource_profiles) != configured_ids | expected_wonders:
        errors.append("Strategic profiles must cover every building and wonder")
    for resource in resource_ids:
        for field in ("produces", "consumes"):
            if not any(profile.get(field, {}).get(resource, 0) > 0 for profile in resource_profiles.values()):
                errors.append(f"Strategic resource has no {field} profile: {resource}")
    for project, profile in resource_profiles.items():
        if profile.get("maximum-level") != (1 if project in expected_wonders else 5):
            errors.append(f"Strategic profile has wrong maximum level: {project}")
        if not re.search(r"[А-Яа-яЁё]", str(profile.get("name", ""))):
            errors.append(f"Strategic profile is not localized: {project}")
        for field in ("produces", "consumes", "capacity"):
            if set(profile.get(field, {})) - resource_ids:
                errors.append(f"Unknown strategic resource in {project}/{field}")
    logistics = load_yaml(modules_dir / "NeverLandTownyLogistics/src/main/resources/config.yml")
    if set(logistics.get("hubs", [])) != {"warehouse", "cargo_terminal", "caravanserai", "trade_port"}:
        errors.append("Logistics must support the four dispatch buildings")
    if set(map(str, logistics.get("levels", {}))) != {"1", "2", "3", "4", "5"}:
        errors.append("Logistics must configure all five building levels")
    production = load_yaml(builds / "src/main/resources/production.yml")
    if {recipe.get("building") for recipe in production.get("recipes", {}).values()} != {"sawmill", "quarry", "apiary", "water_tower", "bakery"}:
        errors.append("Production profiles must cover the five initial producers")
    water = production.get("recipes", {}).get("water_tower_buckets", {})
    if water.get("input") != {"BUCKET": 1} or water.get("output") != {"WATER_BUCKET": 1} or water.get("district-bonus") is not False:
        errors.append("Water production must preserve one empty bucket per filled bucket")
    population = load_yaml(modules_dir / "NeverLandTownyPopulation/src/main/resources/buildings.yml")
    profiles = population.get("buildings", {})
    if set(profiles) != configured_ids | expected_wonders:
        errors.append("Population profiles must cover every building and wonder exactly once")
    for project_id, profile in profiles.items():
        maximum = 1 if project_id in expected_wonders else 5
        if profile.get("minimum-level") != maximum or profile.get("maximum-level") != maximum:
            errors.append(f"{project_id}: default population capacity must require completed construction")
    if profiles.get("residential_quarter", {}).get("housing") != 120:
        errors.append("Completed residential quarter must provide 120 population places")
    districts = load_yaml(modules_dir / "NeverLandTownyDistricts/src/main/resources/projects.yml")
    district_profiles = districts.get("projects", {})
    if set(district_profiles) != configured_ids | expected_wonders:
        errors.append("District profiles must cover every building and wonder exactly once")
    district_types = {"residential", "industrial", "commercial", "military", "port", "agricultural", "administrative"}
    if {p.get("district") for p in district_profiles.values()} != district_types:
        errors.append("District profiles must use all seven expected district types")
    for project_id, profile in district_profiles.items():
        if not re.search("[А-Яа-яЁё]", str(profile.get("name", ""))):
            errors.append(f"{project_id}: district menu project name must be Russian")
    for project_id in ("forge", "foundry", "warehouse"):
        if district_profiles.get(project_id, {}).get("district") != "industrial":
            errors.append(f"{project_id}: industrial combination requires an industrial profile")
    item_names = load_yaml(builds / "src/main/resources/item-names.yml")
    if item_names.get("COBBLED_DEEPSLATE_SLAB") != "Плита из колотого глубинного сланца":
        errors.append("COBBLED_DEEPSLATE_SLAB must have an unambiguous Russian name")
    matcher_source = (builds / "src/main/java/ru/neverland/townybuilds/service/ResourceMatcher.java").read_text(encoding="utf-8")
    transfer_source = (builds / "src/main/java/ru/neverland/townybuilds/service/ResourceTransfer.java").read_text(encoding="utf-8")
    fund_source = (builds / "src/main/java/ru/neverland/townybuilds/data/ResourceFund.java").read_text(encoding="utf-8")
    if "required.hasItemMeta()" not in matcher_source or "ResourceMatcher.matches" not in transfer_source \
            or "ResourceMatcher.matches" not in fund_source:
        errors.append("construction inventory, transfer and fund must share the resource matcher")
    for wonder_id, expected in expected_prerequisites.items():
        requirements = projects["wonders"][wonder_id].get("requires", {})
        if requirements != expected or not set(requirements).issubset(configured_ids):
            errors.append(f"{wonder_id}: building prerequisites differ: {requirements!r}")
    archaeology = load_yaml(modules_dir / "NeverLandTownyArchaeology" / "src/main/resources/artifacts.yml")
    if len(archaeology.get("artifacts", {})) != 28 or set(archaeology.get("wonder-requirements", {})) != expected_wonders:
        errors.append("Archaeology must contain 28 artifacts and requirements for all 11 wonders")
    if not imported_ids.issubset(configured_ids) or len(imported_ids) != 37:
        errors.append("37-model catalog and projects.yml are not synchronized")
    resource_files = sorted(imported_dir.glob("*.nltb"))
    resource_blocks = sum(
        1 for path in resource_files for line in path.read_text(encoding="utf-8").splitlines()
        if line and not line.startswith("#")
    )
    if len(resource_files) != 37 or catalog.get("total_blocks") != 25_497 or resource_blocks != 25_497:
        errors.append(f"imported model resources differ: files={len(resource_files)}, blocks={resource_blocks}")

    if errors:
        print("Release verification failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    jar_status = "source-only" if args.source_only else f"{len(actual_jars)} JARs"
    print(f"OK: {len(addons)} modules, {jar_status}, {yaml_count} YAML files, 80 buildings / 11 wonders")
    return 0


if __name__ == "__main__":
    sys.exit(main())
