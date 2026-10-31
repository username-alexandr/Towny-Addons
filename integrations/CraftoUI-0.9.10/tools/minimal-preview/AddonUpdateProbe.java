package cc.crafto.ui.client;

import cc.crafto.ui.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import java.nio.file.*;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

/** Representative items from Suite 0.52.1. Real AddonScreen, explicit inventory/transport adapters. */
public final class AddonUpdateProbe {
    private record Entry(String plugin,String holder,String title,int rows,int back,int count){}
    private static int checks;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    private static final List<Entry> ENTRIES=List.of(
        new Entry("NeverLandTownyBuilds","ru.neverland.townybuilds.construction.CityStyles$View","Стиль города",3,-1,3),
        new Entry("NeverLandTownyContracts","ru.neverland.mintcontracts.orders.ResourceAdmin$Screen","Серверный склад материалов",6,49,40),
        new Entry("NeverLandTownyContracts","ru.neverland.mintcontracts.service.BuildOrders$View","Материалы для здания",6,49,38),
        new Entry("NeverLandTownyCompanies","ru.neverland.townycompanies.CompanyPayroll$View","Зарплаты компании",6,49,7),
        new Entry("NeverLandTownyChronicles","ru.neverland.townychronicles.service.ContributionsMenu$View","Вклад жителей • NeverLand",6,49,44),
        new Entry("NeverLandTownySiegesPlus","ru.neverland.townysieges.RaidHours$View","Часы нападения • начало",6,-1,24),
        new Entry("NeverLandTownySiegesPlus","ru.neverland.townysieges.RaidRepairs$View","Ремонт городского здания",3,22,2),
        new Entry("NeverLandTownySiegesPlus","ru.neverland.townysieges.CampSupplies$View","Снабжение осадного лагеря",3,22,2),
        new Entry("NeverLandTownySiegesPlus","ru.neverland.townysieges.RaidCamps$View","Осадный лагерь",3,22,3),
        new Entry("NeverLandTownyPopulation","ru.neverland.townypopulation.gui.CohortMenu$View","Жители и районы",6,45,36),
        new Entry("NeverLandTownySpecialization","ru.neverland.townyspecialization.production.ProductionMenu$View","Городское производство",6,45,36),
        new Entry("NeverLandTownyResearch","ru.neverland.townyresearch.licenses.LicenseMenu$View","Технологические лицензии",6,45,36),
        new Entry("NeverLandTownyEnvironment","ru.neverland.townyenvironment.gui.DistrictEnvironmentMenu$View","Экология районов",6,45,36),
        new Entry("NeverLandTownyJobs","ru.neverland.townyjobs.mastery.MasteryService$View","Мастерские заказы",6,45,36),
        new Entry("NeverLandTownyMarket","ru.neverland.townymarket.personal.LimitService$View","Личные заявки рынка",6,45,36),
        new Entry("NeverLandTownyTreasuryPlus","ru.neverland.townytreasury.gui.AsyncDebitMenu$View","Квитанции городских платежей",6,45,36),
        new Entry("NeverLandTownyCamps","ru.neverland.mintcamps.gui.MenuManager$GuestView","Гости лагеря",6,45,36),
        new Entry("NeverLandTownyArchaeology","ru.neverland.archaeology.restoration.RestorationMenu$View","Реставрация находок",6,45,36),
        new Entry("NeverLandTownyEvents","ru.neverland.mintevents.scenarios.ScenarioMenus$Screen","Городское происшествие",6,45,36),
        new Entry("NeverLandTownyQuests","ru.neverland.townyquests.cooperative.CooperativeMenu$View","Общие стройки",6,45,36),
        new Entry("NeverLandTownyChronicles","ru.neverland.townychronicles.chapters.ChapterMenu$View","Главы истории города",6,45,36),
        new Entry("NeverLandTownySeasons","ru.neverland.townyseasons.markets.SeasonMarketsMenu$Holder","Сезонные обмены",6,45,36),
        new Entry("NeverLandTownyCitizens","ru.neverland.townycitizens.AdmissionsController$Page","Заявления в гражданство",6,45,36),
        new Entry("NeverLandTownyCouncil","ru.neverland.townycouncil.DelegationMenus$View","Поручения и расходы",6,45,36),
        new Entry("NeverLandTownyCouncil","ru.neverland.townycouncil.spending.SpendingMenus$View","Поручения и расходы",6,45,36),
        new Entry("NeverLandTownyElections","ru.neverland.townyelections.ElectionProgramsMenu$View","Программы кандидатов",6,45,36),
        new Entry("NeverLandTownyDiplomacy","ru.neverland.townydiplomacy.aid.AidMenus$Holder","Договоры взаимопомощи",6,45,36),
        new Entry("NeverLandTownyCrime","ru.neverland.townycrime.CrimePatrolMenu$View","Патрули города",6,45,36),
        new Entry("NeverLandTownyJustice","ru.neverland.townyjustice.JusticeDossiersMenu$Holder","Материалы судебных дел",6,45,36),
        new Entry("NeverLandTownyArmy","ru.neverland.townyarmy.tactics.TacticsMenus$Page","Тактика армии",6,45,36));
    private static ChestMenu items(Entry entry){
        var menu=new ChestMenu(entry.rows());
        for(int i=0;i<entry.count();i++){
            int slot=entry.title().startsWith("Стиль")?11+i*2:entry.title().startsWith("Часы")?9+i:entry.title().startsWith("Зарплаты")?10+i:entry.title().startsWith("Ремонт")||entry.title().startsWith("Снабжение")?11+i*4:i;
            String title=entry.title().startsWith("Стиль")?List.of("Средневековье","Современный","Фэнтези").get(i):entry.title().startsWith("Часы")?String.format("Начало %02d:00",i):entry.title().startsWith("Вклад")?"Житель "+(i+1):"Пункт "+(i+1);
            String icon=entry.title().startsWith("Стиль")?List.of("stone_bricks","quartz_block","amethyst_block").get(i):entry.title().startsWith("Часы")?"clock":"emerald";
            menu.getSlot(slot).item=new ItemStack("minecraft:"+icon,title,List.of("Данные действующего аддона",entry.title(),"Материалы, условия и суммы проверяет сервер"));
        }
        if(entry.back()>=0)menu.getSlot(entry.back()).item=new ItemStack("minecraft:arrow","Назад",List.of("Предыдущий раздел"));
        return menu;
    }
    private static void run(Entry entry,String theme,int w,int h)throws Exception{
        var client=MinimalProbe.client(theme);var menu=items(entry);var roles=new JsonObject();if(entry.back()>=0)roles.addProperty("back",entry.back());
        var options=object("viewKey",entry.plugin()+"/"+entry.holder()+"/"+entry.title(),"module",entry.title(),"commandModule",entry.plugin(),"navigation","verified-back", "backLabel","Назад","theme",client.lastTheme,"width",620,"height",420,"columns",3,"layout","cards",
            "presentation",object("roles",roles,"auxiliary",new JsonArray(),"hidden",new JsonArray(),"disabled",new JsonObject()));
        Minecraft.getInstance().player.containerMenu=menu;Minecraft.getInstance().gameMode.clicks.clear();
        var screen=new AddonScreen(client,new ContainerScreen(menu,new Object(),Component.literal(entry.title())),options);MinimalProbe.size(screen,w,h);MinimalProbe.draw(screen);Minecraft.getInstance().gui.setScreen(screen);
        var layout=(AddonLayout)MinimalProbe.field(screen,"layout");
        check(layout.width()*layout.scale()<=w&&layout.height()*layout.scale()<=h,"new menu remains inside GUI");
        check(!(Boolean)MinimalProbe.field(screen,"inventoryMode"),"new readonly menu uses custom controls");
        check(((List<?>)MinimalProbe.field(screen,"slots")).size()==entry.count(),"no addon control is dropped or merged");
        var found=new HashSet<Integer>();int pages=(Integer)MinimalProbe.attr(screen,"pages");
        for(int page=0;page<pages;page++){
            MinimalProbe.draw(screen);var cards=(List<?>)MinimalProbe.field(screen,"cards");check(!cards.isEmpty(),"every local page has visible controls");
            for(var card:cards){int slot=(Integer)MinimalProbe.attr(card,"slot");found.add(slot);check(menu.getSlot(slot).getItem()!=ItemStack.EMPTY,"card maps to a real slot");}
            var card=cards.getFirst();int slot=(Integer)MinimalProbe.attr(card,"slot");Object original=menu.getSlot(slot).getItem();
            MinimalProbe.set(screen,"lastClick",0L);MinimalProbe.click(screen,(Integer)MinimalProbe.attr(card,"x"),(Integer)MinimalProbe.attr(card,"y"),0,0);
            check(Minecraft.getInstance().gameMode.clicks.getLast().slot()==slot,"click preserves the original addon raw slot");
            check(menu.getSlot(slot).getItem()==original,"client never replaces the addon item");
            MinimalProbe.draw(screen);if(page+1<pages){screen.keyPressed(new KeyEvent(267));MinimalProbe.draw(screen);}
        }
        check(found.size()==entry.count(),"every addon control remains reachable across local pages");
        screen.keyPressed(new KeyEvent(259));check(str(client.sent.getLast(),"navigation","").equals("verified-back"),"Back uses the server navigation token");
        screen.keyPressed(new KeyEvent(256));check(Minecraft.getInstance().gui.screen()==null,"Esc closes the native container");
        MinimalProbe.set(screen,"page",0);MinimalProbe.draw(screen);
        MinimalProbe.save(screen,"addons-"+theme+"-"+entry.holder().substring(entry.holder().lastIndexOf('.')+1).replace('$','-')+"-"+w+"x"+h);
    }
    public static void main(String[] args)throws Exception{
        MinimalProbe.out=Path.of(args[0]);MinimalProbe.resources=Path.of(args[1]);
        for(String theme:List.of("classic","dark"))for(int[] size:new int[][]{{829,563},{520,340},{320,240},{320,180}})for(var entry:ENTRIES)run(entry,theme,size[0],size[1]);
        System.out.println("PASS "+checks+" addon update renderer/input checks; "+MinimalProbe.renders+" previews; production AddonScreen with representative items and explicit UI/network adapters");
    }
}
