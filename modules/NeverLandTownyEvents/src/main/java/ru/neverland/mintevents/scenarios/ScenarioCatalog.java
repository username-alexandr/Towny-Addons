package ru.neverland.mintevents.scenarios;

import java.nio.file.*;
import java.util.*;
import ru.neverland.core.SafeYaml;
import static ru.neverland.mintevents.scenarios.ScenarioBook.*;

public record ScenarioCatalog(boolean enabled,Map<String,Terms> definitions){
    public ScenarioCatalog{definitions=Map.copyOf(definitions);}
    public static ScenarioCatalog load(Path file)throws java.io.IOException{
        if(Files.size(file)>1_048_576)throw new java.io.IOException("Конфигурация сценариев больше 1 МиБ");var y=SafeYaml.load(file);SafeYaml.keys(y,"enabled","scenarios");boolean enabled=SafeYaml.booleanValue(y,"enabled",true);var root=ScenarioCodec.section(y,"scenarios");var definitions=new HashMap<String,Terms>();
        for(String key:root.getKeys(false)){var definition=ScenarioCodec.terms(ScenarioCodec.section(root,key),false);if(definitions.putIfAbsent(definition.mode(),definition)!=null)throw new IllegalArgumentException("Повтор сценария для "+definition.mode());}
        if(definitions.size()>2)throw new IllegalArgumentException("Поддерживаются только засуха и наводнение");return new ScenarioCatalog(enabled,definitions);
    }
}
