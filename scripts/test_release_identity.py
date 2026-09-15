import unittest
import yaml
from verify_release import release_identity_errors


class ReleaseIdentityTest(unittest.TestCase):
    def test_matrix_tag_and_publication_branch_agree(self):
        workflow = yaml.safe_load("on:\n  push:\n    branches: [release/v0.31.0]\n")
        self.assertEqual([], release_identity_errors({'suite': '0.31.0'}, '0.31.0\n', workflow))

    def test_stale_package_tag_or_publish_branch_fails(self):
        workflow = {'on': {'push': {'branches': ['release/v0.31.0']}}}
        self.assertTrue(release_identity_errors({'suite': '0.31.0'}, '0.30.0', workflow))
        workflow['on']['push']['branches'] = ['release/v0.30.0']
        self.assertTrue(release_identity_errors({'suite': '0.31.0'}, '0.31.0', workflow))


if __name__ == '__main__':
    unittest.main()
