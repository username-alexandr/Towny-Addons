from pathlib import Path
import sys

root = Path(sys.argv[1])

for rel in [
    "build.gradle.kts",
    "src/main/resources/plugin.yml",
    "src/main/java/ru/neverland/phantomdecor/NeverLandPhantomDecorPlugin.java",
    "src/main/java/ru/neverland/phantomdecor/PhantomDecorCommand.java",
]:
    p = root / rel
    s = p.read_text(encoding="utf-8")
    s = s.replace("0.2.2-test", "0.2.3-test")
    p.write_text(s, encoding="utf-8")

p = root / "src/main/resources/config.yml"
s = p.read_text(encoding="utf-8")
s = s.replace("  display-transform: NONE", "  display-transform: GUI")
s = s.replace("  scale: 0.42", "  scale: 0.35")
s = s.replace("  standing-surface-offset: 0.045", "  standing-surface-offset: 0.085")
s = s.replace("  standing-y-offset: 0.56", "  standing-y-offset: 0.72")
p.write_text(s, encoding="utf-8")

p = root / "src/main/java/ru/neverland/phantomdecor/PhantomDecorListener.java"
s = p.read_text(encoding="utf-8")
s = s.replace(
    'String transform = plugin.getConfig().getString("sign-items.display-transform", "NONE");',
    'String transform = plugin.getConfig().getString("sign-items.display-transform", "GUI");'
)
s = s.replace(
    'entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);',
    'entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);'
)
s = s.replace(
    'float scale = (float) plugin.getConfig().getDouble("sign-items.scale", 0.42);',
    'float scale = (float) plugin.getConfig().getDouble("sign-items.scale", 0.35);'
)
s = s.replace(
    'wallSign ? 0.47 : 0.045',
    'wallSign ? 0.47 : 0.085'
)
s = s.replace(
    'wallSign ? 0.50 : 0.56',
    'wallSign ? 0.50 : 0.72'
)
p.write_text(s, encoding="utf-8")
