# Native Folia raid-scaling probe

Test-only integration plugin for Events 0.4.10. Never put this JAR on a production server.

The plugin requires both a file named `ALLOW_DISPOSABLE_RAID_SCALING_PROBE` in the server working directory and `server-ip=127.0.0.1`. It creates a synthetic Towny city, changes that city's building levels, starts a real raid, kills one real mob, tests administrative actions, saves state and shuts down the fixture. A second launch checks persisted data and resumes the raid.

Dependencies tested: Java 25, Folia 26.2 build 7, Towny 0.103.2.0, NeverLandTownyBuilds 0.8.34 and Events 0.4.10. Use a new flat world with a safe floor and the normal unmodified raid catalog. In the fixture only, disable random event selection (`scheduler.enabled: false`), use `runtime.tick-seconds: 1`, `gameplay.raid-wave-interval-seconds: 1` and `gameplay.raid-failed-retry-seconds: 1`. No Vault provider or player client is required for these raid checks.

Compile `RaidScalingProbe.java` with the Paper API/dependency classpath used by Events plus the Builds and Events JARs, then package the resulting classes and `plugin.yml` as `RaidScalingProbe.jar`. Keep it outside release archives intended for `plugins/`.

Start the disposable server twice, retaining all data between those two launches. Require both `plugins/RaidScalingProbe/initial-passed.txt` and `restart-passed.txt`, no `*-failed.txt`, and inspect the corresponding `*-checks.txt` and server logs. A timeout or missing result is a test failure. The suite restores the Builds frozen flag in a `finally` block; all fixture changes are confined to the disposable server.

World time changes and shutdown run on the global scheduler. Terrain/entity operations run on the owning region; shared suite data uses its existing lock. This is a server integration test, not a player-client or load test.
