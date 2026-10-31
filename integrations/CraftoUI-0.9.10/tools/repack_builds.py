#!/usr/bin/env python3
"""Replace only projects.yml in the published Builds JAR; preserve all class bytes."""
from pathlib import Path
from zipfile import ZipFile
import sys

original, catalog, target = map(Path, sys.argv[1:4])
assert original.resolve() != target.resolve()
with ZipFile(original) as source, ZipFile(target, "w") as result:
    entries = source.infolist()
    assert sum(item.filename == "projects.yml" for item in entries) == 1
    assert "plugin.yml" in source.namelist()
    for item in entries:
        data = catalog.read_bytes() if item.filename == "projects.yml" else source.read(item.filename)
        result.writestr(item, data)
with ZipFile(original) as source, ZipFile(target) as result:
    assert source.namelist() == result.namelist()
    assert all(source.read(item) == result.read(item) for item in source.namelist() if item != "projects.yml")
    assert result.read("projects.yml") == catalog.read_bytes()
print(f"Verified {target}: only projects.yml changed")
