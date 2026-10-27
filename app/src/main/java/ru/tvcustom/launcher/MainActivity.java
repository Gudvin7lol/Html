package ru.tvcustom.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import android.content.Intent;
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
    }
    @Override protected void onResume() {
        super.onResume();
        indicatorHandler.removeCallbacks(indicatorTicker);
        indicatorHandler.post(indicatorTicker);
        if(panel==null)return;
        // Returning from a video player should not reset focus/scroll on every resume.
        refreshApps();
        if(pendingYouTube){
            boolean isFresh=android.os.SystemClock.elapsedRealtime()-vpnRequestAt<120000;
            pendingYouTube=false;
            if(isFresh && vpnConnected()){
                TvApp target=youtubeApp();
                if(target!=null){openDirect(target);return;}
            }
        }
        if(knownAppCount!=apps.size()){
            knownAppCount=apps.size();
            render(page);
        }
    }
    @Override protected void onPause() {
        indicatorHandler.removeCallbacks(indicatorTicker);
        super.onPause();
    }
    private void refreshIndicators() {
        if(statusClock!=null) statusClock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
        if(statusVpn!=null) {
            boolean on=vpnConnected();
            statusVpn.setText(on?"●  VPN":"○  VPN");
            statusVpn.setTextColor(on?0xFF89DEB2:ACCENT);
        }
    }
    @Override public boolean onKeyDown(int keyCode,KeyEvent e) {
        if((keyCode==KeyEvent.KEYCODE_MENU || keyCode==KeyEvent.KEYCODE_SETTINGS)
            && e.getRepeatCount()==0) {
            if(page!=6){lastVisitedPage=page; switchTo(6);}
            else switchTo(lastVisitedPage);
            return true;
        }
        return super.onKeyDown(keyCode,e);
    }
    @Override public void onBackPressed() {
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
    private void build(){
        FrameLayout root=new FrameLayout(this);
        background=new SceneView(this);
        background.setScenic(preferences.getBoolean("scenic",true));
        root.addView(background,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout base=col();base.setPadding(dp(22),dp(12),dp(22),dp(16));
        root.addView(base,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=row();
        TextView logo=text("xiaomi TV",25,WHITE,true);
        logo.setLetterSpacing(.055f);
        top.addView(logo,new LinearLayout.LayoutParams(0,dp(52),1f));
        statusClock=text(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()),18,WHITE,false);
        statusClock.setGravity(Gravity.CENTER);top.addView(statusClock,new LinearLayout.LayoutParams(dp(80),dp(52)));
        statusVpn=text(vpnConnected()?"●  VPN":"○  VPN",14,vpnConnected()?0xFF89DEB2:ACCENT,true);
        statusVpn.setGravity(Gravity.CENTER);selectable(statusVpn,SURFACE);
        statusVpn.setOnClickListener(v->switchTo(4));
        top.addView(statusVpn,new LinearLayout.LayoutParams(dp(101),dp(40)));
        TextView gear=text("☷",27,ACCENT,true);
        gear.setGravity(Gravity.CENTER);selectable(gear,SURFACE);
        gear.setOnClickListener(v->switchTo(6));top.addView(gear,new LinearLayout.LayoutParams(dp(46),dp(40)));
        base.addView(top,new LinearLayout.LayoutParams(-1,dp(52)));

        LinearLayout main=row();main.setGravity(Gravity.TOP);
        LinearLayout side=col();side.setPadding(0,dp(16),dp(12),0);
        main.addView(side,new LinearLayout.LayoutParams(dp(174),-1));
        String[] pages={"Главная","Фильмы и ТВ","Приложения","HDMI","Smart VPN","Настройки"};
        for(int i=0;i<pages.length;i++){
            final int destination=i;
            LinearLayout item=row();item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14),0,dp(6),0);
            selectable(item,0xBF17212E);
            item.setOnFocusChangeListener((v,focused)->{
                v.setBackground(glass(destination==page?0xE43C3136:0xA117202D,focused || destination==page,13));
            });
            TvIcon icon=new TvIcon(this,i,ACCENT);
            LinearLayout.LayoutParams iconSize=new LinearLayout.LayoutParams(dp(22),dp(22));
            iconSize.rightMargin=dp(13);
            item.addView(icon,iconSize);
            TextView label=text(pages[i],15,WHITE,i==0);
            navLabels.add(label);
            item.addView(label,new LinearLayout.LayoutParams(0,-1,1f));
            item.setOnClickListener(v->switchTo(destination));
            side.addView(item,new LinearLayout.LayoutParams(-1,dp(46)));
            gap(side,7);nav.add(item);
        }
        gap(side,16);
        side.addView(text("MI TV  ·  ANDROID 11",10,0xFF8798AB,false));
        panel=new FrameLayout(this);
        main.addView(panel,new LinearLayout.LayoutParams(0,-1,1f));
        base.addView(main,new LinearLayout.LayoutParams(-1,0,1f));
        setContentView(root);render(0);
        nav.get(0).requestFocus();
    }
    private void switchTo(int to){
        pendingYouTube=false;
        page=to;
        render(to);
        if(to<nav.size())nav.get(to).requestFocus();
    }
    private void highlightPage(){
        for(int i=0;i<nav.size();i++){
            final boolean chosen=i==page;
            View item=nav.get(i);
            item.setBackground(glass(chosen?0xD1393033:0xA117202D,false,13));
            if(i<navLabels.size()){
                TextView label=navLabels.get(i);
                label.setTypeface(android.graphics.Typeface.DEFAULT,
                    chosen?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
                label.setTextColor(chosen?WHITE:0xFFB8C3D0);
            }
        }
    }
    private void render(int to){
        if(panel==null)return;
        highlightPage();
        panel.removeAllViews();
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);
        sc.setVerticalScrollBarEnabled(false);sc.setClipToPadding(false);
        panel.addView(sc,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout c=col();c.setPadding(dp(6),dp(9),dp(5),dp(9));sc.addView(c);
        switch(to){
          case 1: streaming(c);break;
          case 2: allApps(c);break;
          case 3: hdmi(c);break;
          case 4: vpnPage(c);break;
          case 5: settings(c);break;
          case 6: controlCenter(c);break;
          default:home(c);break;
        }
    }
    private void title(LinearLayout parent,String t,String subtitle){
        parent.addView(text(t,26,WHITE,true));gap(parent,4);
        if(subtitle!=null)parent.addView(text(subtitle,13,MUTED,false));
        gap(parent,16);
    }
    private void home(LinearLayout c){
        FrameLayout hero=new FrameLayout(this);
        hero.setBackground(glass(0xE817202D,false,20));
        hero.setClipToOutline(true);
        Bitmap poster=loadPoster();
        if(poster!=null){
            ImageView bgImage=new ImageView(this);
            bgImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            bgImage.setImageBitmap(poster);
            hero.addView(bgImage,new FrameLayout.LayoutParams(-1,-1));
        }else{
            hero.addView(new HeroScene(this),new FrameLayout.LayoutParams(-1,-1));
        }
        View shade=new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xF708101C,0xEF0B1320,0x9C101625,0x09000000}));
        hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout overlay=col();
        overlay.setPadding(dp(28),dp(20),dp(18),dp(15));
        hero.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        TextView kicker=text("ТВОЙ КИНОТЕАТР   /   XIAOMI TV CUSTOM",11,ACCENT,true);
        kicker.setLetterSpacing(.12f);overlay.addView(kicker);gap(overlay,14);
        TextView headline=text("Кино начинается\nздесь.",36,WHITE,true);
        headline.setLineSpacing(0,1.02f);overlay.addView(headline);gap(overlay,8);
        overlay.addView(text("Фильмы, сериалы и любимые приложения.\nВсё под рукой, без рекламы в меню.",14,0xFFD5DBE5,false));
        gap(overlay,15);
        LinearLayout buttons=row();
        pill(buttons,"Смотреть",this::openBestVideo);
        pill(buttons,"Smart VPN",this::openVpn);
        overlay.addView(buttons);

        int screenHeight=(int)(getResources().getDisplayMetrics().heightPixels/
            getResources().getDisplayMetrics().density);
        int heroHeight=Math.min(330,Math.max(238,(int)(screenHeight*.43f)));
        c.addView(hero,new LinearLayout.LayoutParams(-1,dp(heroHeight)));

        gap(c,17);
        boolean pinned=!getFavoriteIds().isEmpty();
        sectionHead(c,pinned?"Избранное":"Рекомендуемые приложения",
            "Удержание ОК — настроить избранное",()->switchTo(2));
        appStrip(c,pinned?favoriteApps():forHome(),10);
        gap(c,12);
        ArrayList<TvApp> recent=recentApps();
        if(!recent.isEmpty()){
            sectionHead(c,"Недавно открывали","История запуска с этого лаунчера",()->switchTo(2));
            appStrip(c,recent,7);
        }else{
            sectionHead(c,"Быстрые настройки","Сеть, VPN и переход к другим источникам",()->switchTo(6));
            LinearLayout quick=row();
            quickTile(quick,"Smart VPN",vpnConnected()?"Подключён":"Открыть v2RayTun",this::openVpn);
            quickTile(quick,"Интернет",networkSummary(),()->trySettings(Settings.ACTION_WIFI_SETTINGS));
            quickTile(quick,"HDMI","Открыть настройки",()->switchTo(3));
            c.addView(quick);
        }
    }
    private void openBestVideo(){
        // A predictable action: last video service if available, otherwise YouTube,
        // otherwise offer the video-app selection. Never open an arbitrary alphabetic app.
        for(TvApp a:recentApps())if(isMedia(a)){
            if(isYouTube(a))launchYouTube();
            else openDirect(a);
            return;
        }
        TvApp youTube=youtubeApp();
        if(youTube!=null){launchYouTube();return;}
        switchTo(1);
    }
    private ArrayList<TvApp> recentApps(){
        ArrayList<TvApp> result=new ArrayList<>();
        for(String pkg:preferences.getString("recent_apps","").split(",")){
            if(pkg.isEmpty())continue;
            for(TvApp a:apps)if(a.pkg.equals(pkg)){result.add(a);break;}
        }
        return result;
    }
    private void rememberApp(TvApp a){
        ArrayList<String> ids=new ArrayList<>();ids.add(a.pkg);
        for(String p:preferences.getString("recent_apps","").split(",")){
            if(!p.isEmpty()&&!ids.contains(p)&&ids.size()<8)ids.add(p);
        }
        preferences.edit().putString("recent_apps",TextUtils.join(",",ids)).apply();
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
        header.addView(left,new LinearLayout.LayoutParams(0,dp(47),1));
        TextView viewAll=text("Посмотреть все   ›",13,ACCENT,true);
        viewAll.setGravity(Gravity.CENTER);
        selectable(viewAll,0x7A202631);
        viewAll.setOnClickListener(v->action.run());
        header.addView(viewAll,new LinearLayout.LayoutParams(dp(150),dp(38)));
        c.addView(header);gap(c,7);
    }
    private void streaming(LinearLayout c){
        title(c,"Кино и ТВ","Твои приложения для просмотра. Никакой рекламной ленты.");
        ArrayList<TvApp> stream=new ArrayList<>();
        for(TvApp a:apps){
            String x=a.label.toLowerCase(Locale.ROOT);
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
        title(c,"Все приложения",apps.size()+" программ · ОК запускает · удержание ОК добавляет в избранное");
        LinearLayout filters=row();
        String[] labels={"Все","Избранное","Кино и ТВ","Системные","Поиск"};
        String[] ids={"all","fav","media","system","search"};
        for(int i=0;i<labels.length;i++){
            final String id=ids[i];
            TextView t=text(labels[i],13,id.equals(appFilter)?0xFF101319:WHITE,true);
            t.setGravity(Gravity.CENTER);t.setPadding(dp(10),0,dp(10),0);
            selectable(t,id.equals(appFilter)?ACCENT:SURFACE);
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
        for(TvApp a:apps){
            if(appFilter.equals("fav")&&!favorites.contains(a.pkg))continue;
            if(appFilter.equals("media")&&!isMedia(a))continue;
            if(appFilter.equals("system")&&!isSystem(a))continue;
            if(!searchQuery.isEmpty()&&!a.label.toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT)))continue;
            visible.add(a);
        }
        if(!searchQuery.isEmpty()){
            c.addView(text("Поиск: "+searchQuery,14,ACCENT,true));gap(c,12);
        }
        if(visible.isEmpty())c.addView(text("Здесь пока нет приложений. Выбери другую категорию.",16,MUTED,false));
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
        gap(c,16);
        c.addView(text("СЦЕНАРИИ",12,ACCENT,true));gap(c,9);
        setting(c,"VPN перед YouTube","Открывать v2RayTun при отсутствии соединения","youtube_vpn",true);
        action(c,"Настроить VPN","Открыть установленный клиент v2RayTun",this::openVpn);
        gap(c,16);
        c.addView(text("СИСТЕМА",12,ACCENT,true));gap(c,9);
        action(c,"Состояние памяти",freeSpace()+" · нажми для настроек хранилища",
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
        action(c,"Сбросить порядок избранного","Не удаляет установленные приложения",()->{
            new AlertDialog.Builder(this).setTitle("Очистить избранное?")
                .setMessage("Удалить только отметки избранных программ?")
                .setNegativeButton("Отмена",null).setPositiveButton("Очистить",(d,w)->{
                    preferences.edit().remove("favorites").remove("favorite_order").apply();render(5);
                }).show();
        });
        gap(c,13);
        c.addView(text("Xiaomi TV Custom 1.5 · Android TV 11 · без root и фоновых служб",12,MUTED,false));
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
    private void appStrip(LinearLayout c,List<TvApp> list,int max){
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        LinearLayout r=row();r.setPadding(dp(5),dp(5),dp(8),dp(5));r.setClipChildren(false);
        for(int i=0;i<Math.min(max,list.size());i++)tile(r,list.get(i));
        if(list.isEmpty())r.addView(text("Установленные приложения не найдены",15,MUTED,false));
        scroll.addView(r);c.addView(scroll,new LinearLayout.LayoutParams(-1,dp(134)));
    }
    private int tileWidth(){
        float width=getResources().getDisplayMetrics().widthPixels/
                getResources().getDisplayMetrics().density;
        int available=(int)width-44-174-35;
        int cols=Math.max(2,Math.min(5,available/165));
        return Math.max(120,Math.min(180,(available-12)/cols-11));
    }
    private int columns(){
        float width=getResources().getDisplayMetrics().widthPixels/
                getResources().getDisplayMetrics().density;
        return Math.max(2,Math.min(5,((int)width-44-174-35)/165));
    }
    private void appGrid(LinearLayout c,List<TvApp> list){
        int cols=columns();
        for(int i=0;i<list.size();i+=cols){
            LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);
            r.setClipChildren(false);r.setPadding(dp(3),dp(4),dp(3),dp(7));
            for(int j=i;j<Math.min(list.size(),i+cols);j++)tile(r,list.get(j));
            c.addView(r,new LinearLayout.LayoutParams(-1,dp(137)));
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
    private void tile(LinearLayout r,TvApp a){
        FrameLayout tile=new FrameLayout(this);
        int base=cardColor(a);
        tile.setClipToOutline(true);
        selectable(tile,base);
        if(a.banner!=null){
            ImageView banner=new ImageView(this);
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            banner.setImageDrawable(a.banner);
            tile.addView(banner,new FrameLayout.LayoutParams(-1,-1));
            View tint=new View(this);
            tint.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x330B101A,0xD5080C13}));
            tile.addView(tint,new FrameLayout.LayoutParams(-1,-1));
        }else{
            ImageView icon=new ImageView(this);
            icon.setImageDrawable(a.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(64),dp(62),Gravity.CENTER);
            ip.bottomMargin=dp(12);
            tile.addView(icon,ip);
        }
        boolean light=base==0xFFF0F2F6 && a.banner==null;
        TextView name=text(a.label,12,light?0xFF18202A:WHITE,true);
        name.setGravity(Gravity.CENTER);name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams titleParams=new FrameLayout.LayoutParams(-1,dp(28),Gravity.BOTTOM);
        titleParams.leftMargin=dp(7);titleParams.rightMargin=dp(7);
        titleParams.bottomMargin=dp(5);
        tile.addView(name,titleParams);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(tileWidth()),dp(116));
        lp.setMargins(dp(3),0,dp(9),0);
        r.addView(tile,lp);
        tile.setOnClickListener(v->{if(isYouTube(a))launchYouTube();else openDirect(a);});
        tile.setOnLongClickListener(v->{editFavorite(a);return true;});
        if(a.pkg.equals(selectedPackage)){
            selectedPackage="";
            tile.post(tile::requestFocus);
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
            for(TvApp a:apps)if(a.pkg.equals(id)){result.add(a);break;}
        }
        return result;
    }
    private void editFavorite(TvApp app){
        ArrayList<String> favorite=getFavoriteIds();
        boolean contained=favorite.contains(app.pkg);
        String[] options=contained?
            new String[]{"Переместить левее","Переместить правее","Убрать из избранного"}:
            new String[]{"Добавить в избранное"};
        new AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options,(dialog,which)->{
                if(!contained)favorite.add(app.pkg);
                else if(which==0 && favorite.indexOf(app.pkg)>0){
                    int index=favorite.indexOf(app.pkg);
                    Collections.swap(favorite,index,index-1);
                }else if(which==1 && favorite.indexOf(app.pkg)<favorite.size()-1){
                    int index=favorite.indexOf(app.pkg);
                    Collections.swap(favorite,index,index+1);
                }else if(which==2)favorite.remove(app.pkg);
                saveFavoriteIds(favorite);
                selectedPackage=app.pkg;
                render(page);
            }).setNegativeButton("Отмена",null).show();
    }
    private List<TvApp> forHome(){
        ArrayList<TvApp> list=new ArrayList<>(apps);
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
