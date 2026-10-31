# Civic page renderer and interaction probe

Runs the actual production `CivicScreen`, `CivicCardButton`, `CivicStyle`, `CityCardButton`, `FlatButton`, protocol and layouts against explicit AWT drawing, screen, input and network doubles. Reuses adapter definitions from the sibling preview/lifecycle tools through Python AST literal loading; those scripts are not executed during loading. Minecraft's bitmap font is read from a separately supplied original client JAR.

```sh
python3 tools/civic-page-preview/run.py \
  --java-home /path/to/jdk25 \
  --minecraft /path/to/minecraft-26.2.jar \
  --gson /path/to/gson-2.14.0.jar \
  --output build/civic-page-preview
```

Produces 81 full-screen PNGs (synthetic examples, two themes, four logical sizes and a scrolled example), runs the existing 19 lifecycle assertions and 95 civic-page assertions. UI adapters are generated only below the chosen output, outside production source sets. The real client is separately compiled against Minecraft/Fabric dependencies.

This is not a running Minecraft client, real-device input test, network integration test or resource-pack certification. The order widget remains a double here; order rarity rendering is outside this probe. No Mojang JAR, bitmap font or dependency binary is distributed with this tool.
