package cc.crafto.ui.server;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderAcceptancePreviewTest {
    @Test void offerPreviewDescribesTheExactOrderBeforeAnyConfirmation(){
        var offer=JsonParser.parseString("""
            {"title":"Поставка древесины","type":"DELIVERY","rarity":"RARE","goal":128,
             "target":"OAK_LOG","cents":150000,"instructions":["Доставьте ресурсы на склад."]}
            """).getAsJsonObject();
        var preview=CivicWorkbench.acceptPreview(offer);
        assertTrue(preview.getFirst().contains("Поставка древесины"));
        assertTrue(preview.stream().anyMatch(line->line.contains("128 × {craftoui:target}")));
        assertTrue(preview.stream().anyMatch(line->line.contains("Доставьте ресурсы на склад.")));
        assertTrue(preview.stream().anyMatch(line->line.contains("Отмена")&&line.contains("ничего не изменит")));
    }
}
