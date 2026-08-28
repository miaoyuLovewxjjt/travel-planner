package com.zcode.travelapp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 通用工具：dp 换算、配色、日期、凑整、列表行样式等。
 * 全部内容展示遵循「完整显示、自动换行、禁止省略号」原则。
 */
final class Util {

    static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd"); // 数据格式（与网页版一致）
    static final String[] WEEK_CN = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
    static final int[] CAT_COLORS = {0xFF4D96FF, 0xFFFF9F43, 0xFFA55EEA, 0xFF2EC4B6, 0xFFE5484D, 0xFF8E8E93};

    /** 花销分类（与网页版一致，固定 6 类） */
    static final String[][] CATS = {
            {"交通", "🚆"}, {"餐饮", "🍜"}, {"住宿", "🌌"}, {"门票", "🎫"}, {"购物", "🛍️"}, {"其他", "📌"}
    };

    /** 交通方式（与网页版 app.html TRANSPORTS 完全一致，12 种，含火车/其他） */
    static final String[][] TRANS = {
            {"地铁", "🚇"}, {"打车", "🚕"}, {"公交", "🚌"}, {"飞机", "✈️"}, {"高铁", "🚄"},
            {"火车", "🚂"}, {"自驾", "🚗"}, {"租车", "🚙"}, {"游船", "🛥️"}, {"步行", "🚶"},
            {"骑行", "🚲"}, {"其他", "🧭"}
    };

    /** 交通颜色（网页版 TRANS_COLORS；打车跟随行程主题色，火车归其他色） */
    static int transColor(String tr, int accent) {
        if ("打车".equals(tr)) return accent;
        switch (tr == null ? "" : tr) {
            case "高铁": return 0xFF4D96FF;
            case "飞机": return 0xFF7B5CF0;
            case "地铁": return 0xFF0FB5AE;
            case "公交": return 0xFF4ECB71;
            case "自驾": return 0xFFFF8C42;
            case "步行": return 0xFFB0B0BC;
            case "游船": return 0xFF38B2AC;
            case "骑行": return 0xFFF0619B;
            default: return 0xFF8A8A9A; // 火车/其他/未知
        }
    }

    private Util() {}

    static int dp(Context c, float v) { return (int) (c.getResources().getDisplayMetrics().density * v); }
    static int sp(Context c, float v) { return (int) (c.getResources().getDisplayMetrics().scaledDensity * v); }

    static int parseColor(String hex, int fallback) {
        try { return Color.parseColor(hex); } catch (Exception e) { return fallback; }
    }

    /**
     * 消费构成色板：由行程主题色派生（对齐网页 --cat-1..6：交通最深→其他最浅，跨行程随主题变化）
     */
    static int catColor(String category, int accent) {
        int[] cats = themeCats(accent);
        String[] order = {"交通", "餐饮", "住宿", "门票", "购物", "其他"};
        for (int i = 0; i < order.length; i++) if (order[i].equals(category)) return cats[i];
        return cats[5];
    }

    /** 主题派生 6 色（网页 shade / mixWhite 对应） */
    static int[] themeCats(int accent) {
        if (accent == 0) accent = ACCENT;
        return new int[]{
                blend(accent, 0x000000, 0.13f),   // 交通（最深）
                blend(accent, 0x000000, 0.055f),  // 餐饮
                accent,                            // 住宿（主题本色）
                blend(accent, 0xFFFFFFFF, 0.32f), // 门票
                blend(accent, 0xFFFFFFFF, 0.55f), // 购物
                blend(accent, 0xFFFFFFFF, 0.75f), // 其他（最浅）
        };
    }

    /** 分类序号 -> 主题色（未知分类归「其他」） */
    static int catColor(String category) {
        for (int i = 0; i < CATS.length; i++) if (CATS[i][0].equals(category)) return CAT_COLORS[i];
        return CAT_COLORS[5];
    }
    static String catEmoji(String category) {
        for (String[] c : CATS) if (c[0].equals(category)) return c[1];
        return "📌";
    }
    static String transEmoji(String t) {
        for (String[] tr : TRANS) if (tr[0].equals(t)) return tr[1];
        return "🚄";
    }

