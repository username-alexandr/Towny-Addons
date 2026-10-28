#!/usr/bin/env python3
"""Create a NEW disposable loopback fixture from a stopped bootstrap and clean checkpoint."""
from pathlib import Path
import argparse,shutil,yaml
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--template',type=Path,required=True);p.add_argument('--checkpoint',type=Path,required=True);p.add_argument('--server',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[2];template=a.template.resolve();checkpoint=a.checkpoint.resolve();server=a.server.resolve()
assert not server.exists(), 'Refusing to overwrite any existing server'
assert (template/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (template/'server.properties').read_text()
assert checkpoint.is_dir() and (checkpoint/'plugins').is_dir()
server.mkdir(parents=True)
for file in template.iterdir():
    if file.name in {'plugins','war-fixture','war-fixture_nether','war-fixture_the_end','logs','validation','probe-classes'} or file.name.startswith('hs_err'):continue
    if file.is_dir():shutil.copytree(file,server/file.name)
    elif file.is_file():shutil.copy2(file,server/file.name)
for folder in checkpoint.iterdir():
    if folder.is_dir():shutil.copytree(folder,server/folder.name,dirs_exist_ok=True)
versions=yaml.safe_load((root/'versions.yml').read_text())['addons']
for short in ['Builds','Contracts','Companies','Jobs','Army','Logistics','SiegesPlus','Chronicles','Environment','Quests','Policies','Control','Resources']:
    name='NeverLandTowny'+short
    for stale in (server/'plugins').glob(name+'-*.jar'):stale.unlink()
    source=root/'modules'/name/'build/libs'/f'{name}-{versions[name]}.jar';assert source.is_file(),source
    shutil.copy2(source,server/'plugins'/source.name)
assert yaml.safe_load((server/'plugins/NeverLandTownyControl/modules.yml').read_text())['modules']=={}
assert not (server/'plugins/TntRuntimeProbe').exists()
print('Created new opted-in loopback fixture:',server)
