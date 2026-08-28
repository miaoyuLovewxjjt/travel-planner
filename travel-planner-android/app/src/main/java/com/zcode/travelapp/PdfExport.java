package com.zcode.travelapp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行程 PDF 导出（纯框架 android.graphics.pdf，零依赖）。
 * 结构：封面头 → 逐日章节（备注 / 住宿卡 / 路线表 / 记账表 / 每日总结行）→ 行程总结章节（交通汇总表 + 消费构成）。
 * 表格规范（参考网页版）：
 *  - 表头浅灰底加粗，行间分隔线，长文本格内自动换行，表头跨页自动重画
 *  - 空值一律显示 —；金额列右对齐
 */
final class PdfExport {

    private static final int PAGE_W = 595, PAGE_H = 842;          // A4 pt
    private static final int MARGIN = 42;
    private static final int LINE_H = 14;
    private static final int BODY = 11;
    private static final float SMALL = 9.5f;
    private static final int INK = 0xFF1F2230, SUB = 0xFF6B6E80, MUTE2 = 0xFF9BA0AE;
    private static final int HEAD_BG = 0xFFF2F3F7, LINE2 = 0xFFE2E4EA;

    // 路线表列宽（网页版 8 列）：时间|路线|交通|班次|乘客|座位|价格|备注
    private static final int[] SEG_W = {56, 118, 42, 60, 80, 58, 47, 50};
    // 记账表列宽（网页版 3 列）：类别|项目|金额
    private static final int[] EXP_W = {90, 330, 91};
    // 交通汇总表列宽：交通 | 段数 | 在途时长 | 占比
    private static final int[] SUM_W = {150, 50, 150, 120};

    private PdfExport() {}

    private static String wxEmoji(String cond) {
        switch (cond) {
            case "晴": return "☀️";
            case "多云": return "🌤️";
            case "阴": return "☁️";
            case "雨": return "🌧️";
            case "雷阵雨": return "⛈️";
            case "雪": return "❄️";
            case "雾": return "🌫️";
            case "风": return "🌬️";
            default: return "🌤️";
        }
    }

