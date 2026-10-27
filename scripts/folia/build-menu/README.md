# Building-menu regression (0.45.0)

Disposable loopback Folia fixture only. Never install BuildMenuProbe on a live server. Requires the `ALLOW_DISPOSABLE_BUILD_MENU_PROBE` marker, current Builds, Contracts, Towny and CraftoUI server 0.8.14. Compile BuildMenuProbe.java using Java 25 against the bootstrapped server libraries and installed plugins, package only its classes and plugin.yml as BuildMenuProbe.jar.

The probe uses actual CraftItemStacks, Towny residents/towns, BuildService, MenuManager, live Bukkit inventory events, Folia region/entity schedulers and persistent resource funds. Player inventory and transport are explicit proxies. It reproduces the original 272-item codec rejection, checks normalized fingerprint metadata and quantity, checks the real preview of a 272-item requirement, same-container contributions from both sources, stale quotes, duplicate clicks, resource conservation, preview slot and live CraftoUI presentation updates. Both startup phases must return fresh run IDs and zero failures.

The ledger runner waits for this optional probe before shutting down and records its results and CraftoUI server SHA-256. No client connection, game window, real economy deposit or production workload is simulated. Hover visibility is tested separately by the production AddonScreen renderer harness in the CraftoUI source package.
