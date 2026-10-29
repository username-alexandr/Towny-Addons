# Native expedition journeys probe

Disposable loopback-only Folia 26.2, Java25, Towny0.103.2.0, Camps0.2.0, Citizens0.2.0 (the inherited fixture shell), Expeditions0.2.0.

The probe uses actual generated blocks, native Zombies, Bukkit event dispatch, an actual `CraftPlayer`/`ServerPlayer` inventory, PDC and vanilla `saveData()`. Network login/view/online status are explicit fixture behavior. Expedition site selection is fixed and camp-cost debits and real economy-provider payments are not certified. Towny wilderness destroy is explicitly enabled in the disposable world. The first owning-chunk entity enumeration populates the common entity index, matching the production arena guard before its `WorldTasks.find` lookups.

Prepare a fresh directory; never reuse a failed first-phase directory. All boots must be strictly sequential with the shared native-test queue. Do not mutate any JAR between phases.

```
python scripts/expedition-journey-runtime-probe/prepare.py --template ../folia-bootstrap --server ../folia-expedition-journey-new --towny ../toolchain/Towny-0.103.2.0.jar
python scripts/expedition-journey-runtime-probe/build.py --server-dir ../folia-expedition-journey-new --java-home ../toolchain/zulu25.38.107-ca-jdk25.0.5-linux_x64 --annotations ../toolchain/annotations.jar
python scripts/expedition-journey-runtime-probe/run.py --server ../folia-expedition-journey-new --java-home ../toolchain/zulu25.38.107-ca-jdk25.0.5-linux_x64 --phase first
python scripts/expedition-journey-runtime-probe/run.py --server ../folia-expedition-journey-new --java-home ../toolchain/zulu25.38.107-ca-jdk25.0.5-linux_x64 --phase restart
```

For the third phase, **after the second JVM has exited**, back up the disposable server's `plugins/NeverLandTownyExpeditions/journeys.yml`, replace only that file with `schema: 999` and a newline, and create `plugins/ExpeditionJourneyRuntimeProbe/corrupt-requested.txt`. Run the same runner with `--phase corrupt`. The expected optional-journal diagnostic is explicitly allowed; other addon enable/disable/event failures are rejected.

Validated fixture6: first22, restart10, corrupt6; all exits0. Original logs, exact JAR hashes, frozen state and the final13 unique audit receipts are in `docs/validation/city-systems-052/expedition-journeys/`. Earlier attempts diagnosed fixture setup: entity lookup before owning enumeration, default Towny wilderness restrictions, and direct shaded-core ActivityTimer types crossing the probe plugin boundary. No production changes were made merely to satisfy those fixture errors.