    /** PDF 内容预览（导出前展示）：与 build() 同一数据源，不省略任何文字，逐章节生成文本行 */
    static List<String> preview(JSONObject trip) {
        List<String> ls = new ArrayList<>();
        List<String> days = Util.dayKeys(trip);

        // 封面
        ls.add("[封面] " + trip.optString("emoji", "🗺️") + " " + trip.optString("name", "我的行程"));
        String travelers = join(trip.optJSONArray("travelers"));
        ls.add("  日期 " + trip.optString("startDate") + " — " + trip.optString("endDate")
                + " · 共 " + days.size() + " 天 · 出行人 " + (travelers.isEmpty() ? "—" : travelers));
        ls.add("");

        // 目录
        ls.add("[目录]");
        for (int i = 0; i < days.size(); i++)
            ls.add("  📅 Day " + (i + 1) + "  " + Util.displayDate(days.get(i)));
        ls.add("");

        // 每日章节
        for (int i = 0; i < days.size(); i++) {
            String d = days.get(i);
            JSONObject day = trip.optJSONObject("days").optJSONObject(d);
            if (day == null) continue;
            StringBuilder title = new StringBuilder("Day " + (i + 1) + "  " + Util.displayDate(d));
            JSONObject wx = day.optJSONObject("weather");
            if (wx != null && !wx.optString("cond").isEmpty()) {
                title.append("  ").append(wxEmoji(wx.optString("cond"))).append(" ").append(wx.optString("cond"));
                boolean hasLow = !wx.isNull("low"), hasHigh = !wx.isNull("high");
                if (hasLow || hasHigh) {
                    if (hasLow) title.append("  ").append(wx.optInt("low"));
                    title.append("°~");
                    if (hasHigh) title.append(wx.optInt("high"));
                    title.append("°");
                }
            }
            ls.add(title.toString());
            String notes = day.optString("notes");
            if (!notes.isEmpty()) ls.add("  📝 备注 " + notes);
            JSONArray rems = day.optJSONArray("reminders");
            if (rems != null) for (int r = 0; r < rems.length(); r++) {
                JSONObject rem = rems.optJSONObject(r);
                if (rem != null && !rem.optString("title").isEmpty())
                    ls.add("  ⏰ 提醒 " + rem.optString("date", "") + " " + rem.optString("time")
                            + " " + rem.optString("title"));
            }
            JSONObject lodge = day.optJSONObject("lodging");
            if (lodge != null && !lodge.optString("name").isEmpty()) {
                ls.add("  🏨 住宿 " + (lodge.optString("type").isEmpty() ? "" : lodge.optString("type") + " · ")
                        + lodge.optString("name")
                        + (lodge.optString("location").isEmpty() ? "" : " · " + lodge.optString("location"))
                        + (lodge.optDouble("pricePerNight", 0) > 0
                        ? "  ¥" + Util.fmtMoney(lodge.optDouble("pricePerNight", 0)) + "/晚" : ""));
                if (!lodge.optString("notes").isEmpty())
                    ls.add("     💬 " + lodge.optString("notes"));
            }
            JSONArray segs = day.optJSONArray("segments");
            if (segs != null && segs.length() > 0) {
                for (int s = 0; s < segs.length(); s++) {
                    JSONObject sg = segs.optJSONObject(s);
                    if (sg == null) continue;
                    StringBuilder segL = new StringBuilder("  ");
                    String dep = sg.optString("departTime", ""), arr = sg.optString("arriveTime", "");
                    if (!dep.isEmpty() || !arr.isEmpty()) segL.append(dep).append("-").append(arr).append("  ");
                    segL.append(sg.optString("from")).append(" → ").append(sg.optString("to"));
                    if (!sg.optString("transport").isEmpty())
                        segL.append("  ").append(Util.transEmoji(sg.optString("transport")))
                                .append(" ").append(sg.optString("transport"));
                    if (!sg.optString("vehicleNo").isEmpty()) segL.append("-").append(sg.optString("vehicleNo"));
                    if (sg.optDouble("price", 0) > 0) segL.append("  ¥").append(Util.fmtMoney(sg.optDouble("price", 0)));
                    ls.add(segL.toString());
                    if (!sg.optString("notes").isEmpty()) ls.add("     💬 " + sg.optString("notes"));
                }
            } else {
                ls.add("  （当日暂无路线）");
            }
            double dayExp = dayExpTotal(day);
            if (dayExp > 0) ls.add("  当日花费 ¥" + Util.fmtMoney(dayExp));
            ls.add("");
        }

        // 行李清单
        int lug = luggageCount(trip);
        if (lug > 0) {
            ls.add("[🧳 行李清单]");
            JSONArray bags = trip.optJSONArray("luggageBags");
            if (bags != null) for (int b = 0; b < bags.length(); b++) {
                JSONObject bag = bags.optJSONObject(b);
                if (bag == null) continue;
                ls.add("  " + bag.optString("icon", "🧺") + " " + bag.optString("name"));
                JSONArray pk = trip.optJSONArray("packing");
                if (pk != null) for (int p = 0; p < pk.length(); p++) {
                    JSONObject item = pk.optJSONObject(p);
                    if (item != null && bag.optString("id").equals(Util.bagOf(item))) {
                        ls.add("     " + item.optString("name")
                                + (item.optString("notes").isEmpty() ? "" : " 💬 " + item.optString("notes")));
                    }
                }
                JSONArray free = new JSONArray();
                if (pk != null) for (int p = 0; p < pk.length(); p++) {
                    JSONObject item = pk.optJSONObject(p);
                    if (item != null && Util.bagOf(item).isEmpty() && !item.optBoolean("draft", false))
                        free.put(item.optString("name"));
                }
                if (b == bags.length() - 1 && free.length() > 0)
                    ls.add("  📥 待收纳 " + join(free));
            }
            ls.add("");
        }

        // 行程总结
        ls.add("[📊 行程总结]");
        double spent = Util.totalSpent(trip);
        double budget = trip.optDouble("budget", 0);
        ls.add("  💰 总花费 ¥" + Util.fmtMoney(spent)
                + (budget > 0 ? " ／ 总预算 ¥" + Util.fmtMoney(budget)
                + " ／ 结余 ¥" + Util.fmtMoney(budget - spent) : ""));
        ls.add("");
        return ls;
    }

    /** 某天合计（路线价格 + 住宿 + 手动记账，与 build 一致） */
    private static double dayExpTotal(JSONObject day) {
        double t = 0;
        JSONArray segs = day.optJSONArray("segments");
        if (segs != null) for (int i = 0; i < segs.length(); i++)
            t += segs.optJSONObject(i) != null ? segs.optJSONObject(i).optDouble("price", 0) : 0;
        JSONObject lg = day.optJSONObject("lodging");
        if (lg != null && !lg.optString("name").isEmpty()) t += lg.optDouble("pricePerNight", 0);
        JSONArray exps = day.optJSONArray("expenses");
        if (exps != null) for (int i = 0; i < exps.length(); i++)
            t += exps.optJSONObject(i) != null ? exps.optJSONObject(i).optDouble("amount", 0) : 0;
        return t;
    }

