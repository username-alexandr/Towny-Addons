# Disposable Folia check

Never install this probe on a live server. Requires a loopback disposable Folia 26.2 fixture, Towny, Builds and CraftoUI 0.8.5, plus an `ALLOW_DISPOSABLE_CITY_UI_TEST` marker in its working directory. Compile using `build.py --server-dir ... --java-home ...` and run Folia with a parent process that enforces a timeout and sends `stop` on timeout. The probe writes checks/passed or failed in its own data folder and shuts down the fixture.

Towny data and CraftoUI packet handling are real; Player/InventoryView/EntityScheduler are explicit doubles. The entity scheduler double delegates to the global scheduler. No claim is made about true regional ownership or real clients. See docs/TESTS-0.8.5.md.
