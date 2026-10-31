package cc.crafto.ui.server;

import java.util.*;
import static cc.crafto.ui.server.CivicView.*;

/** Validated JDK-only responses. No client-supplied sum, material, town or command is used. */
final class ProcurementView {
    static final Set<String> STATES=Set.of("QUOTE","FUNDING","FUNDED","PUBLISHING","PUBLISHED","SETTLED","REJECTED","REVIEW","CANCELLED","REFUNDING","REFUNDED");
    record Offer(String project,int level,String building,String itemKey,String name,int amount,long cents,long duration){}
    record Order(UUID id,UUID town,UUID actor,Offer offer,long created,long expires,String state,String detail){
        String fingerprint(){return id+"/"+town+"/"+actor+"/"+offer.project()+"/"+offer.level()+"/"+offer.itemKey()+"/"+offer.amount()+"/"+offer.cents()+"/"+offer.duration()+"/"+created+"/"+expires;}
        boolean payable(long now){return state.equals("QUOTE")?now<=expires:Set.of("FUNDING","FUNDED","PUBLISHING").contains(state);}
        boolean cancellable(){return Set.of("QUOTE","FUNDED","PUBLISHING","REFUNDING").contains(state);}
        String actionTitle(){return switch(state){case "QUOTE"->"Оплатить заказ";case "FUNDING"->"Сверить и продолжить оплату";case "FUNDED"->"Опубликовать оплаченный заказ";case "PUBLISHING"->"Завершить публикацию";default->"Публикация недоступна";};}
        List<String> review(boolean cancel){return List.of(offer.building()+" · уровень "+offer.level(),offer.name()+": "+offer.amount()+" шт.",
                cancel?"Возврат оплаченного резерва в казну: "+DevelopmentView.money(state.equals("QUOTE")?0:offer.cents()):Set.of("FUNDED","PUBLISHING").contains(state)?"Уже оплачено из казны: "+DevelopmentView.money(offer.cents())+". Повторного списания не будет.":"Награда из городской казны: "+DevelopmentView.money(offer.cents()),
                "Срок заказа после публикации: "+duration(offer.duration()),
                cancel?"Отмена доступна до публикации. Подтверждённый резерв возвращается один раз; неоплаченная смета ничего не списывает.":"Поставка придёт на общий городской склад. Награда выплачивается из казны, когда игрок выполнит заказ. Условия после оплаты сохраняются.",
                "Текущее состояние: "+status(state));}
    }
    static Offer offer(Object raw,String project){var row=map(raw);String id=identifier(row,"project");if(!id.equals(project))throw invalid();String key=text(row,"itemKey",64);if(!key.matches("[a-f0-9]{64}"))throw invalid();return new Offer(id,(int)number(row,"targetLevel",1,1000),text(row,"building",160),key,text(row,"name",160),(int)number(row,"amount",1,1_000_000),number(row,"rewardCents",1,100_000_000_000L),number(row,"durationSeconds",60,31_536_000));}
    static List<Offer> offers(Object raw,String project){var out=new ArrayList<Offer>();var keys=new HashSet<String>();for(Object value:list(raw,45)){var offer=offer(value,project);if(!keys.add(offer.itemKey()))throw invalid();out.add(offer);}return List.copyOf(out);}
    static Order order(Object raw,UUID town,UUID expected){var row=map(raw);UUID id=uuid(row,"requestId"),owner=uuid(row,"town");if(!owner.equals(town)||expected!=null&&!id.equals(expected)||!id.equals(uuid(row,"contractId")))throw invalid();String state=text(row,"status",20);if(!STATES.contains(state))throw invalid();long created=number(row,"createdAt",1,Long.MAX_VALUE),expires=number(row,"expiresAt",created,Long.MAX_VALUE);String detail=text(row,"detail",400);return new Order(id,owner,uuid(row,"actor"),offer(row,identifier(row,"project")),created,expires,state,detail);}
    static List<Order> orders(Object raw,UUID town){var out=new ArrayList<Order>();var ids=new HashSet<UUID>();for(Object value:list(raw,20)){var order=order(value,town,null);if(!ids.add(order.id()))throw invalid();out.add(order);}return List.copyOf(out);}
    static CivicView offersPage(List<Offer> offers){var view=base("Материалы для ближайшего уровня","procurement-offers");view.lines.add("Фонд здания, городской склад и уже заказанные поставки учтены заново. Выберите материал для точной сметы.");for(var o:offers)view.rows.add(new Row(o.name(),o.building()+" · уровень "+o.level()+" · нужно "+o.amount()+" шт. · из казны "+DevelopmentView.money(o.cents()),new Action("procurement-quote",o.project(),o.itemKey())));if(offers.isEmpty())view.rows.add(Row.info("Материалы уже обеспечены","Учтены фонд здания, склад и открытые закупки."));requestsLink(view);return view;}
    static CivicView ordersPage(List<Order> orders,int page){var view=base("Закупки города · "+page,"procurement-requests");for(var o:orders)view.rows.add(new Row(o.offer().name(),o.offer().building()+" · "+o.offer().amount()+" шт. · "+DevelopmentView.money(o.offer().cents())+" · "+status(o.state()),new Action("procurement-order",o.id().toString())));if(orders.isEmpty())view.rows.add(Row.info("Закупок пока нет","В плане развития откройте материалы нужного здания."));if(page>1)view.rows.add(new Row("Предыдущие закупки","",new Action("procurement-page",Integer.toString(page-1))));if(orders.size()==20)view.rows.add(new Row("Следующие закупки","",new Action("procurement-page",Integer.toString(page+1))));return view;}
    static CivicView orderPage(Order o,long now){var view=base(o.offer().name(),"procurement-order");view.id=o.id().toString();view.lines.addAll(o.review(false));view.lines.add(o.detail());
        String payReason=o.payable(now)?"":o.state().equals("QUOTE")?"Смета истекла. Рассчитайте материалы заново.":status(o.state());
        view.rows.add(new Row(o.actionTitle(),o.state().equals("QUOTE")?"Выделить сумму из казны; после подтверждения оплаты можно опубликовать заказ":"Использовать сохранённую заявку; сначала отдельное подтверждение","",new Action("procurement-review-pay",o.id().toString()),payReason));
        view.rows.add(new Row("Отменить и вернуть резерв","Отдельное подтверждение; только до публикации","",new Action("procurement-review-cancel",o.id().toString()),o.cancellable()?"":o.state().equals("PUBLISHED")?"Заказ уже опубликован. Отмените его в городских контрактах.":status(o.state())));
        view.rows.add(new Row("Сверить состояние","Проверяет сохранённый ответ; не повторяет оплату",new Action("procurement-order",o.id().toString())));
        if(o.state().equals("PUBLISHED"))view.rows.add(new Row("Открыть городской контракт","Просмотреть ход поставки",new Action("open-contract",o.id().toString())));
        if(o.state().equals("QUOTE")&&now>o.expires())view.rows.add(new Row("Рассчитать материалы заново","Сумма и количество будут проверены снова",new Action("procurement-offers",o.offer().project())));
        requestsLink(view);return view;}
    static CivicView unavailable(String message){var view=base("Закупки пока недоступны","procurement-error");view.lines.add(message);view.lines.add("Если действие уже принято, его результат сохранён в закупках города. Неизвестный ответ не означает отказ оплаты.");requestsLink(view);return view;}
    private static CivicView base(String title,String route){var view=new CivicView();view.title=title;view.route=route;return view;}
    static void requestsLink(CivicView view){view.rows.add(new Row("Закупки города","Оплаченные заявки, сверка состояния и возвраты",new Action("procurement-page","1")));}
    static String status(String value){return switch(value){case "QUOTE"->"Ожидает вашего подтверждения";case "FUNDING"->"Казна подтверждает оплату";case "FUNDED"->"Оплачено, ожидает публикации";case "PUBLISHING"->"Публикация ожидает сверки";case "PUBLISHED"->"Опубликован в городских контрактах";case "SETTLED"->"Городской контракт закрыт";case "REJECTED"->"Списание отклонено";case "REVIEW"->"Оплата требует сверки администратора";case "CANCELLED"->"Отменено до оплаты";case "REFUNDING"->"Казна подтверждает возврат";case "REFUNDED"->"Резерв возвращён городу";default->throw invalid();};}
    static String duration(long seconds){if(seconds%86400==0)return seconds/86400+" д.";if(seconds%3600==0)return seconds/3600+" ч.";if(seconds%60==0)return seconds/60+" мин.";return seconds+" сек.";}
    private static Map<?,?> map(Object value){if(!(value instanceof Map<?,?> row))throw invalid();return row;}
    private static List<?> list(Object value,int max){if(!(value instanceof List<?> rows)||rows.size()>max)throw invalid();return rows;}
    private static String text(Map<?,?> row,String key,int max){if(!(row.get(key) instanceof String text)||text.length()>max||!key.equals("detail")&&text.isBlank())throw invalid();return text;}
    private static String identifier(Map<?,?> row,String key){String value=text(row,key,80);if(!value.matches("[a-z0-9_-]{1,80}"))throw invalid();return value;}
    private static long number(Map<?,?> row,String key,long min,long max){Object raw=row.get(key);if(!(raw instanceof Long)&&!(raw instanceof Integer))throw invalid();long n=((Number)raw).longValue();if(n<min||n>max)throw invalid();return n;}
    private static UUID uuid(Map<?,?> row,String key){return UUID.fromString(text(row,key,36));}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Данные закупки изменились или имеют неподдерживаемый формат. Обновите экран.");}
}
