package ru.neverland.townypolicies.model;
import java.util.Map;
public final class DependencyVersions {
    public static final Map<String,String> MINIMUM=Map.of("NeverLandTownyBuilds","0.8.5","NeverLandTownyResources","0.1.5","NeverLandTownyPopulation","0.1.7","NeverLandTownyUpkeep","0.1.3","NeverLandTownyTrade","0.1.9","NeverLandTownyTaxes","0.1.2");
    private DependencyVersions(){}
    public static boolean atLeast(String actual,String required){try{var m=java.util.regex.Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$").matcher(actual);if(!m.matches())return false;String[] parts=required.split("\\.");for(int i=0;i<3;i++){int a=Integer.parseInt(m.group(i+1)),b=Integer.parseInt(parts[i]);if(a!=b)return a>b;}return true;}catch(Exception e){return false;}}
}
