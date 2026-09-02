package ru.neverland.reputation;

import ru.neverland.reputation.util.ColorUtil;

public final class ColorUtilSmoke {
    public static void main(String[] args) {
        String colored = ColorUtil.color("&#55FF55Тест &cцвета");
        if (!colored.toLowerCase(java.util.Locale.ROOT).contains("§x§5§5§f§f§5§5") || !colored.contains("§c")) throw new AssertionError("HEX/legacy conversion failed: " + colored);
        if (!ColorUtil.strip("&#55FF55Тест").equals("Тест")) throw new AssertionError("Color stripping failed");
    }
}