    /** 全行程总花费 = 花销明细 + 路线价格 + 住宿每晚价格（与网页版口径一致） */
    static double totalSpent(JSONObject trip) {
        double sum = 0;
        JSONObject days = trip.optJSONObject("days");
        if (days == null) return 0;
        Iterator<String> it = days.keys();
        while (it.hasNext()) {
            JSONObject day = days.optJSONObject(it.next());
            if (day == null) continue;
            JSONArray exps = day.optJSONArray("expenses");
            if (exps != null) for (int i = 0; i < exps.length(); i++) {
                JSONObject e = exps.optJSONObject(i);
                if (e != null) sum += e.optDouble("amount", 0);
            }
            JSONArray segs = day.optJSONArray("segments");
            if (segs != null) for (int i = 0; i < segs.length(); i++) {
                JSONObject s = segs.optJSONObject(i);
                if (s != null) sum += s.optDouble("price", 0);
            }
            JSONObject lodge = day.optJSONObject("lodging");
            if (lodge != null && lodge.optString("name").length() > 0) {
                sum += lodge.optDouble("pricePerNight", 0);
            }
        }
        return sum;
    }

    /** 按日期升序排好的天数 */
    static List<String> dayKeys(JSONObject trip) {
        List<String> keys = new ArrayList<>();
        JSONObject days = trip.optJSONObject("days");
        if (days != null) {
            Iterator<String> it = days.keys();
            while (it.hasNext()) keys.add(it.next());
        }
        java.util.Collections.sort(keys);
        return keys;
    }

    static String fmtMoney(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format("%.2f", v);
    }

    /**
     * 乘客数据规范化（对应网页版 normPax）：一律返回 [{name, seat}] 顺序列表。
     * 兼容旧数据：字符串数组 + 顶层 seg.seat（单值给第一个乘客）。
     */
    static List<String[]> normPax(JSONObject seg) {
        List<String[]> out = new ArrayList<>();
        if (seg == null) return out;
        JSONArray ps = seg.optJSONArray("passengers");
        if (ps == null || ps.length() == 0) return out;
        Object first = ps.opt(0);
        if (first instanceof String) {
            // 旧数据：字符串数组；seg.seat 为整段单值 → 给第一个乘客
            String wholeSeat = seg.optString("seat");
            for (int i = 0; i < ps.length(); i++) {
                out.add(new String[]{ps.optString(i), i == 0 ? wholeSeat : ""});
            }
        } else {
            for (int i = 0; i < ps.length(); i++) {
                JSONObject p = ps.optJSONObject(i);
                if (p == null) continue;
                out.add(new String[]{p.optString("name"), p.optString("seat")});
            }
        }
        return out;
    }

    /** 乘客展示文本（对应网页版 paxText）：'我（3车5D）、小美' */
    static String paxText(JSONObject seg) {
        StringBuilder sb = new StringBuilder();
        for (String[] p : normPax(seg)) {
            if (sb.length() > 0) sb.append("、");
            sb.append(p[0]);
            if (!p[1].isEmpty()) sb.append("（").append(p[1]).append("）");
        }
        return sb.toString();
    }
    /**
     * 物品所属行李 id；网页版未放入时 bag 为 JSON null（optString 会读出 "null"），统一归一为 ""。
     */
    static String bagOf(JSONObject it) {
        if (it == null || it.isNull("bag")) return "";
        String b = it.optString("bag");
        return b == null || b.isEmpty() ? "" : b;
    }

    static String normalizeTime(String s) {
        if (s == null) return null;
        s = s.trim().replace("：", ":");
        if (s.isEmpty()) return null;
        String[] p = s.split(":");
        try {
            int h = Integer.parseInt(p[0].trim());
            int m = p.length > 1 && !p[1].trim().isEmpty() ? Integer.parseInt(p[1].trim()) : 0;
            return String.format("%02d:%02d", h, m);
        } catch (Exception e) { return null; }
    }

