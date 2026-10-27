# Isolated payout budget probe

Never install this probe on a production server. Use a new loopback-only Folia 26.2 fixture with `ALLOW_DISPOSABLE_PAYOUT_LIMITS_PROBE`, `server-ip=127.0.0.1`, Towny 0.103.2.0, Builds 0.8.39 and Contracts 0.4.2. Do not install a real economy provider or connect players.

Configure `orders.yml` with player-24h 5000 and server-24h 5000000, leaving catalogue v5 with its 670 stock templates. Seed `public-orders.yml` with a valid schema 2 active HUNTING/ZOMBIE order (ID `79bd28b6-000f-4b51-9577-16ddae145a12`, owner `d1440110-3586-44b1-a413-0efb3395e72d`, goal 3, progress 1, cents 10000, remaining 7200000, cycle 20753, rarity COMMON, state ACTIVE, board-day string `'2026-10-27'`). Quote the date so YAML retains its string type.

Compile `PayoutLimitsRuntimeProbe.java` against the actual API and plugin JARs using JDK 25, then package it with `plugin.yml` as `plugins/PayoutLimitsRuntimeProbe.jar`. Start with `python3 run.py /absolute/path/to/fixture --java /absolute/path/to/jdk25/bin/java`. The runner performs two boots and saves results only after each process exits.

The probe creates one HELD test receipt for 12345 cents in this disposable fixture. It makes no bank payment and stops the server itself. Initial and restart runs check the real runtime, configured budgets, accepted progress, catalogue, SQLite availability and duplicate receipt handling. It is not a player or load test.
