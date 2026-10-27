package ru.tvcustom.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Lightweight offline decorative card backdrop. Constant memory use, no bitmaps,
 * downloaded poster requests or infinite animations.
 */
public final class CardArtView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int baseColor;
    public CardArtView(Context context, int tint){
        super(context);
        this.baseColor=tint;
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas){
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        p.setShader(new LinearGradient(0,0,w,h,
            new int[]{blend(baseColor,0xFF35425B,.24f),baseColor,blend(baseColor,0xFF060E19,.53f)},
            new float[]{0,.49f,1},Shader.TileMode.CLAMP));
        canvas.drawRoundRect(0,0,w,h,13,13,p);
        p.setShader(new RadialGradient(w*.79f,h*.11f,Math.max(w,h)*.78f,
            new int[]{0x66F1AF91,0x1AF1AF91,0x00F1AF91},
            new float[]{0f,.4f,1f},Shader.TileMode.CLAMP));
        canvas.drawCircle(w*.79f,h*.11f,Math.max(w,h)*.78f,p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1,w*.004f));
        p.setColor(0x249FD0E1);
        canvas.drawCircle(w*.94f,h*.03f,w*.48f,p);
        canvas.drawCircle(w*.94f,h*.03f,w*.34f,p);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0,h*.58f,0,h,
            new int[]{0x00090D14,0x8C0B111C,0xDE0A0E16},
            null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,h*.58f,w,h,p);
        p.setShader(null);
    }
    private static int blend(int a,int b,float amount){
        float k=Math.max(0,Math.min(1,amount));
        int r=(int)(((a>>16)&255)*(1-k)+((b>>16)&255)*k);
        int g=(int)(((a>>8)&255)*(1-k)+((b>>8)&255)*k);
        int bl=(int)((a&255)*(1-k)+(b&255)*k);
        return 0xFF000000|(r<<16)|(g<<8)|bl;
    }
}