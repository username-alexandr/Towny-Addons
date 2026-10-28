from pathlib import Path
import json
import tempfile
import unittest
import zipfile
from pinned_artifacts import capture,publish,build_lock

class ValidatedArtifactsTest(unittest.TestCase):
    def setUp(self):
        self.folder=tempfile.TemporaryDirectory();self.addCleanup(self.folder.cleanup)
        self.root=Path(self.folder.name);self.classes=self.root/'classes';self.classes.mkdir()
        (self.classes/'Main.class').write_bytes(b'compiled fixture')
        self.source=self.root/'Main.java';self.source.write_text('class Main {}')
        self.target=self.root/'libs/plugin.jar';self.target.parent.mkdir();self.target.write_bytes(b'prior verified JAR')
    def snapshot(self):return capture(self.root,self.root.glob('*.java'))
    def test_changes_during_compile_do_not_publish(self):
        before=self.snapshot();self.source.write_text('class Main { int changed; }')
        with self.assertRaises(RuntimeError):publish(self.classes,self.target,before,self.snapshot,{})
        self.assertEqual(b'prior verified JAR',self.target.read_bytes())
    def test_new_files_are_detected(self):
        before=self.snapshot();(self.root/'Added.java').write_text('class Added {}')
        with self.assertRaises(RuntimeError):publish(self.classes,self.target,before,self.snapshot,{})
        self.assertEqual(b'prior verified JAR',self.target.read_bytes())
    def test_validated_publish_carries_source_and_test_identity(self):
        before=self.snapshot();publish(self.classes,self.target,before,self.snapshot,dict(module='Fixture',version='1',tests=['meaningful.test']))
        with zipfile.ZipFile(self.target) as archive:
            self.assertEqual(b'compiled fixture',archive.read('Main.class'))
            meta=json.loads(archive.read('META-INF/neverland-build.json'))
        self.assertEqual(before,meta['inputs']);self.assertTrue(meta['tests_passed']);self.assertEqual(['meaningful.test'],meta['tests'])
        self.assertFalse(list(self.target.parent.glob('*.tmp')))
    def test_inputs_cannot_escape_source_root(self):
        with self.assertRaises(ValueError):capture(self.root,[self.root/'../outside.java'])
    def test_overlapping_compilers_are_rejected_and_lock_is_reusable(self):
        with build_lock(self.classes):
            with self.assertRaises(RuntimeError):
                with build_lock(self.classes):self.fail('shared classes must be exclusive')
        with build_lock(self.classes):pass

if __name__=='__main__':unittest.main()
