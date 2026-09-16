# Disposable Seasons runtime probe

Creates a fixture town and edits its calendar, building metadata, stocks and events. Never use a player server.

Prepare Purpur 26.2-2632 / Java 25, Towny 0.103.2.0 and all exact addon versions from versions.yml. Add Vault/EssentialsX/PlaceholderAPI and other already configured runtime dependencies. Bind server-ip=127.0.0.1 and use a free local port. Create `ALLOW_DISPOSABLE_RELIABILITY_PROBE` in this disposable server directory.

Run `build.py --server-dir <path> --java-home <jdk25>`; `--extra-jar <annotations.jar>` is available if the compile classpath needs it. Then run `run.py` with the same arguments and `--phase first`, followed by `--phase restart` on the same directory. The runner provides the required JVM opt-in flag. Stop on any failure and inspect logs; restore a clean snapshot before repeating.

The build derives all API contracts and expected versions from source. Assertions cover native calendar/resource/event integration, actual production conservation, crop growth, manual override persistence, flood journal restart, time changes, provider failures, permissions, menu/back behavior and strict reload. Player interfaces are fixture proxies; no clients or load simulation. RealisticSeasons itself is not supplied or exercised, only explicit missing-provider behavior.

Proof files are under plugins/NeverLandSeasonsRuntimeProbe; each phase records the installed JAR SHA256 values. After testing, remove NeverLandSeasonsRuntimeProbe.jar. Do not distribute the probe in release bundles.
