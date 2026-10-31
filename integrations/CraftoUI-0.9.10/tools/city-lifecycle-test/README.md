# CivicScreen lifecycle regression

This test compiles the production CivicScreen and common protocol/layout classes with explicit no-GL UI and transport doubles. These adapters are isolated under the chosen temporary output and are never added to the real client JAR.

```
python3 run.py --java-home /path/to/jdk25 --gson /path/to/gson.jar --output /temporary/lifecycle
```

To reproduce the original failure, add `--screen-source /baseline/client/src/main/java/cc/crafto/ui/client/CivicScreen.java` from the 0.8.5 source. It fails at Protocol.str:69 / CivicScreen.extractRenderState:157 when a response arrives after tick and renders before another tick. The corrected source passes all scenarios. This test does not validate actual Minecraft screen/render integration, resource-pack behaviour, real input devices or networking; the production JAR is separately compiled against genuine Minecraft libraries.
