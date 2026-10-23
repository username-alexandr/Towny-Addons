"""Compile the opt-in construction probe against a disposable, bootstrapped Folia server."""
import argparse
import os
from pathlib import Path
import subprocess

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--server-dir', type=Path, required=True)
p.add_argument('--java-home', type=Path, required=True)
a = p.parse_args()
s = a.server_dir.resolve()
if not (s / 'ALLOW_DISPOSABLE_CONSTRUCTION_PROBE').is_file():
    raise SystemExit('Disposable server marker required; this probe changes world blocks')
source = Path(__file__).resolve().parent
classes = s / 'construction-probe-build'
classes.mkdir(exist_ok=True)
jars = sorted((s / 'versions').rglob('*.jar')) + sorted((s / 'libraries').rglob('*.jar')) + sorted((s / 'plugins').glob('*.jar'))
jars = [j for j in jars if j.name != 'ConstructionProbe.jar']
subprocess.run([str(a.java_home / 'bin/javac'), '-encoding', 'UTF-8', '-d', str(classes),
                '-classpath', os.pathsep.join(map(str, jars)), str(source / 'ConstructionProbe.java')], check=True)
subprocess.run([str(a.java_home / 'bin/jar'), '--create', '--file', str(s / 'plugins/ConstructionProbe.jar'),
                '-C', str(classes), '.', '-C', str(source), 'plugin.yml'], check=True)
