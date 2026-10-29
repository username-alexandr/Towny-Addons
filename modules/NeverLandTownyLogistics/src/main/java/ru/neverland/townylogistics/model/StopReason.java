package ru.neverland.townylogistics.model;
/** Actual navigation outcomes, with safe next steps rather than a generic 'does not work'. */
public enum StopReason {
    NONE("",false),WAIT_REGION("Ожидает обработки своего участка сервера",false),WAIT_PLAYERS("Рядом нет игроков или участок выгружен; курьер продолжит при возвращении игроков",false),NPC_UNAVAILABLE("Нет свободного места для курьера; освободите его точку остановки",false),OUTSIDE("Потеряно право прохода по территории; верните участок городу или проложите обход",true),SLEEPING("Участок не обрабатывает существ; дождитесь игроков поблизости",false),UNLOADED("Следующий участок не загружен; ищем другую дорогу без принудительной загрузки",true),REGION_BOUNDARY("Для проверки дороги нужно дождаться доступности соседних участков сервера",false),BLOCKED("Проход перекрыт; ищем обход по дорожной сети",true),UNSAFE("Нет безопасного прохода; откройте дверь или соедините запасную дорогу",true),BUDGET("Ожидает своей очереди проверки пути",false);
    public final String text;public final boolean reroute;StopReason(String text,boolean reroute){this.text=text;this.reroute=reroute;}
}
