package ru.neverland.townybuilds.storage;
import org.bukkit.inventory.ItemStack;
import java.util.*;
public final class StockMath {
    private StockMath(){}
    public static ItemStack[] copy(ItemStack[] a){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {ItemStack[] b=new ItemStack[a.length];for(int i=0;i<a.length;i++)b[i]=a[i]==null?null:a[i].clone();return b;} }
    public static boolean present(ItemStack s){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return s!=null&&s.getType()!=org.bukkit.Material.AIR&&s.getType()!=org.bukkit.Material.CAVE_AIR&&s.getType()!=org.bukkit.Material.VOID_AIR&&s.getAmount()>0;} }
    public static int total(ItemStack[] a){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {int n=0;for(var s:a)if(present(s))n+=s.getAmount();return n;} }
    public static boolean matches(ItemStack s,ItemStack filter){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return present(s)&&(filter==null||s.isSimilar(filter));} }
    public static int count(ItemStack[] a,ItemStack filter){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {int n=0;for(var s:a)if(matches(s,filter))n+=s.getAmount();return n;} }
    /** Mutates only the supplied working copy; metadata and stack limits are preserved. */
    public static ItemStack[] take(ItemStack[] source,ItemStack filter,int limit,int keep){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        int remaining=Math.max(0,Math.min(limit,count(source,filter)-Math.max(0,keep)));List<ItemStack> cargo=new ArrayList<>();
        for(int i=0;i<source.length&&remaining>0;i++)if(matches(source[i],filter)){
            int n=Math.min(remaining,source[i].getAmount());var part=source[i].clone();part.setAmount(n);cargo.add(part);
            if(n==source[i].getAmount())source[i]=null;else source[i].setAmount(source[i].getAmount()-n);remaining-=n;
        }
        return cargo.toArray(ItemStack[]::new);
    } }
    public static Optional<ItemStack[]> recipe(ItemStack[] stock,List<ItemStack> inputs,ItemStack[] outputs){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        var work=copy(stock);for(var input:inputs){var template=input.clone();template.setAmount(1);if(count(work,template)<input.getAmount())return Optional.empty();take(work,template,input.getAmount(),0);}
        return insert(work,outputs)?Optional.of(work):Optional.empty();
    } }
    public static boolean insert(ItemStack[] destination,ItemStack[] cargo){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
        ItemStack[] work=copy(destination);
        for(ItemStack item:cargo)if(present(item)){
            int left=item.getAmount(),max=Math.min(64,item.getMaxStackSize());
            for(int i=0;i<work.length&&left>0;i++)if(present(work[i])&&work[i].isSimilar(item)){
                int n=Math.max(0,Math.min(left,max-work[i].getAmount()));work[i].setAmount(work[i].getAmount()+n);left-=n;
            }
            for(int i=0;i<work.length&&left>0;i++)if(!present(work[i])){int n=Math.min(left,max);work[i]=item.clone();work[i].setAmount(n);left-=n;}
            if(left>0)return false;
        }
        System.arraycopy(work,0,destination,0,work.length);return true;
    } }
}
