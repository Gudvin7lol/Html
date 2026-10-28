package ru.tvcustom.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

/**
 * Offline poster carousel with exactly two ImageViews and asynchronous decode.
 * A new image is made visible only after successful decode. The 30-second timer
 * starts at the end of each transition, never while the image is loading.
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
    private int displayed=-1;
    private int request=0;
    private boolean active=false;
    private boolean attached=false;
    private final Runnable advance=()->{
        if(active && attached)show((selected+1)%POSTERS.length,true);
    };

    public HeroCarouselView(Context context,SharedPreferences preferences,IntConsumer callback){
        super(context);
        prefs=preferences;
        onPosterChanged=callback;
        selected=Math.floorMod(prefs.getInt("carousel_index",0),POSTERS.length);
        foreground=new ImageView(context);
        foreground.setScaleType(ImageView.ScaleType.CENTER_CROP);
        incoming=new ImageView(context);
        incoming.setScaleType(ImageView.ScaleType.CENTER_CROP);
        incoming.setAlpha(0f);
        addView(foreground,new FrameLayout.LayoutParams(-1,-1));
        addView(incoming,new FrameLayout.LayoutParams(-1,-1));
    }
    public int getSelected(){return selected;}
    public void setActive(boolean enabled){
        active=enabled;
        resetTimer();
    }
    public void next(){show((selected+1)%POSTERS.length,true);}
    public void previous(){show((selected+POSTERS.length-1)%POSTERS.length,true);}
    public void choose(int which){show(Math.floorMod(which,POSTERS.length),true);}
    public void refreshSettings(){resetTimer();}

    private void resetTimer(){
        handler.removeCallbacks(advance);
        if(active && attached && displayed>=0 && prefs.getBoolean("carousel_autoplay",true)){
            int seconds=Math.max(10,Math.min(120,prefs.getInt("carousel_interval",30)));
            handler.postDelayed(advance,seconds*1000L);
        }
    }
    private Bitmap decode(int index){
        try(InputStream stream=getContext().getAssets().open(POSTERS[index])){
            BitmapFactory.Options opts=new BitmapFactory.Options();
            opts.inPreferredConfig=Bitmap.Config.RGB_565;
            opts.inScaled=false; // Assets should never be density-rescaled by Android.
            if(getResources().getDisplayMetrics().widthPixels<1280)opts.inSampleSize=2;
            return BitmapFactory.decodeStream(stream,null,opts);
        }catch(Exception ex){
            Log.w("TVCarousel","Cannot decode slide "+index,ex);
            return null;
        }
    }
    private void show(int index,boolean animate){
        if(!attached){selected=index;return;}
        if(index==displayed && foreground.getDrawable()!=null){
            // Cancel any pending/in-flight transition back to the currently visible image.
            request++;
            incoming.animate().cancel();
            incoming.setImageDrawable(null);
            incoming.setAlpha(0f);
            selected=index;
            resetTimer();
            return;
        }
        selected=index;
        handler.removeCallbacks(advance);
        final int serial=++request;
        decoder.execute(()->{
            final Bitmap bitmap=decode(index);
            handler.post(()->{
                if(serial!=request || !attached){
                    if(bitmap!=null && !bitmap.isRecycled())bitmap.recycle();
                    return;
                }
                if(bitmap==null){
                    selected=displayed>=0?displayed:selected;
                    resetTimer();
                    return;
                }
                incoming.animate().cancel();
                incoming.bringToFront();
                incoming.setImageBitmap(bitmap);
                boolean crossfade=animate && foreground.getDrawable()!=null;
                incoming.setAlpha(crossfade?0f:1f);
                if(crossfade){
                    incoming.animate().alpha(1f).setDuration(850)
                        .withEndAction(()->finishSwitch(serial,index)).start();
                }else finishSwitch(serial,index);
            });
        });
    }
    private void finishSwitch(int serial,int index){
        if(serial!=request || !attached)return;
        ImageView old=foreground;
        foreground=incoming;
        incoming=old;
        incoming.animate().cancel();
        incoming.setImageDrawable(null);
        incoming.setAlpha(0f);
        displayed=index;
        selected=index;
        prefs.edit().putInt("carousel_index",index).apply();
        if(onPosterChanged!=null)onPosterChanged.accept(index);
        Log.i("TVCarousel","visible poster="+index);
        resetTimer();
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
