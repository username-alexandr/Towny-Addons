package ru.neverland.townyresources.model;

import java.util.*;
import java.nio.charset.StandardCharsets;

/** Requests describe a real shortage; they never create stock, payments or automatic contracts. */
public final class ResourceRequests {
    private ResourceRequests(){}
    public static UUID id(UUID town,String label){
        Objects.requireNonNull(town);if(label==null||!label.matches("[a-z0-9_-]{1,48}"))throw new IllegalArgumentException("Название плана: латинские буквы, цифры, _ и -, до 48 знаков");
        return UUID.nameUUIDFromBytes(("nlt-resources-purpose-v1:"+town+":"+label).getBytes(StandardCharsets.UTF_8));
    }
    public static Map<Resource,Long> parse(String text){
        if(text==null||text.length()>512)throw new IllegalArgumentException("Укажите ресурс=количество через запятую; дробная часть через точку");
        var out=new EnumMap<Resource,Long>(Resource.class);
        for(String pair:text.split(",",-1)){String[] parts=pair.split("=",-1);if(parts.length!=2)throw new IllegalArgumentException("Пример: wood=100,stone=25.5");long amount=Amounts.parse(parts[1]);if(amount==0||out.put(Resource.parse(parts[0]),amount)!=null)throw new IllegalArgumentException("Ресурс должен встречаться один раз, количество больше нуля");}
        return Amounts.copy(out);
    }
    public static Map<Resource,Long> deficits(UUID town,Collection<Reservation> reservations,TownState state,Map<Resource,Long> population){
        var required=Amounts.mutable(Map.of());
        for(var receipt:reservations)if(receipt.town().equals(town)&&receipt.status()==Reservation.Status.REQUESTED)for(var r:Resource.values())required.put(r,Math.addExact(required.get(r),receipt.amounts().get(r)));
        for(var r:Resource.values()){
            long minimum=Math.max(state.reserves().get(r),population.getOrDefault(r,0L));
            long free=Math.max(0,state.balances().get(r)-minimum);
            required.put(r,Math.max(0,required.get(r)-free));
        }
        return Collections.unmodifiableMap(required);
    }
}
