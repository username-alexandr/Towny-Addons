# Market0.2.1 budget migration

Exact Market JAR SHA256: `f879090982e66570119126c98c5d12b6cd8522cd74fb22f984361fb50ca9b8c7`.

Cold: 7 programs PASS, including MarketFundingSmoke25 and MarketJournalSmoke22, prior purchase/price/legacy tests, funded personal-book conservation, persistence and audit tests. Native Folia26.2 build7: Treasury fixture6 24+8 and personal-book regression22+11; four JVM exit0 with the same exact Market JAR. All nine terminal market histories have ordered timestamps and a terminal outcome, including records still in the durable audit outbox at shutdown.

Actual MarketService quotes/listings, Towny accounts, Treasury requestDebit, Builds market stock, native CraftPlayer inventories and player data saves are used. The bank adapter, online state and entity callbacks of networkless actors are explicit fixtures. Actors are attached to the current test region because they did not undergo a normal vanilla join/entity lifecycle. The separate personal-book probe also uses explicit inventory-view proxies. This is not a network client, real Vault-provider or full40-addon validation.

Pilot1 exposed a product error: after a bank result the new engine attempted to advance from the earlier pre-bank revision. Strict journal validation retained CREDIT_PENDING and prevented a second payout. Both result transitions now advance from the acknowledged pending row. Final runs exercise unknown debit/credit, manual decisions, replay and restart.

Pilot2 used a Player interface proxy where the real native save API requires CraftEntity; subsequent probes use an actual CraftPlayer for inventory claim. Pilot3/4/5 exposed incomplete region attachment/lifetime of artificial players. The final fixture explicitly maintains those test actors in the current region and uses region-backed authorization callbacks. One intermediate fixture did not compile because CraftEntity.getScheduler is final; no JVM ran for that attempt. Earlier failed disposable servers remain locally available. No final timeout was relaxed.

The final probe also drops an actor callback deliberately: the real product deadline releases the market operation, returns the unpaid stock and never requests a debit. On the second boot it closes Market while a real seller account call is in progress, then verifies COMPLETE in the physical journal before shutdown.

Schema1 imports without rewriting the old file or inventing Treasury authorization for an unknown direct debit. Schema2 adds frozen payment protocol and chronological audit outbox. Existing personal-orders files retain their separate format. Older warehouse I/O remains a common migration task, and the complete addon pack has not been released.