    static PdfDocument build(JSONObject trip) {
        PdfDocument doc = new PdfDocument();
        PageCtx ctx = new PageCtx(doc, trip);

        // ---- 封面（网页 doc-cover：emoji + 名称 + 日期区间·共N天 + 出行人） ----
        int accent = ctx.accent;
        List<String> days = Util.dayKeys(trip);
        ctx.title(trip.optString("emoji", "🗺️") + " " + trip.optString("name", "我的行程"), 20, accent, 6);
        ctx.text("🗓️ " + trip.optString("startDate") + " — " + trip.optString("endDate")
                + " · 共 " + days.size() + " 天", BODY, SUB);
        String travelers = join(trip.optJSONArray("travelers"));
        ctx.text("👥 " + (travelers.isEmpty() ? "一个人说走就走" : travelers), BODY, SUB);
        ctx.gap(6);
        ctx.rule(accent);
        ctx.gap(8);

        // ---- 目录（网页 doc-toc：章节名列表） ----
        ctx.sectionLabel("📑 目录", INK);
        ctx.text("📊 行程总结", BODY, SUB);
        if (luggageCount(trip) > 0) ctx.text("🧳 行李清单", BODY, SUB);
        for (String d : days) ctx.text("📅 Day " + dayNo(days, d) + "  " + Util.displayDate(d), BODY, SUB);
        ctx.gap(4);
        ctx.rule(0xFFEEF0F5);
        ctx.gap(8);

        // ---- 1) 行程总结（网页封面后即总览） ----
        int lodgeNights = 0;
        for (String d : days) {
            JSONObject day = trip.optJSONObject("days").optJSONObject(d);
            JSONObject lodge = day != null ? day.optJSONObject("lodging") : null;
            if (lodge != null && !lodge.optString("name").isEmpty()) lodgeNights++;
        }
        ctx.title("▍📊 行程总结", 14, accent, 6);
        ctx.text("住宿 " + lodgeNights + " 晚  ·  路线共 " + countSegs(trip) + " 段", BODY, SUB);
        double spentAll = Util.totalSpent(trip);
        double budget = trip.optDouble("budget", 0);
        ctx.text("预算使用：" + budgetText(spentAll, budget), BODY, SUB);
        ctx.gap(4);

        ctx.sectionLabel("交通汇总", accent);
        Map<String, int[]> stats = transportStats(trip);
        long totalMin = 0;
        for (int[] v : stats.values()) totalMin += v[1];
        long tripMin = (long) days.size() * 1440;
        List<String[]> sumRows = new ArrayList<>();
        for (Map.Entry<String, int[]> en : stats.entrySet()) {
            int[] v = en.getValue();
            String pct = totalMin > 0 ? String.format("%.0f%%", v[1] * 100.0 / totalMin) : "—";
            sumRows.add(new String[]{Util.transEmoji(en.getKey()) + " " + en.getKey(),
                    String.valueOf(v[0]), v[1] > 0 ? fmtMin(v[1]) : "—", pct});
        }
        if (tripMin - totalMin > 0)
            sumRows.add(new String[]{"其他（未标注交通）", "—", fmtMin(tripMin - totalMin),
                    String.format("%.0f%%", (tripMin - totalMin) * 100.0 / tripMin)});
        if (!sumRows.isEmpty()) {
            ctx.drawTable(SUM_W, new String[]{"交通方式", "段数", "在途时长", "占比"}, sumRows,
                    new boolean[]{false, false, false, true});
        }
        ctx.text("在途总时长：" + fmtMin(totalMin) + " ／ 全程 " + fmtMin(tripMin), BODY, accent);
        ctx.gap(4);

        ctx.sectionLabel("消费构成", accent);
        LinkedHashMap<String, Double> cats = categorySum(trip);
        for (Map.Entry<String, Double> en : cats.entrySet()) {
            ctx.text(Util.catEmoji(en.getKey()) + " " + en.getKey() + "：¥" + Util.fmtMoney(en.getValue()), BODY, SUB);
        }
        ctx.gap(6);

        // ---- 2) 行李清单章节（网页 doc-lug：计划携带物品 + 行李模拟分组） ----
        if (luggageCount(trip) > 0) {
            ctx.newPageIfNeeded(80);
            ctx.title("▍🧳 行李清单", 14, accent, 6);
            ctx.text("🎒 计划携带物品", 12, INK);
            JSONArray packing = trip.optJSONArray("packing");
            if (packing != null) for (int i = 0; i < packing.length(); i++) {
                JSONObject it = packing.optJSONObject(i);
                if (it == null) continue;
                String imp = it.optString("importance", "待定");
                String marks = "";
                if ("必需品".equals(imp)) marks += "  [必]";
                String status = "已携带".equals(it.optString("status")) ? "  ✓已携带" : "  未携带";
                String bagTxt = "";
                if (!Util.bagOf(it).isEmpty()) {
                    JSONArray bagsB = trip.optJSONArray("luggageBags");
                    String bid = Util.bagOf(it);
                    if (bagsB != null) for (int k = 0; k < bagsB.length(); k++) {
                        JSONObject b = bagsB.optJSONObject(k);
                        if (b != null && bid.equals(b.optString("id"))) {
                            bagTxt = "  🧺" + b.optString("icon", "🧳") + b.optString("name", bid);
                            break;
                        }
                    }
                }
                ctx.wrapText(packCatIcon(it.optString("category", "其他")) + " " + it.optString("name")
                        + marks + status + bagTxt, BODY, INK, false);
            }
            ctx.gap(3);
            ctx.text("🧳 行李携带可行性模拟", 12, INK);
            JSONArray bags = trip.optJSONArray("luggageBags");
            boolean anyIn = false;
            if (bags != null) for (int i = 0; i < bags.length(); i++) {
                JSONObject b = bags.optJSONObject(i);
                if (b == null) continue;
                List<String> inB = new ArrayList<>();
                if (packing != null) for (int j = 0; j < packing.length(); j++) {
                    JSONObject it = packing.optJSONObject(j);
                    if (it != null && b.optString("id").equals(Util.bagOf(it))) inB.add(it.optString("name"));
                }
                if (!inB.isEmpty()) {
                    anyIn = true;
                    ctx.text(b.optString("icon", "🧳") + " " + b.optString("name", "行李") + "（" + inB.size() + " 件）",
                            BODY, INK);
                    for (String n : inB) ctx.wrapText("   · " + n, BODY, SUB, false);
                }
            }
            List<String> unplaced = new ArrayList<>();
            if (packing != null) for (int i = 0; i < packing.length(); i++) {
                JSONObject it = packing.optJSONObject(i);
                if (it != null && Util.bagOf(it).isEmpty()) unplaced.add(it.optString("name"));
            }
            if (!unplaced.isEmpty()) {
                anyIn = true;
                ctx.text("📦 未放入行李箱（" + unplaced.size() + " 件）", BODY, INK);
                for (String n : unplaced) ctx.wrapText("   · " + n, BODY, SUB, false);
            }
            if (!anyIn) ctx.text("还没有把物品放入行李箱", BODY, MUTE2);
            ctx.gap(6);
        }

        // ---- 3) 每日章节（网页 doc-day：Day徽章 → 备注 → 提醒表 → 路线表 → 住宿卡 → 记账表 → 每日总结条） ----
        for (String d : days) {
            JSONObject day = trip.optJSONObject("days").optJSONObject(d);
            if (day == null) continue;
            ctx.newPageIfNeeded(70);
            // Day 徽章 + 日期标题（网页 doc-day-title；天气并入标题行，不遗漏）
            JSONObject wx = day.optJSONObject("weather");
            StringBuilder dayTitle = new StringBuilder("Day " + dayNo(days, d) + "  " + Util.displayDate(d)
                    + "  ·  " + d);
            if (wx != null && !wx.optString("cond").isEmpty()) {
                dayTitle.append("   ").append(wxEmoji(wx.optString("cond"))).append(" ").append(wx.optString("cond"));
                boolean hasLow = !wx.isNull("low"), hasHigh = !wx.isNull("high");
                if (hasLow || hasHigh) {
                    if (hasLow) dayTitle.append("  ").append(wx.optInt("low"));
                    dayTitle.append("°~");
                    if (hasHigh) dayTitle.append(wx.optInt("high"));
                    dayTitle.append("°");
                }
            }
            ctx.title(dayTitle.toString(), 14, accent, 8);

            String notes = day.optString("notes");
            if (!notes.isEmpty()) ctx.wrapText("📝 " + notes, BODY, SUB, false);

            // 提醒表（网页 rem 表：提醒时间|提醒内容）
            JSONArray rems = day.optJSONArray("reminders");
            if (rems != null && rems.length() > 0) {
                List<String[]> remRows = new ArrayList<>();
                for (int i = 0; i < rems.length(); i++) {
                    JSONObject r = rems.optJSONObject(i);
                    if (r == null || r.optString("title").isEmpty()) continue;
                    String when = r.optString("date", "") + (r.optString("time").isEmpty() ? "" : " " + r.optString("time"));
                    remRows.add(new String[]{when.trim().isEmpty() ? "—" : when.trim(), r.optString("title")});
                }
                if (!remRows.isEmpty()) {
                    ctx.sectionLabel("⏰ 提醒", accent);
                    ctx.drawTable(new int[]{160, 351}, new String[]{"提醒时间", "提醒内容"}, remRows,
                            new boolean[]{false, false});
                }
            }

            // 路线表（8 列）：标题与详情页一致「🗺️ 行程路线」
            JSONArray segs = day.optJSONArray("segments");
            if (segs != null && segs.length() > 0) {
                ctx.sectionLabel("🗺️ 行程路线（" + segs.length() + " 段）", accent);
                List<String[]> rows = new ArrayList<>();
                for (int i = 0; i < segs.length(); i++) {
                    JSONObject sg = segs.optJSONObject(i);
                    if (sg == null) continue;
                    int cross = crossDays(sg);
                    String time = sg.optString("departTime", "") + "-" + sg.optString("arriveTime", "")
                            + (cross > 0 ? "(+" + cross + ")" : "");
                    String path = sg.optString("from") + " → " + sg.optString("to");
                    String trans = Util.transEmoji(sg.optString("transport")) + " " + sg.optString("transport");
                    String vh = sg.optString("vehicleNo").isEmpty() ? "—" : sg.optString("vehicleNo");
                    String paxNames = "", paxSeats = "";
                    for (String[] px : Util.normPax(sg)) {
                        if (!paxNames.isEmpty()) paxNames += "、";
                        // 乘客-座位 一一对应（如：我-2车8C），与详情页一致
                        paxNames += px[1].isEmpty() ? px[0] : px[0] + "-" + px[1];
                        if (!px[1].isEmpty()) { if (!paxSeats.isEmpty()) paxSeats += "、"; paxSeats += px[1]; }
                    }
                    String price = sg.optDouble("price", 0) > 0 ? Util.fmtMoney(sg.optDouble("price", 0)) : "—";
                    String note = sg.optString("notes").isEmpty() ? "—" : sg.optString("notes");
                    rows.add(new String[]{time, path, trans,
                            vh, paxNames.isEmpty() ? "—" : paxNames,
                            paxSeats.isEmpty() ? "—" : paxSeats, price, note});
                }
                ctx.drawTable(SEG_W, new String[]{"时间", "路线", "交通", "班次", "乘客", "座位", "价格", "备注"},
                        rows, new boolean[]{false, false, false, false, false, false, true, false});
            } else {
                ctx.text("当日暂无路线安排", BODY, MUTE2);
            }

            // 住宿块（网页 doc-lodge：类型 · 名称 · 位置 · 价格/晚 + 💬备注），带标题
            JSONObject lodge = day.optJSONObject("lodging");
            if (lodge != null && !lodge.optString("name").isEmpty()) {
                ctx.sectionLabel("🏨 住宿", accent);
                String ltype = lodge.optString("type", "酒店");
                StringBuilder lb = new StringBuilder(lodgeEmoji(ltype) + " " + ltype + " · " + lodge.optString("name"));
                if (!lodge.optString("location").isEmpty()) lb.append(" · ").append(lodge.optString("location"));
                lb.append(lodge.optDouble("pricePerNight", 0) > 0
                        ? "  ｜  ¥" + Util.fmtMoney(lodge.optDouble("pricePerNight", 0)) + " / 晚" : "  ｜  价格未填");
                ctx.wrapText(lb.toString(), BODY, INK, false);
                if (!lodge.optString("notes").isEmpty())
                    ctx.wrapText("💬 " + lodge.optString("notes"), SMALL, MUTE2, false);
            }

            // 记账表（3 列：自动计入 + 手动，网页 expRows 顺序：交通聚合→住宿→手动）
            {
                List<String[]> rows = new ArrayList<>();
                java.util.LinkedHashMap<String, Double> transSum = new java.util.LinkedHashMap<>();
                if (segs != null) for (int i = 0; i < segs.length(); i++) {
                    JSONObject sg = segs.optJSONObject(i);
                    if (sg == null) continue;
                    double pr = sg.optDouble("price", 0);
                    if (pr > 0) {
                        String tr = sg.optString("transport", "交通");
                        transSum.put(tr, transSum.getOrDefault(tr, 0.0) + pr);
                    }
                }
                for (Map.Entry<String, Double> en : transSum.entrySet()) {
                    rows.add(new String[]{Util.transEmoji(en.getKey()) + " 交通", en.getKey(),
                            Util.fmtMoney(en.getValue())});
                }
                JSONObject lodge2 = day.optJSONObject("lodging");
                if (lodge2 != null && (!lodge2.optString("name").isEmpty() || !lodge2.optString("location").isEmpty())
                        && lodge2.optDouble("pricePerNight", 0) > 0) {
                    rows.add(new String[]{Util.catEmoji("住宿") + " 住宿", lodge2.optString("type", "酒店"),
                            Util.fmtMoney(lodge2.optDouble("pricePerNight", 0))});
                }
                JSONArray exps = day.optJSONArray("expenses");
                if (exps != null) for (int i = 0; i < exps.length(); i++) {
                    JSONObject e = exps.optJSONObject(i);
                    if (e == null) continue;
                    rows.add(new String[]{Util.catEmoji(e.optString("category", "其他")) + " " + e.optString("category", "其他"),
                            e.optString("item", "—"),
                            Util.fmtMoney(e.optDouble("amount", 0))});
                }
                if (!rows.isEmpty()) {
                    int manualN = exps != null ? exps.length() : 0;
                    ctx.sectionLabel("🧾 记账" + (manualN > 0 ? "（" + manualN + " 笔）" : ""), accent);
                    ctx.drawTable(EXP_W, new String[]{"类别", "项目", "金额"}, rows,
                            new boolean[]{false, false, true});
                }
            }

            // 每日总结条（网页 doc-day-sum：花费/时长/住宿/出行人）
            double daySpent = daySpend(day);
            long dm = dayMin(day);
            String dayPax = join(day.optJSONArray("travelers"));
            if (dayPax.isEmpty()) dayPax = travelers;
            List<String> sumParts = new ArrayList<>();
            sumParts.add("花费 ¥" + Util.fmtMoney(daySpent));
            if (dm > 0) sumParts.add("在途 " + fmtMin(dm));
            JSONObject lodgeDay = day.optJSONObject("lodging");
            sumParts.add((lodgeDay != null && !lodgeDay.optString("name").isEmpty())
                    ? "住宿：有" : "无住宿");
            if (!dayPax.isEmpty()) sumParts.add("出行人 " + dayPax);
            ctx.wrapText("每日总结：" + TextUtils.join("  ·  ", sumParts), SMALL, SUB, false);

            ctx.gap(2);
            ctx.rule(0xFFEEF0F5);
            ctx.gap(6);
        }

        // ---- 4) 总花费条 + footer（网页 doc-total / doc-footer） ----
        ctx.newPageIfNeeded(50);
        ctx.rule(accent);
        StringBuilder tb = new StringBuilder("行程总花费 " + Util.fmtMoney(spentAll));
        double remain = budget - spentAll;
        if (budget > 0) tb.append("  ｜  预算 ").append(Util.fmtMoney(budget))
                .append("  ·  结余 ").append(Util.fmtMoney(remain));
        tb.append("  ｜  行程 ").append(days.size()).append(" 天 · 路线 ").append(countSegs(trip))
                .append(" 段 · 住宿 ").append(lodgeNights).append(" 晚");
        ctx.wrapText(tb.toString(), BODY, INK, false);
        ctx.text("🧳 我的行程（个人旅行规划）", SMALL, MUTE2);

        ctx.finishPage();
        return doc;
    }

