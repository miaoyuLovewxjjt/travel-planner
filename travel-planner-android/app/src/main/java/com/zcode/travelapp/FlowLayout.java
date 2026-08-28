package com.zcode.travelapp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * 简易流式布局：子项水平排列、放不下自动换行（用于 chips 标签组）。
 */
public class FlowLayout extends ViewGroup {

    private int hGap = 6, vGap = 6;

    public FlowLayout(Context c) { this(c, null); }
    public FlowLayout(Context c, AttributeSet a) { super(c, a); }

    public void setGaps(int hDp, int vDp, Context c) {
        hGap = Util.dp(c, hDp);
        vGap = Util.dp(c, vDp);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxW = MeasureSpec.getSize(widthMeasureSpec);
        int padL = getPaddingLeft(), padT = getPaddingTop(), padR = getPaddingRight(), padB = getPaddingBottom();
        int usable = maxW - padL - padR;
        int x = padL, y = padT, rowH = 0, totalH = padT + padB;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            measureChild(v,
                    MeasureSpec.makeMeasureSpec(usable, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int cw = v.getMeasuredWidth() + (x == padL ? 0 : hGap);
            if (x + cw > padL + usable && x > padL) { // 换行
                x = padL;
                y += rowH + vGap;
                rowH = 0;
            }
            x += cw;
            rowH = Math.max(rowH, v.getMeasuredHeight());
        }
        totalH += y + rowH - padT;
        setMeasuredDimension(maxW, totalH);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxW = getWidth();
        int padL = getPaddingLeft(), padT = getPaddingTop(), padR = getPaddingRight();
        int usable = maxW - padL - padR;
        int x = padL, y = padT, rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            int cw = v.getMeasuredWidth() + (x == padL ? 0 : hGap);
            if (x + cw > padL + usable && x > padL) {
                x = padL;
                y += rowH + vGap;
                rowH = 0;
            }
            v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
            x += cw;
            rowH = Math.max(rowH, v.getMeasuredHeight());
        }
    }
}