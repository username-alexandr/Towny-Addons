#!/usr/bin/env python3
"""Syntax-based API consumer and executable-test inventory. No Java dependencies required."""
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]

def validate(data, root):
    contracts = {c['contract']: c for c in data['contracts']}
    if len(contracts) != len(data['contracts']):
        raise RuntimeError('Duplicate public API names')
    errors = []
    if Path(root).resolve() == ROOT:
        baseline = ROOT/'scripts/api-v1-baseline.tsv'
        prior = {line.split('\t')[0] for line in baseline.read_text().splitlines() if line and not line.startswith('#')}
        if prior - contracts.keys():
            errors.append('Removed published contracts: '+', '.join(sorted(prior-contracts.keys())))
    for request in data['consumers']:
        contract = contracts.get(request['contract'])
        if not contract:
            errors.append(f"{request['source']}: unknown API {request['contract']}")
            continue
        provider = Path(contract['source']).parts[1]
        if request['plugin'] != provider:
            errors.append(f"{request['source']}: {request['contract']} belongs to {provider}, not {request['plugin']}")
        missing = set(request['capabilities']) - set(contract['capabilities'])
        if missing or request['major'] != contract['major']:
            errors.append(f"{request['source']}: incompatible API {request['contract']}; major={request['major']}, missing={sorted(missing)}")
    covered = {c['source'] for c in data['consumers']} | set(data['dynamic_sites'])
    if set(data['sites']) != covered:
        errors.append(f"Unanalysed call sites: {sorted(set(data['sites']) - covered)}")
    if errors:
        raise RuntimeError('\n'.join(errors))
    return data

def inventory(root=ROOT, java=None):
    if not java:
        java = str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    result = subprocess.run([java, str(ROOT/'scripts/ApiSourceAudit.java'), str(root)], text=True, capture_output=True, check=True)
    return validate(json.loads(result.stdout), root)

def main():
    data = inventory()
    target = ROOT/'build/api-source-inventory.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n')
    print(f"API SOURCE PASS: {len(data['contracts'])} contracts, {len(data['sites'])} call sites, {len(data['consumers'])} resolved requests, {len(data['tests'])} executable tests")
    for site in data['dynamic_sites']:
        print('DYNAMIC (requires runtime coverage): '+site)

if __name__ == '__main__':
    main()
