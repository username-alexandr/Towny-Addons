import unittest
import module_control

class LifecycleGuardTest(unittest.TestCase):
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
        self.assertEqual(module_control.ISOLATED_EDGES,{('NeverLandTownyPolicies','NeverLandTownyQuests')})
        self.assertEqual(module_control.shutdown_dependencies('NewConsumer',{'NeverLandTownyQuests'}),['NeverLandTownyQuests'])
        self.assertEqual(module_control.shutdown_dependencies('NeverLandTownyPolicies',{'NeverLandTownyQuests','NeverLandTownyUpkeep'}),['NeverLandTownyUpkeep'])
        with self.assertRaises(RuntimeError):
            module_control.shutdown_dependencies('NeverLandTownyPolicies',{'NeverLandTownyQuests'},['NeverLandTownyQuests'])

    def test_current_dependency_graph_and_lifecycle(self):
        module_control.validate()

if __name__ == '__main__':
    unittest.main()
