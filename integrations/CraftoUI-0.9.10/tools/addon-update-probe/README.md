# Compatibility probe — Suite 0.51.0

Run the normal release build, including `:server:writeUiTestClasspath`, then:

```sh
python3 tools/addon-update-probe/run.py --java-home /path/to/jdk25 --addon-dir /path/to/NeverLandTowny-0.51.0/plugins --output build/addon-api
```

Checks the actual addon JAR classes, owner-declared command roots, and production CityBuildSchema against the real Builds 0.9.1 BuildingStyle and BuildingBlueprintGenerator APIs. Generates no players, worlds, scheduler doubles or server instance. Uses the resolved Gradle test classpath and unchanged addon binaries. This does not replace an in-game server test.

The new UI fixtures run with `tools/minimal-preview/run.py --addons-update`; platform, inventory and transport are explicit adapters and item data are representative. Tests compile production renderers. Normal/density/preview and civic recovery regressions remain available in their existing tools.
