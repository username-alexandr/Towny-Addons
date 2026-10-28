# Native city-development probe

Requires genuine Folia 26.2 build 7 (14b7fee), Towny 0.103.2.0 and Java 25, the 12 updated modules plus existing Resources. It runs only with `ALLOW_DISPOSABLE_RAID_PROBE` and loopback binding, creates fixture towns/blocks/financial receipts, and shuts the server down twice. Never install the probe on a player server.

`prepare.py` creates a **new** directory from a stopped disposable bootstrap and its pristine empty plugin/world checkpoint. It refuses to overwrite a server. `build.py` requires a classpath file with genuine API/runtime libraries; it adds actual patched server classes and current plugin JARs. `run.py` checks both process exits, fresh markers, exact artifact hashes and plugin/event/watchdog errors. Keep the emitted source/JAR hashes and both console logs with the report.

```sh
python scripts/city-development-runtime-probe/prepare.py --template /absolute/stopped-loopback-bootstrap --checkpoint /absolute/pristine --server /absolute/new-fixture
python scripts/city-development-runtime-probe/build.py --server /absolute/new-fixture --java-home /absolute/jdk25 --classpath-file /absolute/runtime.classpath
python scripts/city-development-runtime-probe/run.py --server /absolute/new-fixture --java-home /absolute/jdk25
```

The probe uses real Folia regions, block data, inventories, Towny account observers and SQLite/file repositories. Actors are offline CraftPlayer handles admitted to their region's identity list, with captured inventories/commands instead of network clients. Normal Towny command resumption is asserted; a fixture then emits the actual NewTownEvent. A deterministic external economy adapter and an uninitialized provider wrapper supply only Towny's economy-type identification; no external Vault/Essentials integration is certified by this fixture. A private pig is used only as an inert fixture entity, not as an authenticated player.

The fixtures test style choice and saved architecture, all native block-data palettes/orientations, repair HP identity, material deficits/quote invalidation/municipal escrow, ecology, irreversible quest choices, delayed vulnerability changes, camp receipt replay and Logistics depot exposure, limited wilderness corridors, actual production CompanyBank salary transfers, SQLite work processing and commendations. The second boot rechecks durable state without repeating bank transfers. Supply/Army conservation and the partial delivery-credit/source-ack failure are also covered by separate Java tests with real persistence. This is not an authenticated-client test of thumbnail rendering, live courier pathfinding, every weapon/work event, real Army muster or SiegeWar battles; those need a server playtest.

A fixture-only onLoad checkpoint clears administrative module-disable requests before feature admission, and reports the file's absolute path. No production control state is modified by the shipped plugins merely because this test resets its own fixture.
