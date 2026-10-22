#!/usr/bin/env python3
"""Refuse publication until GitHub has every intended asset with the exact bytes."""
import hashlib
import json
from pathlib import Path
import sys

def main():
    release = json.loads(Path(sys.argv[1]).read_text())
    files = [Path(p) for p in sys.argv[2:]]
    if not release.get('draft') or not files:
        raise SystemExit('Expected a draft release and a non-empty asset list')
    expected = {p.name:p for p in files}
    assets = {a['name']:a for a in release['assets']}
    if len(expected) != len(files) or len(assets) != len(release['assets']) or set(assets) != set(expected):
        raise SystemExit('Duplicate, missing or unexpected release assets')
    for name,path in expected.items():
        data = path.read_bytes();asset = assets[name]
        if asset['state'] != 'uploaded' or asset['size'] != len(data) or asset.get('digest') != 'sha256:'+hashlib.sha256(data).hexdigest():
            raise SystemExit('Uploaded asset does not match local build: '+name)
    print(f'Verified {len(files)} GitHub assets: exact size and SHA-256')

if __name__ == '__main__':
    main()
