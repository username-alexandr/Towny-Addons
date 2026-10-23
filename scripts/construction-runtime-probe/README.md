# Construction runtime probe

Use an isolated Folia 26.2 build 7 server (Java 25), Towny 0.103.2.0 and the newly built Builds JAR. Never install this probe on a live world: it intentionally changes blocks near 0,80,0.

Bootstrap the disposable server first so its API/libraries are available. Bind `server-ip=127.0.0.1`, choose an unused port, disable metrics, and create the empty marker `ALLOW_DISPOSABLE_CONSTRUCTION_PROBE` in its root. Do not copy real world/player data. Accept the Minecraft EULA only if authorized.

```sh
python scripts/construction-runtime-probe/build.py --server-dir /path/to/disposable --java-home /path/to/jdk25
python scripts/construction-runtime-probe/run.py --server-dir /path/to/disposable --java-home /path/to/jdk25 --phase 1
python scripts/construction-runtime-probe/run.py --server-dir /path/to/disposable --java-home /path/to/jdk25 --phase 2
```

Each phase stops the server and retains a result, log and installed-JAR hashes. The first phase checks recorded-cell restoration, protection of replacements and container contents, batch continuation, real temporary BlockDisplay entities and metadata serialization. The second verifies the exact pending bank state, money, resource metadata and warehouse after restart. It does not assert visual client appearance, menu clicks or an external economy provider's behavior. Fault-injection bank/storage tests run separately through `build_suite.py`.
