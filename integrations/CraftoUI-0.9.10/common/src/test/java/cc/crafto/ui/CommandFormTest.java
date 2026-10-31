package cc.crafto.ui;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static cc.crafto.ui.Protocol.*;
import static org.junit.jupiter.api.Assertions.*;
class CommandFormTest {
    CommandForm role(){return new CommandForm("role","Должность","company","role","",List.of(new CommandForm.Field("player","Игрок","player",true,List.of()),new CommandForm.Field("role","Роль","choice",true,List.of("manager","worker"))));}
    @Test void roleNeverComesFromCommandText(){assertEquals("role Alex manager",role().arguments(object("player","Alex","role","manager")));}
    @Test void extraArgumentsCannotOverrideTheRoot(){assertThrows(IllegalArgumentException.class,()->role().arguments(object("player","Alex","role","manager","command","op")));}
    @Test void noMultiTokenInjectionInPlayerField(){assertThrows(IllegalArgumentException.class,()->role().arguments(object("player","Alex owner","role","manager")));}
    @Test void unknownRoleRejected(){assertThrows(IllegalArgumentException.class,()->role().arguments(object("player","Alex","role","owner")));}
    @Test void fieldsRequired(){assertThrows(IllegalArgumentException.class,()->role().arguments(object("player","Alex")));}
    @Test void rawCommandsSupportUnicodeAndIds(){var f=new CommandForm("raw","Команда","army","","",List.of(new CommandForm.Field("args","Параметры","arguments",false,List.of())));assertEquals("warn Alex Нарушение приказа",f.arguments(object("args","warn  Alex Нарушение приказа")));assertEquals("",f.arguments(new JsonObject()));}
    @Test void rejectLineSeparatorsAndControlCodes(){for(String s:List.of("a\nb","a\rb","a\u0000b","a\tb","a\u2028b","a§cb"))assertThrows(IllegalArgumentException.class,()->CommandForm.clean(s));}
    @Test void limitLongInputs(){assertThrows(IllegalArgumentException.class,()->CommandForm.clean("a".repeat(513)));}
    @Test void configuredFormRoundTrips(){assertEquals(role(),CommandForm.parse(role().json()));}
    @Test void aTailFieldCannotConsumeAnotherField(){var j=role().json();j.getAsJsonArray("fields").get(0).getAsJsonObject().addProperty("type","text");assertThrows(IllegalArgumentException.class,()->CommandForm.parse(j));}
    @Test void requiredFieldCannotFollowOptional(){var j=role().json();j.getAsJsonArray("fields").get(0).getAsJsonObject().addProperty("required",false);assertThrows(IllegalArgumentException.class,()->CommandForm.parse(j));}
    @Test void amountCannotContainCommandTokens(){var f=new CommandForm("pay","Оплата","company","deposit","",List.of(new CommandForm.Field("sum","Сумма","number",true,List.of())));assertEquals("deposit 123.45",f.arguments(object("sum","123.45")));assertThrows(IllegalArgumentException.class,()->f.arguments(object("sum","1e999")));}
}
