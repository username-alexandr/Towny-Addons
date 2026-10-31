package cc.crafto.ui.server;
import com.google.gson.JsonObject;
import java.util.function.Predicate;
import static cc.crafto.ui.Protocol.*;

final class ActionAvailability {
    static String reason(JsonObject node,boolean town,Predicate<String> permission,Predicate<String> plugin){
        if(node==null)return "Это действие сейчас недоступно. Откройте меню заново.";
        String custom=str(node,"unavailableReason","");
        if(!bool(node,"enabled",true))return custom.isBlank()?"Это действие временно отключено администрацией.":custom;
        if(NavigationPolicy.requiresTown(node)&&!town)return "Вступите в город, чтобы пользоваться этим разделом.";
        if(bool(node,"requiresNoTown",false)&&town)return "Вы уже состоите в городе.";
        String owner=str(node,"requiredPlugin","");
        if(!owner.isBlank()&&!plugin.test(owner))return "Этот раздел временно выключен. Попробуйте позже.";
        String access=str(node,"permission","");
        if(!access.isBlank()&&!permission.test(access))return custom.isBlank()?"Вашей роли не разрешено это действие. Обратитесь к мэру или администратору.":custom;
        return "";
    }
}
