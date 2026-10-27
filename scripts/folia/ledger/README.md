# Shared-ledger native regression

Use a disposable, empty Folia 26.2 build 7 / Java 25 server only. The probes create Towny and Chronicles fixture records, edit blocks and spawn entities (the companion FoliaProbe), hold the suite ledger on a worker for about six seconds and stop the server. Never install either probe on a live server.

Prepare dependencies and telemetry opt-outs as described in `../README.md`. Bind to `127.0.0.1`, create the file `ALLOW_DISPOSABLE_LEDGER_PROBE` in the server root, disable Towny economy for this test, and set two region threads. Install all 40 freshly verified suite JARs. Compile `../FoliaProbe.java` and `LedgerProbe.java` with the bootstrapped server libraries and installed plugins on the classpath. Package their classes with `../plugin.yml` and `plugin.yml`, respectively, as separate test plugins.

Run `python3 scripts/folia/ledger/run.py /absolute/disposable/server`. The runner launches and fully shuts down the fixture twice. It retains Towny, Chronicles and command receipt data between launches. Each launch uses a new run ID for result paths and synthetic receipt keys, so stale results cannot pass a new run. It fails on nonzero probe results, missing results, nonzero exit, timeout or a watchdog dump. Results, check details and console logs are retained under the fixture's `validation/` directory.

The ledger probe verifies a live global heartbeat during lock contention, same-thread resumption, exactly-once execution, cancellation, live `/nltlock`, queued Chronicles scans, unavailable-economy handling, background durable save and cached-balance survival after restart. The companion FoliaProbe checks all modules, native region/entity ownership, teleport/retirement, command deduplication and module pause/resume. It does not test a client connection, premium integrations, production balances or player load.

`MixedProtocolProbe.java` is a separate fixture: install only Towny, Vault, current Control and the actual previous Chronicles 0.1.18 JAR. Compile/package it with `mixed-plugin.yml`. It verifies that Control refuses to enable and does not create its module-state file when an old core is present. Keep this fixture and its data separate from the full-suite test.

## Active-raid shutdown regression (0.43.1)

Additionally create `ALLOW_DISPOSABLE_SHUTDOWN_PROBE`, compile `ShutdownProbe.java`
against server libraries and all installed suite JARs, and package its classes with
`shutdown-plugin.yml` renamed to `plugin.yml` as `plugins/ShutdownProbe.jar`.
This is a destructive fixture-only test: it creates a town, an active raid,
a persistent zombie and an unrelated armor stand. Never deploy this probe to players.

When this JAR is present, `run.py` enables `neverland.probe.activeRaid=true`.
The companion FoliaProbe then skips its two final administrative disable/enable
assertions: that dependency cascade disables Events before the server stops and
would prevent exercising the actual failing callback. The other 109 checks remain.
ShutdownProbe requires Events to remain enabled until its real shutdown callback.
Its initial fixture defeats one of three enemies, leaves one alive and one pending,
and saves them through the real EventRepository. Subsequent boots check the saved
wave, progress, new generation, removal of the previous mob body and survival of
an unrelated entity. It then leaves another live body for the next shutdown.

To prove the regression, first install the actual 0.43.0 release JARs and run:

```
python3 scripts/folia/ledger/run.py /absolute/disposable/server --expect-shutdown-error
```

This negative run requires the exact Events 0.4.11 lifecycle error and Folia region
scheduler stack, and writes `validation/baseline/expected-failure.json` with JAR
hashes. Replace only the 40 suite JARs with 0.43.1, retaining the fixture data,
then run without the flag for two successful launches/stops. Each positive run
also requires a fresh ShutdownProbe result and rejects plugin lifecycle errors.
The runner captures console output in memory and writes it after process exit so
workspace synchronization cannot truncate a still-open log.

This regression does not change the gameplay event deadline, simulate player
load, test a real economy provider, or repeat the separate mixed-protocol test.

## Server materials regression (0.44.0)

Compile `ServerResourceProbe.java` against the server libraries and current suite
JARs; package it with `resource-plugin.yml` renamed to `plugin.yml` in
`plugins/ServerResourceProbe.jar`. Create `ALLOW_DISPOSABLE_SERVER_RESOURCE_PROBE`
on the marked loopback fixture. It inserts **synthetic** material delivery records,
then calls the actual registered ServerMaterialsApi to reserve, consume and return
resources. No real player is connected and no bank deposit is performed by this
probe. It retains a private balance checkpoint between boots to verify persistence.
Set the disposable fixture's pre-upgrade order limits to 5000/500000 to exercise
catalogue migration without replacing custom caps. Keep the idle raid fixture's
deadline in the future when rerunning unrelated regressions over a long session.

The runner requires a unique successful resource result in both launches, copies
check details and adds `resources` to its JSON report. These probes are never
included in the player release package. Player inventory transitions, receipt
recovery and database faults are covered separately by ResourceDeliveriesSmoke
with real SQLite and reward files plus a strict inventory test double.
