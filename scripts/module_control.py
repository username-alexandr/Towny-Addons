#!/usr/bin/env python3
"""Conservative safety dependencies, including shared API bridges omitted from load-order metadata."""
import re
from functools import lru_cache
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[1]
TARGET=ROOT/'modules/NeverLandTownyControl/src/main/resources/module-dependencies.yml'
# Audited optional edges, not a blanket softdepend exclusion. Policies preserves an already
# acknowledged quest-gated choice during Quests failure while refusing any new choice;
# Quests is an unlock prerequisite, not the provider that charges its ongoing costs.
ISOLATED_EDGES={('NeverLandTownyPolicies','NeverLandTownyQuests')}
def shutdown_dependencies(name, providers, hard=()):
    result=set(providers)
    for consumer, provider in ISOLATED_EDGES:
        if name==consumer:
            if provider in hard:raise RuntimeError('Audited optional edge became a hard dependency: '+consumer+' -> '+provider)
            result.discard(provider)
    return sorted(result)
@lru_cache(maxsize=1)
def dependencies():
    metadata={}
    for module in (ROOT/'modules').iterdir():
        p=module/'src/main/resources/plugin.yml'
        if p.exists() and module.name!='NeverLandTownyControl':metadata[module.name]=yaml.safe_load(p.read_text())
    shared={p.stem:p.read_text() for p in (ROOT/'shared').rglob('*.java')}
    names=set(shared)
    references={key:set(re.findall(r'\b[A-Za-z_]\w*\b',value)) & names for key,value in shared.items()}
    result={}
    for name, meta in sorted(metadata.items()):
        source='\n'.join(p.read_text() for p in (ROOT/'modules'/name/'src/main/java').rglob('*.java'))
        expanded=set();needed=set(re.findall(r'\b[A-Za-z_]\w*\b',source)) & names
        while needed:
            key=needed.pop()
            if key in expanded:continue
            expanded.add(key);needed.update(references[key]-expanded)
        source+='\n'+'\n'.join(shared[key] for key in sorted(expanded))
        providers=set(meta.get('depend',[])+meta.get('softdepend',[]))
        providers.update(n for n in metadata if n in source)
        result[name]=shutdown_dependencies(name,(providers & metadata.keys())-{name},meta.get('depend',[]))
    return result

def lifecycle_guard(source, method, hook):
    # Resolve only explicit core imports (or its wildcard); an unrelated short class name
    # must not make an async lifecycle appear guarded. The Java compiler validates imports.
    for name in ('ModuleLifecycle','SuiteState'):
        if re.search(r'\b(?:class|interface|enum|record)\s+'+name+r'\b', source):
            continue
        if re.search(r'\bimport\s+ru\.neverland\.core\.(?:'+name+r'|\*)\s*;', source):
            source=re.sub(r'(?<![\w.])'+name+r'\.', 'ru.neverland.core.'+name+'.', source)
    wrapper = r"(?:synchronized\s*\(ru\.neverland\.core\.SuiteState\.lock\(\)\)\s*\{\s*|try\s*\(\s*var\s+\w+\s*=\s*ru\.neverland\.core\.SuiteState\.guard\(\)\s*\)\s*\{\s*)?"
    # A try/finally may surround the guard so a database can close after the ledger lock is released.
    empty_completion=r"(?:java\.util\.concurrent\.CompletionStage\s*<\s*Void\s*>\s+\w+\s*=\s*java\.util\.concurrent\.CompletableFuture\.completedFuture\(null\)\s*;\s*)?" if method=='onDisable' and hook=='end' else ''
    prefix=r"void\s+"+method+r"\s*\(\s*\)\s*\{\s*"+empty_completion+r"(?:try\s*\{\s*)?"+wrapper
    early_return=r"if\s*\(!ru\.neverland\.core\.ModuleLifecycle\."+hook+r"\(this\)\)\s*return;"
    # Shutdown may positively gate plugin cleanup while finally always drains its worker.
    # Do not accept this shape for startup, which must prevent subsequent initialization.
    positive_end=r"if\s*\(ru\.neverland\.core\.ModuleLifecycle\.end\(this\)\)\s*\{"
    return bool(re.search(prefix+early_return,source) or method=='onDisable' and hook=='end' and re.search(prefix+positive_end,source))

def validate():
    if not TARGET.exists() or yaml.safe_load(TARGET.read_text())!={'schema':1,'modules':dependencies()}:
        raise RuntimeError('Stale module safety graph: run python scripts/module_control.py --write')
    catalog=yaml.safe_load((TARGET.parent/'admin-modules.yml').read_text())['modules']
    expected=dependencies()
    if {'NeverLandTowny'+name for name in catalog}!=set(expected):raise RuntimeError('Incomplete admin catalog')
    for short,entry in catalog.items():
        meta=yaml.safe_load((ROOT/'modules'/('NeverLandTowny'+short)/'src/main/resources/plugin.yml').read_text())
        if entry['command'] not in meta['commands'] or entry['permission'] not in meta['permissions']:raise RuntimeError('Invalid admin entry: '+short)
        if entry['activities']:
            source='\n'.join(p.read_text() for p in (ROOT/'modules'/('NeverLandTowny'+short)/'src/main/java').rglob('*.java'))
            if 'ActivityAdmin.attach' not in source:raise RuntimeError('Missing activity adapter: '+short)
    for name in dependencies():
        p=ROOT/'modules'/name/'src/main/java'
        enabled=[f for f in p.rglob('*.java') if re.search(r'void\s+onEnable\s*\(',f.read_text())]
        if len(enabled)!=1 or not lifecycle_guard(enabled[0].read_text(), 'onEnable', 'begin'):
            raise RuntimeError('Missing lifecycle guard: '+name)
        if not lifecycle_guard(enabled[0].read_text(), 'onDisable', 'end'):
            raise RuntimeError('Missing shutdown guard: '+name)

if __name__=='__main__':
    import sys
    if '--write' in sys.argv:
        TARGET.parent.mkdir(parents=True,exist_ok=True);TARGET.write_text(yaml.safe_dump({'schema':1,'modules':dependencies()},allow_unicode=True,sort_keys=False))
    validate();print('MODULE CONTROL PASS:',len(dependencies()),'managed addons')
