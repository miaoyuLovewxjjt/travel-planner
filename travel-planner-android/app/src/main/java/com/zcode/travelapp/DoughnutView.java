package com.zcode.travelapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 消费构成环形图（Canvas 绘制，纯框架）。
 * 数据：一组 (文字, 颜色, 数值)；从 12 点方向顺时针画弧；中心显示总值。
 */
public class DoughnutView extends View {

    static final class Slice {
        final String label;
        final int color;
        final double value;
        Slice(String label, int color, double value) { this.label = label; this.color = color; this.value = value; }
    }

    private final List<Slice> slices = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    public DoughnutView(Context c) { this(c, null); }
    public DoughnutView(Context c, AttributeSet a) {
        super(c, a);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(List<Slice> data) {
        slices.clear();
        slices.addAll(data);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        double total = 0;
        for (Slice s : slices) total += s.value;

        // 放大后略有防锯齿偏移的圆环
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(w * 0.16f);

        if (total <= 0) {
            paint.setColor(0xFFE8E8EE);
            arcRect.set(cx - w * 0.36f, cy - w * 0.36f, cx + w * 0.36f, cy + w * 0.36f);
            canvas.drawArc(arcRect, 0, 360, false, paint);
        } else {
            float start = -90f; // 12 点方向
            for (Slice s : slices) {
                if (s.value <= 0) continue;
                float sweep = (float) (s.value / total * 360f);
                if (slices.size() == 1) sweep = 360f; // 单类整圆
                paint.setColor(s.color);
                arcRect.set(cx - w * 0.36f, cy - w * 0.36f, cx + w * 0.36f, cy + w * 0.36f);
                canvas.drawArc(arcRect, start, sweep - 0.5f, false, paint); // 留细微缝隙
                start += sweep;
            }
        }

        // 中心留空（总花费已写在各卡片标题旁，环内不重复显示）
    }
}