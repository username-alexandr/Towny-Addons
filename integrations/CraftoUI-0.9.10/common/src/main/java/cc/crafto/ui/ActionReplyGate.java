package cc.crafto.ui;

/** Correlates one menu click with its server reply and the intended local destination. */
public final class ActionReplyGate {
    private long sent,applied;private String route="";
    public long sent(String requestedRoute){route=requestedRoute==null?"":requestedRoute;return ++sent;}
    public boolean accept(long reply,String action){
        if(reply!=sent||reply<=applied)return false;
        applied=reply;
        return switch(action){case "settings"->route.equals("client:settings");case "city"->route.equals("city");default->true;};
    }
}
