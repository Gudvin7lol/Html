package ru.tvcustom.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Network;
import android.os.Bundle;
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.content.pm.ApplicationInfo;
import java.util.*;
import java.text.SimpleDateFormat;

public final class MainActivity extends Activity {
    private static final int ACCENT=0xFFF1AF91, WHITE=0xFFF1F3F6, MUTED=0xFFABB7C5;
    private static final int SURFACE=0xE8202631, SURFACE2=0xF1222C39;
    private static final int PANEL_BORDER=0x5B5B6577;
    private final ArrayList<TvApp> apps=new ArrayList<>();
    private final ArrayList<View> nav=new ArrayList<>();
    private final ArrayList<TextView> navLabels=new ArrayList<>();
    private SharedPreferences preferences;
    private FrameLayout panel;
    private SceneView background;
    private TextView statusClock, statusVpn;
    private final android.os.Handler indicatorHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable indicatorTicker = new Runnable() {
        @Override public void run() { refreshIndicators(); indicatorHandler.postDelayed(this, 60000); }
    };
    private int page=0;
    private boolean pendingYouTube=false;
    private long vpnRequestAt=0;
    private String appFilter="all";
    private String searchQuery="";
    private String selectedPackage="";
    private int knownAppCount=0;
    private boolean recentNeedsRefresh=false;
    private View rootView;
    private FrameLayout quickOverlay;
    private View focusBeforeOverlay;
    private ScrollView currentScroll;
    private int lastHomeScrollY=0;
    private boolean packageListDirty=false;
    private final BroadcastReceiver packageUpdates = new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            packageListDirty=true;
        }
    };
    private Bitmap customPoster;
    private static final int REQ_POSTER=439;
    private static final int REQ_EXPORT=440, REQ_IMPORT=441;
    private int lastVisitedPage=0;

    static class TvApp {
        String pkg,label; Drawable icon,banner; Intent intent;
        TvApp(String p,String l,Drawable d,Drawable b,Intent i){pkg=p;label=l;icon=d;banner=b;intent=i;}
    }

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        preferences=getSharedPreferences("tv_custom",MODE_PRIVATE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        refreshApps(); knownAppCount=apps.size(); build();
        IntentFilter packages=new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packages.addDataScheme("package");
        registerReceiver(packageUpdates,packages);
    }
    @Override protected void onResume() {
        super.onResume();
        indicatorHandler.removeCallbacks(indicatorTicker);
        indicatorHandler.post(indicatorTicker);
        if(panel==null)return;
        // Rebuilding all app icons on every return from a player causes lag on low-memory TVs.
        // Installed apps are re-queried only if a package change broadcast was received.
        boolean updated=packageListDirty;
        if(updated){packageListDirty=false;refreshApps();}
        if(pendingYouTube){
            boolean isFresh=android.os.SystemClock.elapsedRealtime()-vpnRequestAt<120000;
            pendingYouTube=false;
            if(isFresh && vpnConnected()){
                TvApp target=youtubeApp();
                if(target!=null){openDirect(target);return;}
            }
        }
        if(updated || (recentNeedsRefresh && page==0)){
            knownAppCount=apps.size();
            recentNeedsRefresh=false;
            if(page==0){
                View focus=getCurrentFocus();
                // Resume with visual focus restored instead of jumping to the sidebar.
                if(focus!=null && focus.getTag() instanceof String)
                    selectedPackage=(String)focus.getTag();
                if(currentScroll!=null)lastHomeScrollY=currentScroll.getScrollY();
            }
            render(page);
        }
    }
    @Override protected void onPause() {
        indicatorHandler.removeCallbacks(indicatorTicker);
        super.onPause();
    }
    @Override protected void onDestroy(){
        indicatorHandler.removeCallbacks(indicatorTicker);
        try{unregisterReceiver(packageUpdates);}catch(Exception ignored){}
        if(customPoster!=null && !customPoster.isRecycled()){
            customPoster.recycle(); customPoster=null;
        }
        super.onDestroy();
    }
    private void refreshIndicators() {
        if(statusClock!=null) statusClock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
        if(statusVpn!=null) {
            boolean on=vpnConnected();
            statusVpn.setText(on?"VPN включён":"VPN отключён");
            statusVpn.setTextColor(on?0xFF8CE5B4:WHITE);
        }
    }
    @Override public boolean onKeyDown(int keyCode,KeyEvent e) {
        if((keyCode==KeyEvent.KEYCODE_MENU || keyCode==KeyEvent.KEYCODE_SETTINGS)
            && e.getRepeatCount()==0) {
            toggleQuickPanel();
            return true;
        }
        return super.onKeyDown(keyCode,e);
    }
    @Override public void onBackPressed() {
        if(quickOverlay!=null){ closeQuickPanel();return; }
        if(page==7){ switchTo(5); return; }
        if(page==6) { switchTo(lastVisitedPage); return; }
        if(page!=0) { switchTo(0); return; }
        super.onBackPressed();
    }
    private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+0.5f);}
    private LinearLayout col(){ LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l; }
    private LinearLayout row(){ LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l; }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private GradientDrawable bg(int color,boolean outlined,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        if(outlined)d.setStroke(dp(2),ACCENT);return d;
    }
    private void gap(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private GradientDrawable glass(int color,boolean focus,int radius) {
        int[] stops;
        if(color==0xFFF0F2F6) {
            stops=focus?new int[]{0xFFFFFFFF,0xFFE4E9F2}:new int[]{0xFFFAFBFE,0xFFE4E9EF};
        }else{
            stops=focus?new int[]{0xF34D3633,0xF5323540}:new int[]{color,0xF1141B27};
        }
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,stops);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(focus?2:1),focus?ACCENT:PANEL_BORDER);
        return d;
    }
    private void selectable(View v,int color) {
        v.setFocusable(true);v.setFocusableInTouchMode(false);
        v.setBackground(glass(color,false,13));
        v.setOnFocusChangeListener((view,focused)->{
            view.setBackground(glass(color,focused,13));
            if(preferences.getBoolean("motion",true)) {
                view.animate().scaleX(focused?1.025f:1f).scaleY(focused?1.025f:1f)
                    .setDuration(110).start();
            }else{view.setScaleX(1f);view.setScaleY(1f);}
            view.setElevation(dp(focused?5:0));
        });
    }
    // Nav targets differ from indices because movies, series and television are
    // three entry points into available installed media services, not fake catalogs.
    private static final int[] TV_NAV={0,1,8,9,2,3,5};
    private void build(){
        FrameLayout root=new FrameLayout(this);
        background=new SceneView(this);
        background.setScenic(preferences.getBoolean("scenic",true));
        root.addView(background,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout main=row();main.setGravity(Gravity.TOP);
        main.setPadding(dp(12),dp(8),dp(10),dp(7));
        root.addView(main,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout side=col();
        side.setPadding(dp(12),dp(14),dp(11),dp(8));
        main.addView(side,new LinearLayout.LayoutParams(dp(184),-1));
        TextView logo=text("xiaomi TV",24,WHITE,true);
        logo.setLetterSpacing(.09f);
        side.addView(logo,new LinearLayout.LayoutParams(-1,dp(60)));
        String[] menu={"Главная","Фильмы","Сериалы","ТВ","Приложения","HDMI","Настройки"};
        int[] glyphs={0,1,1,3,2,3,5};
        for(int i=0;i<menu.length;i++){
            final int target=TV_NAV[i];
            LinearLayout item=row();item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(10),0,dp(4),0);
            item.setFocusable(true);item.setFocusableInTouchMode(false);
            TvIcon icon=new TvIcon(this,glyphs[i],i==0?WHITE:0xFFA7C4DC);
            LinearLayout.LayoutParams il=new LinearLayout.LayoutParams(dp(21),dp(21));
            il.rightMargin=dp(14);item.addView(icon,il);
            TextView label=text(menu[i],14,i==0?WHITE:0xFFB0BCCC,i==0);
            navLabels.add(label);
            item.addView(label,new LinearLayout.LayoutParams(0,-1,1));
            item.setContentDescription(menu[i]);
            item.setOnClickListener(v->switchTo(target));
            item.setOnFocusChangeListener((v,focused)->{
                boolean active=page==target;
                v.setBackground(navBackground(active,focused));
                if(preferences.getBoolean("motion",true))
                    v.animate().scaleX(focused?1.016f:1f).scaleY(focused?1.016f:1f).setDuration(100).start();
            });
            side.addView(item,new LinearLayout.LayoutParams(-1,dp(46)));
            gap(side,compactNavGap());
            nav.add(item);
        }
        side.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
        TextView signature=text("MI TV  •  ANDROID 11",10,0xFF7D94AD,false);
        side.addView(signature,new LinearLayout.LayoutParams(-1,dp(32)));

        panel=new FrameLayout(this);
        main.addView(panel,new LinearLayout.LayoutParams(0,-1,1));
        rootView=root;
        setContentView(root);
        render(0);
        nav.get(0).requestFocus();
    }
    private int compactNavGap(){return 2;}
    private GradientDrawable navBackground(boolean active,boolean focus){
        GradientDrawable drawable=new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            active?new int[]{0xDA563B33,0xC2252832}:
            focus?new int[]{0xC52B3544,0xA21A2737}:new int[]{0x280A1A2D,0x0F0A1725});
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(active||focus?2:1),
            active||focus?0xFFEFB38E:0x225A7389);
        return drawable;
    }
    private void switchTo(int to){
        closeQuickPanel();
        if(page==0 && currentScroll!=null)lastHomeScrollY=currentScroll.getScrollY();
        if((to==6 || to==7) && page!=6 && page!=7)lastVisitedPage=page;
        pendingYouTube=false;
        page=to;
        render(to);
        for(int i=0;i<TV_NAV.length;i++){
            if(TV_NAV[i]==to){nav.get(i).requestFocus();break;}
        }
    }
    private void highlightPage(){
        for(int i=0;i<nav.size();i++){
            boolean chosen=TV_NAV[i]==page;
            nav.get(i).setBackground(navBackground(chosen,false));
            TextView label=navLabels.get(i);
            label.setTypeface(Typeface.DEFAULT,chosen?Typeface.BOLD:Typeface.NORMAL);
            label.setTextColor(chosen?WHITE:0xFFB0BCCC);
        }
    }
    private void render(int to){
        if(panel==null)return;
        highlightPage();
        panel.removeAllViews();
        ScrollView sc=new ScrollView(this);
        currentScroll=sc;
        sc.setFillViewport(true);
        sc.setVerticalScrollBarEnabled(false);sc.setClipToPadding(false);
        panel.addView(sc,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout c=col();c.setPadding(dp(3),dp(1),dp(1),dp(3));sc.addView(c);
        switch(to){
          case 1: streaming(c);break;
          case 8: streaming(c);break;
          case 9: streaming(c);break;
          case 2: allApps(c);break;
          case 3: hdmi(c);break;
          case 4: vpnPage(c);break;
          case 5: settings(c);break;
          case 6: controlCenter(c);break;
          case 7: diagnostics(c);break;
          default:home(c);break;
        }
        wireRemoteFocus(to);
        if(to==0 && lastHomeScrollY>0){
            int offset=lastHomeScrollY;
            sc.post(()->sc.scrollTo(0,offset));
        }
    }
    private void wireRemoteFocus(int currentPage){
        View first=findFirstFocusable(panel);
        int navPage=0;
        for(int i=0;i<TV_NAV.length;i++)if(TV_NAV[i]==currentPage)navPage=i;
        if(first==null || navPage<0)return;
        View activeNav=nav.get(navPage);
        if(first.getId()==View.NO_ID)first.setId(View.generateViewId());
        if(activeNav.getId()==View.NO_ID)activeNav.setId(View.generateViewId());
        for(View n:nav){
            if(n.getId()==View.NO_ID)n.setId(View.generateViewId());
            n.setNextFocusRightId(first.getId());
        }
        // Left from the first content control returns to the active section.
        first.setNextFocusLeftId(activeNav.getId());
    }
    private View findFirstFocusable(View view){
        if(view==null || view.getVisibility()!=View.VISIBLE)return null;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                View candidate=findFirstFocusable(group.getChildAt(i));
                if(candidate!=null)return candidate;
            }
        }
        return view.isFocusable()?view:null;
    }
    private void title(LinearLayout parent,String t,String subtitle){
        parent.addView(text(t,26,WHITE,true));gap(parent,4);
        if(subtitle!=null)parent.addView(text(subtitle,13,MUTED,false));
        gap(parent,16);
    }
    private void home(LinearLayout c){
        float sh=getResources().getDisplayMetrics().heightPixels/
            getResources().getDisplayMetrics().density;
        float sw=getResources().getDisplayMetrics().widthPixels/
            getResources().getDisplayMetrics().density;
        boolean compact=sh<690;
        FrameLayout hero=new FrameLayout(this);
        hero.setBackground(glass(0xFA0C1421,false,17));
        hero.setClipToOutline(true);
        Bitmap poster=loadPoster();
        if(poster!=null){
            ImageView bg=new ImageView(this);
            bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            bg.setImageBitmap(poster);
            hero.addView(bg,new FrameLayout.LayoutParams(-1,-1));
        }else hero.addView(new HeroScene(this),new FrameLayout.LayoutParams(-1,-1));
        View gradient=new View(this);
        gradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xF60A111E,0xE6081120,0xAF0B1120,0x1A08101B}));
        hero.addView(gradient,new FrameLayout.LayoutParams(-1,-1));

        // Cinematic typography and controls over the entire hero, never a separate dock.
        LinearLayout content=col();
        content.setPadding(dp(compact?22:27),dp(compact?63:76),dp(12),dp(10));
        hero.addView(content,new FrameLayout.LayoutParams(-1,-1));
        TextView eyebrow=text("ТВОЙ ДОМАШНИЙ КИНОТЕАТР",compact?11:12,WHITE,true);
        eyebrow.setLetterSpacing(.12f);
        content.addView(eyebrow);gap(content,compact?5:9);
        TextView headline=text("К И Н О",compact?52:60,0xFFFFC293,false);
        headline.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        headline.setLetterSpacing(.08f);
        headline.setShadowLayer(dp(12),0,0,0xCCF9A06B);
        content.addView(headline);
        gap(content,compact?0:2);
        TextView sub=text("ФИЛЬМЫ  ·  СЕРИАЛЫ  ·  ТВ",compact?12:13,0xFFE9D6C9,false);
        sub.setLetterSpacing(.10f);content.addView(sub);
        gap(content,compact?9:14);
        content.addView(text("Любимые истории и установленные сервисы.\nВсё под рукой, без рекламной ленты.",
            compact?12:13,0xFFD8DAE4,false));
        gap(content,compact?11:17);
        LinearLayout controls=row();
        pill(controls,"Смотреть",this::openBestVideo);
        pill(controls,"Приложения",()->switchTo(2));
        content.addView(controls);

        // Status controls belong to the image top edge, as on the reference.
        LinearLayout statusBar=row();
        statusBar.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        FrameLayout.LayoutParams sb=new FrameLayout.LayoutParams(dp(211),dp(41),Gravity.RIGHT|Gravity.TOP);
        sb.topMargin=dp(8);sb.rightMargin=dp(13);
        hero.addView(statusBar,sb);
        FrameLayout search=heroTopButton(9,"Найти приложение",()->{appFilter="search";switchTo(2);});
        FrameLayout settings=heroTopButton(5,"Настройки",()->switchTo(5));
        FrameLayout wifi=heroTopButton(6,"Параметры Wi-Fi",()->trySettings(Settings.ACTION_WIFI_SETTINGS));
        statusBar.addView(search,new LinearLayout.LayoutParams(dp(34),dp(34)));
        statusBar.addView(settings,new LinearLayout.LayoutParams(dp(34),dp(34)));
        statusBar.addView(wifi,new LinearLayout.LayoutParams(dp(34),dp(34)));
        statusClock=text(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()),14,WHITE,false);
        statusClock.setGravity(Gravity.CENTER);
        statusBar.addView(statusClock,new LinearLayout.LayoutParams(dp(70),dp(35)));

        LinearLayout vpn=row();
        vpn.setGravity(Gravity.CENTER_VERTICAL);
        vpn.setPadding(dp(10),0,dp(8),0);
        selectable(vpn,0xF0192636);
        TvIcon shield=new TvIcon(this,4,vpnConnected()?0xFF5FF5A3:ACCENT);
        LinearLayout.LayoutParams si=new LinearLayout.LayoutParams(dp(22),dp(22));
        si.rightMargin=dp(8);vpn.addView(shield,si);
        statusVpn=text(vpnConnected()?"VPN включён":"VPN отключён",12,WHITE,true);
        statusVpn.setMaxLines(1);
        vpn.addView(statusVpn,new LinearLayout.LayoutParams(0,-1,1f));
        vpn.setOnClickListener(v->openVpn());
        FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(dp(155),dp(43),Gravity.RIGHT|Gravity.TOP);
        vp.topMargin=dp(50);vp.rightMargin=dp(18);
        hero.addView(vpn,vp);

        int heroHeight=(int)Math.min(380,Math.max(330,sh*.55f));
        c.addView(hero,new LinearLayout.LayoutParams(-1,dp(heroHeight)));
        gap(c,8);
        List<TvApp> favourites=favoriteApps();
        boolean pinned=!favourites.isEmpty();
        homeSection(c,pinned?"Избранные приложения":"Ваши приложения",()->switchTo(2));
        appStrip(c,pinned?favourites:forHome(),10);
        gap(c,8);
        ArrayList<TvApp> recent=recentApps();
        List<TvApp> media=homeMediaApps();
        if(!recent.isEmpty()&&preferences.getBoolean("show_recent",true)){
            homeSection(c,"Недавно открытые",()->switchTo(2));
            cinemaStrip(c,recent,media);
        }else{
            homeSection(c,"Смотреть и слушать",()->switchTo(1));
            cinemaStrip(c,media,Collections.emptyList());
        }
    }
    private FrameLayout heroTopButton(int glyph,String desc,Runnable action){
        FrameLayout b=new FrameLayout(this);
        b.setContentDescription(desc);
        selectable(b,0x240B1826);
        TvIcon i=new TvIcon(this,glyph,0xFFDBE4EC);
        b.addView(i,new FrameLayout.LayoutParams(dp(18),dp(18),Gravity.CENTER));
        b.setOnClickListener(v->action.run());return b;
    }
    private void homeSection(LinearLayout parent,String heading,Runnable open){
        LinearLayout bar=row();
        TextView name=text(heading,15,WHITE,true);
        bar.addView(name,new LinearLayout.LayoutParams(0,dp(23),1));
        TextView more=text("Все  ›",11,0xFFC7D2DF,false);
        more.setGravity(Gravity.CENTER);
        more.setOnClickListener(v->open.run());more.setFocusable(true);
        bar.addView(more,new LinearLayout.LayoutParams(dp(57),dp(23)));
        parent.addView(bar,new LinearLayout.LayoutParams(-1,dp(23)));
        gap(parent,5);
    }
    private List<TvApp> homeMediaApps(){
        ArrayList<TvApp> media=new ArrayList<>();
        for(TvApp a:forHome())if(isMedia(a))media.add(a);
        return media;
    }
    private void cinemaStrip(LinearLayout parent,List<TvApp> first,List<TvApp> fallback){
        ArrayList<TvApp> chosen=new ArrayList<>();
        for(TvApp a:first)if(chosen.size()<4 && !chosen.contains(a))chosen.add(a);
        for(TvApp a:fallback)if(chosen.size()<4 && !chosen.contains(a))chosen.add(a);
        LinearLayout row=row();
        row.setPadding(dp(2),0,dp(2),dp(2));
        int k=0;
        for(TvApp a:chosen){cinemaCard(row,a.label,a.banner,a.icon,
            ()->{if(isYouTube(a))launchYouTube();else openDirect(a);});k++;}
        if(k<4 && youtubeApp()!=null &&
                chosen.stream().noneMatch(a->isYouTube(a))){
            TvApp youtube=youtubeApp();
            cinemaCard(row,"YouTube",youtube.banner,youtube.icon,this::launchYouTube);k++;
        }
        if(k<4){cinemaCard(row,"Кино и ТВ",null,null,()->switchTo(1));k++;}
        if(k<4){cinemaCard(row,"Все приложения",null,null,()->switchTo(2));k++;}
        if(k<4){cinemaCard(row,"Smart VPN",null,null,this::openVpn);k++;}
        parent.addView(row,new LinearLayout.LayoutParams(-1,dp(117)));
    }
    private void cinemaCard(LinearLayout parent,String label,Drawable banner,Drawable icon,
            Runnable action){
        FrameLayout tile=new FrameLayout(this);
        tile.setBackground(glass(0xEE1A2637,false,13));
        tile.setClipToOutline(true);
        tile.setFocusable(true);tile.setFocusableInTouchMode(false);
        tile.setContentDescription(label);
        if(banner!=null){
            ImageView art=new ImageView(this);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageDrawable(banner);
            tile.addView(art,new FrameLayout.LayoutParams(-1,-1));
        }else tile.addView(new CardArtView(this,0xFF344A66),new FrameLayout.LayoutParams(-1,-1));
        if(icon!=null){
            ImageView appIcon=new ImageView(this);
            appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);appIcon.setImageDrawable(icon);
            FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(dp(38),dp(38),
                Gravity.CENTER);
            ap.bottomMargin=dp(15);tile.addView(appIcon,ap);
        }
        View vignette=new View(this);
        vignette.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x00101824,0x350D141F,0xEE090E18}));
        tile.addView(vignette,new FrameLayout.LayoutParams(-1,-1));
        TextView title=text(label,13,WHITE,true);
        title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(10),0,dp(6),dp(3));
        tile.addView(title,new FrameLayout.LayoutParams(-1,dp(31),Gravity.BOTTOM));
        tile.setForeground(focusStroke(false));
        tile.setOnFocusChangeListener((v,f)->tile.setForeground(focusStroke(f)));
        tile.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
        lp.setMargins(0,0,dp(8),0);
        parent.addView(tile,lp);
    }
    private void quickHomeAction(LinearLayout row,int icon,String title,String subtitle,Runnable action){
        LinearLayout item=new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(12),0,dp(10),0);
        selectable(item,0xED172131);
        TvIcon glyph=new TvIcon(this,icon,ACCENT);
        LinearLayout.LayoutParams gl=new LinearLayout.LayoutParams(dp(21),dp(21));
        gl.rightMargin=dp(11);item.addView(glyph,gl);
        LinearLayout labels=col();
        labels.addView(text(title,14,WHITE,true));
        TextView hint=text(subtitle,10,MUTED,false);
        hint.setSingleLine(true);hint.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(hint);
        item.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        item.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
        lp.setMargins(0,0,dp(9),0);row.addView(item,lp);
    }
    private LinearLayout homeAppDock(int height){
        LinearLayout outer=col();
        outer.setPadding(dp(13),dp(13),dp(13),dp(10));
        outer.setBackground(glass(0xF61B2634,false,18));
        outer.setClipToOutline(true);
        LinearLayout h=row();
        TextView title=text("Все приложения",16,WHITE,true);
        h.addView(title,new LinearLayout.LayoutParams(0,dp(32),1));
        TextView all=text("Все  ›",12,ACCENT,true);
        all.setGravity(Gravity.CENTER);selectable(all,0x912A3544);
        all.setOnClickListener(v->switchTo(2));
        h.addView(all,new LinearLayout.LayoutParams(dp(59),dp(30)));
        outer.addView(h);gap(outer,7);

        List<TvApp> show=forHome();
        int max=Math.min(show.size(),9);
        for(int line=0;line<3;line++){
            LinearLayout r=row();
            r.setGravity(Gravity.CENTER_VERTICAL);
            for(int j=0;j<3;j++){
                int index=line*3+j;
                if(index<max)miniApp(r,show.get(index));
                else{
                    View empty=new View(this);
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(61),1);
                    lp.setMargins(0,0,dp(6),0);r.addView(empty,lp);
                }
            }
            outer.addView(r,new LinearLayout.LayoutParams(-1,0,1));
            if(line<2)gap(outer,5);
        }
        return outer;
    }
    private void miniApp(LinearLayout parent,TvApp app){
        FrameLayout tile=new FrameLayout(this);
        tile.setBackground(glass(cardColor(app),false,11));
        tile.setFocusable(true);tile.setFocusableInTouchMode(false);
        tile.setClipToOutline(true);tile.setTag(app.pkg);
        if(app.banner!=null){
            ImageView banner=new ImageView(this);
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            banner.setImageDrawable(app.banner);
            tile.addView(banner,new FrameLayout.LayoutParams(-1,-1));
        }else{
            ImageView icon=new ImageView(this);
            icon.setImageDrawable(app.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(icon,new FrameLayout.LayoutParams(dp(27),dp(27),Gravity.TOP|Gravity.CENTER_HORIZONTAL));
        }
        View scrim=new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x05070C16,0xCA0B101B}));
        tile.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        TextView name=text(app.label,10,WHITE,true);
        name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams t=new FrameLayout.LayoutParams(-1,dp(20),Gravity.BOTTOM);
        t.leftMargin=dp(3);t.rightMargin=dp(3);
        tile.addView(name,t);
        tile.setForeground(focusStroke(false));
        tile.setOnFocusChangeListener((v,f)->{
            tile.setForeground(focusStroke(f));
            if(preferences.getBoolean("motion",true))
                tile.animate().scaleX(f?1.025f:1f).scaleY(f?1.025f:1f).setDuration(100).start();
        });
        tile.setOnClickListener(v->{if(isYouTube(app))launchYouTube();else openDirect(app);});
        tile.setOnLongClickListener(v->{editFavorite(app);return true;});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
        lp.setMargins(0,0,dp(6),0);
        parent.addView(tile,lp);
    }
    private TvApp lastVideoService(){
        for(TvApp app:recentApps())if(isMedia(app)&&!getHiddenIds().contains(app.pkg))return app;
        return null;
    }
    private void decorateHero(FrameLayout hero,boolean compact){
        LinearLayout stack=col();
        stack.setPadding(dp(4),dp(4),dp(4),dp(4));
        stack.setGravity(Gravity.CENTER_VERTICAL);
        stack.setClipChildren(false);
        FrameLayout.LayoutParams right=new FrameLayout.LayoutParams(dp(compact?169:202),dp(compact?154:177),
            Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        right.rightMargin=dp(compact?12:23);
        hero.addView(stack,right);

        TvApp video=lastVideoService();
        LinearLayout continueCard=col();
        continueCard.setPadding(dp(compact?10:16),dp(compact?7:12),dp(12),dp(6));
        selectable(continueCard,0xDA1E2631);
        TextView caption=text("НЕДАВНО ОТКРЫВАЛИ",10,ACCENT,true);
        caption.setLetterSpacing(.09f);
        continueCard.addView(caption);
        gap(continueCard,4);
        TextView name=text(video==null?"Кино и ТВ":video.label,compact?15:18,WHITE,true);
        name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);
        continueCard.addView(name);
        continueCard.addView(text(video==null?"Все видеоприложения":"Открыть приложение",11,MUTED,false));
        continueCard.setOnClickListener(v->{
            if(video==null)switchTo(1);
            else if(isYouTube(video))launchYouTube();
            else openDirect(video);
        });
        stack.addView(continueCard,new LinearLayout.LayoutParams(-1,dp(compact?72:82)));
        gap(stack,compact?6:9);

        LinearLayout vpnCard=row();
        vpnCard.setPadding(dp(compact?10:15),0,dp(8),0);
        selectable(vpnCard,0xDB1E2631);
        TvIcon shield=new TvIcon(this,4,vpnConnected()?0xFF83DFAF:ACCENT);
        LinearLayout.LayoutParams shieldSize=new LinearLayout.LayoutParams(dp(27),dp(27));
        shieldSize.rightMargin=dp(12);
        vpnCard.addView(shield,shieldSize);
        LinearLayout status=col();
        status.addView(text("SMART VPN",11,ACCENT,true));
        status.addView(text(vpnConnected()?"Подключение активно":"Открыть v2RayTun",12,WHITE,false));
        vpnCard.addView(status);
        vpnCard.setOnClickListener(v->openVpn());
        stack.addView(vpnCard,new LinearLayout.LayoutParams(-1,dp(compact?62:69)));
    }
    private void openBestVideo(){
        TvApp last=lastVideoService();
        if(last!=null){
            if(isYouTube(last))launchYouTube();
            else openDirect(last);
            return;
        }
        TvApp youTube=youtubeApp();
        if(youTube!=null&&!getHiddenIds().contains(youTube.pkg)){launchYouTube();return;}
        switchTo(1);
    }
    private ArrayList<TvApp> recentApps(){
        ArrayList<TvApp> result=new ArrayList<>();
        for(String pkg:preferences.getString("recent_apps","").split(",")){
            if(pkg.isEmpty())continue;
            for(TvApp a:apps)if(a.pkg.equals(pkg)&&!getHiddenIds().contains(a.pkg)){result.add(a);break;}
        }
        return result;
    }
    private void rememberApp(TvApp a){
        ArrayList<String> ids=new ArrayList<>();ids.add(a.pkg);
        for(String p:preferences.getString("recent_apps","").split(",")){
            if(!p.isEmpty()&&!ids.contains(p)&&ids.size()<8)ids.add(p);
        }
        preferences.edit().putString("recent_apps",TextUtils.join(",",ids)).apply();
        recentNeedsRefresh=true;
    }
    private Bitmap loadPoster(){
        if(customPoster!=null)return customPoster;
        String saved=preferences.getString("hero_poster","");
        if(saved.isEmpty()){
            try(java.io.InputStream in=getAssets().open("hero.webp")){
                android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();
                options.inPreferredConfig=android.graphics.Bitmap.Config.RGB_565;
                customPoster=android.graphics.BitmapFactory.decodeStream(in,null,options);
            }catch(Exception ignored){}
            return customPoster;
        }
        try{
            Uri uri=Uri.parse(saved);
            BitmapFactory.Options meta=new BitmapFactory.Options();
            meta.inJustDecodeBounds=true;
            try(InputStream src=getContentResolver().openInputStream(uri)){
                BitmapFactory.decodeStream(src,null,meta);
            }
            if(meta.outWidth<=0||meta.outHeight<=0)return null;
            int sample=1;
            while(meta.outWidth/sample>1400||meta.outHeight/sample>900)sample*=2;
            BitmapFactory.Options opt=new BitmapFactory.Options();
            opt.inSampleSize=sample; opt.inPreferredConfig=Bitmap.Config.RGB_565;
            try(InputStream src=getContentResolver().openInputStream(uri)){
                customPoster=BitmapFactory.decodeStream(src,null,opt);
            }
        }catch(Exception ignored){}
        return customPoster;
    }
    private void exportSettings(){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE,"XiaomiTVCustom-settings.json");
        try{startActivityForResult(intent,REQ_EXPORT);}
        catch(Exception e){Toast.makeText(this,"Экспорт недоступен",Toast.LENGTH_SHORT).show();}
    }
    private void importSettings(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try{startActivityForResult(intent,REQ_IMPORT);}
        catch(Exception e){Toast.makeText(this,"Выбор файла недоступен",Toast.LENGTH_SHORT).show();}
    }
    private void exportToUri(Uri uri){
        try(java.io.OutputStream stream=getContentResolver().openOutputStream(uri,"w")){
            if(stream==null)throw new java.io.IOException("No output stream");
            byte[] bytes=LauncherBackup.exportConfig(preferences).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            stream.write(bytes);stream.flush();
            Toast.makeText(this,"Настройки сохранены",Toast.LENGTH_LONG).show();
        }catch(Exception e){Toast.makeText(this,"Ошибка экспорта: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void loadFromUri(Uri uri){
        try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
            if(in==null)throw new java.io.IOException("No input stream");
            java.io.ByteArrayOutputStream memory=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1){
                if(memory.size()+n>131072)throw new java.io.IOException("Файл больше 128 КБ");
                memory.write(buffer,0,n);
            }
            String json=new String(memory.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
            org.json.JSONObject parsed=new org.json.JSONObject(json);
            if(!"xiaomi-tv-custom-settings".equals(parsed.optString("format")) ||
                 parsed.optInt("schema",-1)!=LauncherBackup.SCHEMA)
                throw new org.json.JSONException("Неизвестный формат настроек");
            new AlertDialog.Builder(this)
                .setTitle("Восстановить настройки?")
                .setMessage("Будет заменено избранное, порядок приложений и настройки лаунчера. VPN-профили не затрагиваются.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Восстановить",(d,w)->{
                    try{
                        int count=LauncherBackup.importConfig(json,preferences);
                        background.setScenic(preferences.getBoolean("scenic",true));
                        render(page);
                        Toast.makeText(this,"Восстановлено. В избранном: "+count,Toast.LENGTH_LONG).show();
                    }catch(Exception e){
                        Toast.makeText(this,"Ошибка импорта: "+e.getMessage(),Toast.LENGTH_LONG).show();
                    }
                }).show();
        }catch(Exception e){Toast.makeText(this,"Не удалось прочитать: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void pickPoster(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try{startActivityForResult(i,REQ_POSTER);}
        catch(Exception e){Toast.makeText(this,"Не удаётся открыть выбор изображения",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null)return;
        if(request==REQ_EXPORT){ exportToUri(data.getData());return; }
        if(request==REQ_IMPORT){ loadFromUri(data.getData());return; }
        if(request==REQ_POSTER){
            Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
            catch(Exception ignored){}
            customPoster=null;
            preferences.edit().putString("hero_poster",uri.toString()).apply();
            render(page);
        }
    }
    private void sectionHead(LinearLayout c,String title,String subtitle,Runnable action){
        LinearLayout header=row();
        LinearLayout left=col();
        left.addView(text(title,19,WHITE,true));
        left.addView(text(subtitle,11,MUTED,false));
        header.addView(left,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView viewAll=text("Посмотреть все   ›",13,ACCENT,true);
        viewAll.setGravity(Gravity.CENTER);
        selectable(viewAll,0x7A202631);
        viewAll.setOnClickListener(v->action.run());
        header.addView(viewAll,new LinearLayout.LayoutParams(dp(150),dp(38)));
        c.addView(header);gap(c,7);
    }
    private void streaming(LinearLayout c){
        String section=page==8?"Сериалы":page==9?"Телевидение":"Фильмы";
        title(c,section,"Откройте доступный видеосервис. Каталоги фильмов принадлежат самим приложениям.");
        ArrayList<TvApp> stream=new ArrayList<>();
        for(TvApp a:apps){
            String x=a.label.toLowerCase(Locale.ROOT);
            if(getHiddenIds().contains(a.pkg))continue;
            if(x.contains("youtube")||x.contains("ivi")||x.contains("иви")||x.contains("кино")||
               x.contains("okko")||x.contains("окко")||x.contains("wink")||x.contains("видео")||
               x.contains("zona")||x.contains("зона")||x.contains("netflix")||x.contains("tv+")||
               x.contains("live tv"))stream.add(a);
        }
        stream.sort(Comparator.comparingInt(a->rank(a,new HashSet<>(getFavoriteIds()))));
        if(stream.isEmpty())c.addView(text("Видеоприложения не обнаружены.",16,MUTED,false));
        else appGrid(c,stream);
    }
    private void allApps(LinearLayout c){
        title(c,"Все приложения",apps.size()+" установлено · Удержание ОК открывает меню приложения");
        LinearLayout filters=row();
        String[] labels={"Все","Избранное","Кино и ТВ","Скрытые","Системные","Поиск"};
        String[] ids={"all","fav","media","hidden","system","search"};
        for(int i=0;i<labels.length;i++){
            final String id=ids[i];
            TextView t=text(labels[i],13,id.equals(appFilter)?0xFF101319:WHITE,true);
            t.setGravity(Gravity.CENTER);t.setPadding(dp(10),0,dp(10),0);
            if(id.equals(appFilter)){
                t.setBackground(bg(ACCENT,false,12));
                t.setOnFocusChangeListener((v,focus)->{
                    t.setBackground(bg(focus?0xFFFFC9AF:ACCENT,false,12));
                    t.setScaleX(focus?1.03f:1f);
                    t.setScaleY(focus?1.03f:1f);
                });
                t.setFocusable(true);t.setFocusableInTouchMode(false);
            }else selectable(t,SURFACE);
            t.setOnClickListener(v->{
                if(id.equals("search")){showSearch();return;}
                appFilter=id;searchQuery="";render(2);
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(42));
            lp.setMargins(0,0,dp(8),0);filters.addView(t,lp);
        }
        HorizontalScrollView filterScroll=new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.addView(filters);c.addView(filterScroll);gap(c,19);
        ArrayList<TvApp> visible=new ArrayList<>();
        Set<String> favorites=new HashSet<>(getFavoriteIds());
        Set<String> hidden=getHiddenIds();
        for(TvApp a:apps){
            if(appFilter.equals("hidden")?!hidden.contains(a.pkg):hidden.contains(a.pkg))continue;
            if(appFilter.equals("fav")&&!favorites.contains(a.pkg))continue;
            if(appFilter.equals("media")&&!isMedia(a))continue;
            if(appFilter.equals("system")&&!isSystem(a))continue;
            if(!searchQuery.isEmpty()&&!a.label.toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT)))continue;
            visible.add(a);
        }
        if(!searchQuery.isEmpty()){
            c.addView(text("Поиск: "+searchQuery,14,ACCENT,true));gap(c,12);
        }
        if(visible.isEmpty())c.addView(text(appFilter.equals("hidden")?"Скрытых приложений нет.":"В этой категории пока ничего нет.",16,MUTED,false));
        else appGrid(c,visible);
    }
    private boolean isMedia(TvApp a){
        String n=a.label.toLowerCase(Locale.ROOT);
        return n.contains("youtube")||n.contains("кино")||n.contains("ivi")||n.contains("иви")||
            n.contains("okko")||n.contains("окко")||n.contains("wink")||n.contains("видео")||
            n.contains("zona")||n.contains("зона")||n.contains("tv")||n.contains("netflix");
    }
    private boolean isSystem(TvApp a){
        try{
            ApplicationInfo info=getPackageManager().getApplicationInfo(a.pkg,0);
            return (info.flags&ApplicationInfo.FLAG_SYSTEM)!=0;
        }catch(Exception ignored){return false;}
    }
    private void showSearch(){
        EditText edit=new EditText(this);
        edit.setSingleLine(true);edit.setText(searchQuery);
        edit.setHint("Название приложения");
        new AlertDialog.Builder(this).setTitle("Поиск приложений")
            .setView(edit)
            .setNegativeButton("Отмена",null)
            .setNeutralButton("Сбросить",(d,w)->{searchQuery="";render(2);})
            .setPositiveButton("Найти",(d,w)->{searchQuery=edit.getText().toString().trim();appFilter="all";render(2);})
            .show();
    }
    private void hdmi(LinearLayout c){
        title(c,"HDMI и устройства","Быстрый доступ к системным источникам и настройкам.");
        action(c,"Выбор HDMI / Источники","Открыть системные настройки телевизора",
          ()->trySettings(Settings.ACTION_SETTINGS));
        action(c,"Экран и звук","Настройки изображения, динамиков и HDMI-CEC",
          ()->trySettings(Settings.ACTION_SETTINGS));
        c.addView(text("Android TV не предоставляет обычным приложениям универсальный API переключения HDMI. Здесь открываются настоящие системные настройки.",13,MUTED,false));
    }
    private void vpnPage(LinearLayout c){
        title(c,"Smart VPN","Подключение v2RayTun и сценарий для YouTube");
        LinearLayout panel=col();
        panel.setPadding(dp(23),dp(19),dp(23),dp(21));
        panel.setBackground(glass(SURFACE,false,18));
        LinearLayout top=row();
        TvIcon shield=new TvIcon(this,4,vpnConnected()?0xFF81DDAF:ACCENT);
        LinearLayout.LayoutParams shieldSize=new LinearLayout.LayoutParams(dp(56),dp(56));
        shieldSize.rightMargin=dp(16);top.addView(shield,shieldSize);
        LinearLayout state=col();
        state.addView(text(vpnConnected()?"VPN подключён":"VPN отключён",24,WHITE,true));
        state.addView(text("Управление соединением: v2RayTun",12,MUTED,false));
        top.addView(state);panel.addView(top);
        gap(panel,18);
        pill(panel,"Открыть v2RayTun",this::openVpn);
        gap(panel,22);
        panel.addView(text("YouTube с VPN",18,ACCENT,true));gap(panel,9);
        panel.addView(text("Перед запуском YouTube проверяем VPN. Если соединение выключено, открываем v2RayTun для подключения. После возвращения откроется YouTube, когда Android подтвердит VPN.",14,0xFFD6DCE6,false));
        gap(panel,15);
        boolean enabled=preferences.getBoolean("youtube_vpn",true);
        pill(panel,enabled?"Проверка включена  ·  изменить":"Проверка выключена  ·  изменить",()->{
            preferences.edit().putBoolean("youtube_vpn",!enabled).apply();
            render(4);
        });
        c.addView(panel);gap(c,16);
        c.addView(text("В этой сборке прямое автоматическое включение туннеля не реализовано. Лаунчер не меняет и не удаляет VPN-профили.",12,MUTED,false));
    }
    private void toggleQuickPanel(){
        if(quickOverlay!=null){closeQuickPanel();return;}
        openQuickPanel();
    }
    private void closeQuickPanel(){
        if(quickOverlay==null)return;
        FrameLayout overlay=quickOverlay;
        quickOverlay=null;
        if(overlay.getParent() instanceof ViewGroup)
            ((ViewGroup)overlay.getParent()).removeView(overlay);
        if(focusBeforeOverlay!=null && focusBeforeOverlay.isAttachedToWindow()){
            View restore=focusBeforeOverlay;
            restore.post(restore::requestFocus);
        }
        focusBeforeOverlay=null;
    }
    private void quickPanelAction(LinearLayout into,int iconId,String heading,
                                  String description,Runnable task){
        LinearLayout item=row();
        item.setPadding(dp(16),0,dp(12),0);
        selectable(item,0xD01E2734);
        item.setContentDescription(heading+": "+description);
        TvIcon symbol=new TvIcon(this,iconId,ACCENT);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(24),dp(24));
        ip.rightMargin=dp(14);item.addView(symbol,ip);
        LinearLayout labels=col();
        TextView name=text(heading,15,WHITE,true);labels.addView(name);
        TextView info=text(description,11,MUTED,false);
        info.setSingleLine(true);info.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(info);
        item.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
        item.setOnClickListener(v->{closeQuickPanel();task.run();});
        into.addView(item,new LinearLayout.LayoutParams(-1,dp(64)));
        gap(into,8);
    }
    private void openQuickPanel(){
        if(rootView==null || quickOverlay!=null)return;
        focusBeforeOverlay=getCurrentFocus();
        FrameLayout root=(FrameLayout)rootView;
        FrameLayout layer=new FrameLayout(this);
        quickOverlay=layer;
        layer.setClickable(true);
        layer.setFocusable(false);
        View dim=new View(this);
        dim.setBackgroundColor(0xB20A0F18);
        dim.setOnClickListener(v->closeQuickPanel());
        layer.addView(dim,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout surface=col();
        surface.setPadding(dp(20),dp(18),dp(20),dp(15));
        surface.setBackground(glass(0xF5232A36,false,22));
        surface.setClipToOutline(true);
        int screen=(int)(getResources().getDisplayMetrics().widthPixels /
            getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(
            dp(Math.min(380,Math.max(278,(int)(screen*.35f)))),-1,
            Gravity.RIGHT|Gravity.TOP);
        lp.setMargins(0,dp(7),dp(8),dp(7));
        layer.addView(surface,lp);

        LinearLayout header=row();
        LinearLayout titles=col();
        TextView top=text("ЦЕНТР УПРАВЛЕНИЯ",12,ACCENT,true);
        top.setLetterSpacing(.11f);titles.addView(top);gap(titles,5);
        titles.addView(text("Быстрые действия",23,WHITE,true));
        header.addView(titles,new LinearLayout.LayoutParams(0,dp(66),1f));
        FrameLayout close=new FrameLayout(this);
        selectable(close,0xE52A3545);
        TvIcon cross=new TvIcon(this,5,ACCENT);
        close.addView(cross,new FrameLayout.LayoutParams(dp(21),dp(21),Gravity.CENTER));
        close.setContentDescription("Закрыть центр управления");
        close.setOnClickListener(v->closeQuickPanel());
        header.addView(close,new LinearLayout.LayoutParams(dp(44),dp(42)));
        surface.addView(header);gap(surface,12);

        // Scroll makes the panel usable on small Android TV displays too.
        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(false);
        sc.setVerticalScrollBarEnabled(false);
        LinearLayout actions=col();actions.setPadding(dp(2),dp(3),dp(2),dp(5));
        sc.addView(actions);
        surface.addView(sc,new LinearLayout.LayoutParams(-1,0,1f));
        quickPanelAction(actions,4,"Smart VPN",vpnConnected()?"VPN подключён":"Открыть v2RayTun",this::openVpn);
        quickPanelAction(actions,8,"YouTube","Запуск с проверкой VPN",this::launchYouTube);
        quickPanelAction(actions,6,"Интернет",networkSummary(),
            ()->trySettings(Settings.ACTION_WIFI_SETTINGS));
        quickPanelAction(actions,3,"HDMI","Системное меню источников",()->switchTo(3));
        quickPanelAction(actions,2,"Приложения","Все установленные программы",()->switchTo(2));
        quickPanelAction(actions,5,"Настройки лаунчера","Оформление и резервная копия",()->switchTo(5));

        TextView foot=text("MENU — закрыть  ·  ОК — выбрать  ·  Назад — выйти",
            11,MUTED,false);
        foot.setGravity(Gravity.CENTER);
        surface.addView(foot,new LinearLayout.LayoutParams(-1,dp(39)));
        root.addView(layer,new FrameLayout.LayoutParams(-1,-1));

        // Focus the first actionable surface. No focus escapes to hidden screen.
        View first=findFirstFocusable(sc);
        if(first!=null)first.post(first::requestFocus);
    }
    private void controlCenter(LinearLayout c){
        title(c,"Центр управления","Быстрые команды с пульта · меню открывается кнопкой MENU");
        LinearLayout status=row();
        quickTile(status,"VPN",vpnConnected()?"Подключён":"Нет подключения",this::openVpn);
        quickTile(status,"Сеть",networkSummary(),()->trySettings(Settings.ACTION_WIFI_SETTINGS));
        quickTile(status,"Хранилище",freeSpace(),()->trySettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        c.addView(status);gap(c,16);
        c.addView(text("Быстрые настройки",19,WHITE,true));gap(c,12);
        LinearLayout r1=row();
        quickTile(r1,"Wi-Fi","Открыть параметры",()->trySettings(Settings.ACTION_WIFI_SETTINGS));
        quickTile(r1,"Bluetooth","Устройства",()->trySettings(Settings.ACTION_BLUETOOTH_SETTINGS));
        c.addView(r1);gap(c,11);
        LinearLayout r2=row();
        quickTile(r2,"YouTube","Проверить VPN",this::launchYouTube);
        quickTile(r2,"Входы HDMI","Настройки телевизора",()->switchTo(3));
        c.addView(r2);gap(c,11);
        LinearLayout r3=row();
        quickTile(r3,"Приложения","Список программ",()->switchTo(2));
        quickTile(r3,"Оформление","Настроить вид",()->switchTo(5));
        c.addView(r3);gap(c,18);
        c.addView(text("Переключатели открывают соответствующие системные настройки. Это не имитация Wi-Fi или Bluetooth.",12,MUTED,false));
    }
    private String asGb(long bytes){
        return String.format(Locale.getDefault(),"%.2f ГБ",bytes/1073741824.0);
    }
    private void diagnostics(LinearLayout c){
        title(c,"Диагностика телевизора","Измерения непосредственно с этого Android TV");
        android.app.ActivityManager manager=
            (android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
        if(manager!=null){
            android.app.ActivityManager.MemoryInfo info=
                new android.app.ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            String ram=asGb(info.availMem)+" свободно из "+asGb(info.totalMem);
            action(c,"Оперативная память (RAM)",ram,()->render(7));
        }
        try{
            StatFs storage=new StatFs(getFilesDir().getPath());
            action(c,"Внутренний накопитель",
                asGb(storage.getAvailableBytes())+" свободно из "+asGb(storage.getTotalBytes()),
                ()->trySettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        }catch(Exception ignored){}
        android.graphics.Point size=new android.graphics.Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        action(c,"Разрешение дисплея",size.x+" × "+size.y+" пикселей",
            ()->trySettings(Settings.ACTION_DISPLAY_SETTINGS));
        action(c,"Android",String.valueOf(android.os.Build.VERSION.RELEASE)+" · API "+
            android.os.Build.VERSION.SDK_INT,()->trySettings(Settings.ACTION_SETTINGS));
        action(c,"Сеть",networkSummary(),()->trySettings(Settings.ACTION_WIFI_SETTINGS));
        action(c,"Активное VPN-соединение",vpnConnected()?"Обнаружено":"Не обнаружено",this::openVpn);
        gap(c,11);
        c.addView(text("Это показания Android, а не результаты теста скорости. Оперативная и постоянная память различаются.",12,MUTED,false));
        gap(c,15);
        LinearLayout controls=row();
        pill(controls,"Обновить показатели",()->render(7));
        pill(controls,"Вернуться",()->switchTo(5));
        c.addView(controls);
    }
    private String networkSummary(){
        try{
            ConnectivityManager m=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            if(m==null)return "Статус неизвестен";
            Network n=m.getActiveNetwork();
            NetworkCapabilities caps=n==null?null:m.getNetworkCapabilities(n);
            if(caps==null)return "Не подключено";
            if(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return "Wi-Fi";
            if(caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))return "Ethernet";
            if(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return "VPN";
            return "Сеть подключена";
        }catch(Exception e){return "Статус неизвестен";}
    }
    private void settings(LinearLayout c){
        title(c,"Настройки","Внешний вид, управление и сценарии Xiaomi TV Custom");
        c.addView(text("ВНЕШНИЙ ВИД",12,ACCENT,true));gap(c,9);
        action(c,"Постер главного экрана","Выбрать свою кинематографическую картинку",this::pickPoster);
        action(c,"Сбросить постер","Вернуть встроенный кинематографический фон",()->{
            customPoster=null;preferences.edit().remove("hero_poster").apply();render(5);
        });
        setting(c,"Кинематографический фон","Тёмные горы и мягкие акценты, без видео","scenic",true);
        setting(c,"Плавные переходы","Лёгкое увеличение активной карточки","motion",true);
        setting(c,"Крупные карточки","Большие приложения и упрощённая сетка","large_cards",true);
        setting(c,"История на главном экране","Недавно открытые приложения под избранным","show_recent",true);
        gap(c,16);
        c.addView(text("СЦЕНАРИИ",12,ACCENT,true));gap(c,9);
        setting(c,"VPN перед YouTube","Открывать v2RayTun при отсутствии соединения","youtube_vpn",true);
        action(c,"Настроить VPN","Открыть установленный клиент v2RayTun",this::openVpn);
        gap(c,16);
        c.addView(text("СИСТЕМА",12,ACCENT,true));gap(c,9);
        action(c,"Диагностика устройства","Память, RAM, разрешение и информация о системе",()->switchTo(7));
        action(c,"Состояние памяти",freeSpace()+" · открыть настройки хранилища",
               ()->trySettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        action(c,"Wi-Fi и Интернет",networkSummary(),()->trySettings(Settings.ACTION_WIFI_SETTINGS));
        action(c,"Выбор главного экрана","Вернуться к Xiaomi или Projectivy можно через настройки",
                ()->trySettings(Settings.ACTION_HOME_SETTINGS));
        action(c,"Настройки Android TV","Оригинальное меню Xiaomi",
                ()->trySettings(Settings.ACTION_SETTINGS));
        c.addView(text("РЕЗЕРВНАЯ КОПИЯ",12,ACCENT,true));gap(c,9);
        action(c,"Экспорт настроек","Избранное, порядок и параметры в JSON",this::exportSettings);
        action(c,"Импорт настроек","Восстановить ранее сохранённую конфигурацию",this::importSettings);
        gap(c,16);
        action(c,"Показать скрытые приложения",
            getHiddenIds().size()+" скрыто · здесь можно вернуть их обратно",()->{
            appFilter="hidden";searchQuery="";switchTo(2);
        });
        action(c,"Вернуть все скрытые приложения","Не удаляет данные или программы",()->{
            if(getHiddenIds().isEmpty()){
                Toast.makeText(this,"Скрытых программ нет",Toast.LENGTH_SHORT).show();return;
            }
            new AlertDialog.Builder(this).setTitle("Показать все приложения?")
                .setMessage("Все ранее скрытые программы появятся в лаунчере.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Показать",(d,w)->{
                    preferences.edit().remove("hidden_packages").apply();
                    render(page);
                }).show();
        });
        action(c,"Сбросить порядок избранного","Не удаляет установленные приложения",()->{
            new AlertDialog.Builder(this).setTitle("Очистить избранное?")
                .setMessage("Удалить только отметки избранных программ?")
                .setNegativeButton("Отмена",null).setPositiveButton("Очистить",(d,w)->{
                    preferences.edit().remove("favorites").remove("favorite_order").apply();render(5);
                }).show();
        });
        gap(c,13);
        c.addView(text("Xiaomi TV Custom 1.10 · Android TV 11 · без root и фоновых служб",12,MUTED,false));
    }
    private void setting(LinearLayout c,String name,String detail,String key,boolean def){
        boolean active=preferences.getBoolean(key,def);
        action(c,name+(active?"     ●":"     ○"),detail,()->{
            preferences.edit().putBoolean(key,!active).apply();
            if(key.equals("scenic"))background.setScenic(!active);
            render(5);
        });
    }
    private void action(LinearLayout c,String label,String detail,Runnable cb){
        LinearLayout r=row();r.setPadding(dp(18),dp(9),dp(18),dp(9));
        selectable(r,SURFACE);r.setOnClickListener(v->cb.run());
        LinearLayout words=col();words.addView(text(label,17,WHITE,true));
        words.addView(text(detail,12,MUTED,false));
        r.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        r.addView(text("›",25,ACCENT,false));
        c.addView(r,new LinearLayout.LayoutParams(-1,dp(75)));gap(c,8);
    }
    private void pill(LinearLayout into,String label,Runnable cb){
        TextView b=text(label,15,WHITE,true);b.setGravity(Gravity.CENTER);
        b.setPadding(dp(20),0,dp(20),0);
        boolean primary="Смотреть".equals(label);
        selectable(b,primary?ACCENT:SURFACE2);
        if(primary){
            b.setTextColor(0xFF1B1E25);
            b.setBackground(bg(ACCENT,false,14));
            b.setOnFocusChangeListener((view,focused)->{
                b.setBackground(bg(focused?0xFFFFD5B7:ACCENT,focused,14));
                if(preferences.getBoolean("motion",true))
                    b.animate().scaleX(focused?1.035f:1f).scaleY(focused?1.035f:1f).setDuration(120).start();
            });
        }
        b.setOnClickListener(v->cb.run());
        if(into.getOrientation()==LinearLayout.HORIZONTAL){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(44));
            lp.setMargins(0,0,dp(10),0);into.addView(b,lp);
        } else into.addView(b,new LinearLayout.LayoutParams(dp(260),dp(49)));
    }
    private int quickSymbol(String label){
        String x=label.toLowerCase(Locale.ROOT);
        if(x.contains("vpn"))return 4;
        if(x.contains("hdmi")||x.contains("экран"))return 3;
        if(x.contains("сеть")||x.contains("wi-fi")||x.contains("интернет"))return 6;
        if(x.contains("хранилищ")||x.contains("память")||x.contains("таймер"))return 7;
        if(x.contains("youtube"))return 8;
        if(x.contains("приложени"))return 2;
        if(x.contains("оформлен")||x.contains("bluetooth"))return 5;
        return 0;
    }
    private void quickTile(LinearLayout into,String title,String subtitle,Runnable cb){
        LinearLayout card=col();
        card.setPadding(dp(17),dp(11),dp(12),dp(9));
        selectable(card,SURFACE);
        LinearLayout header=row();
        TvIcon symbol=new TvIcon(this,quickSymbol(title),ACCENT);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(23),dp(23));
        ip.rightMargin=dp(12);
        header.addView(symbol,ip);
        TextView heading=text(title,15,WHITE,true);
        heading.setMaxLines(1);heading.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(heading,new LinearLayout.LayoutParams(0,dp(29),1f));
        card.addView(header);
        TextView info=text(subtitle,11,MUTED,false);
        info.setMaxLines(1);info.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(info);card.setOnClickListener(v->cb.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(83),1);
        lp.setMargins(0,0,dp(9),0);into.addView(card,lp);
    }
    private int tileHeight(){
        return preferences.getBoolean("large_cards",true)?128:105;
    }
    private int columns(){
        float width=getResources().getDisplayMetrics().widthPixels/
                getResources().getDisplayMetrics().density;
        int available=(int)width-44-174-35;
        int target=preferences.getBoolean("large_cards",true)?205:160;
        return Math.max(2,Math.min(6,available/target));
    }
    private int tileWidth(){
        float width=getResources().getDisplayMetrics().widthPixels/
                getResources().getDisplayMetrics().density;
        int available=(int)width-44-174-35;
        return Math.max(118,Math.min(208,(available-12)/columns()-11));
    }
    private void appStrip(LinearLayout c,List<TvApp> list,int max){
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);scroll.setClipChildren(false);
        LinearLayout r=row();r.setPadding(dp(2),dp(2),dp(5),dp(4));r.setClipChildren(false);
        for(int i=0;i<Math.min(max,list.size());i++)tile(r,list.get(i),true);
        if(list.isEmpty())r.addView(text("Установленные приложения не найдены",15,MUTED,false));
        scroll.addView(r);
        c.addView(scroll,new LinearLayout.LayoutParams(-1,dp(72)));
    }
    private void appGrid(LinearLayout c,List<TvApp> list){
        int cols=columns();
        for(int i=0;i<list.size();i+=cols){
            LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);
            r.setClipChildren(false);r.setPadding(dp(5),dp(5),dp(5),dp(6));
            for(int j=i;j<Math.min(list.size(),i+cols);j++)tile(r,list.get(j),false);
            c.addView(r,new LinearLayout.LayoutParams(-1,dp(tileHeight()+19)));
        }
    }
    private int cardColor(TvApp a){
        String p=a.label.toLowerCase(Locale.ROOT);
        if(p.contains("youtube"))return 0xFFF0F2F6;
        if(p.contains("okko")||p.contains("окко"))return 0xFF2F2256;
        if(p.contains("wink"))return 0xFF883718;
        if(p.contains("иви")||p.equals("ivi"))return 0xFF822445;
        if(p.contains("кино"))return 0xFF30261E;
        if(p.contains("v2ray"))return 0xFF203B57;
        if(p.contains("vk")||p.contains("видео"))return 0xFF164681;
        return SURFACE;
    }
    private GradientDrawable focusStroke(boolean focused){
        GradientDrawable stroke=new GradientDrawable();
        stroke.setColor(0x00000000);
        stroke.setCornerRadius(dp(16));
        stroke.setStroke(dp(focused?3:1),focused?ACCENT:0x66566077);
        return stroke;
    }
    private void tile(LinearLayout r,TvApp a,boolean compactCard){
        FrameLayout card=new FrameLayout(this);
        int tint=cardColor(a);
        card.setBackground(glass(0xEE171F2D,false,16));
        card.setClipToOutline(true);
        card.setClipChildren(true);
        card.setFocusable(true);
        card.setFocusableInTouchMode(false);
        card.setContentDescription(a.label);
        card.setTag(a.pkg);

        // Use installed Android TV banners if available, never substitute fake film posters.
        if(a.banner!=null){
            ImageView backdrop=new ImageView(this);
            backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
            backdrop.setImageDrawable(a.banner);
            backdrop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
            View veil=new View(this);
            veil.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x1108111C,0x330D1320,0xED080F18}));
            card.addView(veil,new FrameLayout.LayoutParams(-1,-1));
        }else{
            card.addView(new CardArtView(this,tint),new FrameLayout.LayoutParams(-1,-1));
            ImageView icon=new ImageView(this);
            icon.setImageDrawable(a.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int iconDp=compactCard?36:(preferences.getBoolean("large_cards",true)?74:60);
            FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(iconDp),dp(iconDp),Gravity.CENTER);
            ip.bottomMargin=dp(compactCard?9:19);
            card.addView(icon,ip);
        }
        // Frosted-looking caption strip. Keep app name legible on both banners and icons.
        LinearLayout labelStrip=row();
        labelStrip.setPadding(dp(compactCard?7:10),0,dp(compactCard?5:9),0);
        labelStrip.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xC6141A24,0xB018202C}));
        FrameLayout.LayoutParams stripLp=new FrameLayout.LayoutParams(-1,dp(compactCard?18:31),Gravity.BOTTOM);
        stripLp.leftMargin=dp(1);stripLp.rightMargin=dp(1);stripLp.bottomMargin=dp(1);
        TextView name=text(a.label,compactCard?10:12,WHITE,true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        labelStrip.addView(name,new LinearLayout.LayoutParams(0,-1,1f));
        if(getFavoriteIds().contains(a.pkg)){
            TextView favorite=text("★",13,ACCENT,true);
            favorite.setGravity(Gravity.CENTER);
            labelStrip.addView(favorite,new LinearLayout.LayoutParams(dp(19),-1));
        }
        card.addView(labelStrip,stripLp);
        card.setForeground(focusStroke(false));
        card.setOnFocusChangeListener((view,focused)->{
            card.setForeground(focusStroke(focused));
            if(preferences.getBoolean("motion",true)){
                card.animate().scaleX(focused?1.028f:1f).scaleY(focused?1.028f:1f)
                    .setDuration(130).start();
            }else{
                card.animate().cancel();
                card.setScaleX(1f);card.setScaleY(1f);
            }
            card.setElevation(dp(focused?9:0));
        });
        float vw=getResources().getDisplayMetrics().widthPixels/
            getResources().getDisplayMetrics().density;
        int compactWidth=Math.max(88,Math.min(128,((int)vw-44-174-35-70)/8));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
            dp(compactCard?compactWidth:tileWidth()),dp(compactCard?65:tileHeight()));
        lp.setMargins(dp(2),0,dp(compactCard?6:9),0);
        r.addView(card,lp);
        card.setOnClickListener(v->{if(isYouTube(a))launchYouTube();else openDirect(a);});
        card.setOnLongClickListener(v->{editFavorite(a);return true;});
        if(a.pkg.equals(selectedPackage)){
            selectedPackage="";
            card.post(card::requestFocus);
        }
    }

    private void refreshApps(){
        PackageManager pm=getPackageManager();HashMap<String,TvApp> unique=new HashMap<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            Intent query=new Intent(Intent.ACTION_MAIN).addCategory(category);
            for(ResolveInfo info:pm.queryIntentActivities(query,0)){
                if(info.activityInfo==null)continue;
                String pkg=info.activityInfo.packageName;
                if(pkg.equals(getPackageName())||unique.containsKey(pkg))continue;
                try{
                    Intent i=new Intent(Intent.ACTION_MAIN).addCategory(category)
                        .setClassName(pkg,info.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    String label=String.valueOf(info.loadLabel(pm));
                    unique.put(pkg,new TvApp(pkg,label,info.loadIcon(pm),info.activityInfo.loadBanner(pm),i));
                }catch(Exception ignored){}
            }
        }
        apps.clear();apps.addAll(unique.values());
        apps.sort(Comparator.comparing(a->a.label.toLowerCase(Locale.ROOT)));
    }
    private ArrayList<String> getFavoriteIds(){
        String order=preferences.getString("favorite_order","");
        ArrayList<String> result=new ArrayList<>();
        if(!order.isEmpty())for(String id:order.split(",")){
            if(!id.isEmpty()&&!result.contains(id))result.add(id);
        }
        // Read older version's set of favourites when present.
        Set<String> legacy=preferences.getStringSet("favorites",Collections.emptySet());
        ArrayList<String> leftovers=new ArrayList<>(legacy);Collections.sort(leftovers);
        for(String id:leftovers)if(!result.contains(id))result.add(id);
        return result;
    }
    private void saveFavoriteIds(ArrayList<String> favorites){
        preferences.edit()
            .putString("favorite_order",TextUtils.join(",",favorites))
            .remove("favorites").apply();
    }
    private List<TvApp> favoriteApps(){
        ArrayList<TvApp> result=new ArrayList<>();
        for(String id:getFavoriteIds()){
            for(TvApp a:apps)if(a.pkg.equals(id)&&!getHiddenIds().contains(a.pkg)){result.add(a);break;}
        }
        return result;
    }
    private Set<String> getHiddenIds(){
        String value=preferences.getString("hidden_packages","");
        Set<String> hidden=new HashSet<>();
        for(String pkg:value.split(","))if(!pkg.isEmpty())hidden.add(pkg);
        return hidden;
    }
    private void toggleHidden(TvApp app){
        Set<String> hidden=getHiddenIds();
        boolean wasHidden=hidden.contains(app.pkg);
        if(wasHidden)hidden.remove(app.pkg);
        else hidden.add(app.pkg);
        ArrayList<String> ordered=new ArrayList<>(hidden);
        Collections.sort(ordered);
        preferences.edit().putString("hidden_packages",TextUtils.join(",",ordered)).apply();
        selectedPackage=wasHidden?app.pkg:"";
        render(page);
        Toast.makeText(this,wasHidden?"Приложение снова отображается":"Скрыто только в лаунчере",Toast.LENGTH_SHORT).show();
    }
    private void editFavorite(TvApp app){
        ArrayList<String> favorite=getFavoriteIds();
        boolean contained=favorite.contains(app.pkg);
        boolean hidden=getHiddenIds().contains(app.pkg);
        ArrayList<String> actions=new ArrayList<>();
        if(contained){
            if(favorite.indexOf(app.pkg)>0)actions.add("Переместить левее");
            if(favorite.indexOf(app.pkg)<favorite.size()-1)actions.add("Переместить правее");
            actions.add("Убрать из избранного");
        }else actions.add("Добавить в избранное");
        actions.add(hidden?"Показать в лаунчере":"Скрыть из лаунчера");
        new AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(actions.toArray(new String[0]),(dialog,which)->{
                String action=actions.get(which);
                if(action.equals("Переместить левее")){
                    int index=favorite.indexOf(app.pkg);
                    if(index>0)Collections.swap(favorite,index,index-1);
                }else if(action.equals("Переместить правее")){
                    int index=favorite.indexOf(app.pkg);
                    if(index>=0&&index<favorite.size()-1)Collections.swap(favorite,index,index+1);
                }else if(action.equals("Убрать из избранного"))favorite.remove(app.pkg);
                else if(action.equals("Добавить в избранное")){
                    if(!favorite.contains(app.pkg))favorite.add(app.pkg);
                }else{toggleHidden(app);return;}
                saveFavoriteIds(favorite);
                selectedPackage=app.pkg;
                render(page);
            }).setNegativeButton("Отмена",null).show();
    }
    private List<TvApp> forHome(){
        ArrayList<TvApp> list=new ArrayList<>();
        for(TvApp a:apps)if(!getHiddenIds().contains(a.pkg))list.add(a);
        Set<String> favorites=new HashSet<>(getFavoriteIds());
        list.sort((a,b)->{
            int x=rank(a,favorites),y=rank(b,favorites);
            return x!=y?Integer.compare(x,y):a.label.compareToIgnoreCase(b.label);
        });return list;
    }
    private int rank(TvApp a,Set<String> fav){
        if(fav.contains(a.pkg))return -100;
        String s=a.label.toLowerCase(Locale.ROOT);
        if(s.contains("youtube"))return 0;
        if(s.contains("кино"))return 1;
        if(s.contains("okko")||s.contains("окко"))return 2;
        if(s.contains("иви")||s.contains("ivi"))return 3;
        if(s.contains("wink"))return 4;
        if(s.contains("v2ray"))return 5;
        if(s.contains("видео"))return 6;
        return 30;
    }
    private TvApp youtubeApp(){
        for(TvApp a:apps)if(isYouTube(a))return a;
        return null;
    }
    private boolean isYouTube(TvApp a){
        return a.pkg.equals("com.google.android.youtube.tv")||a.label.equalsIgnoreCase("youtube");
    }
    private void launchYouTube(){
        TvApp a=youtubeApp();
        if(a==null){Toast.makeText(this,"YouTube не найден",Toast.LENGTH_SHORT).show();return;}
        if(preferences.getBoolean("youtube_vpn",true)&&!vpnConnected()){
            pendingYouTube=true;
            vpnRequestAt=android.os.SystemClock.elapsedRealtime();
            Toast.makeText(this,"Подключите VPN в v2RayTun и вернитесь кнопкой Назад",Toast.LENGTH_LONG).show();
            openVpn();return;
        }
        pendingYouTube=false;openDirect(a);
    }
    private void openVpn(){
        for(TvApp a:apps)if(a.pkg.equals("com.v2raytun.android")||
            a.label.toLowerCase(Locale.ROOT).contains("v2raytun")){openDirect(a);return;}
        Toast.makeText(this,"Установите v2RayTun",Toast.LENGTH_LONG).show();
    }
    private void openDirect(TvApp a){
        try{startActivity(a.intent);rememberApp(a);}
        catch(Exception e){Toast.makeText(this,"Не удаётся открыть "+a.label,Toast.LENGTH_LONG).show();}
    }
    private void trySettings(String action){
        try{startActivity(new Intent(action));}
        catch(Exception e){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}
        catch(Exception ignored){Toast.makeText(this,"Настройки недоступны",Toast.LENGTH_SHORT).show();}}
    }
    private boolean vpnConnected(){
        try{
            ConnectivityManager manager=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            if(manager==null)return false;
            for(Network n:manager.getAllNetworks()){
                NetworkCapabilities c=manager.getNetworkCapabilities(n);
                if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return true;
            }
        }catch(Exception ignored){}
        return false;
    }
    private String freeSpace(){
        try{StatFs s=new StatFs(getFilesDir().getPath());
            return String.format(Locale.getDefault(),"%.1f ГБ свободно",s.getAvailableBytes()/1073741824.0);}
        catch(Exception e){return "Недоступно";}
    }
}
