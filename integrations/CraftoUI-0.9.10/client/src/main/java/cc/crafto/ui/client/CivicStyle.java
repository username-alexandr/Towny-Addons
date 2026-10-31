package cc.crafto.ui.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Theme-derived surfaces and original line pictograms for civic navigation only. */
final class CivicStyle {
    static final float DETAIL_SCALE=0.82f;
    static int financeInk(JsonObject theme,String tone){
        if(!Set.of("positive","negative","warning").contains(tone))return UiTheme.color(theme,"text");
        if(theme.has(tone))return UiTheme.color(theme,tone);
        int text=UiTheme.color(theme,"text");boolean light=((text>>16&255)+(text>>8&255)+(text&255))<384;
        return switch(tone){case "positive"->light?0xFF123D28:0xFF65DF9F;case "negative"->light?0xFF701F27:0xFFFF8F92;default->light?0xFF5B3500:0xFFFFD27B;};
    }
    static int inkAccent(JsonObject theme){
        int text=UiTheme.color(theme,"text"),accent=UiTheme.color(theme,"accent");
        return ((text>>16&255)+(text>>8&255)+(text&255))<384?mix(accent,text,48):accent;
    }
    static int mix(int a,int b,int percent){
        int r=((a>>16&255)*(100-percent)+(b>>16&255)*percent)/100;
        int g=((a>>8&255)*(100-percent)+(b>>8&255)*percent)/100;
        int bl=((a&255)*(100-percent)+(b&255)*percent)/100;
        return 0xff000000|r<<16|g<<8|bl;
    }
    static void panel(GuiGraphicsExtractor g,int x,int y,int w,int h,int color){
        if(w<5||h<5){g.fill(x,y,x+w,y+h,color);return;}
        g.fill(x+2,y,x+w-2,y+h,color);g.fill(x+1,y+1,x+w-1,y+h-1,color);g.fill(x,y+2,x+w,y+h-2,color);
    }
    static String clip(String value,int width){var f=Minecraft.getInstance().font;if(width<=0)return "";if(f.width(value)<=width)return value;int ellipsis=f.width("…");return width<ellipsis?"":f.plainSubstrByWidth(value,width-ellipsis)+"…";}
    static List<String> wrap(String value,int width){
        List<String> out=new ArrayList<>();var f=Minecraft.getInstance().font;
        for(String paragraph:value.split("\n",-1)){
            if(paragraph.isEmpty()){out.add("");continue;}
            for(var line:f.split(Component.literal(paragraph),Math.max(12,width))){StringBuilder s=new StringBuilder();line.accept((i,style,cp)->{s.appendCodePoint(cp);return true;});out.add(s.toString());}
        }return out;
    }
    static void text(GuiGraphicsExtractor g,String value,int x,int y,int width,int color){g.text(Minecraft.getInstance().font,clip(value,width),x,y,color,false);}
    static void wrapped(GuiGraphicsExtractor g,String value,int x,int y,int width,int limit,int color){
        var lines=wrap(value,width);for(int i=0;i<Math.min(limit,lines.size());i++)text(g,lines.get(i)+(i==limit-1&&lines.size()>limit?"…":""),x,y+i*12,width,color);
    }
    static int detailWidth(int width){return Math.max(12,(int)(width/DETAIL_SCALE));}
    static void detail(GuiGraphicsExtractor g,String value,int x,int y,int width,int color){
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(DETAIL_SCALE,DETAIL_SCALE);
        text(g,value,0,0,detailWidth(width),color);g.pose().popMatrix();
    }
    static void detailWrapped(GuiGraphicsExtractor g,String value,int x,int y,int width,int limit,int color){
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(DETAIL_SCALE,DETAIL_SCALE);
        wrapped(g,value,0,0,detailWidth(width),limit,color);g.pose().popMatrix();
    }
    static String title(String route){return switch(route){
        case "planner","planner-catalog","planner-project"->"План развития";case "planner-supply"->"Снабжение плана";case "planner-effects"->"Результат развития";
        case "procurement-offers","procurement-requests","procurement-order"->"Закупки города";case "procurement-error"->"Закупки временно недоступны";
        case "inbox"->"Входящие приглашения";case "materials"->"Недостающие материалы";case "map","plot"->"Карта участков";
        case "goals"->"Общая цель города";case "history","history-entry"->"История города";case "treasury"->"История казны";
        case "orders"->"Подбор заказов";case "mine"->"Мои заказы";case "tree"->"Дерево развития города";case "invite-town"->"Приглашение в город";case "invite-company"->"Приглашение в компанию";
        default->"Мой город";};}
    static String subtitle(String route){return switch(route){
        case "tree"->"Ветви зданий и исследований открываются по мере развития города";
        case "planner","planner-catalog","planner-project"->"Выберите здания и уровни — зависимости и смета рассчитаются вместе";
        case "planner-supply"->"Потребности всей цепочки, сданные материалы, склад и остаток";
        case "planner-effects"->"Участки, расходы, энергия и условия запуска зданий";
        case "procurement-offers","procurement-requests","procurement-order","procurement-error"->"Городские заявки, подтверждённые платежи и возвраты";
        case "inbox"->"Приглашения в города и компании — всё в одном месте";
        case "materials"->"Потребности строек, запасы склада и ваш инвентарь";
        case "goals"->"Общий приоритет города и вклад жителей";
        case "map","plot"->"Выберите участок, чтобы посмотреть владельца и действия";
        case "history","history-entry"->"События, участники и подробности";
        case "treasury"->"Операции бюджета и управление средствами";
        case "invite-town","invite-company"->"Выберите действие — условия появятся перед подтверждением";
        case "build"->"Ход строительства, материалы и управление";case "contract"->"Условия, общий прогресс и ваш вклад";
        case "orders","mine","offer","order"->"Условия и доступные действия по заказам";
        default->"Показатели города и доступные действия";};}
    static String iconFor(String route){if(route.startsWith("planner")||route.equals("tree"))return route.equals("planner-supply")?"materials":"planner";if(route.startsWith("procurement"))return "orders";return switch(route){case "invite-town","invite-company"->"inbox";case "history-entry"->"history";case "plot"->"map";case "offer","order","mine"->"orders";case "contract"->"goals";default->route;};}
    static void icon(GuiGraphicsExtractor g,String id,int x,int y,int c){
        switch(id){
            case "unique"->{g.fill(x+8,y+1,x+10,y+4,c);g.fill(x+4,y+4,x+14,y+6,c);g.fill(x+1,y+8,x+17,y+10,c);g.fill(x+4,y+12,x+14,y+14,c);g.fill(x+8,y+15,x+10,y+18,c);}
            case "planner"->{g.outline(x+1,y+1,16,16,c);g.outline(x+4,y+4,4,4,c);g.outline(x+10,y+10,4,4,c);g.fill(x+7,y+5,x+12,y+6,c);g.fill(x+11,y+5,x+12,y+11,c);}
            case "inbox"->{g.outline(x+1,y+3,16,12,c);for(int i=0;i<7;i++){g.fill(x+2+i,y+4+i/2,x+3+i,y+5+i/2,c);g.fill(x+15-i,y+4+i/2,x+16-i,y+5+i/2,c);}}
            case "city","build"->{g.outline(x+2,y+7,6,10,c);g.outline(x+9,y+2,7,15,c);for(int k=0;k<3;k++)g.fill(x+11,y+4+k*4,x+14,y+6+k*4,c);}
            case "materials"->{g.outline(x+1,y+3,16,13,c);g.fill(x+1,y+7,x+17,y+8,c);g.outline(x+7,y+3,4,7,c);}
            case "map"->{g.outline(x+1,y+1,16,16,c);g.fill(x+6,y+2,x+7,y+16,c);g.fill(x+11,y+2,x+12,y+16,c);g.fill(x+2,y+10,x+16,y+11,c);}
            case "goals"->{g.fill(x+3,y+1,x+5,y+17,c);g.outline(x+5,y+2,11,8,c);g.fill(x+1,y+16,x+8,y+17,c);}
            case "history"->{g.outline(x+2,y+1,14,16,c);g.fill(x+5,y+5,x+13,y+6,c);g.fill(x+5,y+9,x+13,y+10,c);g.fill(x+5,y+13,x+10,y+14,c);}
            case "treasury"->{g.outline(x+1,y+5,16,11,c);g.fill(x+4,y+2,x+14,y+4,c);g.outline(x+11,y+8,6,5,c);}
            case "lock"->{g.outline(x+5,y+1,8,8,c);g.outline(x+2,y+7,14,10,c);g.fill(x+8,y+10,x+10,y+14,c);}
            case "check"->{g.fill(x+3,y+9,x+6,y+12,c);g.fill(x+6,y+12,x+9,y+15,c);for(int i=0;i<8;i++)g.fill(x+8+i,y+13-i,x+10+i,y+15-i,c);}
            case "search"->{g.outline(x+1,y+1,11,11,c);for(int i=0;i<6;i++)g.fill(x+10+i,y+10+i,x+12+i,y+12+i,c);}
            case "favorite"->{g.outline(x+4,y+1,10,16,c);g.fill(x+7,y+13,x+11,y+17,c);}
            case "settings","scale","blur","animation","theme"->{for(int i=0;i<3;i++){g.fill(x+2,y+3+i*5,x+16,y+4+i*5,c);g.outline(x+4+i*3,y+1+i*5,3,5,c);}}
            case "summary"->{g.outline(x+2,y+1,14,16,c);g.fill(x+5,y+4,x+13,y+5,c);g.fill(x+5,y+8,x+11,y+9,c);g.fill(x+5,y+12,x+13,y+13,c);}
            case "commands","form"->{g.outline(x+1,y+2,16,14,c);g.fill(x+4,y+5,x+7,y+7,c);g.fill(x+6,y+7,x+9,y+9,c);g.fill(x+4,y+9,x+7,y+11,c);g.fill(x+10,y+11,x+14,y+12,c);}
            case "hud"->{g.outline(x+1,y+1,16,16,c);g.fill(x+11,y+4,x+15,y+6,c);g.fill(x+11,y+8,x+15,y+10,c);g.fill(x+11,y+12,x+15,y+14,c);}
            case "close"->{for(int i=0;i<12;i++){g.fill(x+3+i,y+3+i,x+5+i,y+5+i,c);g.fill(x+14-i,y+3+i,x+16-i,y+5+i,c);}}
            case "orders"->{g.outline(x+3,y+1,12,16,c);g.fill(x+6,y+5,x+12,y+6,c);g.fill(x+6,y+9,x+12,y+10,c);}
            default->{g.outline(x+2,y+2,14,14,c);g.fill(x+8,y+5,x+10,y+7,c);g.fill(x+8,y+9,x+10,y+13,c);}
        }
    }
}
