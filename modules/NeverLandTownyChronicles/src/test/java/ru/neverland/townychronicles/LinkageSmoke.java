package ru.neverland.townychronicles;

public final class LinkageSmoke {
    public static void main(String[]args)throws Exception{
        String[]types={"ru.neverland.townychronicles.TownyChronicles","ru.neverland.townychronicles.listener.TownyHistoryListener","ru.neverland.townychronicles.integration.ChroniclesExpansion","ru.neverland.townychronicles.integration.DynamicWarBridge","ru.neverland.townychronicles.gui.ChronicleMenuManager","ru.neverland.townychronicles.api.TownyChroniclesApi"};
        for(String type:types)Class.forName(type,false,LinkageSmoke.class.getClassLoader());
        System.out.println("LinkageSmoke OK: "+types.length);
    }
}
