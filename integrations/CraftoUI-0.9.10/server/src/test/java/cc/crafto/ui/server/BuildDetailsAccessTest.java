package cc.crafto.ui.server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class BuildDetailsAccessTest {
    public static class Legacy {}
    public static class Compatible {public void uiOpenDetails(Player player,String project){}}
    public static class WrongSignature {public void uiOpenDetails(String project){}}
    @Test void legacyHasNoDirectLink(){assertFalse(BuildDetailsAccess.supported(Legacy.class));}
    @Test void compatibleForkMayHaveAnyVersion(){assertTrue(BuildDetailsAccess.supported(Compatible.class));}
    @Test void wrongSignatureIsNotSupported(){assertFalse(BuildDetailsAccess.supported(WrongSignature.class));}
}
