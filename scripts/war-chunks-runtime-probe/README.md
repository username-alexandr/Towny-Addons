# Native war chunk probe

Run only on a **fresh disposable loopback Folia 26.2 build 7 server** with Towny 0.103.2.0, Builds 0.8.43, SiegesPlus 0.5.0 and Control 0.4.19. The probe refuses to run without `ALLOW_DISPOSABLE_RAID_PROBE` and `server-ip=127.0.0.1`. It creates test towns, claims, blocks and SQLite records, alters test rules, then shuts down twice. Never install it on a live server or player world.

Use Java 25. Supply a classpath file with the genuine Folia runtime/API libraries and Towny; the builder adds patched server classes itself.

```sh
python scripts/war-chunks-runtime-probe/build.py --server /absolute/fresh-fixture --java-home /absolute/jdk25 --classpath-file /absolute/dependencies.classpath
python scripts/war-chunks-runtime-probe/run.py --server /absolute/fresh-fixture --java-home /absolute/jdk25
```

The runner checks the actual JAR hashes, both process exits, fresh proof markers, SQLite capture/release states and event/plugin/watchdog errors. The fixture uses genuine Towny listeners and permission executors, real TNT explosions, actual SQLite and Folia block-region callbacks. Offline CraftPlayer fixtures are entered only in the current region's identity list, not the login or player ticking lists; their real entity handles pass Folia's ownership check. No authenticated clients are connected. Army/camp admission uses an explicit `WarControl.Access`/`TntRaidEngine.Access` test policy. Missing production Army is separately required to fail closed. This does not certify real-client networking, real Army muster, third-party protection combinations or SiegeWar battles.

Scenarios cover peaceful baseline and declaration PvP, reciprocal melee, arrows, attributed explosions, unrelated visitors, Towny respawn protection, foreign cancellations, all TNT priming causes, direct spawns, dispensers versus droppers, timed occupation, defenders, spectator exclusion, narrow TNT permissions, neighboring chunks, demobilization, temporary camp unavailability, multiple registered building parts, actual TNT fired outside the city, unfinished geometry versus repair eligibility, construction-site permissions, admin buttons/logs, native restart, war identity isolation and cancellation/release. The five-second occupation setting is a fixture override; the production default is 120 seconds.

For the current closure regression, prepare a fresh fixture with `prepare.py --template <marked stopped server> --server <new path> --towny <Towny JAR> --candidate-root <built source root>`. It installs the exact Builds, SiegesPlus and Control versions from `versions.yml`. Run with `--builds-version 0.11.2 --sieges-version 0.6.5 --shutdown-check` to assert the new artifacts and accepted-write drain under an externally held Suite lock. Historical defaults remain 0.8.43/0.5.0.
