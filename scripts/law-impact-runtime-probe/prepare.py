#!/usr/bin/env python3
"""Fresh loopback-only real Folia fixture; never writes an existing server."""
from pathlib import Path
import argparse,shutil,yaml
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--template',type=Path,required=True);p.add_argument('--server',type=Path,required=True);p.add_argument('--towny',type=Path,required=True)
a=p.parse_args();template=a.template.resolve();server=a.server.resolve();root=Path(__file__).resolve().parents[2]
assert not server.exists(), 'Refusing to overwrite an existing server'
assert (template/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (template/'server.properties').read_text()
shutil.copytree(template,server,ignore=shutil.ignore_patterns('logs','bootstrap.log','plugins','crash-reports','validation','probe-classes'))
plugins=server/'plugins';plugins.mkdir(exist_ok=True);shutil.copy2(a.towny.resolve(),plugins/'Towny-0.103.2.0.jar')
versions=yaml.safe_load((root/'versions.yml').read_text())['addons']
for short in ['Builds','Contracts','SiegesPlus','Governance','Control']:
    name='NeverLandTowny'+short;jar=root/'modules'/name/'build/libs'/f'{name}-{versions[name]}.jar';assert jar.is_file(),jar;shutil.copy2(jar,plugins)
cfg=plugins/'Towny/settings';cfg.mkdir(parents=True,exist_ok=True);(cfg/'config.yml').write_text('economy:\n  using_economy: false\n')
governance=plugins/'NeverLandTownyGovernance';governance.mkdir();(governance/'config.yml').write_text('voting:\n  proposal-cooldown-minutes: 0\n  resolution-interval-seconds: 3600\nstorage:\n  autosave-seconds: 3600\nmessages:\n  broadcast-new-proposal: false\n  broadcast-resolution: false\n')
laws=yaml.safe_load((root/'modules/NeverLandTownyGovernance/src/main/resources/laws.yml').read_text());laws['laws']['tax_release']=dict(name='Восстановление обычного налога',category='CIVIC',material='PAPER',**{'exclusive-group':'tax_policy'},description=['Убрать специальный налоговый закон'],effects={});(governance/'laws.yml').write_text(yaml.safe_dump(laws,allow_unicode=True,sort_keys=False))
print('Prepared disposable law impact fixture:',server)
