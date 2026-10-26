package ru.tvcustom.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

public final class SceneView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean scenic = true;
    public SceneView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
    public void setScenic(boolean value) { scenic = value; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        canvas.save();
        canvas.scale(w / 1280f, h / 720f);
        p.setShader(new LinearGradient(0, 0, 1200, 710,
            new int[]{0xFF090E18,0xFF141A27,0xFF342C31,0xFF111821}, null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,1280,720,p); p.setShader(null);
        if (scenic) {
            p.setShader(new RadialGradient(990,110,440,new int[]{0xBBE98653,0x554C3C43,0x00000000},
                null,Shader.TileMode.CLAMP));
            canvas.drawCircle(990,110,440,p); p.setShader(null);
            mountain(canvas,0xFF252C3B,new float[]{0,590,175,480,280,515,450,385,620,540,770,350,945,520,1085,395,1280,550,1280,720,0,720});
            mountain(canvas,0xFF141E2B,new float[]{0,660,140,550,270,625,470,486,590,635,800,465,968,610,1140,490,1280,580,1280,720,0,720});
            mountain(canvas,0xFF0C1420,new float[]{0,685,175,625,340,710,540,606,735,704,890,592,1100,691,1280,630,1280,720,0,720});
        }
        p.setShader(new LinearGradient(0,0,0,720,0x08000000,0xD8090E16,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,1280,720,p); p.setShader(null);
        canvas.restore();
    }
    private void mountain(Canvas c, int color, float[] pts) {
        Path path = new Path(); path.moveTo(pts[0],pts[1]);
        for(int i=2;i<pts.length;i+=2) path.lineTo(pts[i],pts[i+1]);
        path.close(); p.setColor(color); c.drawPath(path,p);
    }
}
