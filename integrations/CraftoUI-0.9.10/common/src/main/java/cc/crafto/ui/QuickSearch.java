package cc.crafto.ui;
import java.text.Normalizer;
import java.util.*;
public final class QuickSearch {
    private QuickSearch(){}
    public static String normalize(String s){return Normalizer.normalize(s,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('ё','е').strip();}
    public static boolean matches(String label,String keywords,String query){
        String text=normalize(label+" "+keywords);String q=normalize(query);
        return q.isEmpty()||Arrays.stream(q.split("\\s+")).allMatch(text::contains);
    }
}
