# CraftoUI 0.7.0 integration methods

Base: release/v0.42.0 (`2aec261f6c488c1856ef0c17d4d6ff448ef7dfdc`).

- Builds 0.8.29: `uiOpenDetails(Player, String)` opens the real project menu after its use/project checks. `uiReturnsPlacedMaterial()` reports the existing construction rule; CraftoUI must distinguish physical missing blocks from expendable supply.
- Contracts 0.3.23: the registered PublicOrders command executor provides `uiSnapshot(Player)` and `uiExecute(Player, action, id, quote)`. Snapshots expose the current daily board and only that player's open orders. The quote is rechecked under `SuiteState.lock()` before accept/claim/cancel. These APIs must be called on the player's entity scheduler. A snapshot is not authorization to mutate later.
- Companies 0.1.15: `/company decline <company UUID>` removes only the caller's live invitation through CompanyService and persists the ledger.

The APIs supplement the existing commands and inventories. Players without CraftoUI keep their normal command flow. No client dependency is added to these plugins.

Validation: Java 25 / Gradle 9.5.1 compilation, Builds 35 executable smoke checks, Contracts 6, Companies 1. The normal Gradle `test` task has no JUnit test discovery in these modules; their actual checks run through `smokeTest` / `scripts/run_smoke.py`.
