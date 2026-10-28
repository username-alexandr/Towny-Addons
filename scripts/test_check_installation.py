import json
import tempfile
import unittest
import zipfile
from pathlib import Path

import yaml

from check_installation import check


class InstalledSetTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def jar(self, file, name, major=69, descriptor='plugin.yml', **extra):
        desc = dict(name=name, main='test.Plugin', version='1.0.0', **{'folia-supported': True})
        desc.update(extra)
        with zipfile.ZipFile(self.root / file, 'w') as archive:
            archive.writestr(descriptor, yaml.safe_dump(desc))
            archive.writestr('test/Plugin.class', b'\xca\xfe\xba\xbe\x00\x00' + major.to_bytes(2, 'big'))
            if name.startswith('NeverLandTowny'):
                archive.writestr('ru/neverland/core/SuiteState.class', b'test')
                archive.writestr('ru/neverland/core/SuiteScheduler.class', b'test')

    def codes(self, result=None):
        return {row['code'] for row in (result or check(self.root))['findings']}

    def test_real_duplicate_version_filenames(self):
        self.jar('Control-0.4.8.jar', 'NeverLandTownyControl')
        self.jar('Control-0.4.9.jar', 'NeverLandTownyControl')
        result = check(self.root)
        self.assertFalse(result['ok'])
        self.assertIn('DUPLICATE_NAME', self.codes(result))

    def test_alias_satisfies_dependency_case_insensitive(self):
        self.jar('provider.jar', 'EconomyBridge', provides=['Vault'])
        self.jar('consumer.jar', 'Consumer', depend=['vault'])
        self.assertTrue(check(self.root)['ok'])

    def test_hard_dependency_missing_and_java(self):
        self.jar('company.jar', 'NeverLandTownyCompanies', depend=['NeverLandTownyContracts'])
        result = check(self.root, java=21)
        self.assertEqual({'MISSING_DEPENDENCY', 'JAVA_TOO_OLD'}, self.codes(result))

    def test_cycle_including_soft_loading_order(self):
        self.jar('a.jar', 'A', depend=['B'])
        self.jar('b.jar', 'B', softdepend=['A'])
        self.assertIn('LOAD_CYCLE', self.codes())

    def test_paper_dependencies(self):
        self.jar('a.jar', 'A', descriptor='paper-plugin.yml', dependencies={'server': {'B': {'required': True, 'load': 'BEFORE'}}})
        self.assertIn('MISSING_DEPENDENCY', self.codes())
        self.jar('b.jar', 'B')
        self.assertTrue(check(self.root)['ok'])

    def test_invalid_jar_and_library(self):
        (self.root / 'broken.jar').write_bytes(b'bad')
        with zipfile.ZipFile(self.root / 'CraftoUI-api.jar', 'w') as archive:
            archive.writestr('a.txt', 'library only')
        self.assertIn('INVALID_JAR', self.codes())
        self.assertIn('NO_DESCRIPTOR', self.codes())

    def test_folia_and_config_no_secret_output(self):
        self.jar('a.jar', 'NeverLandTownyTest', **{'folia-supported': False})
        directory = self.root / 'NeverLandTownyTest'
        directory.mkdir()
        (directory / 'config.yml').write_text('token: [SUPER_SECRET_TOKEN\n')
        result = check(self.root)
        self.assertIn('FOLIA_UNDECLARED', self.codes(result))
        self.assertIn('INVALID_CONFIG', self.codes(result))
        self.assertNotIn('SUPER_SECRET_TOKEN', json.dumps(result))

    def test_empty_directory_not_success(self):
        self.assertFalse(check(self.root)['ok'])

    def test_vault_is_not_treated_as_economy_provider_ready(self):
        self.jar('vault.jar', 'Vault')
        self.assertIn('ECONOMY_RUNTIME_CHECK', self.codes())


if __name__ == '__main__':
    unittest.main()
