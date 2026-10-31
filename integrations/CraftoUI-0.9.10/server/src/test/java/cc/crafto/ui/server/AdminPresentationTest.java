package cc.crafto.ui.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminPresentationTest {
    @Test void onlyTheControlAdminScreensMoveNativeBottomActions(){
        assertTrue(NativeMenuPresentation.adminMenu("NeverLandTownyControl","ru.neverland.townycontrol.AdminMenu$Screen"));
        assertFalse(NativeMenuPresentation.adminMenu("NeverLandTownyControl","ru.neverland.townycontrol.PersonalHistoryMenu$View"));
        assertFalse(NativeMenuPresentation.adminMenu("NeverLandTownyContracts","ru.neverland.townycontrol.AdminMenu$Screen"));
    }
}
