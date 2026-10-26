package ru.tvcustom.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** Lightweight Canvas art: no remote image, no large decoded bitmap, no animated wallpaper. */
public final class HeroScene extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public HeroScene(Context c){super(c);}
    @Override protected void onDraw(Canvas c){
        float w=getWidth(), h=getHeight();
        if(w<=0||h<=0)return;
        c.save(); c.scale(w/1000f,h/300f);
        p.setShader(new LinearGradient(0,0,950,300,
            new int[]{0xFF1C1B27,0xFF4A2730,0xFFB95C37,0xFF211E29},
            new float[]{0f,.45f,.78f,1f},Shader.TileMode.CLAMP));
        c.drawRect(0,0,1000,300,p);p.setShader(null);
        p.setShader(new RadialGradient(750,89,196,
            new int[]{0xFFFFD394,0xDFFF9D59,0x77D06A44,0x00B55A38},
            new float[]{0f,.52f,.72f,1f},Shader.TileMode.CLAMP));
        c.drawCircle(750,89,196,p);p.setShader(null);
        // Ember-coloured atmospheric planet
        p.setShader(new LinearGradient(640,-35,870,245,
            0xFFF5A96B,0xFF803E38,Shader.TileMode.CLAMP));
        c.drawCircle(760,90,122,p);p.setShader(null);
        p.setColor(0x44914637);
        for(int i=0;i<5;i++) c.drawOval(660+i*3,20+i*40,870-i*8,42+i*34,p);
        // Subtle deterministic stars
        p.setColor(0x88FFFFFF);
        for(int i=0;i<34;i++){
            float x=((i*173)%977)+5, y=((i*79)%125)+4;
            c.drawCircle(x,y, i%7==0?1.5f:.65f,p);
        }
        mountain(c,0xFF58333A, new float[]{0,278,180,198,300,262,428,178,565,243,735,157,860,246,1000,190,1000,300,0,300});
        mountain(c,0xFF322A31, new float[]{0,290,110,232,240,282,412,222,530,281,711,220,833,271,1000,226,1000,300,0,300});
        mountain(c,0xFF171B26, new float[]{0,298,200,274,323,294,507,247,655,289,805,254,1000,277,1000,300,0,300});
        p.setShader(new LinearGradient(0,0,790,0,
            new int[]{0xE60B121F,0xC5131721,0x62131822,0x00000000},
            new float[]{0f,.44f,.70f,1f},Shader.TileMode.CLAMP));
        c.drawRect(0,0,1000,300,p);p.setShader(null);
        c.restore();
    }
    private void mountain(Canvas c,int color,float[] v){
        Path path=new Path(); path.moveTo(v[0],v[1]);
        for(int i=2;i<v.length;i+=2)path.lineTo(v[i],v[i+1]);
        path.close();p.setColor(color);c.drawPath(path,p);
    }
}
