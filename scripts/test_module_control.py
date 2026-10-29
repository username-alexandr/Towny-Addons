import unittest
import module_control

class LifecycleGuardTest(unittest.TestCase):
    def test_direct_async_activity_protocol_is_complete(self):
        source='public int adminMenuVersion(){return 1;} public List<Map<String,Object>> adminMenuTargets(CommandSender sender){return rows;} public String adminMenuAction(CommandSender sender,String id,String name,String action,long minutes){return result;}'
        self.assertTrue(module_control.activity_adapter(source))
        for old,new in [('return 1','return 2'),('public int','private int'),('adminMenuTargets','other'),('long minutes','int minutes'),('String action','UUID action')]:
            self.assertFalse(module_control.activity_adapter(source.replace(old,new)))
        self.assertFalse(module_control.activity_adapter('/* '+source+' */'))
        self.assertFalse(module_control.activity_adapter('// ActivityAdmin.attach(this);'))
        self.assertTrue(module_control.activity_adapter('ru.neverland.core.ActivityAdmin.attach(this,"command","permission",targets);'))

    def test_positive_shutdown_gate_with_unconditional_drain(self):
        source='void onDisable(){try(var guard=ru.neverland.core.SuiteState.guard()){if(ru.neverland.core.ModuleLifecycle.end(this)){stop();}}finally{drain();}}'
        self.assertTrue(module_control.lifecycle_guard(source,'onDisable','end'))
        self.assertFalse(module_control.lifecycle_guard(source.replace('onDisable','onEnable').replace('.end(','.begin('),'onEnable','begin'))
        self.assertFalse(module_control.lifecycle_guard(source.replace('if(ru.','stop();if(ru.'),'onDisable','end'))
        self.assertFalse(module_control.lifecycle_guard(source.replace('.end(','.begin('),'onDisable','end'))
        initialized=source.replace('void onDisable(){','void onDisable(){java.util.concurrent.CompletionStage<Void> closing=java.util.concurrent.CompletableFuture.completedFuture(null);')
        self.assertTrue(module_control.lifecycle_guard(initialized,'onDisable','end'))
        self.assertFalse(module_control.lifecycle_guard(initialized.replace('java.util.concurrent.CompletableFuture.completedFuture(null)','loadAndPay()'),'onDisable','end'))
        declared=source.replace('void onDisable(){','void onDisable(){java.util.concurrent.CompletionStage<Void> pending;')
        self.assertTrue(module_control.lifecycle_guard(declared,'onDisable','end'))
        self.assertFalse(module_control.lifecycle_guard(declared.replace('pending;','pending=loadAndPay();'),'onDisable','end'))
        self.assertFalse(module_control.lifecycle_guard(declared.replace('pending;','pending;stop();'),'onDisable','end'))
        self.assertFalse(module_control.lifecycle_guard(declared.replace('.end(','.begin('),'onDisable','end'))
        self.assertFalse(module_control.lifecycle_guard(declared.replace('onDisable','onEnable').replace('.end(','.begin('),'onEnable','begin'))
    def test_imported_lifecycle_in_async_initialization(self):
        source='import ru.neverland.core.*; public void onEnable(){ if(!ModuleLifecycle.begin(this))return; queueConfig(); }'
        self.assertTrue(module_control.lifecycle_guard(source,'onEnable','begin'))
        self.assertTrue(module_control.lifecycle_guard('import ru.neverland.core.ModuleLifecycle; public void onEnable(){ if(!ModuleLifecycle.begin(this))return; queueConfig(); }','onEnable','begin'))
        self.assertFalse(module_control.lifecycle_guard(source.replace('ru.neverland.core','other.library'),'onEnable','begin'))
        self.assertFalse(module_control.lifecycle_guard(source.replace('if(!ModuleLifecycle','queueConfig(); if(!ModuleLifecycle'),'onEnable','begin'))
        self.assertFalse(module_control.lifecycle_guard(source+' class ModuleLifecycle {}','onEnable','begin'))
        self.assertTrue(module_control.lifecycle_guard('import ru.neverland.core.*; void onDisable(){try{try(var guard=SuiteState.guard()){if(!ModuleLifecycle.end(this))return;stop();}}finally{drain();}}','onDisable','end'))

    def test_actual_scopes_and_phase(self):
        for method, hook in [('onEnable', 'begin'), ('onDisable', 'end')]:
            body=f'if (!ru.neverland.core.ModuleLifecycle.{hook}(this)) return;'
            wrappers=[('', ''), ('try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {', '}'), ('synchronized (ru.neverland.core.SuiteState.lock()) {', '}'), ('try { try (var guard = ru.neverland.core.SuiteState.guard()) {', '} } finally { awaitClosed(); }')]
            for opening, closing in wrappers:
                with self.subTest(method=method, scope=opening):
                    self.assertTrue(module_control.lifecycle_guard(f'public void {method}() {{ {opening} {body} {closing} }}', method, hook))
            self.assertFalse(module_control.lifecycle_guard(f'void {method}() {{ loadData(); {body} }}', method, hook))
            self.assertFalse(module_control.lifecycle_guard(f'void {method}() {{ try {{ loadData(); {body} }} finally {{ close(); }} }}', method, hook))
            self.assertFalse(module_control.lifecycle_guard(f'void {method}() {{ if (!ru.neverland.core.ModuleLifecycle.wrong(this)) return; }}', method, hook))

    def test_quests_isolation_is_narrow_and_hard_dependencies_remain(self):
        graph=module_control.dependencies()
        def closure(root):
            found={root}
            while True:
                next_set=found|{name for name,deps in graph.items() if found.intersection(deps)}
                if next_set==found:return found
                found=next_set
        self.assertEqual(closure('NeverLandTownyQuests'),{'NeverLandTownyQuests'})
        self.assertGreater(len(closure('NeverLandTownyBuilds')),1)
        self.assertEqual(module_control.ISOLATED_EDGES,{('NeverLandTownyPolicies','NeverLandTownyQuests'),('NeverLandTownySeasons','NeverLandTownySpecialization'),('NeverLandTownySpecialization','NeverLandTownySeasons'),('NeverLandTownyResearch','NeverLandTownyTreasuryPlus')})
        self.assertNotIn('NeverLandTownySeasons',graph['NeverLandTownySpecialization'])
        self.assertNotIn('NeverLandTownyTreasuryPlus',graph['NeverLandTownyResearch'])
        self.assertNotIn('NeverLandTownySeasons',closure('NeverLandTownySpecialization'))
        self.assertEqual(module_control.shutdown_dependencies('NeverLandTownySeasons',{'NeverLandTownySpecialization'}),[])
        self.assertEqual(module_control.shutdown_dependencies('NewConsumer',{'NeverLandTownyQuests'}),['NeverLandTownyQuests'])
        self.assertEqual(module_control.shutdown_dependencies('NeverLandTownyPolicies',{'NeverLandTownyQuests','NeverLandTownyUpkeep'}),['NeverLandTownyUpkeep'])
        for consumer,provider in module_control.ISOLATED_EDGES:
            with self.assertRaises(RuntimeError):
                module_control.shutdown_dependencies(consumer,{provider},[provider])

    def test_current_dependency_graph_and_lifecycle(self):
        module_control.validate()

if __name__ == '__main__':
    unittest.main()
