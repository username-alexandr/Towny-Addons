#!/usr/bin/env python3
"""Build each module and run every executable test in an isolated JVM; shared by CI and releases."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
from api_audit import inventory
from run_smoke import run_tests

def plugin_jar(candidates, module_name):
    """Select the freshly built plugin JAR and ignore stale versioned outputs."""
    matches = []
    for candidate in candidates:
        try:
            with zipfile.ZipFile(candidate) as archive:
                descriptor = archive.read('plugin.yml').decode('utf-8')
        except (KeyError, OSError, UnicodeDecodeError, zipfile.BadZipFile):
            continue
        name = re.search(r'(?m)^name:\s*["\']?([^"\'\r\n]+)', descriptor)
        version = re.search(r'(?m)^version:\s*["\']?([^"\'\r\n]+)', descriptor)
        if name and version and name.group(1).strip() == module_name:
            matches.append((version.group(1).strip(), candidate))
    expected = re.search(r'(?m)^version:\s*["\']?([^"\'\r\n]+)', (ROOT/'modules'/module_name/'src/main/resources/plugin.yml').read_text())
    exact = [path for version, path in matches if expected and version == expected.group(1).strip()]
    if len(exact) != 1:
        raise RuntimeError(f'Expected one {module_name} JAR at version {expected.group(1).strip() if expected else "?"}: {exact}')
    return exact[0]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('modules', nargs='*', help='Optional full module names or suffixes')
    parser.add_argument('--stage', type=Path)
    parser.add_argument('--report', type=Path, default=ROOT/'build/suite-tests.json')
    args = parser.parse_args()
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text('[]\n')
    records = []
    available = {m.name for m in (ROOT/'modules').iterdir() if m.is_dir()}
    selected = {name if name in available else 'NeverLandTowny'+name for name in args.modules}
    if selected - available:
        raise RuntimeError('Unknown modules: '+', '.join(sorted(selected-available)))
    subprocess.run([os.environ.get('PYTHON', 'python3'), str(ROOT/'scripts/test_api_audit.py')], check=True)
    data = inventory()
    (ROOT/'build').mkdir(exist_ok=True)
    (ROOT/'build/api-source-inventory.json').write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n')
    storage_probes = {
        'NeverLandTownyGovernance': ('ru.neverland.governance.service.GovernanceRepository','data.yml','towns'),
        'NeverLandTownyReputation': ('ru.neverland.reputation.service.ReputationRepository','data.yml','relations'),
        'NeverLandTownyEvents': ('ru.neverland.mintevents.service.EventRepository','events-data.yml','active'),
        'NeverLandTownyIdeologies': ('ru.neverland.townyideologies.data.DataStore','towns.yml','towns'),
        'NeverLandTownyChronicles': ('ru.neverland.townychronicles.service.ChronicleRepository','chronicles.yml','towns'),
        'NeverLandTownyTaxes': ('ru.neverland.townytaxes.data.CivicRepository','data.yml','policies'),
        'NeverLandTownyEspionage': ('ru.neverland.mintespionage.service.EspionageRepository','data.yml','towns'),
    }
    probe_classes = ROOT/'build/api-contract-probe'
    probe_classes.mkdir(parents=True, exist_ok=True)
    javac = str(Path(os.environ['JAVA_HOME'])/'bin/javac') if os.environ.get('JAVA_HOME') else 'javac'
    subprocess.run([javac, '--release', '17', '-d', str(probe_classes), str(ROOT/'scripts/ApiContractProbe.java')], check=True)
    def run(command, cwd):
        subprocess.run(command, cwd=cwd, check=True)
    for module in sorted((ROOT/'modules').iterdir()):
        if not module.is_dir() or (args.modules and module.name not in args.modules and module.name.removeprefix('NeverLandTowny') not in args.modules):
            continue
        start = time.monotonic()
        if (module/'build.gradle.kts').exists():
            run([os.environ.get('GRADLE', 'gradle'), *json.loads(os.environ.get('NLT_GRADLE_ARGS', '[]')), '--no-daemon', '-I', str(ROOT/'scripts/smoke.init.gradle'), 'clean', 'jar', 'writeSmokeClasspath'], module)
            classpath = (module/'build/smoke-classpath.txt').read_text().strip()
            jars = list((module/'build/libs').glob('*.jar'))
        elif (module/'pom.xml').exists():
            output = module/'target/smoke-dependencies.txt'
            run([os.environ.get('MAVEN', 'mvn'), *json.loads(os.environ.get('NLT_MAVEN_ARGS', '[]')), '-B', 'clean', 'package', 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath', '-Dmdep.includeScope=test', '-Dmdep.outputFile='+str(output)], module)
            classpath = os.pathsep.join([str(module/'target/test-classes'), str(module/'target/classes'), output.read_text().strip()])
            jars = list((module/'target').glob('*.jar'))
        else:
            raise RuntimeError('No build definition: '+module.name)
        tests = run_tests(module, classpath, data)
        contracts = [c['contract'] for c in data['contracts'] if Path(c['source']).parts[1] == module.name]
        if contracts:
            run([os.environ.get('JAVA', 'java'), '-ea', '-cp', os.pathsep.join([str(probe_classes), classpath]), 'ApiContractProbe', '--baseline', str(ROOT/'scripts/api-v1-baseline.tsv'), *contracts], module)
        if module.name in storage_probes:
            run([javac, '--release', '17', '-cp', classpath, '-d', str(probe_classes), str(ROOT/'scripts/RepositoryFaultProbe.java')], module)
            run([os.environ.get('JAVA', 'java'), '-ea', '-cp', os.pathsep.join([str(probe_classes), classpath]), 'RepositoryFaultProbe', *storage_probes[module.name]], module)
        if args.stage:
            jars = [p for p in jars if not p.name.endswith(('-sources.jar','-javadoc.jar'))]
            jar = plugin_jar(jars, module.name)
            args.stage.mkdir(parents=True, exist_ok=True)
            shutil.copy2(jar, args.stage/jar.name)
        records.append(dict(module=module.name, tests=tests, api_contracts=contracts, repository_fault_probe=module.name in storage_probes, seconds=round(time.monotonic()-start, 3)))
        args.report.write_text(json.dumps(records, ensure_ascii=False, indent=2)+'\n')
        print(f'PASS {module.name}: {len(tests)} executable tests', flush=True)
    if not records:
        raise RuntimeError('No modules selected')
    print(f'SUITE PASS: {len(records)} modules; {sum(len(r["tests"]) for r in records)} executable tests', flush=True)

if __name__ == '__main__':
    main()
