#!/usr/bin/env python3
"""Create a fresh opted-in native fixture from a stopped disposable Folia bootstrap."""
from pathlib import Path
import argparse,shutil,yaml
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--template',type=Path,required=True);p.add_argument('--server',type=Path,required=True);p.add_argument('--towny',type=Path,required=True)
a=p.parse_args();template=a.template.resolve();server=a.server.resolve();root=Path(__file__).resolve().parents[2]
assert not server.exists(), "Refusing to overwrite any existing server"
assert (template/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (template/'server.properties').read_text()
shutil.copytree(template,server,ignore=shutil.ignore_patterns('logs','bootstrap.log','plugins','crash-reports','validation','probe-classes'))
plugins=server/'plugins';plugins.mkdir(exist_ok=True);shutil.copy2(a.towny.resolve(),plugins/'Towny-0.103.2.0.jar')
versions=yaml.safe_load((root/'versions.yml').read_text())['addons']
for short in ['Builds','Contracts','SiegesPlus']:
    name='NeverLandTowny'+short;source=root/'modules'/name/'build/libs'/f'{name}-{versions[name]}.jar';assert source.is_file(),source;shutil.copy2(source,plugins)
cfg=plugins/'Towny/settings';cfg.mkdir(parents=True,exist_ok=True);(cfg/'config.yml').write_text('economy:\n  using_economy: false\n')
print('Prepared disposable loopback fixture:',server)
