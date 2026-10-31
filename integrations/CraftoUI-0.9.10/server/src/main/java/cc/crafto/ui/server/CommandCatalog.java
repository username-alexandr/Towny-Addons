package cc.crafto.ui.server;

import cc.crafto.ui.CommandForm;
import com.google.gson.*;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

record CommandCatalog(Map<String,Module> modules) {
    record Module(String plugin,String label,String permission,Set<String> roots,List<CommandForm> forms) {}
    static CommandCatalog parse(JsonObject json){
        var modules=new LinkedHashMap<String,Module>();
        for(var e:json.getAsJsonArray("modules")){
            var o=e.getAsJsonObject();String plugin=str(o,"plugin",""),label=str(o,"label",plugin);
            if(plugin.equals("NeverLandTownyStick")&&label.equals("Stick"))label="Проверка предмета";
            if(plugin.equals("NeverLandTownyTaxes")&&label.equals("Taxes"))label="Налоги";
            if(!plugin.matches("[A-Za-z0-9_-]{1,80}")||label.length()>80||modules.size()>=80)throw new IllegalArgumentException("Invalid command module");
            var roots=new HashSet<String>();if(o.has("roots"))for(var r:o.getAsJsonArray("roots")){String name=r.getAsString();if(!name.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid root");roots.add(name);}
            var forms=new ArrayList<CommandForm>();var ids=new HashSet<String>();
            for(var f:o.getAsJsonArray("forms")){var form=CommandForm.parse(f.getAsJsonObject());if(!ids.add(form.id())||forms.size()>=80)throw new IllegalArgumentException("Duplicate/too many forms");if(!roots.isEmpty()&&!roots.contains(form.root()))throw new IllegalArgumentException("Form root excluded");forms.add(form);}
            if(modules.put(plugin,new Module(plugin,label,str(o,"permission",""),Set.copyOf(roots),List.copyOf(forms)))!=null)throw new IllegalArgumentException("Duplicate module");
        }
        return new CommandCatalog(Collections.unmodifiableMap(modules));
    }
    static CommandCatalog empty(){return new CommandCatalog(Map.of());}
}
