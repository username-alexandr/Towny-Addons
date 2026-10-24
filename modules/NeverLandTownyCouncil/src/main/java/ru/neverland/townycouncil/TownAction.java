package ru.neverland.townycouncil;

import java.util.*;

/** Closed list: a mayor cannot manufacture server permissions or delegate management of this list. */
public enum TownAction {
    BUILD("Строительство", "Покупать и улучшать здания", "Оплата строительства из казны", ""),
    BUILD_CANCEL("Строительство", "Отменять строительство", "Отмена с обычной комиссией города", "build"),
    BUILD_RESOURCES("Строительство", "Передавать складские ресурсы стройке", "Расход ресурсов городского склада", "build"),
    BUILD_PLACE("Строительство", "Устанавливать блоки стройки", "Участие в строительстве по чертежу", ""),
    STORAGE_DEPOSIT("Строительство", "Класть ресурсы на склады", "Городской склад и склады зданий", ""),
    STORAGE_WITHDRAW("Строительство", "Забирать ресурсы со складов", "Получение ресурсов из городских складов", ""),
    DISTRICTS("Строительство", "Управлять районами", "Создание и изменение городских районов", ""),
    MOB_FARMS("Строительство", "Менять защиту участков от мобов", "Разрешение мобов и защита фермы", ""),
    BUDGET("Экономика", "Управлять бюджетом", "Изменение правил городского бюджета", ""),
    TAX("Экономика", "Управлять налогами", "Создание и отмена городских налогов", ""),
    EXPORT("Экономика", "Выгружать финансовые отчёты", "Экспорт журнала казны", ""),
    TRADE("Экономика", "Управлять городской торговлей", "Торговые предложения и поставки", ""),
    CONTRACTS("Экономика", "Управлять городскими заказами", "Публикация и отмена заказов из казны", ""),
    MARKET("Экономика", "Управлять городским рынком", "Настройки и управление рынком", ""),
    JOBS("Экономика", "Управлять рабочими местами", "Назначения и рабочие места города", ""),
    ARMY("Безопасность", "Управлять армией", "Мобилизация и управление городскими силами", ""),
    ESPIONAGE("Безопасность", "Проводить разведывательные операции", "Городская разведка", ""),
    JUSTICE("Безопасность", "Управлять правосудием", "Штрафы, ордера и городская тюрьма", ""),
    AGREEMENTS("Политика", "Заключать соглашения", "Предложения, принятие и расторжение договоров", ""),
    SANCTIONS("Политика", "Управлять санкциями", "Введение и снятие санкций", ""),
    LAW_TAX("Политика", "Предлагать налоговые законы", "Не заменяет голосование совета", ""),
    LAW_CONSTRUCTION("Политика", "Предлагать строительные законы", "Не заменяет голосование совета", ""),
    LAW_IDEOLOGY("Политика", "Предлагать законы об идеологии", "Не заменяет голосование совета", ""),
    LAW_CIVIC("Политика", "Предлагать гражданские законы", "Не заменяет голосование совета", ""),
    POLICIES("Политика", "Выбирать городские политики", "Изменение режимов городских служб", ""),
    SPECIALIZATION("Развитие", "Выбирать специализацию", "Изменение специализации города", ""),
    QUESTS("Развитие", "Управлять городскими проектами", "Городские цепочки заданий", ""),
    ACHIEVEMENTS("Развитие", "Управлять наградами достижений", "Доступные действия с достижениями города", ""),
    UPKEEP("Развитие", "Управлять содержанием зданий", "Настройки обслуживания городских построек", "");
    public static final String PREFIX="neverlandtownycouncil.action.";
    private final String category,title,description,parent;
    TownAction(String category,String title,String description,String parent){this.category=category;this.title=title;this.description=description;this.parent=parent;}
    public String id(){return name().toLowerCase(Locale.ROOT);}
    public String permission(){return PREFIX+id();}
    public String category(){return category;}
    public String title(){return title;}
    public String description(){return description;}
    public String parent(){return parent;}
    public static TownAction parse(String text){
        try{return valueOf((text.startsWith(PREFIX)?text.substring(PREFIX.length()):text).toUpperCase(Locale.ROOT));}
        catch(RuntimeException e){throw new IllegalArgumentException("Неизвестное действие: "+text);}
    }
}
