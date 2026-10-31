# Сторонние компоненты

Gradle Wrapper в `gradle/wrapper/` взят из официального Gradle 9.5.1. Лицензия Gradle приведена рядом в `GRADLE-LICENSE.txt`.

Minecraft, Folia, Fabric Loader/API, JDK и Maven-зависимости не включены в этот архив. Их получает среда сборки или администратор из официальных источников. Изображения и модели Minecraft на снимках показывают реально запущенный клиент.


## Аддоны в проверке 0.8.20

Реальные неизменённые JAR из NeverLandTowny 0.51.0 использовались как входы проверки. Их версии и SHA-256 записаны в `validation/0.8.20/addon-api/binary-contracts.json`. Исходники аддонов: https://github.com/username-alexandr/Towny-Addons/commit/4022915b64cc5578f875a7a977f989e07378e4f3 . Актуальные JAR аддонов устанавливаются из их комплекта; полная поставка CraftoUI содержит собственные клиент, сервер и API.
