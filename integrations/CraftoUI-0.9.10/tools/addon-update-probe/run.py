#!/usr/bin/env python3
"""Check shipped addon binaries, owned roots, exact holder classes and real styled blueprint API."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,zipfile,yaml
p=argparse.ArgumentParser()
for key in ('java-home','addon-dir','output'):p.add_argument('--'+key,type=Path,required=True)
a=p.parse_args();root=Path(__file__).resolve().parents[2];out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
resources=root/'server/src/main/resources';modules={};checks=0
def check(ok,why):
    global checks
    if not ok:raise AssertionError(why)
    checks+=1
for jar in sorted(a.addon_dir.glob('NeverLandTowny*.jar')):
    with zipfile.ZipFile(jar) as z:
        assert z.testzip() is None
        plugin=yaml.safe_load(z.read('plugin.yml'));modules[plugin['name']]={'jar':jar,'plugin':plugin,'classes':set(z.namelist())}
catalog=json.loads((resources/'addons.json').read_text());previous=json.loads((resources/'migrations/0.8.20/addons.json').read_text())
was={m['plugin']:set(m['holders']) for m in previous['modules']};added=[]
for m in catalog['modules']:
    for holder in set(m['holders'])-was[m['plugin']]:
        source=modules[m['plugin']];check(holder.replace('.','/')+'.class' in source['classes'],m['plugin']+' does not own '+holder)
        check(holder not in m.get('inventoryHolders',[]),'readonly menu must retain native control semantics')
        added.append({'plugin':m['plugin'],'holder':holder,'permission':m.get('holderPermissions',{}).get(holder,m['permission'])})
legacy=[]
for m in catalog['modules']:
    for holder in m['holders']:
        present=holder.replace('.','/')+'.class' in modules[m['plugin']]['classes']
        if not present and m['plugin']=='NeverLandTownyBuilds' and holder=='ru.neverland.townybuilds.gui.MenuManager$ContributionHolder':
            legacy.append({'plugin':m['plugin'],'holder':holder,'purpose':'Compatibility with earlier Builds; 0.11.2 uses Chronicles contributions.'});continue
        check(present,m['plugin']+' no longer owns '+holder)
quick=json.loads((resources/'quick-actions.json').read_text())
for key,item in quick.items():
    owner=item['requiredPlugin'];parts=item['command'].split();command_root=parts[0]
    if command_root=='town':continue # Towny subcommands are registered by the original addon.
    plugin=modules[owner]['plugin'];roots=plugin.get('commands',{})
    check(command_root in roots or any(command_root in (v or {}).get('aliases',[]) for v in roots.values()),'quick link owner/root: '+key)
check(len(added)==21,'expected all 21 newly added menus')
commands=json.loads((resources/'command-ui.json').read_text());old=json.loads((resources/'migrations/0.8.20/command-ui.json').read_text());old_forms={m['plugin']:{f['id'] for f in m['forms']} for m in old['modules']}
new_forms=[]
for m in commands['modules']:
    for f in m['forms']:
        if f['id'] in old_forms[m['plugin']]:continue
        if m['plugin']=='Towny':continue # External Towny command; source/permission evidence is recorded separately.
        check(f['root'] in modules[m['plugin']]['plugin'].get('commands',{}),m['plugin']+' must own root '+f['root'])
        new_forms.append({'plugin':m['plugin'],'id':f['id'],'root':f['root'],'prefix':f['prefix']})
check(len(new_forms)==32,'expected all 32 curated actions')
proof={'suite':'0.52.1','source_commit':'45d4efeeee2aeba721b8ba44a46de1ee766df0d3','added_holders':added,'legacy_holders':legacy,'added_forms':new_forms,
       'addons':[{'plugin':name,'version':str(item['plugin']['version']),'file':item['jar'].name,'sha256':hashlib.sha256(item['jar'].read_bytes()).hexdigest()} for name,item in modules.items()]}
(out/'binary-contracts.json').write_text(json.dumps(proof,ensure_ascii=False,indent=2)+'\n')
classpath=(root/'server/build/ui-test-classpath.txt').read_text().strip()+os.pathsep+os.pathsep.join(str(m['jar'].resolve()) for m in modules.values())
classes=out/'classes';classes.mkdir(exist_ok=True)
subprocess.run([str(a.java_home/'bin/javac'),'--release','25','-encoding','UTF-8','-cp',classpath,'-d',str(classes),str(Path(__file__).with_name('AddonApiProbe.java'))],check=True)
subprocess.run([str(a.java_home/'bin/java'),'-cp',str(classes)+os.pathsep+classpath,'cc.crafto.ui.server.AddonApiProbe'],check=True)
print(f'PASS {checks} addon binary/root/holder contract checks; actual unchanged release JARs, no running Folia')
