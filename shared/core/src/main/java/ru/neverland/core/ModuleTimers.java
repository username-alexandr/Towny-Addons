package ru.neverland.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Explicit millisecond deadline schemas. Money, receipts, history and duration counters are untouched. */
public final class ModuleTimers {
    private ModuleTimers() {}
    public static Map<String,List<String>> rules(String name) {
        String module=name.replaceFirst("^NeverLandTowny", "");
        return switch(module) {
            case "Events" -> Map.of("events-data.yml", List.of("active.*.ends-at","active.*.paused-at","active.*.last-raid-wave","towns.*.last-event-at","shields.*"));
            case "Contracts" -> Map.of("contract-data.yml",List.of("towns.*.active.*.expires-at","towns.*.active.*.admin-paused-at"));
            case "Trade" -> Map.of("trade-data.yml",List.of("offers.*.expires-at","caravans.*.departed-at","caravans.*.arrives-at","caravans.*.incident-at","caravans.*.admin-paused-at"),"contracts-data.yml",List.of("contracts.*.expires","contracts.*.due","contracts.*.check"));
            case "Expeditions" -> Map.of("data.yml",List.of("active.*.expires","active.*.admin-paused-at"),"return-tickets.yml",List.of("*.expires"));
            case "Camps" -> Map.of("camps.yml",List.of("camps.*.burn-until"));
            case "Espionage" -> Map.of("data.yml",List.of("operations.*.completes-at","operations.*.admin-paused-at","reports.*.*.expires-at"));
            case "Governance" -> Map.of("data.yml",List.of("proposals.*.ends-at","proposals.*.admin-paused-at"));
            case "Elections" -> Map.of("elections.yml",List.of("towns.*.nomination-end","towns.*.voting-end","towns.*.next","towns.*.admin-paused-at"));
            case "Citizens" -> Map.of("citizens.yml",List.of("records.*.expires-at"));
            case "Diplomacy" -> Map.of("diplomacy.yml",List.of("treaties.*.offer-until","treaties.*.activated","treaties.*.expires","treaties.*.notice-until"));
            case "Army" -> Map.of("army-data.yml",List.of("cities.*.next-supply","cities.*.supplied-until"));
            case "Crime" -> Map.of("crime-data.yml",List.of("towns.*.next-cycle","towns.*.next-incident","towns.*.incident.until"));
            case "Companies" -> Map.of("companies.yml",List.of("companies.*.next-tax","companies.*.successor-until"));
            case "Taxes" -> Map.of("data.yml",List.of("policies.*.next","agreements.*.expires"));
            case "Reputation" -> Map.of("data.yml",List.of("relations[].last-changed"));
            case "Justice" -> Map.of("justice-data.yml",List.of("cases.*.due","cases.*.capture-started"));
            case "Seasons" -> Map.of("calendar.yml",List.of("epoch"));
            // These use online ticks, explicit journal recovery, or historical timestamps only.
            case "Achievements","Quests","Archaeology","Builds","Chronicles","Council","Districts","Ideologies","Jobs","Logistics","Market","Policies","Population","Power","Research","Resources","SiegesPlus","Specialization","Stick","TreasuryPlus","Upkeep" -> Map.of();
            default -> throw new IllegalArgumentException("Нет проверенной схемы таймеров: "+name);
        };
    }
    public static String shifted(String text,List<String> rules,long delta)throws Exception {
        if(delta<0) throw new IllegalArgumentException("Часы сервера идут назад");
        var y=new YamlConfiguration();y.loadFromString(text);
        for(String key:new ArrayList<>(y.getKeys(true))) for(String rule:rules) if(matches(key,rule) && live(y,key)) {
            long before=SafeYaml.integer(y,key);
            if(before<0) throw new IOException("Отрицательный таймер: "+key);
            if(before>0) y.set(key,Math.addExact(before,delta));
            break;
        }
        if(rules.contains("relations[].last-changed") && y.contains("relations")) {
            var rows=new ArrayList<Map<String,Object>>();
            for(var row:SafeYaml.maps(y,"relations")) {
                var next=new LinkedHashMap<String,Object>();row.forEach((k,v)->next.put(String.valueOf(k),v));
                Object value=next.get("last-changed");
                if(!(value instanceof Number n)||n.doubleValue()!=n.longValue()||n.longValue()<0)throw new IOException("Некорректное время изменения репутации");
                if(n.longValue()>0)next.put("last-changed",Math.addExact(n.longValue(),delta));rows.add(next);
            }
            y.set("relations",rows);
        }
        return y.saveToString();
    }
    private static boolean live(YamlConfiguration y,String key) {
        int dot=key.lastIndexOf('.');if(dot<0)return true;String root=key.substring(0,dot),field=key.substring(dot+1);
        String phase=y.getString(root+".phase",y.getString(root+".status",""));
        if(Set.of("ENDED","COMPLETED","FAILED","SUCCEEDED","CANCELLED","EXPIRED","PAID","RELEASED","COMPLETE").contains(phase))
            return root.startsWith("towns.") && field.equals("next"); // next election is independent of the last result
        if(root.startsWith("cases.") && field.equals("due") && y.getString(root+".kind","").equals("WARRANT")) return false;
        if(root.startsWith("treaties.") && Set.of("embargo","sanctions").contains(y.getString(root+".type","")))return false;
        if(root.startsWith("companies.") && y.getBoolean(root+".closed"))return false;
        return true;
    }
    static boolean matches(String key,String rule) {
        String[] a=key.split("\\."),b=rule.split("\\.");if(a.length!=b.length)return false;
        for(int i=0;i<a.length;i++)if(!b[i].equals("*")&&!b[i].equals(a[i]))return false;return true;
    }
    /** Journal permits recovery after any individual file replacement, without shifting twice. */
    public static void resume(Path directory,String name,long pausedAt,long now)throws Exception {
        if(now<pausedAt) throw new IOException("Время сервера раньше начала паузы");
        Path journal=directory.resolve("module-resume.yml");
        YamlConfiguration plan=Files.exists(journal)?SafeYaml.load(journal):null;
        if(plan==null || SafeYaml.longValue(plan,"paused-at")!=pausedAt) {
            plan=new YamlConfiguration();plan.set("schema",1);plan.set("paused-at",pausedAt);plan.set("resumed-at",now);plan.createSection("files");
            int index=0;
            for(var rule:new TreeMap<>(rules(name)).entrySet()) {
                Path file=directory.resolve(rule.getKey());if(!Files.exists(file))continue;
                String before=Files.readString(file);String after=shifted(before,rule.getValue(),now-pausedAt);
                String p="files."+(index++)+".";plan.set(p+"name",rule.getKey());plan.set(p+"before",before);plan.set(p+"after",after);
            }
            YamlConfiguration snapshot=plan;AtomicFiles.write(journal,snapshot::saveToString);
        }
        if(SafeYaml.intValue(plan,"schema")!=1)throw new IOException("Неизвестный журнал восстановления таймеров");
        var files=SafeYaml.section(plan,"files");if(files==null)throw new IOException("Повреждён журнал таймеров");
        // Validate the whole batch first; externally modified data must never be overwritten.
        for(String key:files.getKeys(false)) {
            var f=SafeYaml.section(files,key);String filename=SafeYaml.text(f,"name");
            if(!rules(name).containsKey(filename))throw new IOException("Неизвестный файл в журнале: "+filename);
            String current=Files.readString(directory.resolve(filename));
            if(!current.equals(SafeYaml.text(f,"before"))&&!current.equals(SafeYaml.text(f,"after")))throw new IOException("Файл изменён во время восстановления: "+filename);
        }
        for(String key:files.getKeys(false)) {
            var f=SafeYaml.section(files,key);Path file=directory.resolve(SafeYaml.text(f,"name"));String after=SafeYaml.text(f,"after");
            if(!Files.readString(file).equals(after))AtomicFiles.write(file,()->after);
        }
    }
}
