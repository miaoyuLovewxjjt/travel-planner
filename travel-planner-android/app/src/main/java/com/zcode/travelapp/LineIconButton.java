package com.zcode.travelapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * 网页版同款细线条图标按钮（app.html pencilIcon/trashIcon 的 SVG）。
 * 平时：图标为 15/24 比例的细线（stroke 1.6dp）、网页灰 #55556A、无底色；
 * 按压时：底 #E8E8EF、编辑变蓝 #3D5AF1 / 删除变红 #E5484D（网页 :hover 语义）。
 * 全项目统一使用（尺寸固定 30dp，不再忽大忽小）。
 */
public class LineIconButton extends View {

    private static final int NORMAL = 0xFF8A8A9A;   // 淡灰（用户要求"颜色淡一点"）
    private static final int PRESS_BG = 0xFFE8E8EF; // 网页 :hover 底

    private final Path[] paths;
    private final int pressedColor;
    private boolean pressed;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bgRect = new RectF();

    private final float sizeDp;

    private LineIconButton(Context c, Path[] paths, int pressedColor, String desc, float sizeDp) {
        super(c);
        this.paths = paths;
        this.pressedColor = pressedColor;
        this.sizeDp = sizeDp;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeWidth(dp(1.1f)); // 更细（用户要求"再细一点"）
        bg.setColor(PRESS_BG);
        if (desc != null) setContentDescription(desc);
    }

    /** 编辑（铅笔）：默认灰，按压蓝（整体居中：内容 y 2.5~21.5，中心 12） */
    static LineIconButton edit(Context c) {
        Path body = new Path(); // 铅笔轮廓
        body.moveTo(17f, 2.5f);
        body.lineTo(21f, 6.5f);
        body.lineTo(7.5f, 20f);
        body.lineTo(2f, 21.5f);
        body.lineTo(3.5f, 16f);
        body.close();
        Path line = new Path(); // 笔杆斜线
        line.moveTo(15f, 4.5f);
        line.lineTo(19f, 8.5f);
        return build(c, new Path[]{body, line}, 0xFF3D5AF1, "编辑", 28);
    }

    /** 删除（垃圾桶）：默认灰，按压红（内容 y 3~21，中心 12） */
    static LineIconButton del(Context c) {
        Path lid = new Path();   // 桶顶线
        lid.moveTo(3f, 5f);
        lid.lineTo(21f, 5f);
        Path cover = new Path(); // 盖子
        cover.moveTo(8f, 5f);
        cover.lineTo(8f, 3f);
        cover.lineTo(16f, 3f);
        cover.lineTo(16f, 5f);
        Path body = new Path();  // 桶身
        body.moveTo(19f, 5f);
        body.lineTo(18f, 19f);
        body.lineTo(16f, 21f);
        body.lineTo(8f, 21f);
        body.lineTo(6f, 19f);
        body.lineTo(5f, 5f);
        Path lines = new Path(); // 桶身条纹
        lines.moveTo(10f, 10f);
        lines.lineTo(10f, 16f);
        lines.moveTo(14f, 10f);
        lines.lineTo(14f, 16f);
        return build(c, new Path[]{lid, cover, body, lines}, 0xFFE5484D, "删除", 28);
    }

    /** 打印（导出 PDF）：默认淡灰，按压蓝（与编辑/删除同一套线条；内容 y 4~20，中心 12） */
    static LineIconButton printer(Context c) {
        Path paper = new Path();  // 纸（机身顶部露出的纸）
        paper.moveTo(7f, 4f);
        paper.lineTo(17f, 4f);
        paper.lineTo(17f, 10f);
        paper.lineTo(7f, 10f);
        paper.close();
        Path body = new Path();   // 机身（梯形）
        body.moveTo(5f, 10f);
        body.lineTo(19f, 10f);
        body.lineTo(21f, 17f);
        body.lineTo(3f, 17f);
        body.close();
        Path slot = new Path();   // 出纸口
        slot.moveTo(8f, 17f);
        slot.lineTo(8f, 20f);
        slot.lineTo(16f, 20f);
        slot.lineTo(16f, 17f);
        return build(c, new Path[]{paper, body, slot}, 0xFF4D96FF, "PDF", 28);
    }


