#!/usr/bin/env python3
"""Package tracked sources and verified release binaries without build caches."""
import argparse
from pathlib import Path
import subprocess
import zipfile

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
    if len(jars) != 27:
        raise SystemExit(f'Expected 27 freshly verified JARs, got {len(jars)}')
    prefix = f'NeverLandTownySuite-{version}'
    archive(args.output/f'{prefix}-sources.zip', source)
    archive(args.output/f'{prefix}-plugins.zip', jars+['dist/plugins/README.md'])
    archive(args.output/f'{prefix}-full.zip', source+assets+jars+['dist/plugins/README.md'])
    for path in sorted(args.output.glob(prefix+'-*.zip')):
        print(path.name, path.stat().st_size)

if __name__ == '__main__':
    main()
