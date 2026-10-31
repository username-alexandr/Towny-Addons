# Зафиксированные источники и версии

Проверено при разработке 21 октября 2026 года. Фактические версии сборки дополнительно записаны в `gradle.properties` и журналах `validation/`.

| Компонент | Версия/источник |
|---|---|
| Minecraft | 26.2; https://piston-meta.mojang.com/mc/game/version_manifest_v2.json |
| Java | 25; поле `javaVersion.majorVersion` манифеста Minecraft 26.2 |
| Fabric Loader | 0.19.5; https://meta.fabricmc.net/v2/versions/loader/26.2/0.19.5/profile/json |
| Fabric API | 0.161.0+26.2; https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml |
| Loom | 1.17.21; https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml |
| Gradle | 9.5.1; https://services.gradle.org/distributions/gradle-9.5.1-bin.zip.sha256 |
| Paper API для компиляции | 26.2.build.121-stable; https://repo.papermc.io/repository/maven-public/ |
| Folia для запуска | 26.2 build 7; https://fill.papermc.io/v3/projects/folia/versions/26.2/builds |
| LuckPerms API | 5.4; https://luckperms.net/wiki/Developer-API |

Официальная документация:

- https://fabricmc.net/2026/06/15/262.html
- https://fabricmc.net/2026/03/14/261.html
- https://docs.fabricmc.net/develop/networking
- https://docs.fabricmc.net/develop/rendering/gui/custom-screens
- https://docs.fabricmc.net/develop/rendering/gui-graphics
- https://docs.fabricmc.net/develop/key-mappings
- https://docs.papermc.io/paper/dev/plugin-messaging/
- https://docs.papermc.io/paper/dev/folia-support/
- https://jd.papermc.io/folia/26.2/

Сигнатуры клиентских методов сверены с официальным `client.jar` 26.2, а не с API предыдущих выпусков. SHA-1 этого Minecraft JAR по официальному манифесту: `2dc72797acbc1b63fc16a11c4ac393605f453754`.

SHA-256 тестового Folia JAR по официальному API загрузок: `128a634192261cd38bb4a5dc54075018a0f896fd6c6f529e37dca6e99e32b3b3`. Minecraft и Folia не включены в поставляемые архивы.

## Состав NeverLandTowny для 0.2.0

За основу взяты предоставленные файлы `NeverLandTownySuite-0.41.0-sources.zip` и `NeverLandTownySuite-0.41.0-full.zip`. Для Builds, Contracts, Control, Jobs, Taxes, Companies и Events использованы найденные более новые отдельные JAR. Версии читаются из настоящего `plugin.yml`, классы — из Java class files с учётом наследования InventoryHolder. Команды сверены с командными обработчиками исходного набора. Полный состав и хеши: `ADDON-INVENTORY.json`.

Дополнительная тестовая зависимость: Towny 0.103.2.0 из официального релиза https://github.com/TownyAdvanced/Towny/releases/tag/0.103.2.0. SHA-256 Towny JAR: `c8d7d59984eaf2606fce4192e860f7f969f3384e5a51b26b97225efbbe747c70`. Towny и пользовательские аддоны не включены в поставляемые CraftoUI-артефакты.

Контракты InventoryView/InventoryOpenEvent проверены по Paper 26.2 API: https://jd.papermc.io/paper/26.2/org/bukkit/inventory/InventoryView.html. Сигнатуры ContainerScreen/ChestMenu/ContainerInput сверены с оригинальным Minecraft 26.2 JAR. На сервере не используется NMS.

Для клиентского исправления 0.2.1 использован официальный профиль Fabric Loader 0.19.4: https://meta.fabricmc.net/v2/versions/loader/26.2/0.19.4/profile/json. Все библиотеки этого профиля загружены с https://maven.fabricmc.net/ и сверены по опубликованным SHA-1. Их перечень включён в `validation/compatibility-0.2.1/loader-checksums.json`. В метаданных Fabric API 0.161.0+26.2 указан минимум Loader 0.18.4; он не требует 0.19.5.


## Дополнение HUD 0.3.0

- Fabric HUD API: https://docs.fabricmc.net/develop/rendering/hud — HudElementRegistry, слой перед чатом и условие видимости HUD.
- Paper/Folia scheduler guidance: https://docs.papermc.io/paper/dev/folia-support/ — EntityScheduler и асинхронные задачи.
- Форматы личных заказов и платёжных записей проверены по предоставленному настоящему NeverLandTownyContracts 0.3.20; этот сторонний JAR и его декомпиляция в исходный комплект CraftoUI не включены.


## 0.6.0: команды

- Исходники 40 модулей NeverLandTownySuite: plugin.yml, CommandExecutor, TabCompleter; фактические версии в COMMAND-INVENTORY-0.6.0.json.
- Paper API 26.2: https://jd.papermc.io/paper/26.2/org/bukkit/command/PluginCommand.html (owner, permissions, execution, completion).
- Планировщики Folia: https://docs.papermc.io/paper/dev/folia-support/ .
- Исходник PluginCommand и отсутствие автоматической проверки прав в tabComplete: https://github.com/PaperMC/Paper/blob/main/paper-api/src/main/java/org/bukkit/command/PluginCommand.java . CraftoUI проверяет права перед подсказками и исполнением.