    /** 网页样式辅助 */
    private static String lodgeEmoji(String type) {
        if ("民宿".equals(type)) return "🏡";
        if ("青旅".equals(type)) return "🛏️";
        if ("其他".equals(type)) return "🏠";
        return "🏨";
    }

    private static int dayNo(List<String> days, String d) {
        return days.indexOf(d) + 1;
    }

    private static String packCatIcon(String c) {
        if ("证件".equals(c)) return "🪪";
        if ("电子设备".equals(c)) return "🔌";
        if ("衣物".equals(c)) return "👕";
        if ("洗护用品".equals(c)) return "🧴";
        if ("药品".equals(c)) return "💊";
        if ("食品".equals(c)) return "🍫";
        return "📦";
    }

    static int luggageCount(JSONObject trip) {
        JSONArray packing = trip.optJSONArray("packing");
        return packing == null ? 0 : packing.length();
    }

    private static double daySpend(JSONObject day) {
        double sum = 0;
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
        if (lodge != null && !lodge.optString("name").isEmpty()) sum += lodge.optDouble("pricePerNight", 0);
        return sum;
    }

    // ============ 统计（与网页版口径一致） ============

    private static long countSegs(JSONObject trip) {
        long n = 0;
        for (String d : Util.dayKeys(trip)) {
            JSONObject day = trip.optJSONObject("days").optJSONObject(d);
            JSONArray segs = day != null ? day.optJSONArray("segments") : null;
            if (segs != null) n += segs.length();
        }
        return n;
    }

