"""Compile the opt-in probe against an already prepared disposable server."""
import argparse
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--server-dir', type=Path, required=True)
parser.add_argument('--java-home', type=Path, required=True)
args = parser.parse_args()
server = args.server_dir.resolve()
source = Path(__file__).resolve().parent
if not (server / 'ALLOW_DISPOSABLE_COMPANIES_PROBE').is_file():
    raise SystemExit('Refusing to install a fixture without ALLOW_DISPOSABLE_COMPANIES_PROBE')
classes = server / 'companies-probe-build'
classes.mkdir(exist_ok=True)
jars = sorted((server / 'libraries').rglob('*.jar')) + sorted((server / 'plugins').glob('*.jar'))
jars = [p for p in jars if p.name != 'NeverLandCompaniesRuntimeProbe.jar']
subprocess.run([str(args.java_home / 'bin/javac'), '-encoding', 'UTF-8', '-d', str(classes),
                '-classpath', os.pathsep.join(map(str, jars)), str(source / 'CompaniesRuntimeProbe.java')], check=True)
subprocess.run([str(args.java_home / 'bin/jar'), '--create', '--file', str(server / 'plugins/NeverLandCompaniesRuntimeProbe.jar'),
                '-C', str(classes), '.', '-C', str(source), 'plugin.yml'], check=True)
print('Probe compiled into the explicitly marked disposable server.')
