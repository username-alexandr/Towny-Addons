# MixinExtras regression

The probe uses official Fabric Loader, MixinExtras, Fabric Mixin and ASM binaries.
It compiles a real `@Redirect` against Fabric Mixin 0.17.4, reads its bytecode with
ASM and calls the upstream `FactoryRedirectWrapperMixinTransformer` directly.
MixinExtras 0.5.4 must reproduce the `ArrayList` → `AnnotationNode` exception;
0.5.5 must accept the same compiled annotation. Legacy single-value annotations,
`NEW` redirects and multiple `@At` values are also covered.

It additionally verifies the bundled MixinExtras versions, official checksums,
the built CraftoUI metadata and the Fabric version predicate. Neither Minecraft
nor the NLT modpack is launched. These are targeted upstream regression tests,
not end-to-end startup tests.

From the project root, using Java 25 and Python 3:

```sh
python3 tools/loader-regression/run.py \
  --java-home /path/to/jdk25 \
  --cache /path/to/loader-test-cache \
  --craftoui-jar client/build/libs/CraftoUI-Fabric-26.2-0.8.20.jar
```

Dependencies are downloaded from `https://maven.fabricmc.net/`. Official loader
manifests declare dependency versions and SHA-256 values; loader JARs are checked
against their published SHA-1 files. The cache contains test dependencies and is
not intended to be copied to a player's `mods/` directory.

The current probe accepts Loader 0.19.4 and checks the bundled fix. `run_bootstrap.py` additionally starts actual Knot 0.19.4 with the release client and Fabric API, then exits in a TEST-ONLY preLaunch entrypoint after checking selected versions and transforming ContainerEventHandler. It never starts the game window or connects to a server.
