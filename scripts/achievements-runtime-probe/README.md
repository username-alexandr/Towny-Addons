# Disposable achievements integration fixture

Opt-in only: prepare a fresh loopback Purpur 26.2 server with `ALLOW_DISPOSABLE_RELIABILITY_PROBE`, `server-ip=127.0.0.1` and a full addon kit. Do not use a production city, player database or existing proof markers. Uses Java 25. Omit economy plugins to verify unavailable-economy handling; thresholds for balance are also exercised in AchievementsSmoke.

Run `python scripts/achievements-runtime-probe/build.py --server-dir <fixture> --java-home <jdk>` after the server's first dependency bootstrap. Then run the neighbouring `run.py` with the same arguments. It starts two JVMs and validates distinct first/restart proof markers.

Real Towny towns, real Bukkit inventories, actual Achievements, Population and Control services; controlled public Builds, Population and Events observations. Tests city thresholds, permission checks, mayor-only titles, resident cosmetics, native patterned banners, audit category, neutral pause/recheck, provider outage and persistent rewards after restart. Raid ledger persistence and legacy migration use the actual repository in RaidVictoriesSmoke. This fixture does not represent a client render test, a production load test or a complete raid/construction simulation.
