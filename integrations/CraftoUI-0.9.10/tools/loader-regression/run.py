#!/usr/bin/env python3
"""Reproduce the reported MixinExtras failure using official upstream binaries.

Not a full Minecraft launch or an NLT modpack integration test.
"""
from pathlib import Path
import argparse, hashlib, io, json, os, subprocess, urllib.request, zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--java-home', type=Path, required=True)
parser.add_argument('--cache', type=Path, required=True)
parser.add_argument('--craftoui-jar', type=Path, required=True)
args = parser.parse_args()
cache = args.cache.resolve()
cache.mkdir(parents=True, exist_ok=True)

def fetch(url, path, algorithm=None, digest=None):
    if not path.exists():
        data = urllib.request.urlopen(url, timeout=45).read()
        if digest:
            assert hashlib.new(algorithm, data).hexdigest() == digest, url
        path.write_bytes(data)
    data = path.read_bytes()
    if digest:
        assert hashlib.new(algorithm, data).hexdigest() == digest, path
    return data

manifests = {}
for loader, extras in [('0.19.4', '0.5.4'), ('0.19.5', '0.5.5')]:
    stem = 'fabric-loader-' + loader
    base = f'https://maven.fabricmc.net/net/fabricmc/fabric-loader/{loader}/{stem}'
    manifest = json.loads(fetch(base + '.json', cache / (stem + '.json')))
    manifests[loader] = manifest
    checksum = urllib.request.urlopen(base + '.jar.sha1', timeout=45).read().decode().split()[0]
    data = fetch(base + '.jar', cache / (stem + '.jar'), 'sha1', checksum)
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        nested = jar.read(f'META-INF/jars/mixinextras-fabric-{extras}.jar')
    declaration = next(lib for lib in manifest['libraries']['development']
                       if lib['name'] == 'io.github.llamalad7:mixinextras-fabric:' + extras)
    assert hashlib.sha256(nested).hexdigest() == declaration['sha256']
    with zipfile.ZipFile(io.BytesIO(nested)) as jar:
        assert json.loads(jar.read('fabric.mod.json'))['version'] == extras
    (cache / f'mixinextras-fabric-{extras}.jar').write_bytes(nested)
    print(f'PASS verified Fabric Loader {loader} embeds MixinExtras {extras}', flush=True)

libraries = []
for library in manifests['0.19.5']['libraries']['common']:
    group, artifact, version = library['name'].split(':')
    filename = f'{artifact}-{version}.jar'
    url = library['url'] + group.replace('.', '/') + '/' + artifact + '/' + version + '/' + filename
    path = cache / filename
    fetch(url, path, 'sha256', library['sha256'])
    libraries.append(path)

classes = cache / 'probe-classes'
classes.mkdir(exist_ok=True)
def classpath(extras):
    return os.pathsep.join(map(str, [classes, cache / 'fabric-loader-0.19.4.jar',
                                  cache / f'mixinextras-fabric-{extras}.jar', *libraries]))

subprocess.run([str(args.java_home / 'bin/javac'), '-proc:none', '-cp', classpath('0.5.5'),
                '-d', str(classes), str(Path(__file__).with_name('RedirectProbe.java'))], check=True)
for extras in ['0.5.4', '0.5.5']:
    subprocess.run([str(args.java_home / 'bin/java'), '-cp', classpath(extras), 'RedirectProbe',
                    extras, str(args.craftoui_jar.resolve())], check=True)
print('PASS loader/mixin regression; full Minecraft and NLT startup NOT tested', flush=True)
