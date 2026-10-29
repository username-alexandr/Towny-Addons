# Native Logistics routing probe

Requires an explicitly marked disposable loopback-only Folia 26.2 template and validated production JARs. Never run against a player server.

```bash
python scripts/logistics-routing-runtime-probe/prepare.py --template /abs/template --server /abs/fresh-fixture --towny /abs/Towny-0.103.2.0.jar
python scripts/logistics-routing-runtime-probe/build.py --server-dir /abs/fresh-fixture --java-home /abs/jdk25
python scripts/logistics-routing-runtime-probe/run.py --server /abs/fresh-fixture --java-home /abs/jdk25
```

The fixture uses loopback port 25677 and two sequential complete JVM boots. Do not launch another native fixture concurrently. It checks the real Folia NPC Pathfinder, real Towny claims and Builds shipment inventory, delayed physical route-file acknowledgement, same-shipment rerouting, restart, idempotent unload and emergency return. Completed building sites and initial items are deliberately seeded. Player/View/EntityScheduler are explicit offline substitutes; BLOCKED is staged. No actual client, purchased building, or complete walking journey is claimed.

Validated 0.3.0: 13 first-boot + 9 restart assertions. Exact artifacts and limits are recorded under `docs/validation/city-systems-052/logistics-routing/`.
