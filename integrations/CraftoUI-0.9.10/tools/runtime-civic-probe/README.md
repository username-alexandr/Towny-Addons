# Disposable runtime probe

This is test-only source, excluded from release JARs. Never install it on a production server. It requires both loopback binding and the `ALLOW_CRAFTO_UI_DISPOSABLE_TEST` marker. `AUTO_CIVIC_INVITATION_TEST` additionally enables named fixture-account invitation checks. Fixtures use the real Towny and addon handlers and test-only local accounts.

The source uses reflection to inspect CraftoUI internals only for protocol assertions. Production adapters use public addon interfaces. The build helper requires a prepared disposable server and Java 25; dependencies are taken from that server, not replaced with stubs.
