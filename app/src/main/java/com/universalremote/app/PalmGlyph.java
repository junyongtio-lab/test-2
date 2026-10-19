package com.universalremote.app;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Original, resolution-independent line artwork drawn for Palm Remote. */
final class PalmGlyph extends Drawable {
    private final String kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    PalmGlyph(String kind, int color) {
        this.kind=kind;paint.setColor(color);paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);
    }
    @Override public void draw(Canvas canvas) {
        canvas.save();canvas.translate(getBounds().left,getBounds().top);
        canvas.scale(getBounds().width()/32f,getBounds().height()/32f);
        if(kind.equals("tv")) {
            rect(canvas,3,5,29,23,3);line(canvas,11,28,21,28);line(canvas,16,23,16,28);
        } else if(kind.equals("set_top_box")) {
            rect(canvas,3,13,29,26,3);line(canvas,7,18,18,18);circle(canvas,24,20,1);
            arc(canvas,8,1,24,17,220,100);arc(canvas,12,5,20,13,220,100);
        } else if(kind.equals("air_conditioner")) {
            rect(canvas,3,5,29,19,3);line(canvas,6,14,26,14);circle(canvas,24,9,0.6f);
            line(canvas,9,23,9,28);line(canvas,16,22,16,27);line(canvas,23,23,23,28);
        } else if(kind.equals("fan")) {
            circle(canvas,16,13,10);circle(canvas,16,13,2);
            line(canvas,16,15,12,20);line(canvas,18,13,24,14);line(canvas,15,11,12,6);
            line(canvas,16,23,16,28);line(canvas,10,29,22,29);
        } else if(kind.equals("home")) {
            line(canvas,4,15,16,5);line(canvas,16,5,28,15);line(canvas,7,13,7,28);
            line(canvas,25,13,25,28);line(canvas,7,28,25,28);rect(canvas,13,20,19,28,1);
        } else if(kind.equals("help")) {
            circle(canvas,16,16,12);arc(canvas,11,8,21,18,180,270);line(canvas,16,18,16,20);
            circle(canvas,16,24,.6f);
        } else if(kind.equals("devices")) {
            rect(canvas,3,4,18,19,3);rect(canvas,14,13,29,28,3);
        } else {
            rect(canvas,8,2,24,30,6);circle(canvas,16,8,2);circle(canvas,16,18,5);
            line(canvas,13,26,19,26);
        }
        canvas.restore();
    }
    private void line(Canvas c,float x1,float y1,float x2,float y2){c.drawLine(x1,y1,x2,y2,paint);}
    private void rect(Canvas c,float l,float t,float r,float b,float radius){c.drawRoundRect(new RectF(l,t,r,b),radius,radius,paint);}
    private void circle(Canvas c,float x,float y,float radius){c.drawCircle(x,y,radius,paint);}
    private void arc(Canvas c,float l,float t,float r,float b,float start,float sweep){c.drawArc(new RectF(l,t,r,b),start,sweep,false,paint);}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
