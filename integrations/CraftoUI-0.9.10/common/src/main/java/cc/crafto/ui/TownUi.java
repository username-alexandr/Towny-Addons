package cc.crafto.ui;
import java.util.*;

/** Presentation routes only; membership is supplied and enforced by the server. */
public final class TownUi {
    private TownUi(){}
    private static final Set<String> PUBLIC=Set.of("inbox","invite-town","invite-company","orders","mine","offer","order");
    public static boolean requiresTown(String route){return !PUBLIC.contains(route);}
    public static List<String> tabs(boolean member){return member?List.of("city","materials","map","goals","inbox"):List.of("inbox","orders");}
}
