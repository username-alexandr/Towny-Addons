package cc.crafto.ui.server;

import cc.crafto.ui.CommandForm;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CommandPresentationTest {
    @Test void descriptionsDoNotExposeCommandNamesOnCards(){
        assertEquals("Государственные заказы для всех игроков",CommandPresentation.label("Заказы","orders","Государственные заказы для всех игроков"));
        assertEquals("Управление постройками",CommandPresentation.label("Постройки","townybuilds","Администрирование NeverLandTownyBuilds"));
        assertEquals("Управление городом",CommandPresentation.label("Город","town","Town command"));
    }
    @Test void exactCommandIsOnlyASeparateTemplate(){
        var form=new CommandForm("create-town","Создать город","town","new","",List.of(new CommandForm.Field("name","Название города","token",true,List.of())));
        assertEquals("/town new <Название города>",CommandPresentation.template(form));
        assertEquals("Укажите: Название города",CommandPresentation.detail(form));
        assertFalse(CommandPresentation.detail(form).contains("/town"));
    }
}
