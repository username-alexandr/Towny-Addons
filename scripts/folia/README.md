# Native Folia validation

This probe is for a disposable local server only. It creates blocks and armor stands at two distant locations, tests teleports and entity retirement, dispatches validation commands, and disables/re-enables the Quests module. Do not put ValidationProbe.jar on a live server.

Prepare an empty Folia 26.2 build 7 server with Java 25. Bootstrap it once to obtain its libraries and configuration, then stop it. Install Towny 0.103.2.0, VaultUnlocked 2.20.1, PlaceholderAPI 2.12.3 and SiegeWar 3.7.0, plus the 40 suite JARs from `dist/plugins`. For this probe, set Towny's `economy.using_economy` to false; no real economy provider is bundled. Bind the disposable server to loopback and use its offline mode only locally.

Before launch, explicitly disable bStats (`plugins/bStats/config.yml`: `enabled: false`), FastStats (`plugins/faststats/config.properties`: `enabled=false`), PlaceholderAPI `check_updates` and `cloud_enabled`, Vault `update-check`, and Paper/Towny update notifications. The runner refuses to start with telemetry enabled. Set `threaded-regions.threads: 2` in `config/paper-global.yml` to exercise two region workers.

```sh
python3 scripts/folia/build_probe.py --server /absolute/path/to/disposable-server
python3 scripts/folia/run_native.py /absolute/path/to/disposable-server
```

`JAVA_HOME` selects the compiler. `JAVA` selects the executable for the runner. Set them to a Java 25 installation. The runner keeps stdin open, reads `plugins/NeverLandValidationProbe/result.txt`, issues `stop`, and waits for graceful shutdown. It removes only the validation plugin's previous result and reward queue before a run.

The probe checks loaded/enabled modules, shared state across shaded classloaders, native global/region/entity ownership, block edits, teleport completion, entity indexing and retirement, frozen tasks, reward receipts/deduplication, and administrative module shutdown. It does not simulate a player client or validate live Vault balances, premium plugins, every gameplay branch, or production load.
