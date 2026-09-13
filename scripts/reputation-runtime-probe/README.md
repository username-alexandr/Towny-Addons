# Native reputation integration probe

Run only on a disposable, loopback-bound Paper/Purpur 26.2 server with Java 25, Towny 0.103.2.0 and all 31 addons matching `versions.yml`. Create `ALLOW_DISPOSABLE_RELIABILITY_PROBE` in that server directory. Back up all fixture data before installation. The runner checks exact addon names and versions and records JAR SHA-256 values. The fixture is excluded from plugin/release archives.

```
python scripts/reputation-runtime-probe/build.py --server-dir /path/to/test-server --java-home /path/to/jdk25
python scripts/reputation-runtime-probe/run.py --server-dir /path/to/test-server --java-home /path/to/jdk25 --phase first
python scripts/reputation-runtime-probe/run.py --server-dir /path/to/test-server --java-home /path/to/jdk25 --phase restart
```

If compile-only annotations are absent, pass `--extra-jar /path/to/annotations.jar` to the builder. Each phase stops its server automatically. Inspect `plugins/NeverLandReputationRuntimeProbe/*-checks.txt`, `*-passed.txt`, `reputation-*.log`, and JAR hash manifests. Remove the probe JAR after both phases. Repeat from the original disposable snapshot to rerun the first phase.

Coverage: all 31 addons and metadata of all 33 APIs; actual SupplyService cancellations, responsible-party attribution and proposal rejection; live Trade toll calculation; immutable saved caravan prices; native diplomatic expiry and raid resolution; assigned versus unclaimed municipal contract expiry; a lost reply after the reputation provider durably commits; deferred outbox recovery on real restart; conflicting receipt IDs; unavailable provider and worker-thread rejection.

The municipal scenario substitutes only the Companies escrow gateway with a successful zero-value settlement, so it verifies the new outcome path without minting or transferring money. The saved caravan fixture uses the real Trade repository but is kept in the probe's own data folder and is not dispatched. SupplyProcessor fault tests separately cover stock shortages, technical waits and delivery/payment conservation. This is a functional restart test, not a production load benchmark.
