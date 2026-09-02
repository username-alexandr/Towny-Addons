package ru.neverland.mintcamps;

import ru.neverland.mintcamps.data.CampRepository;
import ru.neverland.mintcamps.util.TimeUtil;

import java.lang.reflect.Method;

public final class CampIntegrationSmoke {
    public static void main(String[] args) throws Exception {
        Method api = MintTownyCamps.class.getMethod("repository");
        check(api.getReturnType() == CampRepository.class, "Публичное API лагерей возвращает неверный тип");
        check("4 д. 15 ч. 51 мин.".equals(TimeUtil.formatSeconds(3L * 86_400L + 39L * 3_600L + 51L * 60L)),
                "Дни и часы форматируются без нормализации");
        check("3 д. 0 ч. 0 мин.".equals(TimeUtil.formatSeconds(72L * 3_600L)),
                "Граница в 72 часа форматируется неверно");
        System.out.println("CampIntegrationSmoke OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
