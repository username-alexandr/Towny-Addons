# Сборка и проверка

## Требования

- JDK 25;
- Gradle 9 для модулей с `build.gradle.kts`;
- Maven 3.9+ для Maven-only модулей.

Исходный код компилируется с целевым bytecode Java 17, а сервер запускается на Java 25.

## Локальная проверка релиза

```bash
python3 -m pip install PyYAML
python3 scripts/verify_release.py
```

Скрипт сверяет `versions.yml` с исходными `plugin.yml` и готовыми JAR-файлами, проверяет ZIP/JAR и разбирает YAML-ресурсы.

## Сборка модулей

```bash
for module in modules/*; do
  if [[ -f "$module/build.gradle.kts" ]]; then
    gradle --no-daemon -p "$module" clean jar testClasses
  else
    mvn -B -f "$module/pom.xml" clean package
  fi
done
```

Smoke-классы в `src/test/java` являются автономными `main`-проверками, а не JUnit-тестами. Команда выше компилирует их и не пытается запускать через отсутствующий JUnit-движок.

GitHub Actions выполняет те же операции автоматически при изменениях в `main` и при создании релизной ветки.

Районы и бонусы в 0.8.0: [обновление трёх модулей](UPDATE_0.8.0_RU.md).

Логистика 0.9.0: [установка, журнал грузов и аварийное восстановление](UPDATE_0.9.0_RU.md).

Ресурсы 0.10.0: [установка Resources 0.1.0 и обновление Population 0.1.2](UPDATE_0.10.0_RU.md).

Обслуживание 0.11.0: [матрица обновления, настройки и восстановление платежей](UPDATE_0.11.0_RU.md). Общие классы `shared/upkeep` встраиваются в Builds, Resources, Districts, Logistics и Events.
