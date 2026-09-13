import copy
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from api_audit import ROOT, inventory, validate

class SourceAuditTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        main = cls.root/'modules/NeverLandTownyFixture/src/main/java'
        tests = cls.root/'modules/NeverLandTownyFixture/src/test/java'
        main.mkdir(parents=True); tests.mkdir(parents=True); (cls.root/'shared').mkdir()
        (main/'FixtureApi.java').write_text('''package fixture;
            public interface FixtureApi extends ru.neverland.core.ApiContract {
              default java.util.Set<String> capabilities() { return java.util.Set.of("refund", "snapshot"); }
              String refund(); String snapshot();
            }''')
        (main/'Consumer.java').write_text('''package fixture;
            class Consumer {
              static final String PLUGIN="NeverLandTownyFixture", API="fixture.FixtureApi";
              void first() { helper("refund", new Class<?>[]{}, 1, 2); }
              void second() { helper("snapshot", new Class<?>[]{}); }
              void helper(String cap, Class<?>[] signature, Object... args) {
                 ApiServices.call(PLUGIN, API, cap, signature, args);
              }
              // ApiServices.require("bad", "missing", "release");
            }''')
        (tests/'Different.java').write_text('''package fixture;
            class Different { static public void main(java.lang.String args[]) {} }
            class Varargs { public static void main(String... args) {} }
            class Comment { /* public static void main(String[] args) {} */ }
            class Instance { public void main(String[] args) {} }
        ''')
        cls.data = inventory(cls.root)
    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()
    def test_constants_forwarding_and_varargs(self):
        self.assertEqual({c['capabilities'][0] for c in self.data['consumers']}, {'refund','snapshot'})
        self.assertEqual(self.data['dynamic_sites'], [])
    def test_java_syntax_test_discovery(self):
        self.assertEqual({t['class'] for t in self.data['tests']}, {'fixture.Different','fixture.Varargs'})
    def test_missing_capability_rejected(self):
        data=copy.deepcopy(self.data); data['consumers'][0]['capabilities']=['release']
        with self.assertRaisesRegex(RuntimeError,'release'): validate(data,self.root)
    def test_wrong_provider_rejected(self):
        data=copy.deepcopy(self.data); data['consumers'][0]['plugin']='NeverLandTownyOther'
        with self.assertRaisesRegex(RuntimeError,'belongs to'): validate(data,self.root)
    def test_wrong_major_rejected(self):
        data=copy.deepcopy(self.data); data['consumers'][0]['major']=2
        with self.assertRaisesRegex(RuntimeError,'major=2'): validate(data,self.root)
    def test_unanalysed_call_site_rejected(self):
        data=copy.deepcopy(self.data); data['sites'].append('lost.java:1')
        with self.assertRaisesRegex(RuntimeError,'Unanalysed'): validate(data,self.root)

class CompiledAbiTest(unittest.TestCase):
    def test_record_change_and_removed_default_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);classes=root/'classes';classes.mkdir()
            java=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else None
            javac=str(java/'javac') if java else 'javac'
            jvm=str(java/'java') if java else 'java'
            contract=root/'ApiContract.java';api=root/'FixtureApi.java';snapshot=root/'Snapshot.java'
            contract.write_text('package ru.neverland.core; public interface ApiContract { default int apiVersion(){return 1;} default java.util.Set<String> capabilities(){return java.util.Set.of();} }')
            api.write_text('package ru.neverland.fixture; public interface FixtureApi extends ru.neverland.core.ApiContract { default java.util.Set<String> capabilities(){return java.util.Set.of("snapshot");} default Snapshot snapshot(){return null;} }')
            snapshot.write_text('package ru.neverland.fixture; public record Snapshot(int total) {}')
            def compile(): subprocess.run([javac,'--release','17','-d',str(classes),str(contract),str(api),str(snapshot),str(ROOT/'scripts/ApiContractProbe.java')],check=True,capture_output=True,text=True)
            compile()
            base=root/'baseline.tsv'
            base.write_text(subprocess.check_output([jvm,'-cp',str(classes),'ApiContractProbe','--write-baseline','ru.neverland.fixture.FixtureApi'],text=True))
            snapshot.write_text('package ru.neverland.fixture; public record Snapshot(long total) {}')
            api.write_text(api.read_text().replace('default Snapshot snapshot(){return null;}','Snapshot snapshot();'))
            compile()
            result=subprocess.run([jvm,'-cp',str(classes),'ApiContractProbe','--baseline',str(base),'ru.neverland.fixture.FixtureApi'],text=True,capture_output=True)
            self.assertNotEqual(result.returncode,0)
            self.assertIn('default snapshot()',result.stderr)
            self.assertIn('total:int',result.stderr)

if __name__ == '__main__': unittest.main()
