"""Compile the opt-in access probe against a disposable, bootstrapped Folia server."""
import argparse
import os
from pathlib import Path
import subprocess

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--server-dir', type=Path, required=True)
p.add_argument('--java-home', type=Path, required=True)
p.add_argument('--classpath-extra', default='', help='Optional compile-only dependencies, e.g. JetBrains annotations')
a = p.parse_args()
s = a.server_dir.resolve()
if not (s / 'ALLOW_DISPOSABLE_MOB_FARM_PROBE').is_file():
    raise SystemExit('Disposable server marker required; this probe changes world blocks')
source = Path(__file__).resolve().parent
classes = s / 'access-probe-build'
classes.mkdir(exist_ok=True)
jars = sorted((s / 'versions').rglob('*.jar')) + sorted((s / 'libraries').rglob('*.jar')) + sorted((s / 'plugins').glob('*.jar'))
jars += [Path(x) for x in a.classpath_extra.split(os.pathsep) if x]
jars = [j for j in jars if j.name != 'AccessProbe.jar']
subprocess.run([str(a.java_home / 'bin/javac'), '-encoding', 'UTF-8', '-d', str(classes),
                '-classpath', os.pathsep.join(map(str, jars)), str(source / 'AccessProbe.java')], check=True)
subprocess.run([str(a.java_home / 'bin/jar'), '--create', '--file', str(s / 'plugins/AccessProbe.jar'),
                '-C', str(classes), '.', '-C', str(source), 'plugin.yml'], check=True)
