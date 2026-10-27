package ru.tvcustom.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/**
 * Original offline cinematic backdrop. Deliberately static: no video decoder,
 * network image fetch, Lottie runtime, or allocated full-resolution Bitmap.
 */
public final class HeroScene extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    public HeroScene(Context ctx){ super(ctx); }

    @Override protected void onDraw(Canvas original) {
        final float w=getWidth(), h=getHeight();
        if(w<=0||h<=0)return;
        original.save();
        original.scale(w/1200f,h/440f);
        Canvas c=original;
        p.setShader(new LinearGradient(0,40,1200,440,
            new int[]{0xFF0B1522,0xFF141726,0xFF5C3334,0xFF211723},
            new float[]{0f,.33f,.75f,1f},Shader.TileMode.CLAMP));
        c.drawRect(0,0,1200,440,p);p.setShader(null);

        // Warm glow occupies the right half, leaving low-contrast text space.
        p.setShader(new RadialGradient(882,139,372,
            new int[]{0xF2F8A671,0xAFB95D49,0x334C303D,0x00000000},
            new float[]{0f,.42f,.71f,1f},Shader.TileMode.CLAMP));
        c.drawCircle(882,139,372,p);p.setShader(null);

        // Backlit celestial disc with soft atmospheric rim.
        p.setShader(new RadialGradient(905,110,202,
            new int[]{0xFFFFC58D,0xFFF7A66F,0xFFD57558,0xFF663C3D},
            new float[]{0f,.58f,.83f,1f},Shader.TileMode.CLAMP));
        c.drawCircle(905,110,202,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4f);p.setColor(0x8EFFE2B3);
        c.drawCircle(905,110,202,p);p.setStyle(Paint.Style.FILL);

        p.setShader(new LinearGradient(750,0,1110,250,
            0x0035242D,0x76382435,Shader.TileMode.CLAMP));
        c.drawOval(746,0,1090,239,p);p.setShader(null);

        // Deterministic subtle star dust. Static geometry avoids animation cost.
        for(int i=0;i<72;i++){
            int k=(i*137+53)%1200;
            float y=(i*59+13)%245;
            p.setColor(i%8==0?0xA2FFE7D4:0x4FDFDEEF);
            c.drawCircle(k,y,(i%11==0)?1.4f:.66f,p);
        }
        p.setShader(new LinearGradient(0,170,0,310,0x000F1626,0x77282434,Shader.TileMode.CLAMP));
        c.drawRect(0,170,1200,310,p);p.setShader(null);

        // Multiple distant terrain silhouettes, inspired by the approved look.
        mountain(c,0x995B3B46,new float[]{
            0,378,100,355,235,300,338,345,450,268,566,339,
            714,250,813,310,933,232,1066,313,1200,260,1200,440,0,440});
        mountain(c,0xFF372E39,new float[]{
            0,403,101,345,202,365,317,307,442,384,573,330,
            691,381,814,310,958,385,1098,316,1200,351,1200,440,0,440});
        mountain(c,0xFF1B2432,new float[]{
            0,421,136,374,257,406,392,347,505,416,636,369,
            782,418,899,362,1069,402,1200,365,1200,440,0,440});

        p.setShader(new LinearGradient(0,315,0,440,
            new int[]{0x00222C3C,0x60202432,0xED111825},
            null,Shader.TileMode.CLAMP));
        c.drawRect(0,315,1200,440,p);p.setShader(null);
        original.restore();
    }
    private void mountain(Canvas c,int color,float[] pts){
        path.reset();
        path.moveTo(pts[0],pts[1]);
        for(int i=2;i<pts.length;i+=2)path.lineTo(pts[i],pts[i+1]);
        path.close();p.setColor(color);c.drawPath(path,p);
    }
}
