import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class PublishedAssetsTest(unittest.TestCase):
    def run_gate(self, mutate):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            artifact = root/'example.jar'
            artifact.write_bytes(b'tested bytes')
            release = {'draft': True, 'assets': [{'name': artifact.name, 'state': 'uploaded',
                'size': artifact.stat().st_size,
                'digest': 'sha256:'+hashlib.sha256(artifact.read_bytes()).hexdigest()}]}
            mutate(release)
            metadata = root/'release.json'
            metadata.write_text(json.dumps(release))
            return subprocess.run([sys.executable, str(Path(__file__).with_name('verify_published_assets.py')),
                str(metadata), str(artifact)], capture_output=True, text=True).returncode

    def test_exact_uploaded_bytes_pass(self):
        self.assertEqual(0, self.run_gate(lambda r: None))

    def test_changed_bytes_and_incomplete_upload_block_publication(self):
        for field, value in [('digest', 'sha256:wrong'), ('size', 1), ('state', 'starter')]:
            with self.subTest(field=field):
                self.assertNotEqual(0, self.run_gate(lambda r: r['assets'][0].update({field:value})))

    def test_missing_extra_and_duplicate_assets_block_publication(self):
        for mutate in [lambda r: r['assets'].clear(),
                       lambda r: r['assets'].append(dict(r['assets'][0], name='extra.jar')),
                       lambda r: r['assets'].append(dict(r['assets'][0]))]:
            self.assertNotEqual(0, self.run_gate(mutate))

    def test_published_release_cannot_be_reused(self):
        self.assertNotEqual(0, self.run_gate(lambda r: r.update(draft=False)))


if __name__ == '__main__':
    unittest.main()
