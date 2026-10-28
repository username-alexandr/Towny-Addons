import unittest
import module_control

class LifecycleGuardTest(unittest.TestCase):
    def test_actual_scopes_and_phase(self):
        for method, hook in [('onEnable', 'begin'), ('onDisable', 'end')]:
            body=f'if (!ru.neverland.core.ModuleLifecycle.{hook}(this)) return;'
            wrappers=[('', ''), ('try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {', '}'), ('synchronized (ru.neverland.core.SuiteState.lock()) {', '}')]
            for opening, closing in wrappers:
                with self.subTest(method=method, scope=opening):
                    self.assertTrue(module_control.lifecycle_guard(f'public void {method}() {{ {opening} {body} {closing} }}', method, hook))
            self.assertFalse(module_control.lifecycle_guard(f'void {method}() {{ loadData(); {body} }}', method, hook))
            self.assertFalse(module_control.lifecycle_guard(f'void {method}() {{ if (!ru.neverland.core.ModuleLifecycle.wrong(this)) return; }}', method, hook))

    def test_current_dependency_graph_and_lifecycle(self):
        module_control.validate()

if __name__ == '__main__':
    unittest.main()
