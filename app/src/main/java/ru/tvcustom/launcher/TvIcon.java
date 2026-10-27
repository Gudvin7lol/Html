package ru.tvcustom.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small antialiased native line icons; no emoji font dependency. */
public final class TvIcon extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final int symbol;
    private final int color;
    public TvIcon(Context c,int type,int tint){super(c);symbol=type;color=tint;}
    @Override protected void onDraw(Canvas original){
        if(getWidth()==0||getHeight()==0)return;
        original.save();
        original.translate((getWidth()-Math.min(getWidth(),getHeight()))*.5f,
                            (getHeight()-Math.min(getWidth(),getHeight()))*.5f);
        original.scale(Math.min(getWidth(),getHeight())/24f,
                       Math.min(getWidth(),getHeight())/24f);
        Canvas c=original;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);
        p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        switch(symbol){
          case 0: // Home
            path.reset();path.moveTo(3,11);path.lineTo(12,3);path.lineTo(21,11);
            path.moveTo(5.5f,10);path.lineTo(5.5f,21);path.lineTo(18.5f,21);path.lineTo(18.5f,10);
            path.moveTo(10,21);path.lineTo(10,15);path.lineTo(14,15);path.lineTo(14,21);
            c.drawPath(path,p);break;
          case 1: // Films
            c.drawRoundRect(3,6,21,19,2,2,p);
            c.drawLine(3,10,21,10,p);
            c.drawLine(7,6,10,10,p);c.drawLine(13,6,16,10,p);
            break;
          case 2: // Grid
            for(int x=0;x<2;x++)for(int y=0;y<2;y++)
                c.drawRoundRect(3+x*10,3+y*10,11+x*10,11+y*10,1.5f,1.5f,p);
            break;
          case 3: // HDMI / screen
            c.drawRoundRect(2.5f,4.5f,21.5f,17.5f,2,2,p);
            c.drawLine(9,21,15,21,p);c.drawLine(12,17.5f,12,21,p);
            break;
          case 4: // Shield
            path.reset();path.moveTo(12,2);path.lineTo(20,5);path.lineTo(20,11);
            path.cubicTo(20,16,17,20,12,22);path.cubicTo(7,20,4,16,4,11);
            path.lineTo(4,5);path.close();c.drawPath(path,p);
            path.reset();path.moveTo(9,12);path.lineTo(11,14);path.lineTo(15.5f,9);
            c.drawPath(path,p);
            break;
          case 5: // Gear
            c.drawCircle(12,12,4,p);
            for(int i=0;i<8;i++){
                double a=i*Math.PI/4.0;
                float x=(float)Math.cos(a), y=(float)Math.sin(a);
                c.drawLine(12+x*8,12+y*8,12+x*10.5f,12+y*10.5f,p);
            }
            break;
          case 6: // Wifi
            path.reset();path.moveTo(2.5f,9);
            path.quadTo(12,-.5f,21.5f,9);
            path.moveTo(6.5f,13);path.quadTo(12,8.5f,17.5f,13);
            c.drawPath(path,p);p.setStyle(Paint.Style.FILL);c.drawCircle(12,18,2,p);break;
          case 7: // Clock
            c.drawCircle(12,12,9,p);
            c.drawLine(12,12,12,6.5f,p);c.drawLine(12,12,16.5f,14,p);break;
          case 8: // Play
            path.reset();path.moveTo(8,5);path.lineTo(19,12);path.lineTo(8,19);path.close();
            p.setStyle(Paint.Style.FILL);c.drawPath(path,p);break;
          default: // List
            for(int y=6;y<=18;y+=6){c.drawLine(5,y,20,y,p);c.drawCircle(2.5f,y,.5f,p);}
        }
        original.restore();
    }
}
