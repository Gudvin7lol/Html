package ru.tvcustom.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/**
 * A lightweight, cropped cinematic thumbnail from the existing hero image.
 * No bitmap copies, network calls or allocations during onDraw.
 * The same real installed apps are the destinations, not invented movie items.
 */
public final class PosterSliceView extends View {
    private final Bitmap poster;
    private final int variation;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Rect source=new Rect();
    private final RectF target=new RectF();

    public PosterSliceView(Context context,Bitmap bmp,int variation){
        super(context);
        poster=bmp;
        this.variation=Math.floorMod(variation,4);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(poster==null || poster.isRecycled() || getWidth()<1 || getHeight()<1)return;
        int bw=poster.getWidth(),bh=poster.getHeight();
        float targetRatio=getWidth()/(float)getHeight();
        float wFraction=Math.min(.76f,Math.max(.46f,targetRatio*bh/bw));
        float left;
        switch(variation){
            case 0:left=.40f;break;
            case 1:left=.52f;break;
            case 2:left=.24f;break;
            default:left=.35f;break;
        }
        left=Math.max(0,Math.min(1-wFraction,left));
        int x=(int)(bw*left),cropW=Math.max(1,(int)(bw*wFraction));
        source.set(x,0,Math.min(bw,x+cropW),bh);
        target.set(0,0,getWidth(),getHeight());
        canvas.drawBitmap(poster,source,target,paint);
    }
}
