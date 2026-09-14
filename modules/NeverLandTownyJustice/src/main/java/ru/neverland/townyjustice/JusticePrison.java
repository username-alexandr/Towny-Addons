package ru.neverland.townyjustice;
import java.util.UUID;
public record JusticePrison(UUID id,UUID town,UUID world,int x,int y,int z,String name,boolean ready){
    public JusticePrison{if(id==null||town==null||world==null||Math.abs((long)x)>30000000||Math.abs((long)z)>30000000||y< -2048||y>2048||name==null||!name.matches("[\\p{L}\\p{N}_-]{2,32}"))throw new IllegalArgumentException("Неверная тюрьма");}
    public JusticePrison finish(){return new JusticePrison(id,town,world,x,y,z,name,true);}
}
