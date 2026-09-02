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
    gradle --no-daemon -p "$module" clean build
  else
    mvn -B -f "$module/pom.xml" clean package
  fi
done
```

GitHub Actions выполняет те же операции автоматически при изменениях в `main` и при создании релизной ветки.

