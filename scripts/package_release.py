#!/usr/bin/env python3
"""Package tracked sources and verified release binaries without build caches."""
import argparse
from pathlib import Path
import subprocess
import zipfile
import yaml

ROOT = Path(__file__).resolve().parents[1]

def archive(target, files):
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as output:
        for path in sorted(set(files)):
            output.write(ROOT/path, path)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    version = (ROOT/'VERSION').read_text().strip()
    tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
    tracked = [p for p in tracked if p and (ROOT/p).is_file()]
    excluded = {'build', 'target', '.gradle', '.git', '__pycache__'}
    source = [p for p in tracked if not set(Path(p).parts)&excluded and not p.startswith(('assets/', 'dist/'))]
    assets = [p for p in tracked if p.startswith('assets/')]
    jars = [str(p.relative_to(ROOT)) for p in (ROOT/'dist/plugins').glob('*.jar')]
    matrix = yaml.safe_load((ROOT/'versions.yml').read_text())['addons']
    expected = {f'dist/plugins/{name}-{version}.jar' for name, version in matrix.items()}
    if set(jars) != expected:
        raise SystemExit(f'Release JARs differ from versions.yml: missing={sorted(expected-set(jars))}, extra={sorted(set(jars)-expected)}')
    prefix = f'NeverLandTownySuite-{version}'
    archive(args.output/f'{prefix}-sources.zip', source)
    archive(args.output/f'{prefix}-plugins.zip', jars+['dist/plugins/README.md'])
    archive(args.output/f'{prefix}-full.zip', source+assets+jars+['dist/plugins/README.md'])
    for path in sorted(args.output.glob(prefix+'-*.zip')):
        print(path.name, path.stat().st_size)

if __name__ == '__main__':
    main()
