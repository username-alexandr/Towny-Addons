package ru.neverland.townysieges;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;

public final class TntRulesSmoke {
    private static int checks;
    private static void ok(boolean value,String text){if(!value)throw new AssertionError(text);checks++;}
    public static void main(String[] args)throws Exception {
        var yaml=new YamlConfiguration();try(var in=TntRulesSmoke.class.getResourceAsStream("/config.yml")){yaml.load(new InputStreamReader(in,StandardCharsets.UTF_8));}
        var s=TntSettings.load(yaml);
        ok(TntRules.damage(0,s)==100&&TntRules.damage(1,s)==100,"contact gives full configured damage");
        ok(Math.abs(TntRules.damage(3.5,s)-25)<.00001,"half remaining range means quarter damage with quadratic falloff");
        ok(TntRules.damage(6,s)==0&&TntRules.damage(60,s)==0,"outer boundary and further blocks receive no damage");
        ok(TntRules.damage(Double.NaN,s)==0&&TntRules.damage(-1,s)==0,"invalid distance cannot create healing or damage");
        double previous=Double.POSITIVE_INFINITY;
        for(int i=0;i<=120;i++){double amount=TntRules.damage(i/20.0,s);ok(amount<=previous&&amount>=0,"damage never rises with distance");previous=amount;}
        ok(TntRules.distance(.5,.5,.5,0,0,0)==0,"point inside block is contact");
        ok(TntRules.distance(2,.5,.5,0,0,0)==1,"distance measured to nearest face");
        ok(Math.abs(TntRules.distance(-2,2,2,0,0,0)-Math.sqrt(6))<.00001,"negative coordinates and diagonal distance");
        ok(s.hp("OAK_DOOR")==120&&s.hp("CHERRY_DOOR")==120&&s.hp("BAMBOO_TRAPDOOR")==120,"all wood variants use door health");
        ok(s.hp("IRON_DOOR")==400&&s.hp("IRON_TRAPDOOR")==400,"iron door and trapdoor stronger");
        ok(s.hp("RED_STAINED_GLASS_PANE")==60&&s.hp("OAK_LOG")==200&&s.hp("OBSIDIAN")==1200,"material categories respected");
        ok(TntRules.protectedMaterial("BEDROCK")&&TntRules.protectedMaterial("CHAIN_COMMAND_BLOCK")&&!TntRules.protectedMaterial("STONE"),"system blocks cannot be breached");
        for(String key:new String[]{"tnt.radius","tnt.contact-damage","tnt.hitpoints.default"}) {
            var old=yaml.get(key);yaml.set(key,Double.NaN);try{TntSettings.load(yaml);throw new AssertionError("accepted NaN");}catch(IllegalArgumentException expected){checks++;}yaml.set(key,old);
        }
        yaml.set("tnt.contact-radius",6);try{TntSettings.load(yaml);throw new AssertionError("accepted empty damage range");}catch(IllegalArgumentException expected){checks++;}
        yaml.set("tnt.contact-radius",1);yaml.set("tnt.enabled","false");try{TntSettings.load(yaml);throw new AssertionError("string toggle accepted");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("TntRulesSmoke PASS: "+checks+" assertions");
    }
}
