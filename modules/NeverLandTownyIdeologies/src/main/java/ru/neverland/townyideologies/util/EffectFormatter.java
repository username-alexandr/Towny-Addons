package ru.neverland.townyideologies.util;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class EffectFormatter {
    private static final Map<String, String> DEFAULT_NAMES = Map.ofEntries(
            Map.entry("speed", "Скорость"),
            Map.entry("slowness", "Замедление"),
            Map.entry("haste", "Спешка"),
            Map.entry("mining_fatigue", "Утомление"),
            Map.entry("strength", "Сила"),
            Map.entry("instant_health", "Мгновенное лечение"),
            Map.entry("instant_damage", "Мгновенный урон"),
            Map.entry("jump_boost", "Прыгучесть"),
            Map.entry("nausea", "Тошнота"),
            Map.entry("regeneration", "Регенерация"),
            Map.entry("resistance", "Сопротивление"),
            Map.entry("fire_resistance", "Огнестойкость"),
            Map.entry("water_breathing", "Подводное дыхание"),
            Map.entry("invisibility", "Невидимость"),
            Map.entry("blindness", "Слепота"),
            Map.entry("night_vision", "Ночное зрение"),
            Map.entry("hunger", "Голод"),
            Map.entry("weakness", "Слабость"),
            Map.entry("poison", "Отравление"),
            Map.entry("wither", "Иссушение"),
            Map.entry("health_boost", "Повышение здоровья"),
            Map.entry("absorption", "Поглощение"),
            Map.entry("saturation", "Насыщение"),
            Map.entry("glowing", "Свечение"),
            Map.entry("levitation", "Левитация"),
            Map.entry("luck", "Удача"),
            Map.entry("unluck", "Невезение"),
            Map.entry("slow_falling", "Плавное падение"),
            Map.entry("conduit_power", "Сила источника"),
            Map.entry("dolphins_grace", "Грация дельфина"),
            Map.entry("bad_omen", "Дурное знамение"),
            Map.entry("hero_of_the_village", "Герой деревни"),
            Map.entry("darkness", "Тьма")
    );

    private EffectFormatter() {
    }

    public static String format(String specification, Function<String, String> configuredName) {
        String value = specification == null ? "" : specification.trim();
        int amplifier = 0;
        int separator = value.lastIndexOf(':');
        if (separator >= 0 && separator + 1 < value.length()) {
            try {
                amplifier = Math.max(0, Integer.parseInt(value.substring(separator + 1).trim()));
                value = value.substring(0, separator);
            } catch (NumberFormatException ignored) {
                // Некорректный суффикс остаётся частью идентификатора для понятного вывода.
            }
        }
        int namespace = value.indexOf(':');
        if (namespace >= 0 && namespace + 1 < value.length()) value = value.substring(namespace + 1);
        String key = value.toLowerCase(Locale.ROOT).replace(' ', '_');
        String configured = configuredName == null ? "" : configuredName.apply(key);
        String name = configured == null || configured.isBlank()
                ? DEFAULT_NAMES.getOrDefault(key, humanize(key)) : configured;
        return name + " " + roman(amplifier + 1);
    }

    private static String humanize(String key) {
        String value = key.replace('_', ' ').trim();
        if (value.isEmpty()) return "Неизвестный эффект";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String roman(int value) {
        if (value <= 0 || value > 3999) return Integer.toString(value);
        int[] numbers = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        int remaining = value;
        for (int index = 0; index < numbers.length; index++) {
            while (remaining >= numbers[index]) {
                result.append(numerals[index]);
                remaining -= numbers[index];
            }
        }
        return result.toString();
    }
}
