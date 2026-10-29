package ru.neverland.townyjobs.mastery;
import java.util.*;
import ru.neverland.townyjobs.Profession;

/** A frozen ordered work plan; completion unlocks one existing-cap production specialty, never new money. */
public final class MasteryModel {
    private MasteryModel(){}
    public enum Action {SMELT,HARVEST,CRAFT,HUNT,TRADE,ENCHANT,BREW;
        public boolean supports(Profession profession){return this==CRAFT?(profession==Profession.ENGINEER||profession==Profession.BLACKSMITH||profession==Profession.FARMER):profession()==profession;}
        public Profession profession(){return switch(this){case SMELT->Profession.BLACKSMITH;case HARVEST->Profession.FARMER;case CRAFT->Profession.ENGINEER;case HUNT->Profession.GUARD;case TRADE->Profession.MERCHANT;case ENCHANT->Profession.RESEARCHER;case BREW->Profession.ALCHEMIST;};}
    }
    public record Step(Action action,String target,int amount,String text){
        public Step{Objects.requireNonNull(action);if(target==null||!target.matches("[A-Z0-9_]{1,80}")||amount<1||amount>4096||text==null||text.isBlank()||text.length()>160)throw new IllegalArgumentException("Неверный этап мастерского заказа");}
    }
    public record Definition(String id,Profession profession,String title,String icon,int minimumLevel,double bonus,Set<String> buildings,List<Step> steps){
        public Definition{if(id==null||!id.matches("[a-z0-9_]{1,64}")||profession==null||title==null||title.isBlank()||title.length()>80||icon==null||!icon.matches("[A-Z0-9_]{1,80}")||minimumLevel<1||minimumLevel>5||!Double.isFinite(bonus)||bonus<0||bonus>.05)throw new IllegalArgumentException("Неверная специализация");buildings=Set.copyOf(buildings);steps=List.copyOf(steps);if(buildings.isEmpty()||buildings.size()>32||steps.size()<2||steps.size()>6)throw new IllegalArgumentException("Специализация: здания и 2..6 этапов");for(String id2:buildings)if(!id2.matches("[a-z0-9_]{1,80}"))throw new IllegalArgumentException("Неверное здание");for(var s:steps)if(!s.action().supports(profession))throw new IllegalArgumentException("Этап не соответствует профессии");}
    }
    public record Order(UUID id,UUID town,Definition definition,int stage,int progress,long started){
        public Order{Objects.requireNonNull(id);Objects.requireNonNull(town);Objects.requireNonNull(definition);if(started<0||stage<0||stage>=definition.steps().size()||progress<0||progress>=definition.steps().get(stage).amount())throw new IllegalArgumentException("Повреждён мастерский заказ");}
    }
    public record Worker(Order active,Map<String,Definition> unlocked,Map<Profession,String> chosen){
        public Worker{unlocked=Map.copyOf(unlocked);chosen=Map.copyOf(chosen);if(unlocked.size()>64||chosen.size()>7)throw new IllegalArgumentException("Слишком много специализаций");unlocked.forEach((id,d)->{if(!id.equals(d.id()))throw new IllegalArgumentException("Неверное открытое направление");});for(var e:chosen.entrySet()){var d=unlocked.get(e.getValue());if(d==null||d.profession()!=e.getKey())throw new IllegalArgumentException("Специализация не открыта");}}
        public static Worker empty(){return new Worker(null,Map.of(),Map.of());}
        public Worker start(UUID id,UUID town,Definition definition,long now){if(active!=null)throw new IllegalStateException("Сначала завершите или отмените текущий мастерский заказ");if(unlocked.containsKey(definition.id()))throw new IllegalStateException("Это направление уже открыто");return new Worker(new Order(id,town,definition,0,0,now),unlocked,chosen);}
        public Worker cancel(UUID expected){if(active==null||!active.id().equals(expected))throw new IllegalStateException("Заказ уже изменился");return new Worker(null,unlocked,chosen);}
        public Worker choose(Profession role,String id){var d=unlocked.get(id);if(d==null||d.profession()!=role)throw new IllegalStateException("Сначала завершите мастерский заказ этого направления");var next=new EnumMap<Profession,String>(Profession.class);next.putAll(chosen);next.put(role,id);return new Worker(active,unlocked,next);}
        public Worker action(Proof proof){
            if(active==null||!active.id().equals(proof.order())||!active.town().equals(proof.town())||active.definition().profession()!=proof.profession())return this;
            var step=active.definition().steps().get(active.stage());if(step.action()!=proof.action()||!step.target().equals(proof.target()))return this;
            int progress=Math.min(step.amount(),Math.addExact(active.progress(),proof.amount()));
            if(progress<step.amount())return new Worker(new Order(active.id(),active.town(),active.definition(),active.stage(),progress,active.started()),unlocked,chosen);
            if(active.stage()+1<active.definition().steps().size())return new Worker(new Order(active.id(),active.town(),active.definition(),active.stage()+1,0,active.started()),unlocked,chosen);
            var unlocked2=new HashMap<>(unlocked);unlocked2.put(active.definition().id(),active.definition());return new Worker(null,unlocked2,chosen);
        }
        public double bonus(Profession profession,String project){var id=chosen.get(profession);var d=id==null?null:unlocked.get(id);return d!=null&&d.profession()==profession&&d.buildings().contains(project)?d.bonus():0;}
    }
    public record Proof(UUID id,UUID player,UUID town,UUID order,Profession profession,Action action,String target,int amount,long at){
        public Proof{Objects.requireNonNull(id);Objects.requireNonNull(player);Objects.requireNonNull(town);Objects.requireNonNull(order);new Step(action,target,amount,"Результат работы");if(!action.supports(profession))throw new IllegalArgumentException("Действие другой профессии");if(at<0)throw new IllegalArgumentException("Неверное время работы");}
        public String identity(){return player+":"+town+":"+order+":"+profession+":"+action+":"+target+":"+amount;}
    }
}
