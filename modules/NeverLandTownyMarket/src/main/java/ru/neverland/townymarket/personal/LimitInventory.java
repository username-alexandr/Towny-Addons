package ru.neverland.townymarket.personal;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Pure exact vanilla-stack plans. Custom names, enchantments, contents and other metadata never match. */
public final class LimitInventory {
    private LimitInventory(){}
    public static ItemStack[] take(ItemStack[] before,Material material,int amount){if(amount<1)throw new IllegalArgumentException("Неверное количество");var next=copy(before);var sample=new ItemStack(material,1);int left=amount;for(int i=0;i<next.length&&left>0;i++){var item=next[i];if(item==null||!item.isSimilar(sample))continue;int n=Math.min(left,item.getAmount());left-=n;if(n==item.getAmount())next[i]=null;else item.setAmount(item.getAmount()-n);}return left==0?next:null;}
    public static ItemStack[] give(ItemStack[] before,Material material,int amount){if(amount<1)throw new IllegalArgumentException("Неверное количество");var next=copy(before);var sample=new ItemStack(material,1);int left=amount;for(int i=0;i<next.length&&left>0;i++){var item=next[i];if(item==null||!item.isSimilar(sample))continue;int n=Math.min(left,Math.max(0,item.getMaxStackSize()-item.getAmount()));item.setAmount(item.getAmount()+n);left-=n;}for(int i=0;i<next.length&&left>0;i++)if(next[i]==null||next[i].getType().isAir()){int n=Math.min(left,sample.getMaxStackSize());next[i]=new ItemStack(material,n);left-=n;}return left==0?next:null;}
    private static ItemStack[] copy(ItemStack[] before){var next=new ItemStack[before.length];for(int i=0;i<before.length;i++)next[i]=before[i]==null?null:before[i].clone();return next;}
}
