package com.example.corwin.jukebox.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;

public class CircleView extends View {

    private static final String DRAW_COLOR_HEX = "#000000";
    private static final String FILL_COLOR_HEX = "#FFFFFF";
    private final Paint drawPaint;
    private final Paint fillPaint;
    private       float size;

    public CircleView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        drawPaint = new Paint();
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setColor(Color.parseColor(DRAW_COLOR_HEX));
        drawPaint.setAntiAlias(true);
        fillPaint = new Paint();
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.parseColor(FILL_COLOR_HEX));
        fillPaint.setAlpha(128);
        setOnMeasureCallback();
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(size, size, size, fillPaint);
        canvas.drawCircle(size, size, size, drawPaint);
    }

    private void setOnMeasureCallback() {
        ViewTreeObserver vto = getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                size = getMeasuredWidth() / 2;
            }
        });
    }
}
