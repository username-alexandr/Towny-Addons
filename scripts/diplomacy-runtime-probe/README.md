# Одноразовая серверная проба Diplomacy

Проба создаёт четыре города, жителей и договоры, меняет тестовые назначения/гражданство, моделирует ошибку записи и завершает сервер. Используйте только отдельную копию сервера с резервной копией данных. В релизные JAR и ZIP плагин пробы не входит.

Требуются все 31 аддон 0.27.0, платформа из `versions.yml`, настроенная экономика и Java 25. Сервер должен слушать `127.0.0.1`; в его корне создайте `ALLOW_DISPOSABLE_RELIABILITY_PROBE`.

```sh
python scripts/diplomacy-runtime-probe/build.py --server-dir /path/to/disposable --java-home /path/to/jdk25
python scripts/diplomacy-runtime-probe/run.py --server-dir /path/to/disposable --java-home /path/to/jdk25 --phase first
python scripts/diplomacy-runtime-probe/run.py --server-dir /path/to/disposable --java-home /path/to/jdk25 --phase restart
```

Если в библиотеках сервера нет JetBrains annotations, при сборке передайте `--extra-jar /path/to/annotations.jar`. Runner сам устанавливает JVM-флаг `-Dneverland.runtimeProbe=true`. Запускайте фазы последовательно. После каждой остановки runner проверяет доказательство успеха и сохранённый YAML. Результаты находятся в `plugins/NeverLandDiplomacyRuntimeProbe`.

При существующем результате или уже созданном городе первая фаза отказывается запускаться повторно. Перед новым прогоном остановите сервер, архивируйте доказательства и восстановите исходную копию всех данных плагинов, включая Towny. Вторую фазу выполняйте на данных успешной первой. После проверки удалите JAR пробы из `plugins`.
