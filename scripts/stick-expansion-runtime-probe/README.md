# Native Stick expansion probe

Opt-in, disposable, loopback Folia 26.2 + Towny 0.103.2.0 + Citizens 0.2.0 (offline actor base) + Stick 0.2.0. Never point at a production directory. Native boots must be sequential with all other suite probes.

```sh
python scripts/stick-expansion-runtime-probe/prepare.py --template ../folia-bootstrap --server ../folia-stick-expansion-1 --towny ../toolchain/Towny-0.103.2.0.jar
python scripts/stick-expansion-runtime-probe/build.py --server-dir ../folia-stick-expansion-1 --java-home ../toolchain/zulu25.38.107-ca-jdk25.0.5-linux_x64
python scripts/stick-expansion-runtime-probe/run.py --server ../folia-stick-expansion-1 --java-home ../toolchain/zulu25.38.107-ca-jdk25.0.5-linux_x64 --phase first
python scripts/stick-expansion-runtime-probe/run.py --server ../folia-stick-expansion-1 --java-home ../toolchain/zulu25.38.107-ca-jdk25.0.5-linux_x64 --phase restart
```

The first phase exercises actual `/town claim` interception, saved BFS plan, mayor/permission/revision/area rechecks, physical blocked fsync with a real Suite global heartbeat, the native TownClaim task, independently observed ownership and duplicate refusal. A second durable INTENT deliberately has its offline entity continuation held to model the crash boundary before native execution. Restart must never replay it; actual admin review closes it without land mutation. Audit count must remain exact.

Real plugin repository, original Towny task, API, scheduler and lifecycle; explicit offline Player and EntityScheduler proxies, no network Minecraft client. Economy is deliberately off: no bank payment/provider result is certified. World fixture adds the initial home and one independent city-area change; the first two planned cells are acquired by actual Towny code. The held second continuation is fault injection, not a reported native scheduler failure.
