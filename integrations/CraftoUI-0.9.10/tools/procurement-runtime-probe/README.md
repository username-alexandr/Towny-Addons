# Native procurement UI validation

Requires the Towny-Addons checkout at `dd954e26d44599e8e71883454d86d1dd6d5b7845` (or a compatible validated successor), current validated addon JARs, the CraftoUI JAR built with `tools/build-direct-server.py`, Java 25 and the already bootstrapped loopback-only Folia template marked `ALLOW_DISPOSABLE_RAID_PROBE`.

Run `prepare.py --server <fresh-path> --template <template> --addons-root <checkout> --towny <Towny.jar> --ui-jar <CraftoUI.jar>`, then `build.py --server <fresh-path> --java-home <JDK> --addons-root <checkout>`, then `run.py --server <fresh-path> --java-home <JDK>`. Run only one native Folia JVM at a time.

The builder reuses the explicitly substituted player/bank harness from Towny-Addons, removing `final` from its fixture class in the temporary build directory only. Production JARs are unchanged. The UI probe executes real CivicWorkbench.handle JSON packets and only selects enabled actions present in rendered server responses. Plans are seeded in server session state explicitly. It emulates the existing client's token behavior: `review` does not replace the token received in `view`.

Both JVMs use identical artifacts. Results record every JAR hash, full startup/shutdown logs, checks and UI JSON. No network client, live Vault or visual rendering is claimed. The first pilot incorrectly applied a public-future thread assertion to a private already-completed repository snapshot; the fixture was corrected to distinguish those interfaces, without changing production behavior.