    private static long dayMin(JSONObject day) {
        long m = 0;
        JSONArray segs = day.optJSONArray("segments");
        if (segs == null) return 0;
        for (int i = 0; i < segs.length(); i++) {
            JSONObject s = segs.optJSONObject(i);
            if (s != null) m += segMinutes(s);
        }
        return m;
    }

    private static Map<String, int[]> transportStats(JSONObject trip) {
        Map<String, int[]> m = new HashMap<>();
        for (String d : Util.dayKeys(trip)) {
            JSONObject day = trip.optJSONObject("days").optJSONObject(d);
            JSONArray segs = day != null ? day.optJSONArray("segments") : null;
            if (segs == null) continue;
            for (int i = 0; i < segs.length(); i++) {
                JSONObject s = segs.optJSONObject(i);
                if (s == null) continue;
                String t = s.optString("transport");
                if (t.isEmpty()) continue;
                int[] v = m.get(t);
                if (v == null) { v = new int[]{0, 0}; m.put(t, v); }
                v[0]++;
                v[1] += segMinutes(s);
            }
        }
        return m;
    }

    private static int segMinutes(JSONObject s) {
        String dep = s.optString("departTime"), arr = s.optString("arriveTime");
        if (dep.length() < 5 || arr.length() < 5) return 0;
        try {
            int a = Integer.parseInt(dep.substring(0, 2)) * 60 + Integer.parseInt(dep.substring(3, 5));
            int b = Integer.parseInt(arr.substring(0, 2)) * 60 + Integer.parseInt(arr.substring(3, 5));
            return b - a + crossDays(s) * 1440;
        } catch (Exception e) { return 0; }
    }

