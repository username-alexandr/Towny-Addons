# Mayor access probe

Use a disposable Folia 26.2 server, Java 25, Towny 0.103.2.0 and Council 0.1.14. Bind to `127.0.0.1`, disable telemetry and create the opt-in marker `ALLOW_DISPOSABLE_MOB_FARM_PROBE`. This fixture creates towns/residents and changes persistent rights. Never install it on a live server.

Compile with `python3 scripts/access-runtime-probe/build.py --server-dir DIR --java-home JAVA_HOME --classpath-extra /path/to/annotations-26.1.0.jar`. Run `run.py` with the same server-dir/java-home and `--phase 1`, then `--phase 2`. A fresh first phase is required. Tests use native Towny and inventories; player identities are strict proxies. Output includes tested JAR hashes and per-check results.
