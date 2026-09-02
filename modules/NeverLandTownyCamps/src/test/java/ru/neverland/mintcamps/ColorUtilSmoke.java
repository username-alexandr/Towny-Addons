package ru.neverland.mintcamps;

import net.kyori.adventure.text.Component;
import ru.neverland.mintcamps.util.ColorUtil;

public final class ColorUtilSmoke {
    public static void main(String[] args) {
        String source = "&#55FF55Лагерь &lготов&r: &cошибка";
        String legacy = ColorUtil.legacy(source);
        if (!legacy.contains("§x§5§5§F§F§5§5") || !legacy.contains("§l") || !legacy.contains("§c")) {
            throw new AssertionError("Цветовые коды преобразованы неверно: " + legacy);
        }
        if (!"Лагерь готов: ошибка".equals(ColorUtil.plain(source))) {
            throw new AssertionError("Форматирование не удалено из plain-текста");
        }
        Component component = ColorUtil.component(source);
        if (!"Лагерь готов: ошибка".equals(ColorUtil.plain(component))) {
            throw new AssertionError("Adventure Component собран неверно");
        }
        System.out.println("ColorUtil smoke test: OK");
    }
}