    private static LinkedHashMap<String, Double> categorySum(JSONObject trip) {
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
        Iterator<Map.Entry<String, Double>> it = m.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue() <= 0) it.remove();
        return m;
    }

    private static String budgetText(double spent, double budget) {
        if (budget <= 0) return "已花费 ¥" + Util.fmtMoney(spent);
        double pct = spent / budget * 100;
        return "已花费 ¥" + Util.fmtMoney(spent) + " ／ 预算 ¥" + Util.fmtMoney(budget)
                + "（" + String.format("%.0f", pct) + "%）" + (spent > budget ? "，⚠️ 超支" : "");
    }

    private static int crossDays(JSONObject s) {
        int cd = s.optInt("crossDays", 0);
        if (cd > 1) return cd;
        String dep = s.optString("departTime"), arr = s.optString("arriveTime");
        if (dep.length() == 5 && arr.length() == 5 && arr.compareTo(dep) < 0) return cd > 0 ? cd : 1;
        return 0;
    }

    private static String fmtMin(long min) {
        long h = min / 60, m = min % 60;
        return h > 0 ? (m > 0 ? h + " 小时 " + m + " 分" : h + " 小时") : m + " 分钟";
    }

    private static String join(JSONArray arr) {
        if (arr == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) { if (i > 0) sb.append("、"); sb.append(arr.optString(i)); }
        return sb.toString();
    }

    // ============ 分页画布 + 表格绘制 ============

    private static final class PageCtx {
        final PdfDocument doc;
        final int accent;
        PdfDocument.Page page;
        Canvas canvas;
        int y;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PageCtx(PdfDocument doc, JSONObject trip) {
            this.doc = doc;
            this.accent = Util.parseColor(trip.optString("color"), Util.ACCENT);
            newPage();
        }

        void newPage() {
            finishPage();
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, doc.getPages().size() + 1).create();
            page = doc.startPage(info);
            canvas = page.getCanvas();
            y = MARGIN;
        }

        void newPageIfNeeded(int need) {
            if (y + need > PAGE_H - MARGIN) newPage();
        }

        void finishPage() {
            if (page != null) doc.finishPage(page);
            page = null;
        }

        void title(String s, float size, int color, int topGap) {
            newPageIfNeeded(40);
            y += topGap;
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(size);
            paint.setColor(color);
            canvas.drawText(s, MARGIN, y, paint);
            y += LINE_H + 3;
        }

        void sectionLabel(String s, int color) {
            newPageIfNeeded(30);
            y += 2;
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(12);
            paint.setColor(color);
            canvas.drawText(s, MARGIN, y, paint);
            y += LINE_H;
        }

        void text(String s, float size, int color) {
            wrapText(s, size, color, true);
        }

        void wrapText(String s, float size, int color, boolean isLine) {
            if (s == null || s.isEmpty()) return;
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(size);
            paint.setColor(color);
            float maxW = PAGE_W - MARGIN * 2;
            for (String ln : wrap(s, paint, maxW)) {
                newPageIfNeeded(LINE_H);
                paint.setTypeface(Typeface.DEFAULT);
                paint.setTextSize(size);
                paint.setColor(color);
                canvas.drawText(ln, MARGIN, y, paint);
                y += LINE_H;
            }
        }

        void gap(int dp) { y += dp; }

        void rule(int color) {
            paint.setTypeface(Typeface.DEFAULT);
            paint.setStrokeWidth(1);
            paint.setColor(color);
            float w = PAGE_W - MARGIN * 2;
            canvas.drawLine(MARGIN, y, MARGIN + w, y, paint);
            y += 8;
        }

        /**
         * 标准表格：表头浅灰底加粗；行间分隔线；长文本格内换行；跨页时在新页重画表头；
         * align 数组 true 表示该列右对齐（金额列）。
         */
        void drawTable(int[] ws, String[] header, List<String[]> rows, boolean[] align) {
            if (rows.isEmpty()) return;
            // 统一宽度修正
            int sum = 0;
            for (int w : ws) sum += w;
            float scale = (PAGE_W - MARGIN * 2f) / sum;
            float[] widths = new float[ws.length];
            for (int i = 0; i < ws.length; i++) widths[i] = ws[i] * scale;

            float maxW = PAGE_W - MARGIN * 2f;
            for (int i = 0; i < rows.size(); i++) {
                String[] row = rows.get(i);
                // 计算本行高度
                int rowH = 0;
                for (int c = 0; c < widths.length; c++) {
                    int lines = 1;
                    String cell = row[c] == null ? "" : row[c];
                    if (cell.contains("\n")) {
                        for (String part : cell.split("\n"))
                            lines += wrapNoBr(part, paint, widths[c] - 8).size() - 1;
                    } else {
                        lines = wrapNoBr(cell, paint, widths[c] - 8).size();
                    }
                    rowH = Math.max(rowH, lines);
                }
                rowH = rowH * LINE_H + 8;
                if (y + rowH > PAGE_H - MARGIN) {
                    newPage();                       // 翻页
                    drawHead(ws, widths, header, align); // 表头重画
                } else if (i == 0) {
                    drawHead(ws, widths, header, align);
                }
                drawRow(widths, row, align, rowH);
                y += rowH;
            }
            y += 6;
        }

        private void drawHead(int[] ws, float[] widths, String[] header, boolean[] align) {
            // 表头底色
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(HEAD_BG);
            canvas.drawRect(MARGIN, y, MARGIN + (PAGE_W - MARGIN * 2f), y + LINE_H + 8, paint);
            paint.setStyle(Paint.Style.FILL);
            float x = MARGIN;
            for (int c = 0; c < header.length; c++) {
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextSize(SMALL);
                paint.setColor(INK);
                float tx = align[c] ? x + widths[c] - 8 - paint.measureText(header[c]) : x + 4;
                canvas.drawText(header[c], tx, y + LINE_H - 2, paint);
                x += widths[c];
            }
            y += LINE_H + 8;
        }

        private void drawRow(float[] widths, String[] row, boolean[] align, int rowH) {
            float x = MARGIN;
            for (int c = 0; c < widths.length; c++) {
                String cell = row[c] == null ? "" : row[c];
                paint.setTypeface(Typeface.DEFAULT);
                paint.setTextSize(SMALL);
                paint.setColor(INK);
                float ty = y + LINE_H - 2;
                for (String part : cell.split("\n")) {
                    List<String> lines = wrapNoBr(part, paint, widths[c] - 8);
                    for (String ln : lines) {
                        float tx = align[c] ? x + widths[c] - 8 - paint.measureText(ln) : x + 4;
                        canvas.drawText(ln, tx, ty, paint);
                        ty += LINE_H;
                    }
                }
                x += widths[c];
            }
            // 底线
            paint.setStrokeWidth(0.8f);
            paint.setColor(LINE2);
            canvas.drawLine(MARGIN, y + rowH - 0.5f, MARGIN + (PAGE_W - MARGIN * 2f), y + rowH - 0.5f, paint);
        }

        /** 按字符断行（\n 已由调用方拆开） */
        private List<String> wrapNoBr(String s, Paint p, float maxW) {
            List<String> out = new ArrayList<>();
            if (s == null || s.isEmpty()) { out.add(""); return out; }
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (p.measureText(line.toString() + ch) > maxW) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                line.append(ch);
            }
            out.add(line.toString());
            return out;
        }

        private List<String> wrap(String s, Paint p, float maxW) {
            return wrapNoBr(s, p, maxW);
        }
    }
}