package cc.crafto.ui;

import com.google.gson.*;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

/** Data-only forms. Only the server chooses the command root and fixed prefix. */
public record CommandForm(String id,String label,String root,String prefix,String permission,List<Field> fields) {
    public record Field(String id,String label,String type,boolean required,List<String> choices) {}
    public static CommandForm parse(JsonObject o) {
        String id=str(o,"id",""),label=str(o,"label",""),root=str(o,"root","");
        if(!id.matches("[a-z0-9_-]{1,64}")||label.isBlank()||label.length()>80||!root.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid command form");
        String prefix=clean(str(o,"prefix",""));
        var fields=new ArrayList<Field>();var ids=new HashSet<String>();boolean optional=false,tail=false;
        for(var e:o.getAsJsonArray("fields")){
            var f=e.getAsJsonObject();String key=str(f,"id",""),type=str(f,"type","token"),name=str(f,"label",key);boolean required=bool(f,"required",true);
            if(fields.size()>=8||!key.matches("[a-z0-9_-]{1,40}")||!ids.add(key)||name.isBlank()||name.length()>80||tail||optional&&required||!Set.of("token","player","number","text","arguments","choice").contains(type))throw new IllegalArgumentException("Invalid form field");
            var choices=new ArrayList<String>();if(f.has("choices"))for(var c:f.getAsJsonArray("choices")){String value=clean(c.getAsString());if(value.isBlank()||value.contains(" ")||value.length()>128||choices.size()>=32)throw new IllegalArgumentException("Invalid choice");choices.add(value);}
            if(type.equals("choice")&&choices.isEmpty())throw new IllegalArgumentException("Missing choices");
            fields.add(new Field(key,name,type,required,List.copyOf(choices)));optional=!required;tail=type.equals("text")||type.equals("arguments");
        }
        return new CommandForm(id,label,root,prefix,str(o,"permission",""),List.copyOf(fields));
    }
    public static String clean(String value){
        if(value.length()>512||value.codePoints().anyMatch(c->Character.isISOControl(c)||c==0x2028||c==0x2029||c==0x00a7))throw new IllegalArgumentException("Текст содержит недопустимые символы или длиннее 512 знаков.");
        return value.strip().replaceAll(" +"," ");
    }
    public String arguments(JsonObject values){
        for(String key:values.keySet())if(fields.stream().noneMatch(f->f.id().equals(key)))throw new IllegalArgumentException("Неизвестное поле.");
        var out=new ArrayList<String>();if(!prefix.isEmpty())out.add(prefix);boolean empty=false;
        for(Field f:fields){String v=clean(str(values,f.id(),""));
            if(v.isEmpty()){if(f.required())throw new IllegalArgumentException("Заполните поле «"+f.label()+"».");empty=true;continue;}
            if(empty)throw new IllegalArgumentException("Сначала заполните предыдущее поле.");
            if(!f.type().equals("text")&&!f.type().equals("arguments")&&(v.contains(" ")||v.length()>128))throw new IllegalArgumentException("«"+f.label()+"»: нужен один аргумент.");
            if(f.type().equals("player")&&!v.matches("[A-Za-z0-9_]{1,16}|[a-fA-F0-9-]{36}"))throw new IllegalArgumentException("Укажите ник или UUID игрока.");
            if(f.type().equals("number")&&!v.matches("-?[0-9]{1,16}(\\.[0-9]{1,8})?"))throw new IllegalArgumentException("«"+f.label()+"»: введите число.");
            if(f.type().equals("choice")&&!f.choices().contains(v))throw new IllegalArgumentException("Выберите значение из списка.");
            out.add(v);
        }
        String result=String.join(" ",out);if(result.length()>512)throw new IllegalArgumentException("Слишком длинная команда.");return result;
    }
    public JsonObject json(){return object("id",id,"label",label,"root",root,"prefix",prefix,"permission",permission,"fields",fields);}
}
