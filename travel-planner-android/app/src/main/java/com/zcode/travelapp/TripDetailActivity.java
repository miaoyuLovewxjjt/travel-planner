package com.zcode.travelapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行程详情：
 * 双级导航（每日行程 / 行程总结）＋ PDF 导出 ＋ 渐变概览卡 ＋ 日期胶囊 chips ＋
 * 路线/住宿/记账区块（时间徽章、跨天徽章、交通圆底图标、车次/座位/乘客 chip 组、普通圆形✎/✕按钮）。
 * 内容保真：所有用户文字自动换行完整显示，无省略号。
 */
public class TripDetailActivity extends Activity {

    private static final int REQ_EDIT_TRIP = 1;
    private static final int REQ_SEGMENT = 2;
    private static final int REQ_EXPENSE = 3;
    private static final int REQ_PDF = 4;

    private String tripId;
    private JSONObject trip;
    private String selectedDay = "";
    private String expandedCat;      // 图例展开的分类（每日/总结共用）
    private String viewMode = "daily"; // daily 每日 | packing 行李清单 | summary 行程总结
    private int accent = Util.ACCENT;

    private LinearLayout headerBox, chipBox, dayBox, navPills;
    private ScrollView dayScroll;
    private HorizontalScrollView chipScroll;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tripId = getIntent().getStringExtra("tripId");
        reload();
    }

    private void reload() {
        trip = EditTripActivity.findTrip(Store.load(this), tripId);
        if (trip == null) { finish(); return; }
        accent = Util.parseColor(trip.optString("color"), Util.ACCENT);
        List<String> keys = Util.dayKeys(trip);
        if (keys.isEmpty()) { finish(); return; }
        if (!keys.contains(selectedDay)) selectedDay = keys.get(0);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = Util.vBox(this);
        root.setBackgroundColor(Util.BG);
        setContentView(root);

        // ---- 顶栏：返回 / 标题（保留空间）/ PDF（紧凑）/ 编辑（顶部留出间距） ----
        LinearLayout top = Util.hBox(this);
        top.setBackgroundColor(0xFFFFFFFF);
        top.setGravity(Gravity.CENTER_VERTICAL); // 所有子元素垂直居中
        top.setPadding(Util.dp(this, 12), Util.dp(this, 14), Util.dp(this, 10), Util.dp(this, 10));

        TextView title = Util.text(this, trip.optString("name", "行程"), 14.5f, Util.INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        // 不设 maxLines：标题永远完整显示（超长自动换行，允许顶栏两行高）
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        // 右侧顺序：✎编辑 → 🖨️PDF → ⌂主页（主页最右，点击返回列表）
        LineIconButton edit = LineIconButton.sized(LineIconButton.edit(this), 36);
        edit.setOnClickListener(v -> {
            Intent it = new Intent(this, EditTripActivity.class);
            it.putExtra("tripId", tripId);
            startActivityForResult(it, REQ_EDIT_TRIP);
        });
        LineIconButton pdf = LineIconButton.sized(LineIconButton.printer(this), 36);
        pdf.setOnClickListener(v -> showPdfPreview());
        LineIconButton home = LineIconButton.sized(LineIconButton.home(this), 36);
        home.setOnClickListener(v -> finish());
        LinearLayout detailIcons = Util.actionGroup(this, edit, pdf, home);
        top.addView(detailIcons);
        root.addView(top);
        View hairline = new View(this);
        hairline.setBackgroundColor(Util.LINE);
        Util.setHeight(hairline, 1, this);
        root.addView(hairline);

        // ---- 概览卡（放入滚动区：下拉时渐变卡随内容滚走，不占固定位置） ----

        // ---- 导航 pills：放进滚动区、紧贴标题（渐变卡）下方 4dp（随标题滚动） ----
        navPills = new LinearLayout(this);
        navPills.setOrientation(LinearLayout.HORIZONTAL);
        navPills.setPadding(Util.dp(this, 16), Util.dp(this, 16), Util.dp(this, 16), 0);

        // ---- 日期导航（放入滚动区：标题卡 → pills → 日期 → 内容） ----
        chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipBox = new LinearLayout(this);
        chipBox.setOrientation(LinearLayout.HORIZONTAL);
        chipBox.setPadding(Util.dp(this, 16), Util.dp(this, 8), Util.dp(this, 16), 0);
        chipScroll.addView(chipBox);

        // ---- 内容区（概览卡 + 当日内容一起滚动） ----
        dayScroll = new ScrollView(this);
        dayScroll.setFillViewport(true);
        dayBox = Util.vBox(this);
        dayBox.setPadding(Util.dp(this, 16) + Util.hPad(this), Util.dp(this, 12), Util.dp(this, 16) + Util.hPad(this), Util.dp(this, 30)); // 平板限宽居中
        headerBox = Util.vBox(this);
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(-1, -2);
        chp.setMargins(Util.dp(this, 16) + Util.hPad(this), Util.dp(this, 8), Util.dp(this, 16) + Util.hPad(this), 0); // 概览卡与顶栏之间留间距 + 平板限宽
        headerBox.setLayoutParams(chp);
        LinearLayout content = Util.vBox(this);
        content.addView(headerBox);
        content.addView(navPills);   // 标题下方 4dp（navPills padding top=4dp）
        content.addView(chipScroll); // 日期导航在导航钮下方，随内容滚动
        content.addView(dayBox);
        dayScroll.addView(content);
        root.addView(dayScroll, new LinearLayout.LayoutParams(-1, -1));

        renderNavPills();
        renderHeader();
        renderAll();
    }

    private void renderNavPills() {
        navPills.removeAllViews();
        // 顺序：🎒 行李清单 → 📅 每日行程 → 📊 总结（行李清单在每日行程左侧）
        Button pack = new Button(this);
        pack.setText("🎒 行李清单");
        Button daily = new Button(this);
        daily.setText("📅 每日行程");
        Button sum = new Button(this);
        sum.setText("📊 总结");
        styleNavPill(pack, "packing".equals(viewMode));
        styleNavPill(daily, "daily".equals(viewMode));
        styleNavPill(sum, "summary".equals(viewMode));
        pack.setOnClickListener(v -> setViewMode("packing"));
        daily.setOnClickListener(v -> setViewMode("daily"));
        sum.setOnClickListener(v -> setViewMode("summary"));
        navPills.addView(pack, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams dp_ = new LinearLayout.LayoutParams(0, -2, 1);
        dp_.leftMargin = Util.dp(this, 8);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -2, 1);
        sp.leftMargin = Util.dp(this, 8);
        navPills.addView(daily, dp_);
        navPills.addView(sum, sp);
    }

    private void setViewMode(String mode) {
        if (mode.equals(viewMode)) return;
        viewMode = mode;
        renderNavPills();
        renderAll();
        dayScroll.scrollTo(0, 0);
    }

    private void styleNavPill(Button b, boolean selected) {
        b.setTextSize(13.5f);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        if (selected) {
            b.setTextColor(0xFFFFFFFF);
            b.setBackground(Util.coverGradient(accent, 18, this));
            b.setElevation(Util.dp(this, 2));
        } else {
            b.setTextColor(Util.SUB);
            b.setBackground(Util.cardStyle(this, 18));
        }
        b.setMinHeight(Util.dp(this, 40));
        b.setMinWidth(0);
        b.setStateListAnimator(null);
    }

    private void renderAll() {
        if ("packing".equals(viewMode)) {
            chipScroll.setVisibility(View.GONE);
            renderPacking();
        } else if ("summary".equals(viewMode)) {
            chipScroll.setVisibility(View.GONE);
            renderSummary();
        } else {
            chipScroll.setVisibility(View.VISIBLE);
            renderChips();
            renderDay();
        }
    }

    private void renderHeader() {
        headerBox.removeAllViews();
        final String emoji = trip.optString("emoji", "🗺️");
        final String name = trip.optString("name", "未命名");

        LinearLayout cover = Util.vBox(this);
        cover.setBackground(Util.coverGradient(accent, 20, this));
        cover.setPadding(Util.dp(this, 16), Util.dp(this, 20), Util.dp(this, 16), Util.dp(this, 14)); // 顶部留白，标题不贴边
        // 标题只留行程名（去 emoji，不再挤占标题）
        LinearLayout row1 = Util.hBox(this);
        TextView nameTv = Util.text(this, name, 18, 0xFFFFFFFF);
        nameTv.setTypeface(Typeface.DEFAULT_BOLD);
        nameTv.setLineSpacing(Util.dp(this, 2), 1f);
        row1.addView(nameTv);
        cover.addView(row1);
        String dates = Util.displayDate(trip.optString("startDate")) + " → " + Util.displayDate(trip.optString("endDate"));
        TextView dateTv = Util.text(this, "📅 " + dates, 12.5f, 0xE6FFFFFF);
        LinearLayout.LayoutParams dateP = new LinearLayout.LayoutParams(-1, -2);
        dateP.topMargin = Util.dp(this, 10); // 与标题明显拉开
        cover.addView(dateTv, dateP);
        String travelers = joinArr(trip.optJSONArray("travelers"));
        if (!travelers.isEmpty()) {
            TextView trTv = Util.text(this, "👥 " + travelers, 12, 0xD9FFFFFF);
            LinearLayout.LayoutParams trP = new LinearLayout.LayoutParams(-1, -2);
            trP.topMargin = Util.dp(this, 8);
            cover.addView(trTv, trP);
        }

        // 花费/预算不再有底色框（简洁统一）
        LinearLayout body = Util.vBox(this);
        body.setPadding(Util.dp(this, 0), Util.dp(this, 14), Util.dp(this, 0), Util.dp(this, 0));
        double spent = Util.totalSpent(trip);
        double budget = trip.optDouble("budget", 0);
        boolean over = budget > 0 && spent > budget;
        // 渐变底上：已花费/预算统一白色（可读性好）；超支用浅红
        LinearLayout spendRow = Util.hBox(this);
        TextView spentTv = Util.text(this, "💰 已花费 ¥" + Util.fmtMoney(spent), 14, 0xFFFFFFFF);
        spentTv.setTypeface(Typeface.DEFAULT_BOLD);
        spendRow.addView(spentTv);
        if (budget > 0) {
            TextView budgetTv = Util.text(this, "／ 预算 ¥" + Util.fmtMoney(budget), 14,
                    over ? 0xFFFFC9C9 : 0xE6FFFFFF);
            budgetTv.setTypeface(Typeface.DEFAULT_BOLD);
            budgetTv.setPadding(Util.dp(this, 8), 0, 0, 0);
            budgetTv.setSingleLine(true);
            spendRow.addView(budgetTv);
            if (over) {
                TextView overTv = Util.text(this, " ⚠️超支", 14, 0xFFFFC9C9);
                overTv.setTypeface(Typeface.DEFAULT_BOLD);
                overTv.setSingleLine(true);
                spendRow.addView(overTv);
            }
        }
        body.addView(spendRow);
        cover.addView(body);
        headerBox.addView(cover);
    }

// ===================== 行程总结视图 =====================

    private void renderSummary() {
        dayBox.removeAllViews();

        // 1) 行程概览统计
        int nights = 0, segsTotal = 0;
        for (String d : Util.dayKeys(trip)) {
            JSONObject day = trip.optJSONObject("days").optJSONObject(d);
            if (day == null) continue;
            JSONObject lodge = day.optJSONObject("lodging");
            if (lodge != null && !lodge.optString("name").isEmpty()) nights++;
            JSONArray segs = day.optJSONArray("segments");
            if (segs != null) segsTotal += segs.length();
        }
        LinearLayout statCard = card();
        statCard.addView(summaryTitle("🏷️ 行程概览"));
        TextView statTv = Util.text(this, "🏨 住宿 " + nights + " 晚  ·  🚗 路线共 " + segsTotal + " 段",
                13.5f, Util.SUB);
        statTv.setPadding(0, Util.dp(this, 4), 0, 0);
        statCard.addView(statTv);
        dayBox.addView(statCard);

        // 2) 交通方式统计 + 在途时长占比条
        Map<String, long[]> transStats = new java.util.LinkedHashMap<>();
        for (String d : Util.dayKeys(trip)) {
            JSONObject day = trip.optJSONObject("days").optJSONObject(d);
            JSONArray segs = day != null ? day.optJSONArray("segments") : null;
            if (segs == null) continue;
            for (int i = 0; i < segs.length(); i++) {
                JSONObject s = segs.optJSONObject(i);
                if (s == null || s.optString("transport").isEmpty()) continue;
                String t = s.optString("transport");
                long[] v = transStats.get(t);
                if (v == null) { v = new long[]{0, 0}; transStats.put(t, v); }
                v[0]++;
                v[1] += segMinutes(s);
            }
        }
        long totalMin = 0;
        for (long[] v : transStats.values()) totalMin += v[1];
        long tripMin = (long) Util.dayKeys(trip).size() * 1440;

        LinearLayout transCard = card();
        transCard.addView(summaryTitle("🚗 交通方式"));
        if (transStats.isEmpty()) {
            TextView emptyTrans = Util.text(this, "还没有标注交通方式的路线", 13, Util.MUTE);
            emptyTrans.setPadding(0, Util.dp(this, 8), 0, 0);
            transCard.addView(emptyTrans);
        } else {
            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setPadding(0, Util.dp(this, 8), 0, Util.dp(this, 8));
            final double[] acc = {0};
            for (Map.Entry<String, long[]> en : transStats.entrySet()) {
                long v1 = en.getValue()[1];
                View seg = new View(this);
                seg.setBackground(Util.chipBg(Util.transColor(en.getKey(), accent), 3, this));
                LinearLayout.LayoutParams segP = new LinearLayout.LayoutParams(0, Util.dp(this, 8),
                        (float) (acc[0] + v1 > 0 ? v1 : 0));
                if (v1 > 0) acc[0] += v1;
                seg.setLayoutParams(segP);
                bar.addView(seg);
            }
            if (totalMin < tripMin) {
                View rest = new View(this);
                rest.setBackground(Util.chipBg(0xFFE4E6EC, 3, this));
                LinearLayout.LayoutParams restP = new LinearLayout.LayoutParams(0, Util.dp(this, 8),
                        Math.max(0, (float) (tripMin - totalMin)));
                rest.setLayoutParams(restP);
                bar.addView(rest);
            }
            bar.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            transCard.addView(bar);
            FlowLayout chips = new FlowLayout(this);
            chips.setGaps(6, 6, this);
            for (Map.Entry<String, long[]> en : transStats.entrySet()) {
                int color = Util.transColor(en.getKey(), accent);
                String t = en.getKey();
                long[] v = en.getValue();
                TextView chip = Util.text(this, Util.transEmoji(t) + " " + t + " " + v[0] + " 段"
                        + (v[1] > 0 ? " · " + fmtMin(v[1]) : ""), 12, Util.INK);
                chip.setBackground(Util.chipBg(Util.blend(color, 0xFFFFFFFF, 0.88f), 9, this));
                chip.setPadding(Util.dp(this, 9), Util.dp(this, 4), Util.dp(this, 9), Util.dp(this, 4));
                chips.addView(chip);
            }
            transCard.addView(chips);
            TextView totalTv = Util.text(this, "⏱️ 在途总时长 " + fmtMin(totalMin)
                    + (tripMin > totalMin ? " ／ 全程 " + fmtMin(tripMin) : ""), 13, accent);
            totalTv.setTypeface(Typeface.DEFAULT_BOLD);
            totalTv.setPadding(0, Util.dp(this, 6), 0, 0);
            transCard.addView(totalTv);
        }
        dayBox.addView(transCard);

        // 3) 预算使用（在消费构成上方；只文字，已使用百分比不重复）
        double spent = Util.totalSpent(trip);
        double budget = trip.optDouble("budget", 0);
        LinearLayout budgetCard = card();
        budgetCard.addView(summaryTitle("🎯 预算使用"));
        TextView bt = Util.text(this, "💰 已花费 ¥" + Util.fmtMoney(spent)
                + (budget > 0 ? " ／ 预算 ¥" + Util.fmtMoney(budget) : ""), 14,
                budget > 0 && spent > budget ? 0xFFE5484D : accent);
        bt.setTypeface(Typeface.DEFAULT_BOLD);
        bt.setPadding(0, Util.dp(this, 4), 0, 0);
        budgetCard.addView(bt);
        dayBox.addView(budgetCard);

        // 4) 消费构成：环图 + 图例行（点击就地展开该分类跨日明细，不弹窗）
        LinkedHashMap<String, Double> cats = categorySum();
        LinearLayout expCard = card();
        expCard.addView(summaryTitle("💰 消费构成"));
        double total = 0;
        for (double v : cats.values()) total += v;
        if (cats.isEmpty()) {
            TextView emptyExp = Util.text(this, "暂无花销记录", 13, Util.MUTE);
            emptyExp.setPadding(0, Util.dp(this, 8), 0, 0);
            expCard.addView(emptyExp);
        } else {
            LinearLayout chartRow = Util.hBox(this);
            chartRow.setGravity(Gravity.CENTER_VERTICAL);
            chartRow.setPadding(0, Util.dp(this, 6), 0, 0);
            DoughnutView dv = new DoughnutView(this);
            List<DoughnutView.Slice> slices = new ArrayList<>();
            for (Map.Entry<String, Double> en : cats.entrySet())
                slices.add(new DoughnutView.Slice(en.getKey(), Util.catColor(en.getKey(), accent), en.getValue()));
            dv.setData(slices);
            chartRow.addView(dv, new LinearLayout.LayoutParams(Util.dp(this, 120), Util.dp(this, 120)));
            LinearLayout legend = Util.vBox(this);
            legend.setPadding(Util.dp(this, 14), 0, 0, 0);
            final LinearLayout expandedBox = Util.vBox(this);
            expandedBox.setPadding(0, Util.dp(this, 2), 0, 0);
            for (Map.Entry<String, Double> en : cats.entrySet()) {
                final String c = en.getKey();
                LinearLayout item = Util.hBox(this);
                item.setPadding(0, Util.dp(this, 3), 0, Util.dp(this, 3));
                View dot = new View(this);
                dot.setBackground(Util.chipBg(Util.catColor(c, accent), 5, this));
                dot.setLayoutParams(new LinearLayout.LayoutParams(Util.dp(this, 9), Util.dp(this, 9)));
                item.addView(dot);
                TextView lt = Util.text(this, Util.catEmoji(c) + " " + c, 13, Util.INK);
                lt.setPadding(Util.dp(this, 7), 0, 0, 0);
                lt.setOnClickListener(v -> expandCatInline(expandedBox, c));
                item.addView(lt, new LinearLayout.LayoutParams(0, -2, 1));
                TextView amt = Util.text(this, "¥" + Util.fmtMoney(en.getValue()), 13, Util.SUB);
                amt.setTypeface(Typeface.DEFAULT_BOLD);
                item.addView(amt);
                legend.addView(item);
            }
            chartRow.addView(legend, new LinearLayout.LayoutParams(0, -2, 1));
            expCard.addView(chartRow);
            expCard.addView(expandedBox);
        }
        dayBox.addView(expCard);
    }

    /** 总结图例点击：就地展开/收起该分类的跨日明细（对齐每日记账的展开交互） */
    private void expandCatInline(LinearLayout box, String cat) {
        if (box.getChildCount() > 0 && cat.equals(box.getTag())) {
            box.removeAllViews();
            box.setTag(null);
            return;
        }
        box.removeAllViews();
        List<String> lines = new ArrayList<>();
        for (String d : Util.dayKeys(trip)) {
            JSONObject day = trip.optJSONObject("days").optJSONObject(d);
            if (day == null) continue;
            String dayLabel = Util.displayDate(d);
            JSONArray segsD = day.optJSONArray("segments");
            if (segsD != null && "交通".equals(cat)) for (int i = 0; i < segsD.length(); i++) {
                JSONObject sg = segsD.optJSONObject(i);
                if (sg != null && sg.optDouble("price", 0) > 0)
                    lines.add(dayLabel + " " + sg.optString("from") + "→" + sg.optString("to")
                            + " ¥" + Util.fmtMoney(sg.optDouble("price", 0)) + "（路线）");
            }
            JSONObject lg = day.optJSONObject("lodging");
            if (lg != null && "住宿".equals(cat) && lg.optDouble("pricePerNight", 0) > 0)
                lines.add(dayLabel + " " + lg.optString("name")
                        + " ¥" + Util.fmtMoney(lg.optDouble("pricePerNight", 0)) + "（住宿）");
            JSONArray expsB = day.optJSONArray("expenses");
            if (expsB != null) for (int i = 0; i < expsB.length(); i++) {
                JSONObject e = expsB.optJSONObject(i);
                if (e != null && cat.equals(e.optString("category", "其他")))
                    lines.add(dayLabel + " " + e.optString("item")
                            + " ¥" + Util.fmtMoney(e.optDouble("amount", 0)));
            }
        }
        if (lines.isEmpty()) lines.add("该分类暂无明细");
        box.setTag(cat);
        for (String ln : lines) {
            TextView t = Util.text(this, "• " + ln, 11f, Util.MUTE);
            t.setPadding(0, Util.dp(this, 1), 0, Util.dp(this, 1));
            box.addView(t);
        }
    }

    private TextView summaryTitle(String s) {
        TextView t = Util.text(this, s, 15, Util.INK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** 行程总结：点击图例弹出该分类跨日明细 */
    private void showCatDetailsDialog(String cat) {
        List<String> lines = new ArrayList<>();
        for (String d : Util.dayKeys(trip)) {
            JSONObject day = trip.optJSONObject("days").optJSONObject(d);
            if (day == null) continue;
            String dayLabel = Util.displayDate(d);
            JSONArray segsD = day.optJSONArray("segments");
            if (segsD != null && "交通".equals(cat)) for (int i = 0; i < segsD.length(); i++) {
                JSONObject sg = segsD.optJSONObject(i);
                if (sg != null && sg.optDouble("price", 0) > 0)
                    lines.add(dayLabel + " " + sg.optString("from") + "→" + sg.optString("to")
                            + " ¥" + Util.fmtMoney(sg.optDouble("price", 0)) + "（路线）");
            }
            JSONObject lg = day.optJSONObject("lodging");
            if (lg != null && "住宿".equals(cat) && lg.optDouble("pricePerNight", 0) > 0)
                lines.add(dayLabel + " " + lg.optString("name") + " ¥" + Util.fmtMoney(lg.optDouble("pricePerNight", 0)) + "（住宿）");
            JSONArray expsB = day.optJSONArray("expenses");
            if (expsB != null) for (int i = 0; i < expsB.length(); i++) {
                JSONObject e = expsB.optJSONObject(i);
                if (e != null && cat.equals(e.optString("category", "其他")))
                    lines.add(dayLabel + " " + e.optString("item") + " ¥" + Util.fmtMoney(e.optDouble("amount", 0)));
            }
        }
        if (lines.isEmpty()) lines.add("该分类暂无明细");
        new AlertDialog.Builder(this)
                .setTitle(Util.catEmoji(cat) + " " + cat + " 跨日明细")
                .setItems(lines.toArray(new String[0]), null)
                .setNegativeButton("关闭", null)
                .show();
    }

    // 统计口径（与网页一致）
    private int segMinutes(JSONObject s) {
        String dep = s.optString("departTime"), arr = s.optString("arriveTime");
        if (dep.length() < 5 || arr.length() < 5) return 0;
        try {
            int a = Integer.parseInt(dep.substring(0, 2)) * 60 + Integer.parseInt(dep.substring(3, 5));
            int b = Integer.parseInt(arr.substring(0, 2)) * 60 + Integer.parseInt(arr.substring(3, 5));
            return b - a + crossDays(s) * 1440;
        } catch (Exception e) { return 0; }
    }

    private LinkedHashMap<String, Double> categorySum() {
        LinkedHashMap<String, Double> m = new LinkedHashMap<>();
        for (String[] c : Util.CATS) m.put(c[0], 0.0);
        for (String d : Util.dayKeys(trip)) {
            JSONObject day = trip.optJSONObject("days").optJSONObject(d);
            if (day == null) continue;
            JSONArray exps = day.optJSONArray("expenses");
            if (exps != null) for (int i = 0; i < exps.length(); i++) {
                JSONObject e = exps.optJSONObject(i);
                if (e == null) continue;
                String c = e.optString("category", "其他");
                if (!m.containsKey(c)) m.put(c, 0.0);
                m.put(c, m.get(c) + e.optDouble("amount", 0));
            }
            JSONArray segs = day.optJSONArray("segments");
            if (segs != null) for (int i = 0; i < segs.length(); i++) {
                JSONObject s = segs.optJSONObject(i);
                if (s != null) m.put("交通", m.get("交通") + s.optDouble("price", 0));
            }
            JSONObject lodge = day.optJSONObject("lodging");
            if (lodge != null && !lodge.optString("name").isEmpty())
                m.put("住宿", m.get("住宿") + lodge.optDouble("pricePerNight", 0));
        }
        java.util.Iterator<Map.Entry<String, Double>> it = m.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue() <= 0) it.remove();
        return m;
    }

    private String fmtMin(long min) {
        long h = min / 60, m = min % 60;
        return h > 0 ? (m > 0 ? h + " 小时 " + m + " 分" : h + " 小时") : m + " 分钟";
    }

    // ===================== 每日视图 =====================

    private void renderChips() {
        chipBox.removeAllViews();
        for (String d : Util.dayKeys(trip)) {
            boolean sel = d.equals(selectedDay);
            LinearLayout chip = Util.vBox(this);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Util.dp(this, 16), Util.dp(this, 7), Util.dp(this, 16), Util.dp(this, 7));
            if (sel) {
                chip.setBackground(Util.coverGradient(accent, 16, this));
                chip.setElevation(Util.dp(this, 2));
            } else {
                chip.setBackground(Util.cardStyle(this, 16));
            }
            TextView dayN = Util.text(this, dayNum(d), 15, sel ? 0xFFFFFFFF : Util.INK);
            dayN.setTypeface(Typeface.DEFAULT_BOLD);
            TextView dayW = Util.text(this, Util.weekday(d), 11, sel ? 0xE6FFFFFF : Util.MUTE);
            chip.addView(dayN);
            chip.addView(dayW);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
            p.setMargins(0, 0, Util.dp(this, 10), 0);
            chip.setLayoutParams(p);
            final String dd = d;
            chip.setOnClickListener(v -> {
                selectedDay = dd;
                expandedCat = null;
                renderAll();
            });
            chipBox.addView(chip);
        }
    }

    private String dayNum(String date) {
        try { return String.valueOf(java.time.LocalDate.parse(date).getDayOfMonth()); } catch (Exception e) { return ""; }
    }

    private JSONObject dayObj() { return trip.optJSONObject("days").optJSONObject(selectedDay); }

    /** 气候枚举（与网页版完全一致） */
    private static final String[][] WEATHERS = {
            {"☀️", "晴"}, {"🌤️", "多云"}, {"☁️", "阴"}, {"🌧️", "雨"},
            {"⛈️", "雷阵雨"}, {"❄️", "雪"}, {"🌫️", "雾"}, {"🌬️", "风"}
    };

    private String wxEmoji(String cond) {
        for (String[] w : WEATHERS) if (w[1].equals(cond)) return w[0];
        return "🌤️";
    }

    /** 天气徽章行：有天气→"☀️ 晴 10°~20°"；无→"＋ 天气"占位；点击打开设置 */
    private View weatherRow(final JSONObject day) {
        LinearLayout wrap = Util.hBox(this);
        wrap.setPadding(0, Util.dp(this, 4), 0, Util.dp(this, 2));
        // 左侧：日期 + 周几（如 9/26 周六），天气徽章跟在后面，整行不再空荡
        String wxDate = "";
        try {
            String[] p = selectedDay.split("-");
            if (p.length == 3) wxDate = Integer.parseInt(p[1]) + "/" + Integer.parseInt(p[2]);
        } catch (Exception ignored) {}
        String wkd = Util.weekday(selectedDay);
        if (!wxDate.isEmpty() || !wkd.isEmpty()) {
            TextView dateTv = Util.text(this, (wxDate.isEmpty() ? "" : wxDate + " ")
                    + wkd, 14, Util.INK);
            dateTv.setTypeface(Typeface.DEFAULT_BOLD);
            dateTv.setPadding(0, Util.dp(this, 1), 0, 0);
            dateTv.setContentDescription("WX-" + wxDate + "-" + wkd);
            wrap.addView(dateTv);
            TextView gapTv = Util.text(this, "  ", 14, Util.INK);
            wrap.addView(gapTv);
        }
        JSONObject wx = day.optJSONObject("weather");
        TextView badge;
        if (wx != null && !wx.optString("cond").isEmpty()) {
            StringBuilder t = new StringBuilder(wxEmoji(wx.optString("cond")) + " " + wx.optString("cond"));
            boolean hasLow = !wx.isNull("low");
            boolean hasHigh = !wx.isNull("high");
            if (hasLow && hasHigh) t.append(" ").append(wx.optInt("low")).append("°~").append(wx.optInt("high")).append("°");
            else if (hasLow) t.append(" ").append(wx.optInt("low")).append("°");
            else if (hasHigh) t.append(" ~").append(wx.optInt("high")).append("°");
            badge = Util.chip(this, t.toString(), accent, Util.accentSoft(accent, this));
            badge.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            badge = Util.chip(this, "＋ 天气", Util.MUTE, 0xFFF2F3F7);
        }
        badge.setPadding(Util.dp(this, 10), Util.dp(this, 5), Util.dp(this, 10), Util.dp(this, 5));
        badge.setClickable(true);
        badge.setOnClickListener(v -> openWeatherDialog(day));
        wrap.addView(badge);
        return wrap;
    }

    /** 天气设置弹窗（对齐网页版：气候 + 最低/最高温；无温度时视为无效并删除） */
    private void openWeatherDialog(final JSONObject day) {
        final JSONObject wx = day.optJSONObject("weather");
        LinearLayout form = Util.vBox(this);
        int pad = Util.dp(this, 18);
        form.setPadding(pad, 0, pad, 0);
        Spinner condSp = new Spinner(this);
        List<String> condItems = new ArrayList<>();
        for (String[] w : WEATHERS) condItems.add(w[0] + " " + w[1]);
        ArrayAdapter<String> condAd = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, condItems);
        condAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        condSp.setAdapter(condAd);
        String curCond = wx != null ? wx.optString("cond") : "";
        for (int i = 0; i < WEATHERS.length; i++) if (WEATHERS[i][1].equals(curCond)) condSp.setSelection(i);
        form.addView(condSp);
        EditText lowEt = new EditText(this);
        EditTripActivity.Style.input(lowEt);
        lowEt.setHint("最低温度（℃，可留空）");
        lowEt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        if (wx != null && !wx.isNull("low")) lowEt.setText(String.valueOf(wx.optInt("low")));
        form.addView(lowEt);
        EditText highEt = new EditText(this);
        EditTripActivity.Style.input(highEt);
        highEt.setHint("最高温度（℃，可留空）");
        highEt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        if (wx != null && !wx.isNull("high")) highEt.setText(String.valueOf(wx.optInt("high")));
        form.addView(highEt);
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("🌤️ 设置当天天气")
                .setView(form)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String cond = WEATHERS[condSp.getSelectedItemPosition()][1];
                Integer low = parseIntOpt(lowEt.getText().toString());
                Integer high = parseIntOpt(highEt.getText().toString());
                if (low != null || high != null) {
                    JSONObject w = new JSONObject();
                    w.put("cond", cond);
                    w.put("low", low);
                    w.put("high", high);
                    day.put("weather", w);
                } else {
                    day.remove("weather");
                }
                saveDay();
                renderDay();
                dlg.dismiss();
            } catch (Exception e) { toast(e); }
        }));
        dlg.show();
        // 已有天气时补"删除天气"（与网页版 wx-del 一致）
        if (wx != null && !wx.optString("cond").isEmpty()) {
            Button delWx = new Button(this);
            delWx.setText("删除天气");
            delWx.setTextSize(13);
            Util.styleTextButton(delWx, 0xFFE5484D, this);
            delWx.setOnClickListener(v -> {
                day.remove("weather");
                saveDay();
                renderDay();
                dlg.dismiss();
            });
            LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(-2, -2);
            dp2.leftMargin = Util.dp(this, 18);
            dp2.topMargin = Util.dp(this, 2);
            form.addView(delWx, dp2);
        }
    }

    private Integer parseIntOpt(String s) {
        try { return Integer.valueOf(s.trim()); } catch (Exception e) { return null; }
    }

    private void renderDay() {
        dayBox.removeAllViews();
        JSONObject day = dayObj();

        // 天气（每日可设置，徽章在备注上方；字段与网页版一致：cond + low/high）
        dayBox.addView(weatherRow(day));

        // 备注（可编辑、可一键清空）
        LinearLayout notesCard = sectionCard("📝 当日备注", () -> editNotes(day));
        final JSONObject fDay = day;
        Button notesDel = Util.delBtn(this);
        notesDel.setContentDescription("清空备注");
        notesDel.setOnClickListener(v -> confirmDelete("清空当日备注？", () -> {
            try {
                fDay.remove("notes");
                saveDay();
                renderDay();
            } catch (Exception e) { toast(e); }
        }));
        ((LinearLayout) notesCard.getChildAt(0)).addView(notesDel);
        String notes = day.optString("notes");
        if (notes.isEmpty()) {
            TextView nh = Util.text(this, "（未填写备注）", 13, Util.MUTE);
            nh.setPadding(0, Util.dp(this, 8), 0, 0);
            notesCard.addView(nh);
        } else {
            TextView nv = Util.text(this, notes, 14, Util.SUB);
            nv.setLineSpacing(Util.dp(this, 3), 1f);
            nv.setPadding(0, Util.dp(this, 8), 0, 0);
            notesCard.addView(nv);
        }
        dayBox.addView(notesCard);

        // 提醒（day.reminders）
        dayBox.addView(reminderCard(day));

        // 住宿
        JSONObject lodge = day.optJSONObject("lodging");
        if (lodge != null && !lodge.optString("name").isEmpty()) {
            dayBox.addView(lodgeCard(lodge, day));
        } else if (lodge != null) {
            dayBox.addView(sectionEmpty("🏨 住宿", "未登记住宿", () -> editLodging(day)));
        }

        // 路线
        JSONArray segs = day.optJSONArray("segments");
        if (segs != null && segs.length() > 0) {
            LinearLayout card = sectionCard("🚗 路线（" + segs.length() + " 段）", null);
            for (int i = 0; i < segs.length(); i++) {
                card.addView(segmentRow(segs.optJSONObject(i), i));
                if (i < segs.length() - 1) {
                    View dv = new View(this);
                    dv.setBackgroundColor(Util.LINE);
                    Util.setHeight(dv, 1, this);
                    Util.margin(dv, 0, 10, 0, 10, this);
                    card.addView(dv);
                }
            }
            card.addView(dashedAdd("＋ 添加路线", () -> openSegment(-1)));
            dayBox.addView(card);
        } else {
            dayBox.addView(sectionEmpty("🚗 路线", "今天还没有路线", () -> openSegment(-1)));
        }

        // 记账
        JSONArray exps = day.optJSONArray("expenses");
        if (exps != null && exps.length() > 0) {
            dayBox.addView(expenseCard(day, exps));
        } else {
            dayBox.addView(sectionEmpty("💰 记账", "今天还没有花销", () -> openExpense(-1)));
        }
    }

    private LinearLayout sectionCard(String title, Runnable onEdit) {
        LinearLayout card = card();
        LinearLayout head = Util.hBox(this);
        TextView t = Util.sectionTitle(this, title, accent);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (onEdit != null) {
            LineIconButton b = LineIconButton.edit(this);
            b.setOnClickListener(v -> onEdit.run());
            head.addView(b);
        }
        card.addView(head);
        View dv = new View(this);
        dv.setBackgroundColor(Util.LINE);
        Util.setHeight(dv, 1, this);
        Util.margin(dv, 0, 8, 0, 6, this);
        card.addView(dv);
        return card;
    }

    private LinearLayout sectionEmpty(String title, String hint, Runnable onAdd) {
        LinearLayout card = card();
        card.addView(Util.sectionTitle(this, title, accent));
        TextView h = Util.text(this, hint, 13, Util.MUTE);
        h.setPadding(0, Util.dp(this, 8), 0, 0);
        card.addView(h);
        card.addView(dashedAdd("＋ 添加", onAdd));
        return card;
    }

    private LinearLayout card() {
        LinearLayout card = Util.vBox(this);
        card.setPadding(Util.dp(this, 16), Util.dp(this, 14), Util.dp(this, 16), Util.dp(this, 14));
        card.setBackground(Util.cardStyle(this, 20));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = Util.dp(this, 18);
        card.setLayoutParams(p);
        return card;
    }

    private Button dashedAdd(String s, Runnable r) {
        Button b = new Button(this);
        b.setText(s);
        Util.styleDashedAdd(b, accent, this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = Util.dp(this, 10);
        b.setLayoutParams(p);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private View hairline() {
        View dv = new View(this);
        dv.setBackgroundColor(Util.LINE);
        Util.setHeight(dv, 1, this);
        Util.margin(dv, 0, 8, 0, 6, this);
        return dv;
    }

// ===================== 备注 / 提醒 =====================

    private void editNotes(JSONObject day) {
        EditText et = new EditText(this);
        EditTripActivity.Style.input(et);
        et.setSingleLine(false);
        et.setMinLines(4);
        et.setGravity(Gravity.TOP);
        et.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)});
        et.setText(day.optString("notes"));
        new AlertDialog.Builder(this)
                .setTitle("当日备注（全字显示）")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        String s = et.getText().toString().trim();
                        if (s.isEmpty()) day.remove("notes"); else day.put("notes", s);
                        saveDay();
                        renderDay();
                    } catch (Exception e) { toast(e); }
                })
                .setNegativeButton("取消", null).show();
    }

    private View reminderCard(JSONObject day) {
        LinearLayout card = sectionCard("⏰ 当日提醒", () -> editReminder(day, null, -1));
        JSONArray rems = day.optJSONArray("reminders");
        boolean has = false;
        if (rems != null) for (int i = 0; i < rems.length(); i++) {
            JSONObject r = rems.optJSONObject(i);
            if (r == null) continue;
            has = true;
            final JSONObject fR = r;
            final int fi = i;
            LinearLayout row = Util.hBox(this);
            row.setPadding(0, Util.dp(this, 4), 0, Util.dp(this, 4));
            String when = r.optString("date", "");
            if (!when.isEmpty()) {
                try { when = Util.displayDate(when); } catch (Exception ignored) {}
            }
            if (!r.optString("time").isEmpty()) when = when.trim() + " " + r.optString("time");
            if (when.trim().isEmpty()) when = "随时";
            TextView time = Util.text(this, "⏰ " + when.trim(), 12.5f, accent);
            time.setTypeface(Typeface.DEFAULT_BOLD);
            time.setBackground(Util.chipBg(Util.accentSoft(accent, this), 8, this));
            time.setPadding(Util.dp(this, 8), Util.dp(this, 3), Util.dp(this, 8), Util.dp(this, 3));
            row.addView(time);
            TextView tt = Util.text(this, r.optString("title", ""), 13.5f, Util.INK);
            tt.setPadding(Util.dp(this, 8), 0, 0, 0);
            row.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
            LineIconButton ed = LineIconButton.edit(this);
            ed.setOnClickListener(v -> editReminder(day, fR, fi));
            LineIconButton dl = LineIconButton.del(this);
            dl.setOnClickListener(v -> confirmDelete("删除提醒「" + fR.optString("title") + "」？", () -> {
                JSONArray rs = day.optJSONArray("reminders");
                if (rs != null && fi < rs.length()) rs.remove(fi);
                saveDay();
                renderDay();
            }));
            row.addView(Util.actionGroup(this, ed, dl));
            card.addView(row);
        }
        if (!has) {
            TextView h = Util.text(this, "无提醒（如：9点抢票）", 13, Util.MUTE);
            h.setPadding(0, Util.dp(this, 8), 0, 0);
            card.addView(h);
        }
        card.addView(dashedAdd("＋ 添加提醒", () -> editReminder(day, null, -1)));
        return card;
    }

    private void editReminder(JSONObject day, JSONObject rem, int index) {
        final boolean isNew = rem == null;
        final JSONObject fR;
        if (isNew) {
            fR = new JSONObject();
            try {
                fR.put("id", "r" + System.currentTimeMillis());
                fR.put("date", selectedDay);
            } catch (Exception ignored) {}
        } else {
            fR = rem;
        }
        LinearLayout form = Util.vBox(this);
        int pad = Util.dp(this, 18);
        form.setPadding(pad, 0, pad, 0);
        TextView dateLabel = Util.text(this, "日期：" + Util.displayDate(fR.optString("date", selectedDay)), 13, Util.INK);
        dateLabel.setTypeface(Typeface.DEFAULT_BOLD);
        form.addView(dateLabel);
        EditText titleEt = new EditText(this);
        EditTripActivity.Style.input(titleEt);
        titleEt.setHint("提醒内容（如：9点抢票，≤30字）");
        titleEt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(30)});
        titleEt.setText(fR.optString("title"));
        form.addView(titleEt);
        EditText timeEt = new EditText(this);
        EditTripActivity.Style.input(timeEt);
        timeEt.setHint("时间（如 09:00，可不填）");
        timeEt.setText(fR.optString("time"));
        form.addView(timeEt);
        new AlertDialog.Builder(this)
                .setTitle(isNew ? "添加提醒" : "编辑提醒")
                .setView(form)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        String title = titleEt.getText().toString().trim();
                        if (title.isEmpty()) { Toast.makeText(this, "请填写提醒内容", Toast.LENGTH_SHORT).show(); return; }
                        String tm = Util.normalizeTime(timeEt.getText().toString());
                        if (tm == null) tm = "";
                        fR.put("title", title);
                        fR.put("time", tm);
                        fR.put("date", fR.optString("date", selectedDay));
                        JSONArray rems = day.optJSONArray("reminders");
                        if (rems == null) { rems = new JSONArray(); day.put("reminders", rems); }
                        if (!isNew && index >= 0 && index < rems.length()) rems.put(index, fR);
                        else rems.put(fR);
                        saveDay();
                        renderDay();
                    } catch (Exception e) { toast(e); }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ===================== 住宿 =====================

    private View lodgeCard(JSONObject lodge, JSONObject day) {
        LinearLayout card = sectionCard("🏨 住宿", () -> editLodging(day));
        String type = lodge.optString("type", "酒店");
        String name = lodge.optString("name");
        TextView line1Tv = Util.text(this, type + " · " + name, 14.5f, Util.INK);
        line1Tv.setTypeface(Typeface.DEFAULT_BOLD);
        line1Tv.setPadding(0, Util.dp(this, 4), 0, 0);
        card.addView(line1Tv);
        String location = lodge.optString("location");
        if (!location.isEmpty()) {
            TextView locTv = Util.text(this, "📍 " + location, 13, Util.SUB);
            locTv.setPadding(0, Util.dp(this, 3), 0, 0);
            card.addView(locTv);
        }
        double price = lodge.optDouble("pricePerNight", 0);
        if (price > 0) {
            TextView p = Util.text(this, "¥" + Util.fmtMoney(price) + " / 晚", 13.5f, accent);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setPadding(0, Util.dp(this, 4), 0, 0);
            card.addView(p);
        }
        String ln = lodge.optString("notes");
        if (!ln.isEmpty()) {
            TextView nt = Util.text(this, "💬 " + ln, 13, Util.SUB);
            nt.setPadding(0, Util.dp(this, 3), 0, 0);
            card.addView(nt);
        }
        return card;
    }

    private void editLodging(JSONObject day) {
        JSONObject existing = day.optJSONObject("lodging");
        final JSONObject cur;
        if (existing == null) {
            cur = new JSONObject();
            try { day.put("lodging", cur); } catch (Exception ignored) {}
        } else {
            cur = existing;
        }
        LinearLayout form = Util.vBox(this);
        int pad = Util.dp(this, 18);
        form.setPadding(pad, 0, pad, 0);

        Spinner typeSp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"酒店 🏨", "民宿 🏡", "青旅 🛏️", "其他 🏠"});
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSp.setAdapter(ad);
        String types[] = {"酒店", "民宿", "青旅", "其他"};
        String curType = cur.optString("type", "酒店");
        for (int i = 0; i < types.length; i++) if (types[i].equals(curType)) typeSp.setSelection(i);
        form.addView(typeSp);

        EditText nameEt = inputBox("名称（如：千岛湖临湖民宿，≤30字）");
        nameEt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(30)});
        nameEt.setText(cur.optString("name"));
        form.addView(nameEt);
        EditText locEt = inputBox("位置（如：杭州淳安县）");
        locEt.setText(cur.optString("location"));
        form.addView(locEt);
        EditText priceEt = inputBox("每晚价格（元）");
        priceEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (cur.optDouble("pricePerNight", 0) > 0) priceEt.setText(Util.fmtMoney(cur.optDouble("pricePerNight", 0)));
        form.addView(priceEt);
        EditText noteEt = inputBox("备注（≤80字）");
        noteEt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        noteEt.setText(cur.optString("notes"));
        form.addView(noteEt);

        new AlertDialog.Builder(this)
                .setTitle("住宿信息")
                .setView(form)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        cur.put("type", types[typeSp.getSelectedItemPosition()]);
                        String name = nameEt.getText().toString().trim();
                        String loc = locEt.getText().toString().trim();
                        double price = 0;
                        try { price = Double.parseDouble(priceEt.getText().toString().trim()); } catch (Exception ignored) {}
                        if (name.isEmpty()) cur.put("name", ""); else cur.put("name", name);
                        if (loc.isEmpty()) cur.put("location", ""); else cur.put("location", loc);
                        cur.put("pricePerNight", price);
                        cur.put("notes", noteEt.getText().toString().trim());
                        saveDay();
                        renderAll();
                    } catch (Exception e) { toast(e); }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText inputBox(String hint) {
        EditText et = new EditText(this);
        EditTripActivity.Style.input(et);
        et.setHint(hint);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = Util.dp(this, 8);
        et.setLayoutParams(p);
        return et;
    }

    // ===================== 路线 =====================

    private View segmentRow(JSONObject s, int index) {
        LinearLayout row = Util.vBox(this);
        row.setPadding(0, Util.dp(this, 4), 0, Util.dp(this, 4));
        LinearLayout head = Util.hBox(this);
        int cross = crossDays(s);
        String time = s.optString("departTime", "") + "-" + s.optString("arriveTime", "");
        head.addView(Util.timeBadge(this, time, accent));
        if (cross > 0) {
            TextView cb = Util.text(this, "+" + cross, 10.5f, 0xFFFFFFFF);
            cb.setBackground(Util.chipBg(accent, 8, this));
            cb.setPadding(Util.dp(this, 5), Util.dp(this, 2), Util.dp(this, 5), Util.dp(this, 2));
            head.addView(cb);
        }
        TextView path = Util.text(this, s.optString("from") + "  →  " + s.optString("to"), 14.5f, Util.INK);
        path.setTypeface(Typeface.DEFAULT_BOLD);
        path.setLineSpacing(Util.dp(this, 2), 1f);
        path.setPadding(Util.dp(this, 8), 0, 0, 0);
        head.addView(path, new LinearLayout.LayoutParams(0, -2, 1));
        LineIconButton ed = LineIconButton.edit(this);
        ed.setOnClickListener(v -> openSegment(index));
        LineIconButton dl = LineIconButton.del(this);
        dl.setOnClickListener(v -> confirmDelete("删除这段路线？", () -> {
            JSONArray segs = dayObj() != null ? dayObj().optJSONArray("segments") : null;
            if (segs != null && index < segs.length()) segs.remove(index);
            saveDay();
            renderDay();
        }));
        head.addView(Util.actionGroup(this, ed, dl));
        row.addView(head);
        LinearLayout sub = Util.hBox(this);
        String trans = s.optString("transport", "交通");
        sub.addView(Util.roundBadge(this, Util.transEmoji(trans), 0xFFF2F3F7, 16));
        TextView trTv = Util.text(this, trans, 13, Util.SUB);
        trTv.setPadding(Util.dp(this, 8), 0, 0, 0);
        sub.addView(trTv, new LinearLayout.LayoutParams(0, -2, 1));
        if (s.optDouble("price", 0) > 0) {
            TextView price = Util.text(this, "¥" + Util.fmtMoney(s.optDouble("price", 0)), 14, accent);
            price.setTypeface(Typeface.DEFAULT_BOLD);
            sub.addView(price);
        }
        row.addView(sub);
        FlowLayout chips = new FlowLayout(this);
        chips.setGaps(6, 6, this);
        if (!s.optString("vehicleNo").isEmpty())
            chips.addView(Util.chip(this, Util.transEmoji(trans) + " " + s.optString("vehicleNo"), Util.INK, 0xFFF2F3F7));
        for (String[] p : Util.normPax(s)) {
            String label = "👤 " + p[0] + (p[1].isEmpty() ? "" : "-" + p[1]);
            chips.addView(Util.chip(this, label, Util.INK, 0xFFF2F3F7));
        }
        if (chips.getChildCount() > 0) {
            chips.setPadding(0, Util.dp(this, 6), 0, 0);
            row.addView(chips);
        }
        String nn = s.optString("notes");
        if (!nn.isEmpty()) {
            TextView noteTv = Util.text(this, "💬 " + nn, 13, Util.SUB);
            noteTv.setPadding(0, Util.dp(this, 3), 0, 0);
            row.addView(noteTv);
        }
        return row;
    }

    private int crossDays(JSONObject s) {
        int cd = s.optInt("crossDays", 0);
        if (cd > 1) return cd;
        String dep = s.optString("departTime"), arr = s.optString("arriveTime");
        if (dep.length() == 5 && arr.length() == 5 && arr.compareTo(dep) < 0) return cd > 0 ? cd : 1;
        return 0;
    }

    private void openSegment(int index) {
        Intent it = new Intent(this, EditSegmentActivity.class);
        it.putExtra("tripId", tripId);
        it.putExtra("date", selectedDay);
        it.putExtra("index", index);
        startActivityForResult(it, REQ_SEGMENT);
    }

    private void openExpense(int index) {
        Intent it = new Intent(this, EditExpenseActivity.class);
        it.putExtra("tripId", tripId);
        it.putExtra("date", selectedDay);
        it.putExtra("index", index);
        startActivityForResult(it, REQ_EXPENSE);
    }

    // ===================== 记账 =====================

    /** 记账（对齐网页）：环图 + 纯色圆点图例（点击圆点展开该分类明细，默认无文字）；
     *  下方明细区每行独占整行：自动计入只读行（标注「路线」「住宿」来源）＋ 手动行（可编辑删除）＋ 合计 */
    private LinearLayout expenseCard(JSONObject day, JSONArray exps) {
        double segTotal = 0, lodgeTotal = 0;
        java.util.LinkedHashMap<String, Double> transSum = new java.util.LinkedHashMap<>();
        JSONArray segsDay = day.optJSONArray("segments");
        if (segsDay != null) for (int i = 0; i < segsDay.length(); i++) {
            JSONObject sg = segsDay.optJSONObject(i);
            if (sg == null || sg.optDouble("price", 0) <= 0) continue;
            double pp = sg.optDouble("price", 0);
            segTotal += pp;
            String tr = sg.optString("transport", "交通");
            transSum.put(tr, transSum.getOrDefault(tr, 0.0) + pp);
        }
        JSONObject lodgeDay = day.optJSONObject("lodging");
        if (lodgeDay != null && !lodgeDay.optString("name").isEmpty()
                && lodgeDay.optDouble("pricePerNight", 0) > 0) {
            lodgeTotal = lodgeDay.optDouble("pricePerNight", 0);
        }
        double manualTotal = 0;
        for (int i = 0; i < exps.length(); i++) {
            JSONObject e = exps.optJSONObject(i);
            if (e != null) manualTotal += e.optDouble("amount", 0);
        }
        double total = segTotal + lodgeTotal + manualTotal;

        // 标题行：💰 记账 ＋ 当日总金额（右侧，不入环图）
        LinearLayout card = card();
        LinearLayout head = Util.hBox(this);
        TextView t0 = Util.sectionTitle(this, "💰 记账", accent);
        head.addView(t0, new LinearLayout.LayoutParams(0, -2, 1));
        TextView headAmt = Util.text(this, "¥" + Util.fmtMoney(total), 14, accent);
        headAmt.setTypeface(Typeface.DEFAULT_BOLD);
        headAmt.setPadding(Util.dp(this, 6), 0, 0, 0);
        head.addView(headAmt);
        card.addView(head);
        View dv0 = new View(this);
        dv0.setBackgroundColor(Util.LINE);
        Util.setHeight(dv0, 1, this);
        Util.margin(dv0, 0, 8, 0, 6, this);
        card.addView(dv0);

        java.util.LinkedHashMap<String, Double> catMap = new java.util.LinkedHashMap<>();
        catMap.put("交通", segTotal);
        catMap.put("住宿", lodgeTotal);
        for (int i = 0; i < exps.length(); i++) {
            JSONObject e = exps.optJSONObject(i);
            if (e == null) continue;
            String c = e.optString("category", "其他");
            catMap.put(c, catMap.getOrDefault(c, 0.0) + e.optDouble("amount", 0));
        }
        catMap.entrySet().removeIf(en -> en.getValue() <= 0);
        List<String> cats = new ArrayList<>(catMap.keySet());
        List<Double> sums = new ArrayList<>(catMap.values());

        // 环图区（独占一行，居中；点击彩色圆点才展开该分类明细，默认无文字）
        LinearLayout chartArea = Util.vBox(this);
        chartArea.setPadding(0, Util.dp(this, 6), 0, Util.dp(this, 8));
        DoughnutView dv = new DoughnutView(this);
        List<DoughnutView.Slice> slices = new ArrayList<>();
        for (int i = 0; i < cats.size(); i++)
            slices.add(new DoughnutView.Slice(cats.get(i), Util.catColor(cats.get(i), accent), sums.get(i)));
        dv.setData(slices);
        LinearLayout.LayoutParams dvp = new LinearLayout.LayoutParams(Util.dp(this, 110), Util.dp(this, 110));
        dvp.gravity = Gravity.CENTER_HORIZONTAL;
        chartArea.addView(dv, dvp);
        final LinearLayout expandedBox = Util.vBox(this);
        expandedBox.setPadding(0, Util.dp(this, 4), 0, 0);
        LinearLayout dots = Util.hBox(this);
        dots.setGravity(Gravity.CENTER_HORIZONTAL);
        dots.setPadding(0, Util.dp(this, 4), 0, 0);
        for (int i = 0; i < cats.size(); i++) {
            final String c = cats.get(i);
            View dot = new View(this);
            dot.setBackground(Util.chipBg(Util.catColor(c, accent), 6, this));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Util.dp(this, 12), Util.dp(this, 12));
            dlp.rightMargin = Util.dp(this, 7);
            dot.setLayoutParams(dlp);
            dot.setContentDescription(c);
            dot.setOnClickListener(v -> toggleExpanded(expandedBox, c));
            dots.addView(dot);
        }
        chartArea.addView(dots);
        chartArea.addView(expandedBox);
        card.addView(chartArea);

        View sep = new View(this);
        sep.setBackgroundColor(Util.LINE);
        Util.setHeight(sep, 1, this);
        Util.margin(sep, 0, 2, 0, 4, this);
        card.addView(sep);

        // 明细区（每行独占整行）
        LinearLayout listBox = Util.vBox(this);
        for (java.util.Map.Entry<String, Double> en : transSum.entrySet()) {
            listBox.addView(autoExpRow(Util.transEmoji(en.getKey()), "交通", en.getKey(),
                    en.getValue(), "路线"));
        }
        if (lodgeTotal > 0) {
            listBox.addView(autoExpRow(Util.catEmoji("住宿"), "住宿",
                    lodgeDay.optString("type", "酒店"), lodgeTotal, "住宿"));
        }
        for (int i = 0; i < exps.length(); i++) {
            JSONObject e = exps.optJSONObject(i);
            if (e == null) continue;
            final int idx2 = i;
            LinearLayout row = Util.hBox(this);
            row.setPadding(0, Util.dp(this, 5), 0, Util.dp(this, 5));
            String cat = e.optString("category", "其他");
            row.addView(Util.roundBadge(this, Util.catEmoji(cat),
                    Util.blend(Util.catColor(cat, accent), 0xFFFFFFFF, 0.85f), 15));
            TextView item = Util.text(this, e.optString("item"), 14, Util.INK);
            item.setPadding(Util.dp(this, 8), 0, Util.dp(this, 8), 0);
            row.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
            String when = e.optString("when");
            if (!when.isEmpty()) {
                TextView wt = Util.text(this, when, 11.5f, Util.MUTE);
                wt.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(wt);
            }
            TextView amt = Util.text(this, "¥" + Util.fmtMoney(e.optDouble("amount", 0)), 14, Util.INK);
            amt.setTypeface(Typeface.DEFAULT_BOLD);
            amt.setPadding(Util.dp(this, 6), 0, 0, 0);
            row.addView(amt);
            LineIconButton ed2 = LineIconButton.edit(this);
            ed2.setOnClickListener(v -> openExpense(idx2));
            LineIconButton dl2 = LineIconButton.del(this);
            dl2.setOnClickListener(v -> confirmDelete("删除这笔花销？", () -> {
                JSONArray arr = dayObj() != null ? dayObj().optJSONArray("expenses") : null;
                if (arr != null && idx2 < arr.length()) arr.remove(idx2);
                saveDay();
                renderDay();
            }));
            row.addView(Util.actionGroup(this, ed2, dl2));
            listBox.addView(row);
        }
        if (transSum.isEmpty() && lodgeTotal <= 0 && exps.length() == 0) {
            TextView empty = Util.text(this, "还没有花销记录", 13, Util.MUTE);
            empty.setPadding(0, Util.dp(this, 4), 0, 0);
            listBox.addView(empty);
        }
        LinearLayout totalRow = Util.hBox(this);
        totalRow.setPadding(0, Util.dp(this, 8), 0, 0);
        TextView totalLabel = Util.text(this, "🧾 当日合计", 13, Util.MUTE);
        totalLabel.setTypeface(Typeface.DEFAULT_BOLD);
        totalRow.addView(totalLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView totalAmt = Util.text(this, "¥" + Util.fmtMoney(total), 17, accent);
        totalAmt.setTypeface(Typeface.DEFAULT_BOLD);
        totalRow.addView(totalAmt);
        listBox.addView(totalRow);
        card.addView(listBox);

        card.addView(dashedAdd("＋ 添加花销", () -> openExpense(-1)));
        return card;
    }

    /** 点击圆点：就地展开/收起该分类明细（手动+自动计入混排），默认无文字 */
    private void toggleExpanded(LinearLayout box, String cat) {
        box.removeAllViews();
        JSONObject day = dayObj();
        if (day == null) return;
        List<String> lines = new ArrayList<>();
        if ("交通".equals(cat)) {
            JSONArray segsD = day.optJSONArray("segments");
            if (segsD != null) for (int i = 0; i < segsD.length(); i++) {
                JSONObject sg = segsD.optJSONObject(i);
                if (sg != null && sg.optDouble("price", 0) > 0)
                    lines.add(sg.optString("from") + "→" + sg.optString("to")
                            + " ¥" + Util.fmtMoney(sg.optDouble("price", 0)) + "（路线）");
            }
        }
        if ("住宿".equals(cat)) {
            JSONObject lg = day.optJSONObject("lodging");
            if (lg != null && lg.optDouble("pricePerNight", 0) > 0)
                lines.add(lg.optString("name") + " ¥" + Util.fmtMoney(lg.optDouble("pricePerNight", 0))
                        + "（住宿）");
        }
        JSONArray expsB = day.optJSONArray("expenses");
        if (expsB != null) for (int i = 0; i < expsB.length(); i++) {
            JSONObject e = expsB.optJSONObject(i);
            if (e != null && cat.equals(e.optString("category", "其他"))) {
                lines.add(e.optString("item")
                        + (e.optString("when").isEmpty() ? "" : "（" + e.optString("when") + "）")
                        + " ¥" + Util.fmtMoney(e.optDouble("amount", 0)));
            }
        }
        if (lines.isEmpty()) lines.add("该分类暂无明细");
        for (String ln : lines) {
            TextView t = Util.text(this, "• " + ln, 11f, Util.MUTE);
            t.setTag(cat);
            t.setSingleLine(true);
            t.setPadding(0, Util.dp(this, 1), 0, Util.dp(this, 1));
            box.addView(t);
        }
    }

    /** 自动计入只读行：图标徽章与手动行完全同款（同底同字号 15）、正文 14px、来源小标签替代编辑/删除 */
    private View autoExpRow(String emoji, String catName, String itemText, double amount, String source) {
        LinearLayout row = Util.hBox(this);
        row.setPadding(0, Util.dp(this, 5), 0, Util.dp(this, 5));
        row.addView(Util.roundBadge(this, emoji, Util.blend(Util.catColor(catName, accent), 0xFFFFFFFF, 0.85f), 15));
        TextView cat = Util.text(this, catName, 14, Util.INK);
        cat.setPadding(Util.dp(this, 8), 0, Util.dp(this, 8), 0);
        row.addView(cat);
        TextView item = Util.text(this, itemText, 14, Util.INK);
        item.setPadding(0, 0, Util.dp(this, 8), 0);
        row.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        TextView amt = Util.text(this, "¥" + Util.fmtMoney(amount), 14, Util.INK);
        amt.setTypeface(Typeface.DEFAULT_BOLD);
        amt.setPadding(Util.dp(this, 4), 0, Util.dp(this, 2), 0);
        row.addView(amt);
        TextView src = Util.text(this, source, 10.5f, Util.MUTE);
        src.setBackground(Util.chipBg(0xFFF0F0F5, 99, this));
        src.setPadding(Util.dp(this, 7), Util.dp(this, 2), Util.dp(this, 7), Util.dp(this, 2));
        src.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.leftMargin = Util.dp(this, 6);
        src.setLayoutParams(sp);
        row.addView(src);
        return row;
    }

