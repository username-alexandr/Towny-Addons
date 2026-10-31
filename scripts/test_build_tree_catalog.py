import copy
import unittest

from verify_release import ROOT, building_tree_errors, load_yaml


class BuildingTreeCatalogTest(unittest.TestCase):
    def setUp(self):
        path = ROOT / "modules/NeverLandTownyBuilds/src/main/resources/projects.yml"
        self.buildings = load_yaml(path)["buildings"]

    def test_shipped_tree_has_valid_categories_and_reachable_dependencies(self):
        self.assertEqual([], building_tree_errors(self.buildings))

    def test_missing_dependency_and_cycle_are_rejected(self):
        modified = copy.deepcopy(self.buildings)
        modified["forge"]["requires"] = {"missing_city": 1}
        self.assertTrue(building_tree_errors(modified))
        modified["forge"]["requires"] = {"miners_guild": 1}
        self.assertTrue(any("cyclic" in error for error in building_tree_errors(modified)))

    def test_bad_level_and_missing_category_are_rejected(self):
        modified = copy.deepcopy(self.buildings)
        modified["forge"]["requires"] = {"town_hall": 6}
        modified["forge"].pop("category")
        self.assertGreaterEqual(len(building_tree_errors(modified)), 2)


if __name__ == "__main__":
    unittest.main()
