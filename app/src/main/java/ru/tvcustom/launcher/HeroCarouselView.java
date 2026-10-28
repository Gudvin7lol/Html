package ru.tvcustom.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

/**
 * Offline cinematic carousel. Full-HD WebP files are shipped with the APK.
 * Only the displayed frame and the incoming frame can be in memory at once.
 */
public final class HeroCarouselView extends FrameLayout {
    public static final String[] POSTERS={"poster1.webp","poster2.webp","poster3.webp","poster4.webp"};
    private final SharedPreferences prefs;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final IntConsumer onPosterChanged;
    private ImageView foreground;
    private ImageView incoming;
    private int selected;
    private int request=0;
    private boolean active=false;
    private boolean attached=false;
    private final Runnable advance=new Runnable(){
        @Override public void run(){ if(active && attached)show((selected+1)%POSTERS.length,true); }
    };

    public HeroCarouselView(Context context,SharedPreferences preferences,IntConsumer onChange){
        super(context);
        prefs=preferences;
        onPosterChanged=onChange;
        selected=Math.floorMod(preferences.getInt("carousel_index",0),POSTERS.length);
        foreground=new ImageView(context);
        foreground.setScaleType(ImageView.ScaleType.CENTER_CROP);
        incoming=new ImageView(context);
        incoming.setScaleType(ImageView.ScaleType.CENTER_CROP);
        incoming.setAlpha(0f);
        addView(foreground,new FrameLayout.LayoutParams(-1,-1));
        addView(incoming,new FrameLayout.LayoutParams(-1,-1));
    }
    public int getSelected(){return selected;}
    public void setActive(boolean on){
        active=on;
        schedule();
    }
    public void next(){show((selected+1)%POSTERS.length,true);}
    public void previous(){show((selected+POSTERS.length-1)%POSTERS.length,true);}
    public void choose(int which){show(Math.floorMod(which,POSTERS.length),true);}
    private void schedule(){
        handler.removeCallbacks(advance);
        if(active && attached && prefs.getBoolean("carousel_autoplay",true)){
            int seconds=Math.max(10,Math.min(120,prefs.getInt("carousel_interval",30)));
            handler.postDelayed(advance,seconds*1000L);
        }
    }
    private Bitmap decode(int index){
        try(InputStream stream=getContext().getAssets().open(POSTERS[index])){
            BitmapFactory.Options opts=new BitmapFactory.Options();
            opts.inPreferredConfig=Bitmap.Config.RGB_565;
            // 1080p when needed; 960p-size in memory on smaller physical displays.
            if(getResources().getDisplayMetrics().widthPixels<1280)opts.inSampleSize=2;
            return BitmapFactory.decodeStream(stream,null,opts);
        }catch(Exception ex){return null;}
    }
    private void show(int index,boolean animate){
        if(!attached)return;
        selected=index;
        prefs.edit().putInt("carousel_index",index).apply();
        schedule();
        int serial=++request;
        decoder.execute(()->{
            final Bitmap bitmap=decode(index);
            handler.post(()->{
                if(serial!=request || !attached || bitmap==null){
                    if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();
                    return;
                }
                incoming.animate().cancel();
                incoming.bringToFront();
                incoming.setImageBitmap(bitmap);
                incoming.setAlpha(animate?0f:1f);
                if(animate){
                    incoming.animate().alpha(1f).setDuration(850).withEndAction(()->finishSwitch(serial)).start();
                } else finishSwitch(serial);
                if(onPosterChanged!=null)onPosterChanged.accept(index);
            });
        });
    }
    private void finishSwitch(int serial){
        if(serial!=request||!attached)return;
        ImageView previous=foreground;
        foreground=incoming;
        incoming=previous;
        incoming.setImageDrawable(null);
        incoming.setAlpha(0f);
        // Old bitmap becomes GC-eligible after its ImageView releases it.
    }
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        attached=true;
        show(selected,false);
    }
    @Override protected void onDetachedFromWindow(){
        attached=false;
        active=false;
        request++;
        handler.removeCallbacks(advance);
        foreground.animate().cancel();
        incoming.animate().cancel();
        foreground.setImageDrawable(null);
        incoming.setImageDrawable(null);
        decoder.shutdownNow();
        super.onDetachedFromWindow();
    }
}