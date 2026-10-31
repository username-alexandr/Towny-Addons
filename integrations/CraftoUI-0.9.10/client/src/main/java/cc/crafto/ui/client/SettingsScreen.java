package cc.crafto.ui.client;

/** Changes only local client preferences. Each setting has an explicit current value. */
final class SettingsScreen extends PanelScreen {
    final MenuScreen parent;private final CraftoUIClient client;private boolean returning;private int section;
    SettingsScreen(MenuScreen parent,CraftoUIClient client){super("Настройки CraftoUI",client);this.parent=parent;this.client=client;parent.suspended=true;}
    void resume(){returning=false;changed();}
    @Override protected void init(){
        begin(parent.resolvedTheme(),"Настройки интерфейса","Оформление и панель контрактов — только для вашего клиента","settings");
        tab("Оформление","theme",0,2,section==0,()->{section=0;resetScroll();changed();});tab("Панель контрактов","hud",1,2,section==1,()->{section=1;resetScroll();changed();});
        footer(this::onClose,"",null);var s=client.settings;int cols=columns(260,2),w=cellWidth(cols);
        if(section==0){
            setting("Масштаб меню","Размер основных меню и окон аддонов","scale",Math.round(s.scale*100)+"%",false,0,cols,w,0,()->{s.scale=s.scale>=1.25f?.75f:Math.min(1.25f,s.scale+.1f);s.save();changed();});
            setting("Тема оформления","Использовать цвета сервера или тёмную тему","theme",s.darkTheme?"Тёмная":"Сервер",s.darkTheme,1,cols,w,0,()->{s.darkTheme=!s.darkTheme;s.save();parent.reloadTheme();changed();});
            setting("Размытие фона","Эффект в основных меню и окнах аддонов","blur",s.blur?"Вкл.":"Выкл.",s.blur,2,cols,w,0,()->{s.blur=!s.blur;s.save();changed();});
            setting("Анимации","Плавное появление основных меню","animation",s.animations?"Вкл.":"Выкл.",s.animations,3,cols,w,0,()->{s.animations=!s.animations;s.save();changed();});
            status="Размер меню зависит от масштаба интерфейса в настройках игры";
        }else{
            int y=info("Панель во время игры","Здесь можно включить панель. В настройке ниже выберите задания, их порядок, размер и место на экране.","hud",0);
            setting("Показывать панель","Закреплённые задания поверх игрового мира","hud",s.contractsHud?"Вкл.":"Выкл.",s.contractsHud,0,cols,w,y,()->{s.contractsHud=!s.contractsHud;s.save();changed();});
            setting("Выбрать и расположить","Открыть понятную настройку заданий и панели","settings","Открыть",false,1,cols,w,y,()->{returning=true;minecraft.gui.setScreen(new PersonalScreen(this,client));});
            info("Что можно изменить","Отметьте нужные контракты, измените порядок стрелками и перетащите панель в удобное место. Настройки сохранятся автоматически.","orders",y+(2+cols-1)/cols*64+10);
        }finish();
    }
    private void setting(String title,String detail,String icon,String badge,boolean enabled,int index,int cols,int w,int start,Runnable action){card(title,detail,icon,"setting",enabled,index%cols*(w+10),start+index/cols*64,w,58,action).badge(badge);}
    @Override public void tick(){synchronizeWidgets();}
    @Override public void onClose(){returning=true;parent.suspended=false;minecraft.gui.setScreen(parent);}
    @Override public void removed(){if(!returning)parent.releaseSession();}
}
