# NeverLandTownyCrime 0.1.0

Отдельный аддон преступности для Paper/Purpur 26.2, Towny 0.103.2.0, Java 25.
Требуются Builds и Population. Jobs улучшает стражу; Resources включает кражи виртуального склада. Подробнее: [обновление 0.29.0](../../docs/UPDATE_0.29.0_RU.md).

- `/t crime` или `/townycrime` — меню своего города.
- `/t crime help` — справка.
- `/townycrime town <город>` — просмотр другого города администратором.
- `/townycrime reload` — проверка и применение настроек; ошибка не заменяет рабочие настройки.
- `neverlandtownycrime.use` — обычные игроки; `neverlandtownycrime.admin` — администраторы, по умолчанию op.

Публичный v1 `ru.neverland.townycrime.api.TownyCrimeApi`: `crime(UUID)`, `towns()`, `shopIncomeBasisPoints(UUID)`. Методы требуют основного потока; снимки состоят из неизменяемых объектов JDK. Отсутствующий город возвращает пустой Optional; приостановленный — снимок с `paused=true`. Новые котировки при сбое отклоняются. Доля выручки — целое число 5000–10000 в базисных пунктах (10000 = 100%).

`CrimeIncidentEvent` — уведомление Bukkit после сохранённого применения происшествия. Это уведомление не является очередью доставки; долговечные обработчики должны проверять ID на повтор. Надёжный результат доступен в `crime-data.yml` и снимке API.

PlaceholderAPI: `%townycrime_level%`, `%townycrime_happiness%`, `%townycrime_guard%`, `%townycrime_loss%`, `%townycrime_status%`. Без города, при паузе и при вызове из фонового потока численные значения возвращают `—`.

Сборка и все исполняемые проверки: `python scripts/build_suite.py Crime`. Чистое ядро тестируется отдельно от Bukkit, восстановление — с потерей ответов и сбоями записи. Нативный стенд: `scripts/crime-runtime-probe`.
