#!/usr/bin/env python3
"""Create fresh loopback-only Folia fixture with independent, genuine Sponge v3 NBT."""
from pathlib import Path
import argparse,gzip,struct,shutil,yaml

def models(server,changed=False):
    folder=server/'plugins/NeverLandTownyBuilds';source=folder/'blueprints/source';source.mkdir(parents=True,exist_ok=True)
    def text(s):
        raw=s.encode();return struct.pack('>H',len(raw))+raw
    def tag(kind,name,payload):return bytes([kind])+text(name)+payload
    def number(name,n):return tag(3,name,struct.pack('>i',n))
    width=4 if changed else 3
    states=['minecraft:air','minecraft:gold_block' if changed else 'minecraft:stone_bricks','minecraft:lectern[facing=north,has_book=false,powered=false]','minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=true]','minecraft:oak_log[axis=x]']
    palette=b''.join(number(state,index) for index,state in enumerate(states))+b'\0'
    cells=bytearray(width*2*3);cells[0]=1;cells[1]=2;cells[2]=3;cells[width*3]=4
    if changed:cells[3]=1
    blocks=tag(10,'Palette',palette)+tag(7,'Data',struct.pack('>i',len(cells))+cells)+b'\0'
    content=number('Version',3)+number('DataVersion',5000)
    for name,value in [('Width',width),('Height',2),('Length',3)]:content+=tag(2,name,struct.pack('>H',value))
    # Deliberately unrelated world offset. The plugin uses local origin [1, 1], never this offset.
    content+=tag(11,'Offset',struct.pack('>iiii',3,-200,-64,800))+tag(10,'Blocks',blocks)+b'\0'
    raw=tag(10,'Schematic',content)
    for level in range(1,6):(source/f'hall-{level}.schem').write_bytes(gzip.compress(raw,mtime=0))
    missing=bytearray(cells);missing[0]=0
    broken=raw.replace(tag(7,'Data',struct.pack('>i',len(cells))+cells),tag(7,'Data',struct.pack('>i',len(missing))+missing))
    (source/'invalid-level.schem').write_bytes(gzip.compress(broken,mtime=0))
    config={'schema':1,'projects':{'town_hall':{'medieval':{'levels':{str(n):{'file':f'hall-{n}.schem','origin':[1,1]} for n in range(1,6)}}}}}
    (folder/'blueprints.yml').write_text(yaml.safe_dump(config,sort_keys=False))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--template',type=Path,required=True);p.add_argument('--server',type=Path,required=True);p.add_argument('--towny',type=Path,required=True)
    a=p.parse_args();template=a.template.resolve();server=a.server.resolve();root=Path(__file__).resolve().parents[2]
    assert not server.exists(), 'Refusing to overwrite an existing server'
    assert (template/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (template/'server.properties').read_text()
    shutil.copytree(template,server,ignore=shutil.ignore_patterns('logs','bootstrap.log','plugins','crash-reports','validation','probe-classes'))
    plugins=server/'plugins';plugins.mkdir(exist_ok=True);shutil.copy2(a.towny.resolve(),plugins/'Towny-0.103.2.0.jar')
    versions=yaml.safe_load((root/'versions.yml').read_text())['addons']
    for short in ['Builds','Contracts','SiegesPlus','Resources','Upkeep','TreasuryPlus','Control','Taxes']:
        name='NeverLandTowny'+short;jar=root/'modules'/name/'build/libs'/f'{name}-{versions[name]}.jar';assert jar.is_file(),jar;shutil.copy2(jar,plugins)
    cfg=plugins/'Towny/settings';cfg.mkdir(parents=True,exist_ok=True);(cfg/'config.yml').write_text('economy:\n  using_economy: false\n')
    models(server);print('Prepared disposable loopback schematic fixture:',server)
