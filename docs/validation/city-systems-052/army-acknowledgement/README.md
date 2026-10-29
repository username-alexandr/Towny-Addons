# Army / Sieges acknowledgement evidence

Final artifacts: Army 0.2.2 and SiegesPlus 0.6.4; exact hashes are in `hashes.json`, complete input manifests in `scripts/army-async-runtime-probe/validated-versions.json`.

- Cold build: 7 Army and 14 Sieges programs; ArmyAcknowledgementSmoke adds 14 checks with a real delayed file writer, overlap, stale image, shutdown and receipt invariants.
- Native final fixture `/dev/shm/neverland-army-async-4`: **36 + 15 assertions**, two sequential Folia 26.2 JVMs, both exit 0, unchanged JAR hashes. Full logs and per-assertion proof are retained here.
- The fixture ran real Towny 0.103.2.0, Builds 0.11.2, Resources 0.2.2, Citizens 0.2.0, Army 0.2.2 and SiegesPlus 0.6.4. Actual inventories and service/file boundaries; offline Player/View/EntityScheduler, accepted buildings/war metadata and virtual elapsed duty are explicit substitutes. No network player, live PvP, bank or full 40-addon claim.
- Native checks cover stock-before-ACK, read health during write, double-click prevention, quit and idle accounting, exact camp receipt, replay, no retrospective missed-minute charge, ended/disabled camp, malformed provider reply, consumed supply recovery, and accepted write plus final duty drain under external Suite at shutdown.

Earlier pilots are not substituted for this proof. Fixture 1 failed because reflective access to all Army fields tried to resolve an absent optional PlaceholderAPI type. Fixture 2 passed its first 30 assertions but the runner rejected a compile-only PlaceholderAPI JAR missing its bStats runtime dependency. The final fixture accesses the service/scheduler through public contracts and does not install or claim to validate PlaceholderAPI. Fixture 3 passed 32+15 on the same final production JARs; fixture 4 adds missed-minute and unknown-reply checks. No production defect was hidden by relaxing the runner's error checks.

Expected environment-only Mojang network/OSHI warnings are present. Shared I/O migration outside Army's own file remains open: old Resources/Builds migration operations and the camp file still use their previous paths. Quests isolation code was not changed by this stage; the prior isolated-addon proof does not become a new full-pack proof.
