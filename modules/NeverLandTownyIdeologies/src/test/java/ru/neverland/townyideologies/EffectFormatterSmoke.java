package ru.neverland.townyideologies;

import ru.neverland.townyideologies.util.EffectFormatter;

public final class EffectFormatterSmoke {
    public static void main(String[] args) {
        require("Прыгучесть I".equals(EffectFormatter.format("JUMP_BOOST:0", key -> "")));
        require("Сила II".equals(EffectFormatter.format("STRENGTH:1", key -> "")));
        require("Защита III".equals(EffectFormatter.format("RESISTANCE:2",
                key -> key.equals("resistance") ? "Защита" : "")));
        require("Прыгучесть I".equals(EffectFormatter.format("minecraft:jump_boost:0", key -> "")));
        System.out.println("Effect formatter smoke test: OK");
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Некорректное форматирование эффекта");
    }
}
