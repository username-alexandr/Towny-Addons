#!/usr/bin/env python3
"""One discovery and execution rule for Gradle smokeTest and the full suite."""
import argparse
import os
from pathlib import Path
import re
import subprocess
from api_audit import ROOT, inventory

def run_tests(module, classpath, data):
    names = []
    relative = module.relative_to(ROOT).as_posix()+'/'
    for test in data['tests']:
        if not test['source'].startswith(relative):
            continue
        text = (ROOT/test['source']).read_text()
        command = [os.environ.get('JAVA', 'java'), '-ea', '-cp', classpath, test['class']]
        # Three legacy configuration probes take a mandatory resource-root argument.
        if re.search(r'args\s*\[\s*0\s*\]', text) and not re.search(r'args\s*\.\s*length', text):
            command.append(str(module/'src/main/resources'))
        print('TEST '+module.name+' '+test['class'], flush=True)
        subprocess.run(command, cwd=module, check=True)
        names.append(test['class'])
    return names

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--module', type=Path, required=True)
    p.add_argument('--classpath', required=True)
    a = p.parse_args()
    names = run_tests(a.module.resolve(), a.classpath, inventory())
    print(f'SMOKE PASS: {len(names)} executable tests')
