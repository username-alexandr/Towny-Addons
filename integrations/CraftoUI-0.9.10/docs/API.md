# Интеграционный API

`CraftoUI-API-0.5.0.jar` содержит публичный API и общие классы схемы. Подключать как `compileOnly`; не встраивать его повторно в другой плагин. В `plugin.yml` интеграции указать `depend: [CraftoUI]` или `softdepend` с корректной обработкой отсутствующего сервиса.

```gradle
dependencies {
    compileOnly files('libs/CraftoUI-API-0.5.0.jar')
    compileOnly 'io.papermc.paper:paper-api:26.2.build.121-stable'
}
```

Получение:

```java
CraftoUIApi api = getServer().getServicesManager().load(CraftoUIApi.class);
if (api == null) return; // необязательная интеграция выключена
```

## Действие и экран

```java
import cc.crafto.ui.CraftoUIApi;
import cc.crafto.ui.Protocol;
import com.google.gson.JsonArray;

api.registerAction(this, "myplugin:hello", "myplugin.hello", ctx -> {
    // Этот callback работает на EntityScheduler именно ctx.player().
    // Здесь разрешена работа с этим игроком, запрещены блокирующие I/O.
    return CraftoUIApi.ActionResult.ok("Привет, " + ctx.player().getName());
});

JsonArray components = new JsonArray();
components.add(Protocol.object(
    "id", "hello", "type", "button",
    "x", 24, "y", 80, "w", 512, "h", 40,
    "text", "Поздороваться", "action", "myplugin:hello"
));
api.registerMenu(this, Protocol.object(
    "id", "myplugin:home", "title", "Мой плагин",
    "width", 560, "height", 460, "theme", "classic",
    "permission", "myplugin.hello", "components", components
));
```

`openMenu(player, "myplugin:home")`, `closeMenu(player)` и `refresh(player)` можно вызвать с любого потока: API переводит работу на EntityScheduler. Ссылка на Player должна быть получена корректно вашей интеграцией. Не искать/читать игроков и миры асинхронно ради вызова API.

`registerMenu` копирует и валидирует JSON. Регистрации привязаны к плагину-владельцу; чужую запись перезаписать нельзя. Действия API обязательно имеют namespace. Изменение регистраций инвалидирует открытые сессии. При отключении плагина его регистрации удаляются. Можно вызвать `unregister(this)` явно.

В 0.5.0 зарегистрированные меню доступны в поиске и избранном после обновления каталога. Сервер фильтрует их по правам и повторно проверяет доступ при запуске. Произвольные API-действия в поиск автоматически не добавляются: каталог предназначен для переходов к экранам. Фиксированные команды открытия меню аддонов задаются в `quick-actions.json`.

## Источники данных

```java
api.registerDataProvider(this, "town", snapshot ->
    myAsyncRepository.loadTown(snapshot.uuid()).thenApply(town -> Map.of(
        "name", town.name(),
        "level", Integer.toString(town.level())
    ))
);
```

`myAsyncRepository` в примере — уже существующий асинхронный сервис интеграции, не часть CraftoUI. Возвращаемый тип — `CompletionStage<Map<String,String>>`. Provider вызывается на AsyncScheduler, получает только неизменяемый `PlayerSnapshot` (`uuid`, `name`, `values`) и не должен обращаться к живому Bukkit Player/World.

Для данных Towny/Vault, которые доступны только из игрового/глобального/владеющего региона, интеграция должна сначала безопасно обновлять собственный кэш через нужный scheduler, а provider возвращает снимок кэша. Нельзя просто перенести вызов Vault/Towny в async: их потоковая безопасность зависит от реализации.

В меню: `${town.name}`, `${town.level}`. Providers вызываются при открытии и явном refresh; это намеренно не постоянный опрос БД. Завершившийся ответ возвращается на EntityScheduler и применяется только к исходной активной сессии/ревизии. Ошибка, закрытие или смена меню не создаёт устаревший экран. Лимит ответа одного provider: до 64 ключей, значения до 512 символов; полный PATCH также ограничен протоколом. Сервер ожидает ответ до 3 секунд; интеграции должны отдельно задавать таймауты своего HTTP/БД-клиента.

## Границы ответственности

- UI-сессия защищает связь клика с экраном; операции экономики дополнительно проверяют баланс/предметы/условия и выполняют свою транзакцию.
- Параметры `ActionContext.arguments()` приходят из серверной конфигурации, а не из клиента.
- Если действие возвращает `null` или выбрасывает исключение, пользователю возвращается ошибка без стектрейса.
- `hasClient(uuid)` показывает согласованное подключение мода. Возможность открыть определённый экран всё равно зависит от прав.
- LuckPerms — встроенная необязательная интеграция. В 0.5.0 есть отдельные адаптеры обзора Towny, Builds 0.8.27 и Contracts 0.3.20/0.3.21; их ограничения описаны в PERSONAL-0.5.0.md. Другие источники, включая CraftoSync, можно подключить через этот API.

В `server/src/apiProbe/` находится отдельный исполняемый тестовый плагин, который проверяет реальную загрузку API другим classloader, права, поток действий и async-provider. Он специально умеет изменять тестовые права и **не входит в release**. Не устанавливать его на рабочий сервер.
