#!/usr/bin/env python3
"""Finish the already built 0.42.0 draft, with independent CI artifact and archive verification."""
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile
import yaml

REPO = 'username-alexandr/Towny-Addons'
COMMIT = '2aec261f6c488c1856ef0c17d4d6ff448ef7dfdc'
RUN = 39754713645
RELEASE = 420533698
ARTIFACT = 12416580188
TAG = 'v0.42.0'
ROOT = Path(__file__).resolve().parents[1]


def api(path):
    return json.loads(subprocess.check_output(['gh', 'api', f'repos/{REPO}/{path}']))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def download(path, target):
    with target.open('wb') as stream:
        accept = 'application/json' if path.startswith('actions/') else 'application/octet-stream'
        subprocess.run(['gh', 'api', '-H', 'Accept: '+accept,
                        f'repos/{REPO}/{path}'], stdout=stream, check=True)


def check_sums(text, files):
    expected = {}
    for line in text.strip().splitlines():
        checksum, name = line.split('  ', 1)
        assert name not in expected and name in files, f'Unexpected checksum: {name}'
        expected[name] = checksum
    assert set(expected) == set(files), 'Incomplete checksums'
    for name, data in files.items():
        assert digest(data) == expected[name], f'Checksum mismatch: {name}'


def main():
    assert os.environ['GITHUB_REPOSITORY'] == REPO
    run = api(f'actions/runs/{RUN}')
    assert run['head_sha'] == COMMIT and run['status'] == 'completed'
    jobs = api(f'actions/runs/{RUN}/jobs')['jobs']
    assert len(jobs) == 1
    steps = {s['name']: s for s in jobs[0]['steps']}
    for name in ('Build release JARs', 'Verify bundled artifacts', 'Create release archives'):
        assert steps[name]['conclusion'] == 'success', f'Build gate did not pass: {name}'
    release = api(f'releases/{RELEASE}')
    assert release['tag_name'] == TAG and release['target_commitish'] == COMMIT
    assert release['draft'], 'Refusing to change a published release'
    artifact = api(f'actions/artifacts/{ARTIFACT}')
    assert artifact['workflow_run']['id'] == RUN and not artifact['expired']
    source_bytes = subprocess.check_output(['git', 'archive', '--format=zip', COMMIT], cwd=ROOT)
    with zipfile.ZipFile(io.BytesIO(source_bytes)) as source:
        tracked = {p: source.read(p) for p in source.namelist() if not p.endswith('/')}
    matrix = yaml.safe_load(tracked['versions.yml'])
    assert matrix['suite'] == '0.42.0' and len(matrix['addons']) == 40
    jars = {f'{name}-{version}.jar' for name, version in matrix['addons'].items()}
    prefix = 'NeverLandTownySuite-0.42.0'
    bundles = {f'{prefix}-{kind}.zip' for kind in ('sources', 'plugins', 'full')}
    models = {Path(p).name for p in tracked if p.startswith('assets/models/') and p.endswith('.zip')}
    reports = {'suite-tests.json', 'api-source-inventory.json', 'VALIDATION.txt',
               'versions.yml', 'RELEASE_NOTES_RU.md', 'resource-balance.csv', 'SHA256SUMS.txt'}
    expected = jars | bundles | models | reports
    assert len(expected) == 51
    assert len(release['assets']) == len(expected) and {a['name'] for a in release['assets']} == expected
    with tempfile.TemporaryDirectory(prefix='neverland-finish-') as folder:
        work = Path(folder)
        download(f'actions/artifacts/{ARTIFACT}/zip', work/'ci.zip')
        assert 'sha256:'+digest((work/'ci.zip').read_bytes()) == artifact['digest'], 'CI artifact digest mismatch'
        with zipfile.ZipFile(work/'ci.zip') as ci:
            assert ci.testzip() is None
            original = {p: ci.read(p) for p in ci.namelist() if not p.endswith('/')}
        files = {}
        for asset in release['assets']:
            path = work/asset['name']
            download(f'releases/assets/{asset["id"]}', path)
            files[asset['name']] = path.read_bytes()
        metadata = work/'release.json'
        metadata.write_text(json.dumps(release))
        subprocess.run(['python', str(ROOT/'scripts/verify_published_assets.py'), str(metadata),
                        *[str(work/name) for name in sorted(expected)]], check=True)
        for name, ci_name in [('suite-tests.json', 'build/suite-tests.json'),
                              ('api-source-inventory.json', 'build/api-source-inventory.json'),
                              ('VALIDATION.txt', 'dist/plugins/VALIDATION.txt')]:
            assert files[name] == original[ci_name], f'Report differs from completed CI: {name}'
        records = json.loads(files['suite-tests.json'])
        assert len(records) == 40 and {r['module'] for r in records} == set(matrix['addons'])
        assert sum(len(r['tests']) for r in records) == 133
        assert COMMIT in files['VALIDATION.txt'].decode()
        assert files['versions.yml'] == tracked['versions.yml']
        assert files['RELEASE_NOTES_RU.md'] == tracked['RELEASE_NOTES_RU.md']
        check_sums(original['dist/plugins/SHA256SUMS.txt'].decode(), {n: files[n] for n in jars})
        check_sums(files['SHA256SUMS.txt'].decode(), {n: files[n] for n in jars | bundles | models})
        excluded = {'build', 'target', '.gradle', '.git', '__pycache__'}
        sources = {p: data for p, data in tracked.items()
                   if not (set(Path(p).parts) & excluded) and not p.startswith(('assets/', 'dist/'))}
        packaged_plugins = {f'dist/plugins/{n}': files[n] for n in jars}
        packaged_plugins['dist/plugins/README.md'] = tracked['dist/plugins/README.md']
        for name in ('SHA256SUMS.txt', 'VALIDATION.txt'):
            packaged_plugins['dist/plugins/'+name] = original['dist/plugins/'+name]
        contents = {'sources': sources, 'plugins': packaged_plugins,
                    'full': sources | {p: data for p, data in tracked.items() if p.startswith('assets/')} | packaged_plugins}
        for kind, expected_files in contents.items():
            with zipfile.ZipFile(io.BytesIO(files[f'{prefix}-{kind}.zip'])) as archive:
                assert archive.testzip() is None
                assert len(archive.namelist()) == len(expected_files) and set(archive.namelist()) == set(expected_files)
                for name, data in expected_files.items():
                    assert archive.read(name) == data, f'Archive contents mismatch: {kind}/{name}'
        for name in models:
            assert files[name] == tracked['assets/models/'+name]
        print('Verified 40 JARs against immutable CI checksums; 133 test programs; all 51 assets; exact source and full archives.', flush=True)
        # Read once more before the only mutation. Do not publish if somebody changed this draft.
        fresh = api(f'releases/{RELEASE}')
        assert fresh['draft'] and fresh['target_commitish'] == COMMIT and fresh['tag_name'] == TAG
        assert [(a['id'], a['name'], a['digest'], a['size']) for a in fresh['assets']] == [
            (a['id'], a['name'], a['digest'], a['size']) for a in release['assets']]
        subprocess.run(['gh', 'api', '--method', 'PATCH', f'repos/{REPO}/releases/{RELEASE}',
                        '-F', 'draft=false', '-f', 'make_latest=true'], check=True, stdout=subprocess.DEVNULL)
        published = api(f'releases/{RELEASE}')
        assert not published['draft'] and published['tag_name'] == TAG
        ref = api(f'git/ref/tags/{TAG}')['object']
        if ref['type'] == 'tag':
            ref = api(f'git/tags/{ref["sha"]}')['object']
        assert ref['sha'] == COMMIT
        print('Published '+published['html_url'], flush=True)


if __name__ == '__main__':
    main()
