# Army specialties, orders and finite kit evidence

Final artifacts: Army 0.3.0 `f7a52c63fe1aff74cbde36825ef064977c744cff4d7e437d504a719fd226898c`, Control 0.6.4 `697563d4076866c43f689d700716538e74915c1d0aeead509512e05307aeff62`. Exact input manifests: `scripts/army-tactics-runtime-probe/validated-versions.json`.

- Cold build against actual Folia 26.2 dependencies: 10 Army and 15 Control executable test programs. New coverage: 20 model, 14 durable optional-repository, 13 core equipment-stock, 5 admin-audit checks. Existing Army acknowledgement, supply and migration checks remain included.
- Native `/dev/shm/neverland-army-tactics-2`: **49 + 17 + 10 assertions**, three sequential JVMs, all exit 0. Fixed JAR hashes across first use, actual restart, and optional-file corruption.
- Native acknowledgement regression `/dev/shm/neverland-army-async-tactics-1`: **36 + 15 assertions**, two more sequential JVMs, Army 0.3.0 plus unchanged SiegesPlus 0.6.4. The prior camp receipt, quit/idle duty, consumed supply recovery and shutdown checks all pass on the new Army artifact.

Tactics coverage includes role authority, current permissions, position/revision checks, four actual item kits, full inventory, delayed physical file ACK, double click, inventory changes before delivery, real disabled player-data saving, exact durable recovery marker, pause while funding, exact refund after resume, unknown armed state after restart, already-returned crash boundary, manual administrator decision and actual Control log-category menu. The corrupt-file phase leaves the old roster/core API, Builds, Resources, Citizens, Sieges and Control enabled and unfrozen.

Player inventory, PDC and `player.dat` are native Minecraft implementations. Online status, views, entity scheduler and positions are controlled proxies. Accepted army roster/base/building and interrupted transfer states are explicitly seeded fixtures. No network-client, live PvP, Vault payment or full 40-addon compatibility claim is made.

Pilot 1 passed 46 first-phase checks and stopped in its restart phase because the test expected a permission denial as a future, while the service correctly rejected synchronously. The test was corrected. Production checks were also strengthened with fresh use permission checks and cost details in the audit, then both final JARs were cold-built again and tested from a fresh fixture. No expected assertion or lifecycle-error filter was removed. Cold compile pilots caught menu text-adapter mismatch and test-only material-registry usage; these do not appear in final artifacts.

The intentionally corrupted file is not repaired or overwritten by the plugin. Mojang/OSHI environment warnings remain in full logs. This work does not claim completion of old cross-addon synchronous I/O or the unfinished Sieges/CraftoUI stages. Quests source was not changed; prior Quests-isolation evidence remains separate and still needs the final full-pack gate.
