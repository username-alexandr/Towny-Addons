#!/usr/bin/env python3
"""Prepare the exact locally validated 0.41.0 binaries for GitHub Releases."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import zipfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    source, output = map(Path, sys.argv[1:])
    manifest = json.loads((source / 'manifest.json').read_text())
    assert manifest['version'] == '0.41.0' and manifest['tag'] == 'v0.41.0'
    output.mkdir(parents=True, exist_ok=True)
    assert not any(output.iterdir()), 'Use an empty publication directory'
    archives = manifest['archives']
    expected = {f'NeverLandTownySuite-0.41.0-{kind}.zip' for kind in ('plugins', 'sources', 'full')}
    assert {item['name'] for item in archives} == expected and len(archives) == 3
    for item in archives:
        archive = source / item['name']
        assert archive.stat().st_size == item['size']
        assert digest(archive.read_bytes()) == item['sha256'], archive.name
        with zipfile.ZipFile(archive) as zip_file:
            assert zip_file.testzip() is None, archive.name
        shutil.copyfile(archive, output / archive.name)

    with zipfile.ZipFile(source / 'NeverLandTownySuite-0.41.0-sources.zip') as sources:
        report = json.loads(sources.read('docs/FOLIA_VALIDATION.json'))
        for name in ('FOLIA_VALIDATION.md', 'FOLIA_VALIDATION.json', 'FOLIA_NATIVE_PROBE.txt'):
            (output / name).write_bytes(sources.read('docs/' + name))
    modules = report['modules']
    assert len(modules) == 40
    with zipfile.ZipFile(source / 'NeverLandTownySuite-0.41.0-plugins.zip') as plugins:
        expected_jars = {f"dist/plugins/{module['jar']}" for module in modules}
        assert {name for name in plugins.namelist() if name.endswith('.jar')} == expected_jars
        for module in modules:
            data = plugins.read('dist/plugins/' + module['jar'])
            assert digest(data) == module['sha256'], module['jar']
            (output / module['jar']).write_bytes(data)

    sums = [f'{digest(path.read_bytes())}  {path.name}' for path in sorted(output.iterdir())]
    (output / 'SHA256SUMS.txt').write_text('\n'.join(sums) + '\n')
    print(f'Prepared {len(sums) + 1} assets; original archive and native-test JAR hashes match')


if __name__ == '__main__':
    main()
