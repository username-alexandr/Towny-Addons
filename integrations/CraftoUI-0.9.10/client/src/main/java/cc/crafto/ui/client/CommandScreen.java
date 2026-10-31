package cc.crafto.ui.client;

import cc.crafto.ui.*;
import com.google.gson.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

/** Data-driven addon forms. Presentation never selects executable command roots or permissions. */
final class CommandScreen extends PanelScreen {
    private final CraftoUIClient client;private Screen parent;private final String returnModule,initialForm;private boolean nativeReview;
    private String token="",module="",moduleLabel="",query="",id="",stage="list",usage="",notice="Загрузка…",confirmation="",preview="",commandTemplate="",lastCommand="";
    private CommandForm form;private final JsonObject values=new JsonObject();
    private List<JsonObject> entries=List.of();private List<String> suggestions=List.of();private final List<String> feedback=new ArrayList<>();
    private int page,pages=1,sequence,awaited,suggestRequest,fieldIndex,suggestPage,lastCapacity;
    private long suggestDue,queryDue,feedbackUntil,waitingUntil;private boolean initialized,returning,busy;
    private EditBox input;
    CommandScreen(CraftoUIClient client,Screen parent,String module){
        this(client,parent,module,"");
    }
    CommandScreen(CraftoUIClient client,Screen parent,String module,String initialForm){
        super("Все действия",client);this.client=client;this.parent=parent;this.module=module;this.initialForm=initialForm;returnModule=parent==null?module:"";
        if(parent instanceof MenuScreen m)m.suspended=true;if(parent instanceof PersonalScreen p)p.suspendForCommands();
    }
    void resumeNative(JsonObject b){
        initialized=true;token=str(b,"token","");id=str(b,"id","");form=CommandForm.parse(b.getAsJsonObject("form"));
        module="Towny";moduleLabel="Создание города";nativeReview=true;confirmation=str(b,"confirmation","");
        preview=str(b,"command","");lastCommand=str(b,"executedCommand","/town new <название>");stage="review";notice="Подтверждение города после выбора стиля";
        if(b.has("theme"))client.lastTheme=b.getAsJsonObject("theme").deepCopy();resetScroll();changed();
    }
    private int capacity(){return CommandCatalogLayout.capacity(bottom-bodyTop,columns(174,3));}
    private void request(String op,JsonObject body){
        body.addProperty("op",op);body.addProperty("token",token);body.addProperty("request",++sequence);
        if(op.equals("suggest"))suggestRequest=sequence;else {awaited=sequence;busy=true;waitingUntil=System.nanoTime()+8_000_000_000L;changed();}
        client.send(Kind.COMMANDS,body);
    }
    private void list(boolean reopen){stage="list";confirmation="";suggestDue=0;queryDue=0;suggestions=List.of();notice="Загрузка…";request(reopen?"open":"list",object("module",module,"query",query,"page",page,"size",capacity()));}
    private boolean canAct(){return !busy&&!dirty&&queryDue==0;}
    @Override protected void init(){
        String title=stage.equals("list")?(module.isEmpty()?"Все действия":moduleLabel):form==null?"Все действия":form.label();
        String subtitle=switch(stage){case "list"->"Выберите раздел и действие";case "form"->"Укажите данные для действия";case "review"->"Проверьте условия перед подтверждением";default->"Ответ сервера также появится в чате";};
        begin(UiTheme.resolve(client.lastTheme,client.settings.darkTheme),title,subtitle,stage.equals("review")?"check":"commands");input=null;
        tab("Разделы","commands",0,3,stage.equals("list"),()->{if(!canAct())return;if(stage.equals("review"))cancelReview();query="";page=0;resetScroll();list(false);});
        var params=tab("Параметры","form",1,3,stage.equals("form"),()->{if(stage.equals("review")&&!busy){cancelReview();stage="form";resetScroll();changed();}});params.active=stage.equals("review")||stage.equals("form");
        var review=tab(stage.equals("feedback")?"Результат":"Подтверждение","check",2,3,stage.equals("review")||stage.equals("feedback"),()->{});review.active=false;
        footer(this::onClose,stage.equals("list")?"Обновить":"",stage.equals("list")?()->{if(busy)return;query="";page=0;resetScroll();list(true);}:null);
        if(!initialized){initialized=true;lastCapacity=capacity();if(initialForm.isBlank())list(true);else {stage="form";request("open",object("module",module,"form",initialForm));}}
        else if(stage.equals("list")&&lastCapacity!=capacity()&&!busy){lastCapacity=capacity();page=0;list(false);}
        if(stage.equals("list"))catalog();else if(stage.equals("form"))editor();else summary();
        status=busy?"Ожидание ответа сервера…":notice;finish();
    }
    private void catalog(){
        input=input("Поиск действий","Поиск раздела или действия…",query,6,CommandCatalogLayout.SEARCH_Y,span-20,80,s->{if(!stage.equals("list"))return;query=s;queryDue=System.nanoTime()+250_000_000L;page=0;});
        int cols=columns(174,3),w=cellWidth(cols);
        for(int i=0;i<entries.size();i++){
            var e=entries.get(i);boolean section=str(e,"type","").equals("module");
            var b=card(str(e,"label",""),str(e,"detail",""),section?"commands":"form","catalog",false,i%cols*(w+10),CommandCatalogLayout.CARD_Y+i/cols*CommandCatalogLayout.ROW_STRIDE,w,CommandCatalogLayout.CARD_HEIGHT,()->{
                if(!canAct())return;query="";page=0;resetScroll();if(section){module=str(e,"id","");list(false);}else {id=str(e,"id","");request("form",object("id",id));}
            });b.active=!busy;if(!section)b.setTooltip(Tooltip.create(Component.literal("Команда: "+str(e,"command",""))));
        }
        if(entries.isEmpty())info(busy?"Загружаем действия…":"Доступных действий нет",busy?"Получаем список с сервера.":query.isBlank()?"Сервер показывает только доступные вам разделы.":"Попробуйте другое название.","search",CommandCatalogLayout.CARD_Y);
        pager(page,pages,()->changePage(-1),()->changePage(1));
    }
    private void changePage(int delta){if(!canAct())return;int next=Math.clamp(page+delta,0,pages-1);if(next==page)return;page=next;resetScroll();list(false);}
    private void editor(){
        if(form==null)return;
        if(form.fields().isEmpty()){
            int y=info("Параметры не требуются","Сначала проверьте действие. Затем сервер предложит подтвердить выполнение.","form",0);
            var b=bodyPrimary("Проверить действие",0,y,span-8,()->{if(canAct())prepare();});b.active=!busy;b.setTooltip(Tooltip.create(Component.literal("Команда: "+commandForValues())));return;
        }
        fieldIndex=Math.clamp(fieldIndex,0,form.fields().size()-1);var field=form.fields().get(fieldIndex);
        int y=info(field.type().equals("arguments")?"Что вы хотите сделать?":field.label(),field.type().equals("arguments")?"Выберите действие ниже или введите его название. Сервер проверит доступ перед выполнением.":"Шаг "+(fieldIndex+1)+" из "+form.fields().size()+(field.required()?" · обязательное поле":" · можно пропустить"),"form",0);
        input=input(field.label(),field.type().equals("arguments")?"Название действия и параметры":"Введите значение или выберите вариант",str(values,field.id(),""),6,y+4,span-20,field.type().equals("text")||field.type().equals("arguments")?512:128,s->{
            if(!stage.equals("form")||form==null||form.fields().get(fieldIndex)!=field)return;values.addProperty(field.id(),s);suggestDue=System.nanoTime()+250_000_000L;suggestions=List.of();suggestPage=0;suggestRequest=-1;changed();
        });y+=42;
        int cols=columns(190,3),w=cellWidth(cols),limit=cols*3,total=Math.max(1,(suggestions.size()+limit-1)/limit);suggestPage=Math.clamp(suggestPage,0,total-1);
        if(!suggestions.isEmpty()){
            if(total>1){bodyButton("‹ Варианты",0,y,100,()->{suggestPage=Math.max(0,suggestPage-1);changed();});bodyButton((suggestPage+1)+" / "+total+" ›",span-108,y,100,()->{suggestPage=Math.min(total-1,suggestPage+1);changed();});y+=32;}
            int shown=Math.min(limit,suggestions.size()-suggestPage*limit);
            for(int i=0;i<shown;i++){
                String value=suggestions.get(suggestPage*limit+i);var choice=card(CommandChoice.label(value,field.type()),CommandChoice.detail(value,field.type()),"form","compact",false,i%cols*(w+10),y+i/cols*68,w,62,()->{
                    if(!canAct())return;String next=value;if(field.type().equals("arguments")){String old=str(values,field.id(),"");int space=old.lastIndexOf(' ');next=(space<0?"":old.substring(0,space+1))+value+" ";}input.setValue(next);
                });choice.setTooltip(Tooltip.create(Component.literal("Ввести: "+value)));
            }y+=(shown+cols-1)/cols*68+8;
        }
        int half=(span-18)/2;var prev=bodyButton("← Предыдущее поле",0,y,half,()->{if(canAct())changeField(-1);});prev.active=fieldIndex>0&&!busy;
        var next=bodyPrimary(fieldIndex+1<form.fields().size()?"Следующее поле →":"Проверить действие",half+10,y,span-half-18,()->{if(!canAct())return;if(fieldIndex+1<form.fields().size())changeField(1);else prepare();});next.active=!busy;
        if(fieldIndex+1==form.fields().size())next.setTooltip(Tooltip.create(Component.literal("Команда: "+commandForValues())));
        setInitialFocus(input);
    }
    private void changeField(int delta){fieldIndex+=delta;suggestions=List.of();suggestPage=0;suggestRequest=-1;suggestDue=System.nanoTime()+200_000_000L;notice="";resetScroll();changed();}
    private void prepare(){try{form.arguments(values);notice="Проверка…";suggestDue=0;request("prepare",object("id",id,"values",values));}catch(IllegalArgumentException e){notice=e.getMessage();changed();}}
    private String commandForValues(){
        if(form==null)return commandTemplate;
        try{String args=form.arguments(values);return "/"+form.root()+(args.isEmpty()?"":" "+args);}catch(IllegalArgumentException incomplete){return commandTemplate;}
    }
    private void summary(){
        int y=info(stage.equals("review")?(nativeReview?"Подтверждение сервера":"Будет выполнено"):"Отправленное действие",nativeReview&&stage.equals("review")?preview:form==null?"Действие":form.label(),"commands",0);
        if(stage.equals("review"))y=info("Проверьте условия",nativeReview?"Проверьте стоимость и условия. После подтверждения сервер выполнит действие. Назад — отменить.":"Проверьте параметры и условия действия. Подтверждение действует 30 секунд. Аддон повторно проверит условия.","check",y);
        else y=info("Ответ сервера",feedback.isEmpty()?"Ожидаем ответ. Сообщения также сохраняются в чате.":String.join("\n",feedback),"summary",y);
        int half=(span-18)/2;
        var edit=bodyButton("Изменить параметры",0,y,half,()->{if(!canAct())return;cancelReview();stage="form";notice="";resetScroll();changed();});edit.active=!busy&&form!=null;
        var execute=bodyPrimary(stage.equals("review")?"Подтвердить":"К списку действий",half+10,y,span-half-18,()->{
            if(!canAct())return;if(stage.equals("review")){String ticket=confirmation;if(ticket.isEmpty())return;confirmation="";notice="Выполняется…";request("confirm",object("confirmation",ticket));}else {resetScroll();list(false);}
        });execute.active=!busy&&(!stage.equals("review")||!confirmation.isEmpty());if(stage.equals("review"))execute.setTooltip(Tooltip.create(Component.literal("Команда: "+lastCommand)));
    }
    void receive(JsonObject b){
        String op=str(b,"op","");int request=integer(b,"request",0);
        if(op.equals("suggest")){if(request!=suggestRequest||!stage.equals("form")||integer(b,"field",-1)!=fieldIndex)return;var list=new ArrayList<String>();for(var e:b.getAsJsonArray("entries"))list.add(e.getAsString());suggestions=List.copyOf(list);changed();return;}
        if(request!=awaited)return;busy=false;
        if(b.has("theme"))client.lastTheme=b.getAsJsonObject("theme").deepCopy();
        switch(op){
            case "list"->{token=str(b,"token","");module=str(b,"module","");moduleLabel=str(b,"moduleLabel",module);page=integer(b,"page",0);pages=integer(b,"pages",1);var list=new ArrayList<JsonObject>();for(var e:b.getAsJsonArray("entries"))list.add(e.getAsJsonObject());entries=List.copyOf(list);stage="list";notice=integer(b,"count",0)+" доступно";}
            case "form"->{token=str(b,"token",token);module=str(b,"module",module);moduleLabel=str(b,"moduleLabel",moduleLabel);nativeReview=false;form=CommandForm.parse(b.getAsJsonObject("form"));id=str(b,"id","");usage=str(b,"usage","");commandTemplate=str(b,"command","");values.keySet().clear();fieldIndex=0;stage="form";notice=usage;suggestions=List.of();suggestRequest=-1;suggestDue=System.nanoTime()+200_000_000L;resetScroll();}
            case "review"->{nativeReview=bool(b,"native",false);confirmation=str(b,"confirmation","");preview=str(b,"command","");lastCommand=str(b,"executedCommand",nativeReview?commandTemplate:preview);stage="review";suggestDue=0;notice="Без подтверждения действие не выполняется.";resetScroll();}
            case "dispatched"->{stage="feedback";notice=str(b,"message","");feedback.clear();feedbackUntil=System.nanoTime()+8_000_000_000L;resetScroll();detachParent();}
            case "error"->{notice=str(b,"message","Ошибка");confirmation="";if(stage.equals("review"))stage="form";}
            default->{}
        }changed();
    }
    void feedback(Component message){if(System.nanoTime()>feedbackUntil)return;if(feedback.size()>=32)feedback.removeFirst();String text=message.getString();feedback.add(text.length()>2048?text.substring(0,2048):text);changed();}
    @Override public void tick(){
        long now=System.nanoTime();
        if(busy&&now>waitingUntil){busy=false;notice="Нет ответа сервера. Повторите запрос или обновите список.";confirmation="";if(stage.equals("review"))stage="form";changed();}
        if(queryDue>0&&now>=queryDue&&stage.equals("list")){queryDue=0;resetScroll();list(false);}
        if(suggestDue>0&&now>=suggestDue&&stage.equals("form")){suggestDue=0;request("suggest",object("id",id,"field",fieldIndex,"values",values));}
        synchronizeWidgets();
    }
    @Override public void onClose(){
        if(busy){busy=false;awaited=++sequence;}queryDue=0;suggestDue=0;suggestRequest=-1;resetScroll();
        if(stage.equals("review")){cancelReview();stage="form";notice="";changed();}
        else if(!stage.equals("list")){query="";page=0;list(false);}else if(!module.isEmpty()){module="";query="";page=0;list(false);}else returnToMenu();
    }
    private void cancelReview(){client.send(Kind.COMMANDS,object("op","cancel","token",token,"request",0));confirmation="";}
    private void detachParent(){if(parent instanceof MenuScreen m)m.detach();if(parent instanceof PersonalScreen p){p.launched();if(p.parent!=null)p.parent.detach();}parent=null;}
    private void returnToMenu(){
        returning=true;client.send(Kind.COMMANDS,object("op","close","token",token));
        if(parent instanceof MenuScreen m)m.suspended=false;if(parent instanceof PersonalScreen p)p.resume();minecraft.gui.setScreen(parent);
        if(parent==null){if(returnModule.isEmpty())client.request("hub");else client.send(Kind.COMMANDS,object("op","return","module",returnModule));}
    }
    @Override public void removed(){if(!returning){if(parent instanceof MenuScreen m)m.releaseSession();if(parent instanceof PersonalScreen p&&p.parent!=null)p.parent.releaseSession();}}
}
