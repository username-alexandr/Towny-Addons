from pathlib import Path
import re
import sys

root = Path(sys.argv[1])

for name in ("build.gradle.kts", "build.gradle"):
    p = root / name
    if p.exists():
        s = p.read_text(encoding="utf-8")
        s = s.replace("0.2.0-SNAPSHOT", "0.2.1-SNAPSHOT")
        s = s.replace('version = "0.2.0"', 'version = "0.2.1-SNAPSHOT"')
        p.write_text(s, encoding="utf-8")

for p in root.rglob("plugin.yml"):
    s = p.read_text(encoding="utf-8")
    s = re.sub(r"(?m)^version:\s*0\.2\.0(?:-SNAPSHOT|-test)?\s*$", "version: 0.2.1-test", s)
    p.write_text(s, encoding="utf-8")

for p in root.rglob("config.yml"):
    s = p.read_text(encoding="utf-8")
    if "suppress-named-entity-death-logs" not in s:
        s += """

console:
  # Disables vanilla/Spigot console spam: Named entity ... died:
  suppress-named-entity-death-logs: true
"""
        p.write_text(s, encoding="utf-8")
        break

main = None
for p in root.rglob("*.java"):
    s = p.read_text(encoding="utf-8")
    if "class NeverLandPiratesPlugin" in s and "extends JavaPlugin" in s:
        main = p
        break

if main is None:
    raise SystemExit("NeverLandPiratesPlugin.java not found")

s = main.read_text(encoding="utf-8")

if "applyNamedDeathLogSuppression();" not in s:
    if "saveDefaultConfig();" in s:
        s = s.replace(
            "saveDefaultConfig();",
            "saveDefaultConfig();\n        applyNamedDeathLogSuppression();",
            1
        )
    else:
        s = s.replace(
            "public void onEnable() {",
            "public void onEnable() {\n        applyNamedDeathLogSuppression();",
            1
        )

method = '''
    private void applyNamedDeathLogSuppression() {
        if (!getConfig().getBoolean("console.suppress-named-entity-death-logs", true)) {
            return;
        }

        try {
            Class<?> spigotConfig = Class.forName("org.spigotmc.SpigotConfig");
            var field = spigotConfig.getField("logNamedDeaths");
            field.setBoolean(null, false);
            getLogger().info("Named entity death logging is disabled for this server session.");
        } catch (ReflectiveOperationException ex) {
            getLogger().warning(
                    "Could not disable named entity death logging at runtime. "
                            + "Set settings.log-named-deaths: false in spigot.yml."
            );
        }
    }
'''

if "private void applyNamedDeathLogSuppression()" not in s:
    pos = s.rfind("}")
    if pos < 0:
        raise SystemExit("Cannot find class closing brace")
    s = s[:pos] + method + "\n" + s[pos:]

s = s.replace("NeverLandPirates 0.2.0", "NeverLandPirates 0.2.1")
main.write_text(s, encoding="utf-8")
