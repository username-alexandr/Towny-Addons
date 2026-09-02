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
    args = parser.parse_args()
    matrix = load_yaml(ROOT / "versions.yml")
    addons: dict[str, object] = matrix["addons"]
    modules_dir = ROOT / "modules"
    plugins_dir = ROOT / "dist" / "plugins"
    errors: list[str] = []

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
    if len(configured_ids) != 62 or len(projects["wonders"]) != 5:
        errors.append("NeverLandTownyBuilds must contain 62 buildings and 5 wonders")
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
    print(f"OK: {len(addons)} modules, {jar_status}, {yaml_count} YAML files, 37 models / 25,497 blocks")
    return 0


if __name__ == "__main__":
    sys.exit(main())