// ===================== 行李清单 =====================

    private static final String[][] PACK_CATS = {
            {"🪪", "证件"}, {"🔌", "电子设备"}, {"👕", "衣物"}, {"🧴", "洗护用品"}, {"💊", "药品"}, {"🍫", "食品"}, {"📌", "其他"}
    };
    private static final String[] IMPORTS = {"必需品", "非必需", "待定"};

    private void ensurePacking() {
        try {
            if (trip.has("packing") && trip.has("luggageBags")) return;
            JSONArray packing = new JSONArray();
            JSONArray old = trip.optJSONArray("checklist");
            if (old != null) {
                for (int i = 0; i < old.length(); i++) {
                    JSONObject o = old.optJSONObject(i);
                    if (o == null) continue;
                    JSONObject item = new JSONObject();
                    item.put("id", "p" + System.currentTimeMillis() + i);
                    item.put("category", "其他");
                    item.put("name", o.optString("text", "物品"));
                    item.put("importance", "待定");
                    item.put("status", o.optBoolean("done", false) ? "已携带" : "未携带");
                    item.put("notes", "");
                    packing.put(item);
                }
            }
            trip.put("packing", packing);
            trip.put("packingOrder", new JSONArray());
            JSONArray bags = new JSONArray();
            bags.put(bagObj("bag1", "🧳", "1号行李箱"));
            bags.put(bagObj("bag2", "👜", "2号手提袋"));
            bags.put(bagObj("bag3", "🎒", "3号随身携带"));
            trip.put("luggageBags", bags);
            saveDay();
        } catch (Exception e) { toast(e); }
    }

    private JSONObject bagObj(String id, String icon, String name) {
        JSONObject b = new JSONObject();
        try { b.put("id", id); b.put("icon", icon); b.put("name", name); } catch (Exception ignored) {}
        return b;
    }

    private void renderPacking() {
        ensurePacking();
        dayBox.removeAllViews();
        JSONArray packing = trip.optJSONArray("packing");
        JSONArray bags = trip.optJSONArray("luggageBags");

        // 块1 计划携带物品（按品类分组；物品行可长按拖入行李卡，行内点击行李文字可改袋）
        LinearLayout card1 = card();
        card1.addView(Util.sectionTitle(this, "🎒 计划携带物品", accent));
        card1.addView(hairline());
        if (packing.length() == 0) {
            TextView h = Util.text(this, "还没有物品，点下方「＋ 添加物品」开始", 13, Util.MUTE);
            h.setPadding(0, Util.dp(this, 8), 0, 0);
            card1.addView(h);
        } else {
            LinkedHashMap<String, List<JSONObject>> groups = new LinkedHashMap<>();
            for (int i = 0; i < packing.length(); i++) {
                JSONObject it = packing.optJSONObject(i);
                if (it == null) continue;
                String c = it.optString("category", "其他");
                if (!groups.containsKey(c)) groups.put(c, new ArrayList<>());
                groups.get(c).add(it);
            }
            for (Map.Entry<String, List<JSONObject>> g : groups.entrySet()) {
                LinearLayout head = Util.hBox(this);
                head.setPadding(0, Util.dp(this, 8), 0, Util.dp(this, 2));
                head.addView(Util.roundBadge(this, packCatIcon(g.getKey()), 0xFFF2F3F7, 12));
                TextView gt = Util.text(this, g.getKey() + "（" + g.getValue().size() + " 件）", 13.5f, Util.INK);
                gt.setTypeface(Typeface.DEFAULT_BOLD);
                gt.setPadding(Util.dp(this, 6), 0, 0, 0);
                head.addView(gt);
                card1.addView(head);
                for (JSONObject it : g.getValue()) card1.addView(packItemRow(packing, it));
            }
        }
        card1.addView(dashedAdd("＋ 添加物品", () -> editItem(null)));
        dayBox.addView(card1);

        // 块2 行李携带可行性模拟（行李卡是拖放目标，下方待收纳池可拖回）
        LinearLayout card2 = card();
        card2.addView(Util.sectionTitle(this, "🧳 行李携带可行性模拟", accent));
        card2.addView(hairline());
        if (bags.length() > 0) {
            for (int i = 0; i < bags.length(); i++) card2.addView(bagRow(bags, bags.optJSONObject(i)));
        }
        card2.addView(dashedAdd("＋ 新增行李箱／袋", () -> addBag()));
        int inBag = 0, total = packing.length();
        for (int i = 0; i < packing.length(); i++) {
            JSONObject it = packing.optJSONObject(i);
            if (it != null && !Util.bagOf(it).isEmpty()) inBag++;
        }
        TextView tip = Util.text(this,
                total == 0 ? "🪄 添加物品后，把物品拖进行李箱模拟收纳" :
                inBag == total ? "✅ 已全部收纳（" + total + " 件），可点下面按钮同步为「已携带」"
                        : "⚠️ 还有 " + (total - inBag) + " 件未放入行李箱（已放 " + inBag + "/" + total + "）",
                13, total == 0 ? Util.MUTE : (inBag == total ? accent : 0xFFE5484D));
        tip.setTypeface(Typeface.DEFAULT_BOLD);
        tip.setPadding(0, Util.dp(this, 8), 0, 0);
        card2.addView(tip);
        LinearLayout poolHead = Util.hBox(this);
        poolHead.setPadding(0, Util.dp(this, 8), 0, 0);
        TextView poolLabel = Util.text(this, "待收纳物品池（" + (total - inBag) + "）", 12.5f, Util.MUTE);
        poolHead.addView(poolLabel, new LinearLayout.LayoutParams(0, -2, 1));
        Button sync = new Button(this);
        sync.setText("✓ 同步");
        sync.setTextSize(12);
        Util.styleSoft(sync, accent, Util.accentSoft(accent, this), this);
        sync.setMinHeight(Util.dp(this, 30));
        sync.setOnClickListener(v -> {
            try {
                for (int i = 0; i < packing.length(); i++) {
                    JSONObject it = packing.optJSONObject(i);
                    if (it == null) continue;
                    it.put("status", Util.bagOf(it).isEmpty() ? "未携带" : "已携带");
                }
                saveDay();
                renderPacking();
                Toast.makeText(this, "已同步：放入行李的物品标记为「已携带」", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { toast(e); }
        });
        poolHead.addView(sync);
        card2.addView(poolHead);

        // 待收纳物品池：未放入物品的 chip；长按拖入上方行李卡；此处也是拖回目标
        final LinearLayout poolWrap = new LinearLayout(this);
        poolWrap.setOrientation(LinearLayout.VERTICAL);
        poolWrap.setPadding(0, Util.dp(this, 6), 0, 0);
        FlowLayout pool = new FlowLayout(this);
        pool.setGaps(6, 6, this);
        boolean hasUnpacked = false;
        for (int i = 0; i < packing.length(); i++) {
            final JSONObject it = packing.optJSONObject(i);
            if (it == null || !Util.bagOf(it).isEmpty()) continue;
            hasUnpacked = true;
            final TextView chip = packChip(it, false, null);
            chip.setOnLongClickListener(v -> {
                ClipData cd = ClipData.newPlainText("pk", it.optString("id"));
                chip.startDragAndDrop(cd, new View.DragShadowBuilder(chip), it, 0);
                return true;
            });
            pool.addView(chip);
        }
        if (!hasUnpacked) {
            TextView h = Util.text(this, total == 0 ? "（暂无可收纳物品）" : "🎉 全部物品已放入行李箱",
                    13, total == 0 ? Util.MUTE : accent);
            if (total > 0) h.setTypeface(Typeface.DEFAULT_BOLD);
            pool.addView(h);
        }
        poolWrap.addView(pool);
        poolWrap.setOnDragListener((v, ev) -> poolDragListener(ev, null));
        card2.addView(poolWrap);
        dayBox.addView(card2);
    }

    private String packCatIcon(String c) {
        for (String[] p : PACK_CATS) if (p[1].equals(c)) return p[0];
        return "📦";
    }

    private View packItemRow(JSONArray packing, JSONObject it) {
        final JSONObject fIt = it;
        LinearLayout row = Util.vBox(this);
        row.setPadding(0, Util.dp(this, 5), 0, Util.dp(this, 5));
        LinearLayout r1 = Util.hBox(this);
        r1.addView(Util.roundBadge(this, packCatIcon(it.optString("category", "其他")), 0xFFF2F3F7, 13));
        TextView name = Util.text(this, it.optString("name", "物品"), 14, Util.INK);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setPadding(Util.dp(this, 7), 0, 0, 0);
        r1.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        String imp = it.optString("importance", "待定");
        r1.addView(Util.chip(this, "📌 " + imp, impColor(imp), impBg(imp)));
        row.addView(r1);
        String notes = it.optString("notes");
        if (!notes.isEmpty()) {
            TextView nt = Util.text(this, "💬 " + notes, 12.5f, Util.MUTE);
            nt.setPadding(Util.dp(this, 22), Util.dp(this, 2), 0, 0);
            row.addView(nt);
        }
        LinearLayout r2 = Util.hBox(this);
        String bagId = Util.bagOf(it);
        final TextView bagTv = Util.text(this, bagId.isEmpty() ? "未携带" : "🧺 " + bagName(bagId), 14, Util.INK);
        bagTv.setPadding(Util.dp(this, 22), Util.dp(this, 3), 0, 0);
        bagTv.setOnClickListener(v -> showBagPicker(fIt));
        r2.addView(bagTv, new LinearLayout.LayoutParams(0, -2, 1));
        LineIconButton ed = LineIconButton.edit(this);
        ed.setOnClickListener(v -> editItem(fIt));
        LineIconButton dl = LineIconButton.del(this);
        final JSONObject fDel = it;
        dl.setOnClickListener(v -> confirmDelete("删除物品「" + fDel.optString("name") + "」？", () -> {
            int idx = indexOf(packing, fDel);
            if (idx >= 0) packing.remove(idx);
            saveDay();
            renderPacking();
        }));
        r2.addView(Util.actionGroup(this, ed, dl));
        row.addView(r2);
        // 物品行本身也可长按拖拽（对齐网页 pk-row）
        row.setOnLongClickListener(v -> {
            ClipData cd = ClipData.newPlainText("pk", fIt.optString("id"));
            row.startDragAndDrop(cd, new View.DragShadowBuilder(row), fIt, 0);
            return true;
        });
        return row;
    }

    private int indexOf(JSONArray arr, JSONObject target) {
        for (int i = 0; i < arr.length(); i++) if (arr.optJSONObject(i) == target) return i;
        return -1;
    }

    private int impColor(String imp) {
        if ("必需品".equals(imp)) return 0xFFC0392B;
        if ("非必需".equals(imp)) return 0xFF5B6B7F;
        return 0xFF8A7A45;
    }

    private int impBg(String imp) {
        if ("必需品".equals(imp)) return 0xFFFFE9E9;
        if ("非必需".equals(imp)) return 0xFFEEF2F7;
        return 0xFFF4F1E8;
    }

    private String bagName(String bagId) {
        JSONArray bags = trip.optJSONArray("luggageBags");
        if (bags != null) for (int i = 0; i < bags.length(); i++) {
            JSONObject b = bags.optJSONObject(i);
            if (b != null && bagId.equals(b.optString("id")))
                return b.optString("icon", "🧺") + " " + b.optString("name", bagId);
        }
        return bagId;
    }

    private View bagRow(JSONArray bags, final JSONObject bag) {
        if (bag == null) return new View(this);
        final JSONObject fBag = bag;
        LinearLayout wrap = Util.vBox(this);
        wrap.setPadding(0, Util.dp(this, 6), 0, 0);
        wrap.setOnDragListener((v, ev) -> {
            switch (ev.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case android.view.DragEvent.ACTION_DRAG_ENTERED:
                    v.setBackgroundColor(Util.accentSoft(accent, this));
                    return true;
                case android.view.DragEvent.ACTION_DRAG_EXITED:
                    v.setBackgroundColor(0x00000000);
                    return true;
                case android.view.DragEvent.ACTION_DROP: {
                    Object o = ev.getLocalState();
                    if (o instanceof JSONObject) {
                        try {
                            JSONObject it = (JSONObject) o;
                            it.put("bag", bag.optString("id"));
                            it.put("status", "已携带");
                            saveDay();
                            renderPacking();
                        } catch (Exception e) { toast(e); }
                    }
                    return true;
                }
                case android.view.DragEvent.ACTION_DRAG_ENDED:
                    v.setBackgroundColor(0x00000000);
                    return true;
            }
            return false;
        });
        LinearLayout head = Util.hBox(this);
        head.setBackground(Util.chipBg(0xFFF7F8FB, 12, this));
        head.setPadding(Util.dp(this, 10), Util.dp(this, 6), Util.dp(this, 8), Util.dp(this, 6));
        head.addView(Util.text(this, bag.optString("icon", "🧺"), 15, Util.INK));
        TextView bn = Util.text(this, bag.optString("name", "行李"), 13.5f, Util.INK);
        bn.setTypeface(Typeface.DEFAULT_BOLD);
        bn.setPadding(Util.dp(this, 6), 0, 0, 0);
        head.addView(bn, new LinearLayout.LayoutParams(0, -2, 1));
        int cnt = bagCount(fBag.optString("id"));
        TextView cntTv = Util.text(this, cnt + " 件", 12, Util.MUTE);
        head.addView(cntTv);
        LineIconButton edb = LineIconButton.edit(this);
        edb.setOnClickListener(v -> editBag(fBag));
        LineIconButton dlb = LineIconButton.del(this);
        dlb.setOnClickListener(v -> confirmDelete("删除行李「" + fBag.optString("name")
                + "」？袋内物品将移回待收纳池。", () -> {
            try {
                JSONArray packing = trip.optJSONArray("packing");
                if (packing != null) for (int i = 0; i < packing.length(); i++) {
                    JSONObject it = packing.optJSONObject(i);
                    if (it != null && fBag.optString("id").equals(Util.bagOf(it))) it.remove("bag");
                }
                bags.remove(indexOf(bags, fBag));
                saveDay();
                renderPacking();
            } catch (Exception e) { toast(e); }
        }));
        head.addView(edb);
        head.addView(dlb);
        wrap.addView(head);
        FlowLayout inner = new FlowLayout(this);
        inner.setGaps(6, 6, this);
        JSONArray packing = trip.optJSONArray("packing");
        boolean hasIn = false;
        if (packing != null) for (int i = 0; i < packing.length(); i++) {
            final JSONObject it = packing.optJSONObject(i);
            if (it == null || !bag.optString("id").equals(Util.bagOf(it))) continue;
            hasIn = true;
            final TextView chip = packChip(it, true, () -> dragItemToPool(it));
            chip.setBackground(Util.chipBg(Util.accentSoft(accent, this), 9, this));
            chip.setOnLongClickListener(v -> {
                ClipData cd = ClipData.newPlainText("pk", it.optString("id"));
                chip.startDragAndDrop(cd, new View.DragShadowBuilder(chip), it, 0);
                return true;
            });
            inner.addView(chip);
        }
        if (!hasIn) {
            TextView empty = Util.text(this, "把下方物品拖到这里", 12.5f, Util.MUTE);
            empty.setPadding(Util.dp(this, 4), Util.dp(this, 4), 0, 0);
            inner.addView(empty);
        }
        inner.setPadding(0, Util.dp(this, 5), 0, 0);
        wrap.addView(inner);
        return wrap;
    }

    private TextView packChip(JSONObject it, boolean inBag, Runnable onRemove) {
        String name = it.optString("name", "物品");
        boolean must = "必需品".equals(it.optString("importance"));
        TextView c = Util.chip(this, packCatIcon(it.optString("category", "其他")) + " " + name,
                Util.INK, 0xFFF2F3F7);
        if (must) {
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(
                    packCatIcon(it.optString("category", "其他")) + " " + name);
            int start = sb.length();
            sb.append(inBag ? "  ✕" : "");
            sb.append(" 必");
            sb.setSpan(new android.text.style.ForegroundColorSpan(0xFFC0392B), start, sb.length(), 0);
            sb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), start, sb.length(), 0);
            c.setText(sb);
        }
        if (inBag && onRemove != null) c.setOnClickListener(v -> onRemove.run());
        return c;
    }

    private void dragItemToPool(JSONObject it) {
        try {
            it.remove("bag");
            it.put("status", "未携带");
            saveDay();
            renderPacking();
        } catch (Exception e) { toast(e); }
    }

    private boolean poolDragListener(android.view.DragEvent ev, String unused) {
        switch (ev.getAction()) {
            case android.view.DragEvent.ACTION_DRAG_STARTED:
                return true;
            case android.view.DragEvent.ACTION_DROP: {
                Object o = ev.getLocalState();
                if (o instanceof JSONObject) dragItemToPool((JSONObject) o);
                return true;
            }
            case android.view.DragEvent.ACTION_DRAG_ENDED:
                return true;
        }
        return false;
    }

    private void showBagPicker(JSONObject item) {
        final JSONObject fItem = item;
        JSONArray bags = trip.optJSONArray("luggageBags");
        if (bags == null || bags.length() == 0) { Toast.makeText(this, "请先添加行李袋", Toast.LENGTH_SHORT).show(); return; }
        String[] options = new String[bags.length() + 1];
        for (int i = 0; i < bags.length(); i++) {
            JSONObject b = bags.optJSONObject(i);
            options[i] = b.optString("icon", "🧺") + " " + b.optString("name", "行李")
                    + "（已装 " + bagCount(b.optString("id")) + " 件）";
        }
        options[bags.length()] = "暂不放入";
        new AlertDialog.Builder(this)
                .setTitle("「" + fItem.optString("name") + "」放入哪个行李？")
                .setItems(options, (d, w) -> {
                    try {
                        if (w < bags.length()) {
                            fItem.put("bag", bags.optJSONObject(w).optString("id"));
                            fItem.put("status", "已携带");
                        } else {
                            fItem.remove("bag");
                            fItem.put("status", "未携带");
                        }
                        saveDay();
                        renderPacking();
                    } catch (Exception e) { toast(e); }
                })
                .show();
    }

    private int bagCount(String bagId) {
        JSONArray packing = trip.optJSONArray("packing");
        int n = 0;
        if (packing != null) for (int i = 0; i < packing.length(); i++) {
            JSONObject it = packing.optJSONObject(i);
            if (it != null && bagId.equals(Util.bagOf(it))) n++;
        }
        return n;
    }

    private void editItem(JSONObject item) {
        final boolean isNew = item == null;
        final JSONObject fItem;
        if (isNew) {
            fItem = new JSONObject();
            try {
                fItem.put("id", "p" + System.currentTimeMillis());
                fItem.put("importance", "待定");
                fItem.put("status", "未携带");
                fItem.put("notes", "");
            } catch (Exception ignored) {}
        } else {
            fItem = item;
        }
        LinearLayout form = Util.vBox(this);
        int pad = Util.dp(this, 18);
        form.setPadding(pad, 0, pad, 0);
        List<String> catItems = new ArrayList<>();
        List<String> catVals = new ArrayList<>();
        for (String[] c : PACK_CATS) { catItems.add(c[0] + " " + c[1]); catVals.add(c[1]); }
        JSONArray packing = trip.optJSONArray("packing");
        if (packing != null) for (int i = 0; i < packing.length(); i++) {
            String c = packing.optJSONObject(i) != null ? packing.optJSONObject(i).optString("category") : "";
            if (!c.isEmpty() && !catVals.contains(c)) { catItems.add("📦 " + c); catVals.add(c); }
        }
        catItems.add("📝 自定义分类…"); catVals.add("__custom__");
        Spinner catSp = new Spinner(this);
        ArrayAdapter<String> catAd = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, catItems.toArray(new String[0]));
        catAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        catSp.setAdapter(catAd);
        String curCat = fItem.optString("category", "其他");
        for (int i = 0; i < catVals.size(); i++) if (catVals.get(i).equals(curCat)) catSp.setSelection(i);
        form.addView(catSp);
        final EditText customCatEt = new EditText(this);
        EditTripActivity.Style.input(customCatEt);
        customCatEt.setHint("自定义分类名（≤12字）");
        customCatEt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(12)});
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = Util.dp(this, 8);
        customCatEt.setLayoutParams(cp);
        form.addView(customCatEt);
        catSp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
                customCatEt.setVisibility(catVals.get(pos).equals("__custom__") ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        customCatEt.setVisibility(View.GONE);
        EditText nameEt = new EditText(this);
        EditTripActivity.Style.input(nameEt);
        nameEt.setHint("名称（≤20 字，全字显示）");
        nameEt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(20)});
        nameEt.setText(fItem.optString("name"));
        nameEt.setSingleLine(false);
        nameEt.setMinLines(2);
        form.addView(nameEt);
        Spinner impSp = new Spinner(this);
        ArrayAdapter<String> impAd = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, IMPORTS);
        impAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        impSp.setAdapter(impAd);
        String curImp = fItem.optString("importance", "待定");
        for (int i = 0; i < IMPORTS.length; i++) if (IMPORTS[i].equals(curImp)) impSp.setSelection(i);
        form.addView(impSp);
        // 放入哪个行李袋（状态由袋自动派生：有袋=已携带，无袋=未携带，与网页一致）
        JSONArray bagsForPick = trip.optJSONArray("luggageBags");
        List<String> bagLabels = new ArrayList<>();
        List<String> bagIds = new ArrayList<>();
        bagLabels.add("暂不放入");
        bagIds.add("");
        int curBagSel = 0;
        if (bagsForPick != null) for (int i = 0; i < bagsForPick.length(); i++) {
            JSONObject b = bagsForPick.optJSONObject(i);
            if (b == null) continue;
            String id = b.optString("id");
            bagLabels.add(b.optString("icon", "🧺") + " " + b.optString("name", "行李"));
            bagIds.add(id);
            if (id.equals(Util.bagOf(fItem))) curBagSel = bagLabels.size() - 1;
        }
        Spinner bagSp = new Spinner(this);
        ArrayAdapter<String> bagAd = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                bagLabels.toArray(new String[0]));
        bagAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bagSp.setAdapter(bagAd);
        bagSp.setSelection(curBagSel);
        form.addView(bagSp);
        EditText noteEt = new EditText(this);
        EditTripActivity.Style.input(noteEt);
        noteEt.setHint("备注（≤80字）");
        noteEt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        noteEt.setText(fItem.optString("notes"));
        form.addView(noteEt);
        new AlertDialog.Builder(this)
                .setTitle(isNew ? "添加物品" : "编辑物品")
                .setView(form)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        String name = nameEt.getText().toString().trim();
                        if (name.isEmpty()) { Toast.makeText(this, "请填写名称", Toast.LENGTH_SHORT).show(); return; }
                        String cat = catVals.get(catSp.getSelectedItemPosition());
                        if ("__custom__".equals(cat)) {
                            cat = customCatEt.getText().toString().trim();
                            if (cat.isEmpty()) { Toast.makeText(this, "请填写自定义分类名", Toast.LENGTH_SHORT).show(); return; }
                            if (cat.length() > 12) cat = cat.substring(0, 12);
                        }
                        fItem.put("category", cat);
                        fItem.put("name", name);
                        fItem.put("importance", IMPORTS[impSp.getSelectedItemPosition()]);
                        String pickBag = bagIds.get(bagSp.getSelectedItemPosition());
                        if (pickBag.isEmpty()) {
                            fItem.remove("bag");
                            fItem.put("status", "未携带");
                        } else {
                            fItem.put("bag", pickBag);
                            fItem.put("status", "已携带");
                        }
                        fItem.put("notes", noteEt.getText().toString().trim());
                        if (isNew) packing.put(fItem);
                        saveDay();
                        renderPacking();
                    } catch (Exception e) { toast(e); }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addBag() { editBag(null); }

    private void editBag(JSONObject bag) {
        final boolean isNew = bag == null;
        final JSONObject fBag;
        if (isNew) {
            fBag = new JSONObject();
            try {
                fBag.put("id", "bag" + System.currentTimeMillis());
                fBag.put("icon", "🧺");
            } catch (Exception ignored) {}
        } else {
            fBag = bag;
        }
        LinearLayout form = Util.vBox(this);
        int pad = Util.dp(this, 18);
        form.setPadding(pad, 0, pad, 0);
        EditText iconEt = new EditText(this);
        EditTripActivity.Style.input(iconEt);
        iconEt.setHint("图标（一个 emoji，如 🧳）");
        iconEt.setText(fBag.optString("icon", "🧺"));
        form.addView(iconEt);
        EditText nameEt = new EditText(this);
        EditTripActivity.Style.input(nameEt);
        nameEt.setHint("名称（如：2号手提袋，≤12字）");
        nameEt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(12)});
        nameEt.setText(fBag.optString("name"));
        form.addView(nameEt);
        new AlertDialog.Builder(this)
                .setTitle(isNew ? "新增行李袋" : "编辑行李袋")
                .setView(form)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        String nm = nameEt.getText().toString().trim();
                        if (nm.isEmpty()) { Toast.makeText(this, "请填写名称", Toast.LENGTH_SHORT).show(); return; }
                        String ic = iconEt.getText().toString().trim();
                        fBag.put("icon", ic.isEmpty() ? "🧺" : ic);
                        fBag.put("name", nm);
                        if (isNew) {
                            JSONArray bags = trip.optJSONArray("luggageBags");
                            if (bags != null) bags.put(fBag);
                        }
                        saveDay();
                        renderPacking();
                    } catch (Exception e) { toast(e); }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ===================== PDF 导出 =====================

    /** PDF 预览弹窗：先看将生成的内容（与导出同一数据源），确认后再导出 */
    private void showPdfPreview() {
        List<String> lines = PdfExport.preview(trip);
        final Dialog dlg2 = new Dialog(this);
        dlg2.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout box = Util.vBox(this);
        box.setBackgroundColor(Util.BG);
        // 标题条
        LinearLayout head = Util.hBox(this);
        head.setBackgroundColor(0xFFFFFFFF);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Util.dp(this, 16), Util.dp(this, 14), Util.dp(this, 12), Util.dp(this, 10));
        TextView ht = Util.text(this, "📄 PDF 预览", 16, Util.INK);
        ht.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1));
        Button closeX = Util.iconCircle(this, "✕", Util.INK, 0xFFF2F3F7, 34);
        closeX.setContentDescription("关闭预览");
        closeX.setOnClickListener(v -> dlg2.dismiss());
        head.addView(closeX);
        box.addView(head);
        // 内容（滚动）
        ScrollView sv = new ScrollView(this);
        LinearLayout content = Util.vBox(this);
        content.setPadding(Util.dp(this, 16), Util.dp(this, 12), Util.dp(this, 16), Util.dp(this, 12));
        for (String ln : lines) {
            boolean section = ln.startsWith("[");
            TextView tv = Util.text(this, ln, section ? 14.5f : 13f,
                    section ? 0xFF1F2230 : (ln.startsWith("     ") ? 0xFF9BA0AE : 0xFF6B6E80));
            if (section) tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setLineSpacing(Util.dp(this, 2), 1f);
            content.addView(tv);
            if (section) {
                View gap = new View(this);
                Util.setHeight(gap, 5, this);
                content.addView(gap);
            }
        }
        sv.addView(content);
        box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        // 底部：导出 / 关闭
        LinearLayout foot = Util.hBox(this);
        foot.setBackgroundColor(0xFFFFFFFF);
        foot.setPadding(Util.dp(this, 16), Util.dp(this, 10), Util.dp(this, 16), Util.dp(this, 12));
        Button expB = new Button(this);
        expB.setText("📄 导出 PDF");
        expB.setTextSize(14);
        Util.stylePrimary(expB, accent, this);
        LinearLayout.LayoutParams ebp = new LinearLayout.LayoutParams(0, -2, 1);
        foot.addView(expB, ebp);
        Button cancelB = new Button(this);
        cancelB.setText("关闭");
        cancelB.setTextSize(14);
        Util.styleSoft(cancelB, Util.INK, 0xFFF2F3F7, this);
        LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(-2, -2);
        cbp.leftMargin = Util.dp(this, 10);
        foot.addView(cancelB, cbp);
        box.addView(foot);
        dlg2.setContentView(box);
        android.view.Window win = dlg2.getWindow();
        if (win != null) {
            win.setLayout(-1, -1);
            win.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Util.BG));
        }
        expB.setOnClickListener(v -> { dlg2.dismiss(); exportPdf(); });
        cancelB.setOnClickListener(v -> dlg2.dismiss());
        closeX.setOnClickListener(v -> dlg2.dismiss());
        dlg2.show();
    }

    private void exportPdf() {
        Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("application/pdf");
        String safe = trip.optString("name", "行程").replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safe.length() > 40) safe = safe.substring(0, 40);
        it.putExtra(Intent.EXTRA_TITLE, safe + ".pdf");
        startActivityForResult(it, REQ_PDF);
    }

    // ===================== 通用 =====================

    private void saveDay() {
        try {
            JSONArray trips = Store.load(this);
            boolean found = false;
            for (int i = 0; i < trips.length(); i++) {
                JSONObject t = trips.optJSONObject(i);
                if (t != null && tripId.equals(t.optString("id"))) { trips.put(i, trip); found = true; break; }
            }
            if (!found) trips.put(trip);
            Store.save(this, trips);
        } catch (Exception e) { toast(e); }
    }

    private void toast(Exception e) { Toast.makeText(this, "操作失败：" + e.getMessage(), Toast.LENGTH_LONG).show(); }

    private void confirmDelete(String msg, Runnable onOk) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage(msg)
                .setPositiveButton("删除", (d, w) -> onOk.run())
                .setNegativeButton("取消", null)
                .show();
    }

    private String joinArr(JSONArray arr) {
        if (arr == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) { if (i > 0) sb.append("、"); sb.append(arr.optString(i)); }
        return sb.toString();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK) return;
        try {
            if (req == REQ_EDIT_TRIP) {
                reload();
            } else if (req == REQ_SEGMENT || req == REQ_EXPENSE) {
                // 子页面已写盘：强制从磁盘重载整份行程再渲染，保证记账/路线/总花费必然同步
                trip = EditTripActivity.findTrip(Store.load(this), tripId);
                if (trip == null) { finish(); return; }
                renderAll();
            } else if (req == REQ_PDF) {
                Uri uri = data.getData();
                PdfDocument doc = PdfExport.build(trip);
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    doc.writeTo(out);
                    Toast.makeText(this, "PDF 已导出（" + doc.getPages().size() + " 页）", Toast.LENGTH_SHORT).show();
                }
                doc.close();
            }
            renderHeader();
        } catch (Exception e) {
            toast(e);
        }
    }
}
