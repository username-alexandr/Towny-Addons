package ru.neverland.townyenvironment.model;

import java.math.*;
import java.util.*;

/** A read-only geographical explanation of the existing city pressure, never a new pollution simulation. */
public final class DistrictEnvironment {
    private DistrictEnvironment(){}
    public static final String CITY="city",NATURE="@nature";
    public record Source(String id,String name,int level,BigDecimal emissions,BigDecimal cleaning){public Source{if(id==null||!(id.equals(NATURE)||id.matches("[a-z][a-z0-9_]{0,63}"))||name==null||name.isBlank()||name.length()>160||level<0||level>5)throw new IllegalArgumentException("Неверный источник экологии");amount(emissions);amount(cleaning);}}
    public record District(String id,String name){public District{if(id==null||!id.matches("[a-z0-9_-]{1,32}")||name==null||name.isBlank()||name.length()>80)throw new IllegalArgumentException("Неверный район экологии");}}
    public record Input(BigDecimal emissions,BigDecimal cleaning,Map<String,Source> sources,Map<String,District> districts,Map<String,String> assignments){public Input{amount(emissions);amount(cleaning);sources=Map.copyOf(sources);districts=Map.copyOf(districts);assignments=Map.copyOf(assignments);if(sources.size()>257||districts.size()>128||assignments.size()>1024||!sources.containsKey(NATURE))throw new IllegalArgumentException("Неверный объём источников");for(var e:sources.entrySet())if(!e.getKey().equals(e.getValue().id()))throw new IllegalArgumentException("Источник не совпадает с ключом");for(var e:districts.entrySet())if(!e.getKey().equals(e.getValue().id()))throw new IllegalArgumentException("Район не совпадает с ключом");for(var e:assignments.entrySet())if(e.getKey().equals(NATURE)||!e.getKey().matches("[a-z][a-z0-9_]{0,63}")||!districts.containsKey(e.getValue()))throw new IllegalArgumentException("Неверное назначение источника");}}
    private static void amount(BigDecimal value){if(value==null||value.signum()<0||value.compareTo(BigDecimal.valueOf(20_000))>0||value.precision()>40||Math.abs((long)value.scale())>30)throw new IllegalArgumentException("Неверная нагрузка");}
    private record Fraction(String id,BigDecimal remainder){}
    /** Exact city sums while correcting only floating-point accumulation noise from the unchanged engine. */
    private static Map<String,BigDecimal> split(BigDecimal total,Map<String,BigDecimal> weights){
        int scale=Math.max(12,total.scale());var unit=BigDecimal.ONE.scaleByPowerOfTen(-scale);var sum=weights.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);var result=new TreeMap<String,BigDecimal>();var fractions=new ArrayList<Fraction>();var used=BigDecimal.ZERO;
        if(sum.signum()==0){if(total.signum()!=0)throw new IllegalArgumentException("Нет источника для городского итога");weights.keySet().forEach(id->result.put(id,BigDecimal.ZERO));return Map.copyOf(result);}
        if(total.subtract(sum).abs().compareTo(BigDecimal.valueOf(1e-8))>0)throw new IllegalArgumentException("Источники не соответствуют городскому итогу");
        for(var e:weights.entrySet()){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Расчёт остановлен");var product=total.multiply(e.getValue());var share=product.divide(sum,scale,RoundingMode.DOWN);result.put(e.getKey(),share);used=used.add(share);fractions.add(new Fraction(e.getKey(),product.subtract(share.multiply(sum))));}
        int remaining=total.subtract(used).movePointRight(scale).intValueExact();fractions.sort(Comparator.comparing(Fraction::remainder).reversed().thenComparing(Fraction::id));for(int i=0;i<remaining;i++){String id=fractions.get(i).id();result.put(id,result.get(id).add(unit));}return Map.copyOf(result);
    }
    public static Map<String,Object> calculate(Input input){
        var emissions=new TreeMap<String,BigDecimal>();var cleaning=new TreeMap<String,BigDecimal>();input.sources().forEach((id,s)->{emissions.put(id,s.emissions());cleaning.put(id,s.cleaning());});var allocatedEmissions=split(input.emissions(),emissions);var allocatedCleaning=split(input.cleaning(),cleaning);
        var names=new TreeMap<String,String>();names.put(CITY,"Общегородские источники");input.districts().forEach((id,d)->names.put("district:"+id,d.name()));var rows=new TreeMap<String,Object>();
        for(var group:names.entrySet()){
            BigDecimal e=BigDecimal.ZERO,c=BigDecimal.ZERO;var sources=new TreeMap<String,Object>();int green=0;
            for(var source:input.sources().values()){String district=input.assignments().get(source.id());String destination=district==null?CITY:"district:"+district;if(!destination.equals(group.getKey()))continue;var se=allocatedEmissions.get(source.id());var sc=allocatedCleaning.get(source.id());e=e.add(se);c=c.add(sc);if(!source.id().equals(NATURE)&&sc.signum()>0)green++;
                sources.put(source.id(),Map.of("id",source.id(),"name",source.name(),"level",source.level(),"emissions",se.toPlainString(),"cleaning",sc.toPlainString(),"natural",source.id().equals(NATURE)));
            }
            rows.put(group.getKey(),Map.of("id",group.getKey(),"name",group.getValue(),"district",!group.getKey().equals(CITY),"emissions",e.toPlainString(),"cleaning",c.toPlainString(),"change",e.subtract(c).toPlainString(),"greenBuildings",green,"sources",Map.copyOf(sources)));
        }
        return Map.of("rows",Map.copyOf(rows),"totals",Map.of("emissions",input.emissions().toPlainString(),"cleaning",input.cleaning().toPlainString(),"change",input.emissions().subtract(input.cleaning()).toPlainString()));
    }
}
