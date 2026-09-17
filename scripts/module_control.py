#!/usr/bin/env python3
"""Conservative safety dependencies, including shared API bridges omitted from load-order metadata."""
import re
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[1]
TARGET=ROOT/'modules/NeverLandTownyControl/src/main/resources/module-dependencies.yml'
def dependencies():
    metadata={}
    for module in (ROOT/'modules').iterdir():
        p=module/'src/main/resources/plugin.yml'
        if p.exists() and module.name!='NeverLandTownyControl':metadata[module.name]=yaml.safe_load(p.read_text())
    shared={p.stem:p.read_text() for p in (ROOT/'shared').rglob('*.java')}
    result={}
    for name, meta in sorted(metadata.items()):
        source='\n'.join(p.read_text() for p in (ROOT/'modules'/name/'src/main/java').rglob('*.java'))
        expanded=set()
        while True:
            needed={k for k in shared if k not in expanded and re.search(r'\b'+re.escape(k)+r'\b',source)}
            if not needed:break
            expanded.update(needed);source+='\n'+'\n'.join(shared[k] for k in sorted(needed))
        providers=set(meta.get('depend',[])+meta.get('softdepend',[]))
        providers.update(n for n in metadata if n in source)
        result[name]=sorted((providers & metadata.keys())-{name})
    return result

def validate():
    if not TARGET.exists() or yaml.safe_load(TARGET.read_text())!={'schema':1,'modules':dependencies()}:
        raise RuntimeError('Stale module safety graph: run python scripts/module_control.py --write')
    for name in dependencies():
        p=ROOT/'modules'/name/'src/main/java'
        enabled=[f for f in p.rglob('*.java') if re.search(r'void\s+onEnable\s*\(',f.read_text())]
        if len(enabled)!=1 or not re.search(r'void\s+onEnable\s*\(\s*\)\s*\{\s*if\s*\(!ru\.neverland\.core\.ModuleLifecycle\.begin\(this\)\)\s*return;',enabled[0].read_text()):
            raise RuntimeError('Missing lifecycle guard: '+name)
        if not re.search(r'void\s+onDisable\s*\(\s*\)\s*\{\s*if\s*\(!ru\.neverland\.core\.ModuleLifecycle\.end\(this\)\)\s*return;',enabled[0].read_text()):
            raise RuntimeError('Missing shutdown guard: '+name)

if __name__=='__main__':
    import sys
    if '--write' in sys.argv:
        TARGET.parent.mkdir(parents=True,exist_ok=True);TARGET.write_text(yaml.safe_dump({'schema':1,'modules':dependencies()},allow_unicode=True,sort_keys=False))
    validate();print('MODULE CONTROL PASS:',len(dependencies()),'managed addons')