    /** 圆角卡片背景 */
    static GradientDrawable cardBg(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** 胶囊标签背景 */
    static GradientDrawable chipBg(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    // ================= 视觉设计系统（网页版风格：浅灰底 / 白卡 / 圆角 / 主题色贯穿） =================

    static final int BG    = 0xFFF4F4F7;   // 页面浅灰底
    static final int CARD  = 0xFFFFFFFF;   // 卡片白
    static final int LINE  = 0xFFE9EBF0;   // 细描边/分隔线
    static final int INK   = 0xFF1F2230;   // 主文字
    static final int SUB   = 0xFF6B6E80;   // 次级文字
    static final int MUTE  = 0xFF9BA0AE;   // 弱化文字/hint
    static final int ACCENT = 0xFF4D96FF;  // 默认主题蓝

    /** 颜色微调（>1 变亮 <1 变暗；保持色相） */
    static int shade(int color, float f) {
        int r = Math.min(255, (int) (Color.red(color) * f));
        int g = Math.min(255, (int) (Color.green(color) * f));
        int b = Math.min(255, (int) (Color.blue(color) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** 主题色配套浅底（网页版 --accent-soft 类似） */
    static int accentSoft(int accent, Context c) {
        return blend(accent, 0xFFFFFFFF, 0.88f);
    }

    /** 白卡 + 圆角 + 细描边（标准区块卡） */
    static GradientDrawable cardStyle(Context c, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(dp(c, 1), LINE);
        return g;
    }

    /** 行程封面渐变（加深→变亮，对角），随行程主题色 */
    static GradientDrawable coverGradient(int accent, int radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{shade(accent, 1.18f), shade(accent, 0.82f)});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** 主按钮：主题色胶囊白字加粗 */
    static void stylePrimary(Button b, int accent, Context c) {
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(accent);
        g.setCornerRadius(dp(c, 21));
        b.setBackground(g);
        b.setMinHeight(dp(c, 42));
        b.setMinWidth(0);
        b.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        b.setStateListAnimator(null);
    }

    /** 次要按钮：浅底圆角胶囊 */
    static void styleSoft(Button b, int textColor, int bgColor, Context c) {
        b.setTextColor(textColor);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bgColor);
        g.setCornerRadius(dp(c, 21));
        b.setBackground(g);
        b.setMinHeight(dp(c, 42));
        b.setMinWidth(0);
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        b.setStateListAnimator(null);
    }

    /** 文字按钮（顶栏取消/保存） */
    static void styleTextButton(Button b, int color, Context c) {
        b.setTextColor(color);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackgroundColor(0x00000000);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(dp(c, 8), 0, dp(c, 8), 0);
    }

    /** 虚线"添加"按钮（全宽，网页版风格） */
    static void styleDashedAdd(Button b, int accent, Context c) {
        b.setTextColor(accent);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x00FFFFFF);
        g.setStroke(dp(c, 1), shade(accent, 0.72f), dp(c, 6), dp(c, 6));
        g.setCornerRadius(dp(c, 14));
        b.setBackground(g);
        b.setMinHeight(dp(c, 44));
        b.setMinWidth(0);
    }

    /** 区块标题（板块小标题，浅色） */
    static TextView sectionTitle(Context c, String s, int accent) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(15);
        t.setTextColor(INK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** 图标圆底徽章（如交通 emoji / 分类 emoji 的圆底） */
    static TextView roundBadge(Context c, String emoji, int bgColor, int radiusDp) {
        TextView t = new TextView(c);
        t.setText(emoji);
        t.setTextSize(14);
        t.setIncludeFontPadding(false); // emoji 垂直居中（部分 emoji 字体 metrics 偏下，去掉字体内边距后居中）
        t.setGravity(Gravity.CENTER);
        t.setBackground(chipBg(bgColor, radiusDp, c));
        t.setMinWidth(dp(c, radiusDp * 2));
        t.setMinHeight(dp(c, radiusDp * 2));
        return t;
    }

    /** 时间胶囊徽章（浅色底 + 主题色字） */
    static TextView timeBadge(Context c, String time, int accent) {
        TextView t = new TextView(c);
        t.setText(time);
        t.setTextSize(12.5f);
        t.setTextColor(accent);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setBackground(chipBg(accentSoft(accent, c), 8, c));
        t.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3));
        return t;
    }

    /** 半透明叠加（网页版 color-mix 效果） */
    static int blend(int color, int base, float ratio) {
        int r = (int) (Color.red(color) * (1 - ratio) + Color.red(base) * ratio);
        int g = (int) (Color.green(color) * (1 - ratio) + Color.green(base) * ratio);
        int b = (int) (Color.blue(color) * (1 - ratio) + Color.blue(base) * ratio);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * 统一编辑按钮：✎ 灰字，无底，30dp 轻触区（全项目唯一编辑图标样式）
     */
    static Button editBtn(Context c) {
        return iconBtn(c, "✎", 0xFF6B6E80, "编辑");
    }

    /**
     * 统一删除按钮：✕ 红字，无底，30dp 轻触区（全项目唯一删除图标样式）
     */
    static Button delBtn(Context c) {
        return iconBtn(c, "✕", 0xFFE5484D, "删除");
    }

    private static Button iconBtn(Context c, String glyph, int color, String desc) {
        Button b = new Button(c);
        b.setText(glyph);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT);
        b.setTextColor(color);
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setBackgroundColor(0x00000000);
        b.setStateListAnimator(null);
        if (desc != null) b.setContentDescription(desc);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(c, 30), dp(c, 30));
        p.leftMargin = dp(c, 4);
        b.setLayoutParams(p);
        return b;
    }

    /** 圆形小图标按钮（文字/删除等，普通样式：浅底圆 + 单色字形） */
    static Button iconCircle(Context c, String glyph, int color, int bgColor, int sizeDp) {
        Button b = new Button(c);
        b.setText(glyph);
        b.setTextSize(13);
        b.setTextColor(color);
        b.setGravity(Gravity.CENTER);
        b.setBackground(chipBg(bgColor, sizeDp / 2f, c));
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        p.leftMargin = dp(c, 5);
        b.setLayoutParams(p);
        return b;
    }

    /** 小标签 chip（车次/座位/乘客等，浅底圆角） */
    static TextView chip(Context c, String s, int textColor, int bgColor) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(textColor);
        t.setBackground(chipBg(bgColor, 9, c));
        t.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3));
        return t;
    }

    /**
     * 编辑/删除紧凑排列：无背景、无留白，只是让两个圆钮挨在一起（间距 2dp）。
     */
    static LinearLayout actionGroup(Context c, View... buttons) {
        LinearLayout g = new LinearLayout(c);
        g.setOrientation(LinearLayout.HORIZONTAL);
        g.setGravity(Gravity.CENTER_VERTICAL);
        for (View b : buttons) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) b.getLayoutParams();
            if (p == null) { p = new LinearLayout.LayoutParams(-2, -2); }
            p.leftMargin = dp(c, 2);
            g.addView(b, p);
        }
        return g;
    }

    /** 行内小标题 */
    static TextView label(Context c, String text, int color) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(c, 2));
        return t;
    }

    /** 正文（自动换行、不省略） */
    static TextView text(Context c, String s, float sizeSp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        return t;
    }

    static LinearLayout vBox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout hBox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return l;
    }

    static void margin(View v, int l, int t, int r, int b, Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        v.setLayoutParams(p);
    }

    /** 设置固定高度；注意：必须在 addView 之后调用，否则 getLayoutParams() 为 null */
    static void setHeight(View v, int dpVal, Context c) {
        android.view.ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p == null) {
            p = new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            v.setLayoutParams(p);
        }
        p.height = dp(c, dpVal);
    }

    /** 周几 */
    static String weekday(String date) {
        try { return WEEK_CN[LocalDate.parse(date).getDayOfWeek().getValue() % 7]; } catch (Exception e) { return ""; }
    }

    static String displayDate(String date) {
        try {
            LocalDate d = LocalDate.parse(date);
            return (d.getMonthValue()) + "月" + d.getDayOfMonth() + "日 " + WEEK_CN[d.getDayOfWeek().getValue() % 7];
        } catch (Exception e) { return date; }
    }
}