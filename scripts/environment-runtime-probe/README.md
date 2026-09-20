# Disposable ecology integration fixture

This opt-in probe creates fictional Towny records, replaces selected API observations with deterministic proxies, invokes service cycles and shuts down the test server. Never install it on a production server.

Prepare a **fresh** Purpur 26.2 / Towny 0.103.2.0 directory with the addon kit, Java 25, `server-ip=127.0.0.1`, EULA and a file named `ALLOW_DISPOSABLE_RELIABILITY_PROBE`. Set Environment `cycle-seconds: 30` before the first run. Server libraries must already be bootstrapped; stop that bootstrap before installing the probe. Other ecology settings must retain the shipped defaults. No economy provider or PlaceholderAPI is required.

```sh
python3 scripts/environment-runtime-probe/build.py --server-dir /absolute/disposable-server --java-home /absolute/jdk25
python3 scripts/environment-runtime-probe/run.py --server-dir /absolute/disposable-server --java-home /absolute/jdk25
```

The runner requires the loopback setting and marker, supplies `-Dneverland.runtimeProbe=true`, and starts two fresh JVMs. The probe itself independently checks these guards. It creates a city, resident and claim; controls public building/upkeep/power/specialization observations; sets initial pollution through the actual repository; calls ecological pulses explicitly; checks real Resources and Population calculations, inventory protection and administrative permissions; disables Environment; and verifies saved data on the next JVM.

Outputs: `plugins/NeverLandEnvironmentRuntimeProbe/{checks.txt,first-passed.txt,restart-passed.txt,plugins.json,saved-environment.yml}` and server logs. A `failed.txt` or missing pass marker fails the runner. Existing pass markers are refused: use a new disposable fixture for each attempt. Optional `--extra-jar` arguments to the builder supply missing compile-only annotation dependencies.