    /** 新建（＋）：十字 */
    static LineIconButton plus(Context c) {
        Path p = new Path();
        p.moveTo(12f, 5f); p.lineTo(12f, 19f);
        p.moveTo(5f, 12f); p.lineTo(19f, 12f);
        return build(c, new Path[]{p}, 0xFF4D96FF, "新建", 28);
    }

    /** 导出（向上箭头 + 托盘底） */
    static LineIconButton export(Context c) {
        Path p = new Path();
        p.moveTo(12f, 4f); p.lineTo(12f, 14f);
        p.moveTo(7f, 9f); p.lineTo(12f, 4f); p.lineTo(17f, 9f);
        p.moveTo(4f, 15f); p.lineTo(4f, 20f); p.lineTo(20f, 20f); p.lineTo(20f, 15f);
        return build(c, new Path[]{p}, 0xFF4D96FF, "导出", 28);
    }

    /** 导入（向下箭头 + 托盘顶） */
    static LineIconButton import_(Context c) {
        Path p = new Path();
        p.moveTo(12f, 14f); p.lineTo(12f, 4f);
        p.moveTo(7f, 9f); p.lineTo(12f, 14f); p.lineTo(17f, 9f);
        p.moveTo(4f, 15f); p.lineTo(4f, 20f); p.lineTo(20f, 20f); p.lineTo(20f, 15f);
        return build(c, new Path[]{p}, 0xFF4D96FF, "导入", 28);
    }

    /** 设置（齿轮：外圆 + 内圆 + 齿） */
    static LineIconButton gear(Context c) {
        Path teeth = new Path();
        float r = 7f;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            double x1 = 12 + r * Math.cos(a), y1 = 12 + r * Math.sin(a);
            double x2 = 12 + r * 1.55 * Math.cos(a), y2 = 12 + r * 1.55 * Math.sin(a);
            teeth.moveTo((float) x1, (float) y1);
            teeth.lineTo((float) x2, (float) y2);
        }
        Path inner = new Path();
        inner.addCircle(12f, 12f, 3.4f, android.graphics.Path.Direction.CW);
        return build(c, new Path[]{teeth, inner}, 0xFF4D96FF, "设置", 28);
    }

    /** 返回（左箭头） */
    static LineIconButton back(Context c) {
        Path p = new Path();
        p.moveTo(19f, 12f); p.lineTo(5f, 12f);
        p.moveTo(11f, 6f); p.lineTo(5f, 12f); p.lineTo(11f, 18f);
        return build(c, new Path[]{p}, 0xFF4D96FF, "返回", 28);
    }

    /** 主页（屋顶 + 门） */
    static LineIconButton home(Context c) {
        Path p = new Path();
        p.moveTo(4f, 11f); p.lineTo(12f, 4f); p.lineTo(20f, 11f);
        p.moveTo(6f, 10f); p.lineTo(6f, 20f); p.lineTo(18f, 20f); p.lineTo(18f, 10f);
        return build(c, new Path[]{p}, 0xFF4D96FF, "主页", 28);
    }

    /** 尺寸变体：顶栏功能图标用 32dp（更醒目），行内编辑/删除保持 28dp */
    static LineIconButton sized(LineIconButton b, int dp) {
        return build(b.getContext(), b.paths, b.pressedColor,
                (String) b.getContentDescription(), dp);
    }

    private static LineIconButton build(Context c, Path[] paths, int color, String desc, float size) {
        return new LineIconButton(c, paths, color, desc, size);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int s = (int) dp(sizeDp * Util.k(getContext())); // 平板放大：尺寸由 sized() 指定值 × 平板系数
        setMeasuredDimension(s, s);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (pressed) {
            canvas.drawRoundRect(bgRect, dp(8), dp(8), bg);
        }
        // 图标 15/24 页面比例（网页 icon-btn 里 15px 图标），居中
        float scale = getWidth() / 24f * 0.52f;
        float dx = (getWidth() - 24f * scale) / 2f;
        float dy = (getHeight() - 24f * scale) / 2f;
        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);
        stroke.setColor(pressed ? pressedColor : NORMAL);
        for (Path p : paths) canvas.drawPath(p, stroke);
        canvas.restore();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        bgRect.set(0, 0, w, h);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean was = pressed;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                break;
            case MotionEvent.ACTION_UP:
                pressed = false;
                performClick();
                break;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                break;
        }
        if (was != pressed) invalidate();
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}