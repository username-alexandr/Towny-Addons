#!/usr/bin/env python3
"""Download real, hash-verified Minecraft 26.2 compile dependencies. No stubs."""
import concurrent.futures, hashlib, json, pathlib, sys, urllib.request, time
DEST=pathlib.Path(sys.argv[1] if len(sys.argv)>1 else '.minecraft-compile').resolve()
DEST.mkdir(parents=True,exist_ok=True)
def fetch(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url,timeout=45) as r:return r.read()
        except Exception:
            if attempt==2:raise
            time.sleep(1)
manifest=json.loads(fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
ref=next(v for v in manifest['versions'] if v['id']=='26.2')
raw=fetch(ref['url'])
assert hashlib.sha1(raw).hexdigest()==ref['sha1'],'Version manifest checksum mismatch'
version=json.loads(raw)
downloads=[('minecraft-26.2.jar',version['downloads']['client'])]
for library in version['libraries']:
    artifact=library.get('downloads',{}).get('artifact')
    if artifact and 'natives-' not in artifact['path']:downloads.append(('libraries/'+artifact['path'],artifact))
def download(item):
    name,artifact=item;target=DEST/name
    if target.exists() and hashlib.sha1(target.read_bytes()).hexdigest()==artifact['sha1']:return
    data=fetch(artifact['url'])
    assert len(data)==artifact['size'] and hashlib.sha1(data).hexdigest()==artifact['sha1'],name+' checksum mismatch'
    target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    print(name,flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:list(pool.map(download,downloads))
(DEST/'manifest.json').write_text(json.dumps(version,indent=2))
print('Verified dependencies:',DEST)
