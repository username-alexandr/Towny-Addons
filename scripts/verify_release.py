#!/usr/bin/env python3
"""Validate the NeverLand Towny release matrix and bundled artifacts."""

from __future__ import annotations

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


def main() -> int:
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
    if expected_jars != actual_jars:
        errors.append(f"JAR set differs: expected={sorted(expected_jars)}, actual={sorted(actual_jars)}")

    yaml_count = 0
    for yaml_path in sorted(modules_dir.rglob("*.yml")) + sorted(modules_dir.rglob("*.yaml")):
        try:
            load_yaml(yaml_path)
            yaml_count += 1
        except Exception as exc:  # noqa: BLE001
            errors.append(f"invalid YAML {yaml_path.relative_to(ROOT)}: {exc}")

    for name, raw_version in addons.items():
        version = str(raw_version)
        module = modules_dir / name
        source_plugin = module / "src" / "main" / "resources" / "plugin.yml"
        if not source_plugin.exists():
            errors.append(f"missing {source_plugin.relative_to(ROOT)}")
            continue

        source_meta = load_yaml(source_plugin)
        if source_meta.get("name") != name:
            errors.append(f"{name}: source plugin name is {source_meta.get('name')!r}")
        source_version = str(source_meta.get("version"))
        if source_version not in {version, "${version}"}:
            errors.append(f"{name}: source plugin version is {source_meta.get('version')!r}, expected {version}")

        declared = project_version(module)
        if declared != version:
            errors.append(f"{name}: build version is {declared!r}, expected {version}")

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

    models = ROOT / "assets" / "models" / "NeverLandTownyBuilds-Models-0.1.0.zip"
    try:
        with zipfile.ZipFile(models) as archive:
            bad = archive.testzip()
            if bad:
                errors.append(f"model pack has corrupt entry {bad}")
    except Exception as exc:  # noqa: BLE001
        errors.append(f"cannot inspect model pack: {exc}")

    if errors:
        print("Release verification failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print(f"OK: {len(addons)} modules, {len(actual_jars)} JARs, {yaml_count} YAML files, model pack")
    return 0


if __name__ == "__main__":
    sys.exit(main())
