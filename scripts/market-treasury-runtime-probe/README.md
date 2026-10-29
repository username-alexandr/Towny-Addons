# Native Market / Treasury fixture

Opt-in, disposable loopback Folia 26.2 server only. Prepare refuses to overwrite a directory; runtime JARs are linked from an explicitly marked stopped template. Addon JARs are copied and their SHA-256 must remain identical across both JVM boots.

The probe exercises actual MarketService quotes, acknowledged MarketJournal, Treasury requestDebit, Towny accounts, Builds market storage, native CraftPlayer inventory and player saves. The bank adapter and online status of networkless CraftPlayer actors are explicit fixtures. Their scheduler routes to the actor's region because these actors did not go through the vanilla join/entity lifecycle. No network client or real Vault economy provider is claimed.

First boot: offers reserve actual warehouse stock; missing installed Treasury cannot bypass budget; permission revocation rejects debit; city/local purchases conserve money and goods; repeated claim cannot duplicate; lost credit/debit responses wait for manual verification; a stalled actor callback expires without blocking the market indefinitely; old unknown direct debt remains unresolved; server stops after a real Treasury debit before delivery.

Second boot: saved payment resumes delivery without a new debit, completed/history receipts do not replay, and closing the market during a running seller bank call drains the final confirmed financial snapshot before stopping.

Use `prepare.py --template <stopped-marked-server> --server <new-dir> --towny <jar>`, `build.py --server-dir <new-dir> --java-home <jdk25> --annotations <jar>`, then `run.py --server <new-dir> --java-home <jdk25>`. Run only one native Folia JVM at a time. A failed fixture must be preserved and replaced with a fresh one; no rerun over its mutated state.
