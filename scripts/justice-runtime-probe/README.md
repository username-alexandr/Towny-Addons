# Disposable Justice integration probe

Run only on a stopped, backed-up disposable loopback server with `ALLOW_DISPOSABLE_RELIABILITY_PROBE` and no players. Never distribute or install the probe on production. The fixture creates a town, residents, native bank entries and a municipal jail at Towny cell 132,132.

Install the exact 33-addon matrix from `versions.yml`, Towny, Vault, an economy provider and PlaceholderAPI. In the fixture's Council config, add `neverlandtownycouncil.action.justice` to defense permissions. Compile with `python build.py --server-dir PATH --java-home JDK25` (optional `--extra-jar` for compile annotations). Run `python run.py --server-dir PATH --java-home JDK25 --phase first`, then the same command with `--phase restart`. Each phase shuts the server down and writes assertions and artifact hashes. Restore a complete stopped disposable snapshot before repeating `first`.

Tests use actual Bukkit services, Towny accounts, Treasury budgets, Council permissions, jail objects and a real process restart. Player command/menu inputs use explicit proxies. No client logs in: physical arrival gates have an exhaustive pure test; native offline custody must not award bounty; confirmed capture is an explicitly seeded journal fixture for real-bank payout testing. This does not establish visual RP quality or an end-to-end live-player teleport.
