# NeverLandTownyArchaeology 0.1.5

Основные команды: `/t archaeology`, `/t archaeology journal`, `/t archaeology donate [all]`, `/t archaeology nearest`, `/t museum`. Команда `/archaeology` сохранена как прямая запасная форма. Артефакты выдаются администраторами через `/artifact give <игрок> <артефакт> [количество]`; прежняя `/townyarchaeology give` также работает.

Археологические раскопки, защищённые артефакты и городские музеи для Paper/Purpur 26.2 и Towny 0.103.2.0.

Новые чанки могут получить тематический участок с подозрительным песком или гравием. Находки извлекаются обычной кистью. Каждый артефакт или выданная администратором партия имеет уникальный серийный номер и HMAC-подпись, поэтому музей отклоняет подделки и не позволяет сдать больше экземпляров, чем содержит подписанная партия.

Команды: `/archaeology`, `/archaeology journal`, `/archaeology donate [all]`, `/archaeology nearest`, `/t museum`, `/artifact give`, `/townyarchaeology`.

ItemsAdder и PlaceholderAPI необязательны. Для расходования музейных артефактов при строительстве чудес установите NeverLandTownyBuilds 0.1.1.

Плейсхолдеры: `%townyarchaeology_player_found%`, `%townyarchaeology_player_unique%`, `%townyarchaeology_town_points%`, `%townyarchaeology_town_unique%`, `%townyarchaeology_town_available%`, `%townyarchaeology_town_collections%`, `%townyarchaeology_artifact_<id>%`, `%townyarchaeology_wonder_ready_<id>%`.

Файл `secret.key` создаётся при первом запуске и подписывает все артефакты. Храните его вместе с резервной копией `data.yml`: удаление или замена ключа сделает ранее найденные предметы недействительными.
