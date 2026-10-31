package cc.crafto.ui.server;

import cc.crafto.ui.CommandForm;
import java.util.*;

/** Display-only labels. The original PluginCommand and permission gates remain authoritative. */
final class CommandPresentation {
    private static final Map<String,String> LABELS=Map.ofEntries(
        Map.entry("town","Управление городом"),Map.entry("nation","Управление нацией"),
        Map.entry("resident","Личный статус и приглашения"),Map.entry("invite","Ответить на приглашение"),
        Map.entry("townyarchaeology","Управление археологией"),Map.entry("artifact","Выдать артефакт"),
        Map.entry("townybuilds","Управление постройками"),Map.entry("buildeditor","Редактор построек"),
        Map.entry("townycamps","Управление лагерями"),Map.entry("townychronicles","Управление летописью"),
        Map.entry("townycontracts","Управление заказами"),Map.entry("nltoutbox","Сверить выдачу наград"),
        Map.entry("nltaudit","Просмотреть журнал операций"),Map.entry("nltmodules","Управление модулями"),
        Map.entry("nltadmin","Панель администратора"),Map.entry("townyevents","Управление событиями"),
        Map.entry("townyespionage","Управление разведкой"),Map.entry("townyexpeditions","Управление экспедициями"),
        Map.entry("townygovernance","Управление законами"),Map.entry("townyideologies","Управление идеологиями"),
        Map.entry("townyreputation","Управление репутацией"),Map.entry("townystick","Управление проверками"),
        Map.entry("townytaxes","Управление налогами"),Map.entry("townytrade","Управление поставками")
    );
    private CommandPresentation(){}
    static String label(String module,String root,String description){
        if(LABELS.containsKey(root))return LABELS.get(root);
        String value=description==null?"":description.replaceAll("(?i)§[0-9a-fk-orx]","").strip();
        if(value.length()<=80&&value.matches(".*[А-Яа-яЁё].*")&&!value.contains("NeverLand")&&!value.contains("/")&&!value.toLowerCase(Locale.ROOT).startsWith("администрирование"))return value;
        return "Действия: "+module;
    }
    static String detail(CommandForm form){
        if(form.id().startsWith("raw-"))return "Выбрать действие и ввести его параметры";
        if(form.fields().isEmpty())return "Открыть раздел или выполнить действие";
        String fields=String.join(", ",form.fields().stream().map(CommandForm.Field::label).limit(3).toList());
        return "Укажите: "+fields+(form.fields().size()>3?" и другие данные":"");
    }
    static String template(CommandForm form){
        StringBuilder command=new StringBuilder("/").append(form.root());
        if(!form.prefix().isBlank())command.append(' ').append(form.prefix());
        for(var field:form.fields())command.append(' ').append(field.required()?'<':'[').append(field.label()).append(field.required()?'>':']');
        return command.toString();
    }
}
