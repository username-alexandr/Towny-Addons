"""Compile the opt-in probe against an already prepared disposable server."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import yaml

parser = argparse.ArgumentParser()
parser.add_argument('--server-dir', type=Path, required=True)
parser.add_argument('--java-home', type=Path, required=True)
parser.add_argument('--extra-jar', type=Path, action='append', default=[], help='Compile-only annotations JAR if absent from server libraries')
args = parser.parse_args()
server = args.server_dir.resolve()
source = Path(__file__).resolve().parent
if not (server / 'ALLOW_DISPOSABLE_RELIABILITY_PROBE').is_file():
    raise SystemExit('Refusing to install a fixture without ALLOW_DISPOSABLE_RELIABILITY_PROBE')
classes = server / 'justice-probe-build'
classes.mkdir(exist_ok=True)
jars = sorted((server / 'versions').rglob('*.jar')) + sorted((server / 'libraries').rglob('*.jar')) + sorted((server / 'plugins').glob('*.jar'))
jars = [p for p in jars if p.name != 'NeverLandJusticeRuntimeProbe.jar']
jars += [p.resolve() for p in args.extra_jar]
subprocess.run([str(args.java_home / 'bin/javac'), '-encoding', 'UTF-8', '-d', str(classes),
                '-classpath', os.pathsep.join(map(str, jars)), str(source / 'JusticeRuntimeProbe.java')], check=True)
# Generate the native inventory from the exact source under test, never a stale list.
repo = source.parents[1]
sys.path.insert(0, str(repo/'scripts'))
from api_audit import inventory
data = inventory(repo, str(args.java_home/'bin/java'))
versions = yaml.safe_load((repo/'versions.yml').read_text())['addons']
(classes/'expected-counts.properties').write_text('addons='+str(len(versions))+'\ncontracts='+str(len(data['contracts']))+'\n')
(classes/'contracts.tsv').write_text(''.join(c['source'].split('/')[1]+'\t'+c['contract']+'\t'+','.join(c['capabilities'])+'\t'+versions[c['source'].split('/')[1]]+'\n' for c in data['contracts']))
(classes/'consumers.tsv').write_text(''.join(c['plugin']+'\t'+c['contract']+'\t'+','.join(c['capabilities'])+'\n' for c in data['consumers']))
subprocess.run([str(args.java_home / 'bin/jar'), '--create', '--file', str(server / 'plugins/NeverLandJusticeRuntimeProbe.jar'),
                '-C', str(classes), '.', '-C', str(source), 'plugin.yml'], check=True)
print('Probe compiled into the explicitly marked disposable server.')
