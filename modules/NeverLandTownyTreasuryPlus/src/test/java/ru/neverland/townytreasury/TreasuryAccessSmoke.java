package ru.neverland.townytreasury;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.command.CommandSender;
import ru.neverland.townytreasury.service.TreasuryAccess;

public final class TreasuryAccessSmoke {
    public static void main(String[] args) {
        AtomicBoolean export=new AtomicBoolean(false);
        CommandSender admin=(CommandSender)Proxy.newProxyInstance(CommandSender.class.getClassLoader(),new Class<?>[]{CommandSender.class},(p,m,a)->{
            if(m.getName().equals("hasPermission")) return TreasuryAccess.EXPORT.equals(a[0])?export.get():true;
            if(m.getName().equals("isOp"))return true;
            if(m.getReturnType()==boolean.class)return false;
            return null;
        });
        if(TreasuryAccess.canExport(admin))throw new AssertionError("admin cannot bypass explicit export denial");
        export.set(true);if(!TreasuryAccess.canExport(admin))throw new AssertionError("explicit grant ignored");
        export.set(false);if(TreasuryAccess.canExport(admin))throw new AssertionError("revoked permission cached");
        System.out.println("TreasuryAccessSmoke PASS");
    }
}
