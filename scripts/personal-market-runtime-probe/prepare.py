#!/usr/bin/env python3
"""Fresh loopback-only native fixture. Requires validated JARs and an explicitly marked stopped template."""
from pathlib import Path
import argparse,shutil,yaml,hashlib,uuid,time
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--template',type=Path,required=True);p.add_argument('--server',type=Path,required=True);p.add_argument('--towny',type=Path,required=True);p.add_argument('--control-jar',type=Path)
a=p.parse_args();template=a.template.resolve();server=a.server.resolve();root=Path(__file__).resolve().parents[2]
assert not server.exists(),'Refusing to overwrite existing server'
assert (template/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (template/'server.properties').read_text()
shutil.copytree(template,server,ignore=shutil.ignore_patterns('logs','plugins','crash-reports','validation','probe-classes','session.lock'))
plugins=server/'plugins';plugins.mkdir();shutil.copy2(a.towny,plugins/'Towny-0.103.2.0.jar')
versions=yaml.safe_load((root/'versions.yml').read_text())['addons']
for short in ['Builds','Market','Control']:
    name='NeverLandTowny'+short;jar=a.control_jar if short=='Control' and a.control_jar else root/'modules'/name/'build/libs'/f'{name}-{versions[name]}.jar';assert jar.is_file(),jar;shutil.copy2(jar,plugins)
for rel,data in [('Towny/settings/config.yml',{'economy':{'using_economy':False},'plugin':{'update_notifications':False}}),('bStats/config.yml',{'enabled':False})]:
    path=plugins/rel;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(yaml.safe_dump(data))
path=plugins/'faststats/config.properties';path.parent.mkdir(exist_ok=True);path.write_text('enabled=false\n')
config=server/'config/paper-global.yml';data=yaml.safe_load(config.read_text());data.setdefault('update-checker',{})['enabled']=False;data.setdefault('threaded-regions',{})['threads']=2;config.write_text(yaml.safe_dump(data))
properties=server/'server.properties';properties.write_text(properties.read_text().replace('server-port=25671','server-port=25672'))
print('Prepared isolated personal market fixture:',server)

def named(key):
    raw=bytearray(hashlib.md5(('personal-market-020-'+key).encode()).digest());raw[6]=(raw[6]&15)|48;raw[8]=(raw[8]&63)|128;return str(uuid.UUID(bytes=bytes(raw)))
legacy=named('history');order=named('history-order');fund=named('history-funding')
receipts={fund:dict(owner=legacy,kind='BUY_FUND',order=order,product='STONE',quantity=1,cents=2000,created=1000,status='DONE',**{'resolved-at':1000})}
for n in range(1025):
    receipts[named('history-claim-'+str(n))]=dict(owner=legacy,kind='CASH_CLAIM',order='',product='',quantity=0,cents=1,created=1001+n,status='DONE',**{'resolved-at':1001+n})
ledger=dict(schema=1,orders={order:dict(owner=legacy,side='BUY',product='STONE',quantity=1,remaining=0,**{'unit-cents':2000},created=1000,status='CANCELLED')},receipts=receipts,wallets={legacy:975},items={},fills={})
file=plugins/'NeverLandTownyMarket/personal-orders.yml';file.parent.mkdir();file.write_text(yaml.safe_dump(ledger,sort_keys=False))
