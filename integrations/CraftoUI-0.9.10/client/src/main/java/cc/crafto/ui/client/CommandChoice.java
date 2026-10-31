package cc.crafto.ui.client;

import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;

/** Human captions for known addon subcommands; the original token stays in the input. */
final class CommandChoice {
    private record Caption(String title,String detail){}
    private static final Map<String,Caption> ACTIONS=Map.ofEntries(
        Map.entry("admin",new Caption("Управление","Настройки для администрации")),
        Map.entry("banner",new Caption("Знамя","Изменить знамя города")),
        Map.entry("cosmetic",new Caption("Оформление","Настроить внешний вид")),
        Map.entry("info",new Caption("Сведения","Посмотреть информацию")),
        Map.entry("menu",new Caption("Открыть меню","Показать действия раздела")),
        Map.entry("reload",new Caption("Обновить настройки","Перечитать конфигурацию")),
        Map.entry("rewards",new Caption("Награды","Просмотреть доступные награды")),
        Map.entry("title",new Caption("Название","Изменить название или титул")),
        Map.entry("track",new Caption("Отслеживать","Показывать текущий прогресс")),
        Map.entry("list",new Caption("Список","Показать доступные записи")),
        Map.entry("help",new Caption("Справка","Показать помощь по разделу")),
        Map.entry("create",new Caption("Создать","Добавить новую запись")),
        Map.entry("invite",new Caption("Пригласить","Отправить приглашение")),
        Map.entry("accept",new Caption("Принять","Подтвердить приглашение")),
        Map.entry("deny",new Caption("Отклонить","Отказаться от приглашения")),
        Map.entry("leave",new Caption("Покинуть","Выйти из раздела или группы")),
        Map.entry("history",new Caption("История","Посмотреть прошлые действия")),
        Map.entry("status",new Caption("Состояние","Проверить текущее состояние")),
        Map.entry("progress",new Caption("Прогресс","Проверить выполнение")),
        Map.entry("claim",new Caption("Получить награду","Забрать доступную награду")));
    private CommandChoice(){}
    static String label(String token,String type){
        if(type.equals("arguments")){
            var caption=ACTIONS.get(token.toLowerCase(Locale.ROOT));
            if(caption!=null)return caption.title;
        }
        return Component.translatableWithFallback("craftoui.option."+token,token).getString();
    }
    static String detail(String token,String type){
        var caption=type.equals("arguments")?ACTIONS.get(token.toLowerCase(Locale.ROOT)):null;
        return caption==null?"Подставить в поле":caption.detail;
    }
}
