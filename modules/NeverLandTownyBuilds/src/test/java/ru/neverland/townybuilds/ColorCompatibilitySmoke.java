package ru.neverland.townybuilds;

import ru.neverland.townybuilds.util.ColorUtil;

public final class ColorCompatibilitySmoke {
    public static void main(String[] args) { String legacy = ColorUtil.legacy("&#55FF55Проверка &lцвета"); if (!legacy.toLowerCase(java.util.Locale.ROOT).contains("§x§5§5§f§f§5§5") || !legacy.contains("§l")) throw new AssertionError(legacy); String plain = ColorUtil.plain(legacy); if (!plain.equals("Проверка цвета")) throw new AssertionError(plain); }
}
