# Events 0.5.0 — optional scenario validation

PASS on actual Folia 26.2 build 7 / 14b7fee, Java 25.0.5 and Towny 0.103.2.0. Fresh disposable fixture: `folia-event-scenarios-1`, loopback 127.0.0.1:25677. All three JVMs ran serially and exited 0. Exact artifacts are recorded in `native-results.json` and `build-validation.json`.

| Phase | Assertions | Covered boundary |
| --- | ---: | --- |
| First JVM | 25 | Actual main drought, saved warning and decision, mayor permission, native plan inventory, physically delayed file ACK, simultaneous benefit/drawback, admin restart/pause/cancel, module freeze, recovery from submitted main progress, next flood with a separate identity |
| Complete restart | 12 | Exact source identity/start, paused worked time, frozen selected plan/actor, neutral cancellation history, drained audit, restored flood effects, one real main success and one failure, terminal identity linkage, replay rejection |
| Corrupt optional files | 12 | Broken scenario journal and catalog remain intact; new choices close; ordinary drought, fire, raid and neutral cancellation continue; no additional fire repair debt or failure history |

The main event repository, contribution/result methods, scenario state, YAML files, inventory items, Towny town/resident and Folia schedulers are actual implementations. Only Player/InventoryView/EntityScheduler and elapsed-clock samples are explicit offline substitutes. There is no live Minecraft client claim. Scenario plans have no bank, inventory-transfer or reward-command operation; this probe does not claim a new financial transaction test or full raid combat simulation.

Cold validation passed all 12 existing/new smoke programs, including 41 scenario assertions and 13 raid-victory/repository assertions. Four of the latter verify old active/history data migration, deterministic legacy identity, paused timer persistence and exact terminal linkage. Tests include full journal capacity, immutable terms after catalog changes, actual writer-lock delay, disk failure, corruption preservation and rejection of duplicate choices. `cold-tests.txt` records the run.

The two expected Events warnings in the third JVM are deliberate malformed YAML. No addon enable/disable/event error or Folia watchdog marker occurred. Environment diagnostics include the sandbox's unavailable Mojang Yggdrasil public-key hostname, OSHI process metrics warnings and the usual offline-loopback/root/Unsafe notices. These are not hidden or counted as application regressions; representative excerpts are retained beside the assertion lists.

New scenario loading, snapshot encoding/replacement and audit writes run outside the shared SuiteState guard with bounded queues. Existing synchronous main EventRepository saves were not migrated by this feature. No production server was accessed. Reproduction tools: `scripts/event-scenarios-runtime-probe/prepare.py`, `build.py`, `run.py`; they require a marked stopped disposable template and never overwrite an existing fixture.
