package com.zcode.travelapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 添加/编辑路线段（对齐网页版编辑行）：
 * 出发地/到达地/时间/跨天/交通/价格/车次/备注；
 * 「👥 乘客与座位」弹窗（网页版 pax-panel）：大行程出行人 chips 点选 / 自定义添加 /
 * 选中乘客自动生成座位行（可留空）→ passengers 对象数组 [{name,seat}]。
 */
public class EditSegmentActivity extends Activity {

    private LinearLayout box;
    private EditText fromEt, toEt, depEt, arrEt, priceEt, vehicleEt, noteEt;
    private Spinner crossSp, transSp;
    private Button paxBtn;
    private String tripId, date;
    private int segIndex; // -1 = 新建

    private final LinkedHashMap<String, String> selPax = new LinkedHashMap<>(); // 乘客 -> 座位（有序）

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_edit_segment);
        tripId = getIntent().getStringExtra("tripId");
        date = getIntent().getStringExtra("date");
        segIndex = getIntent().getIntExtra("index", -1);
        ((TextView) findViewById(R.id.title_tv)).setText(segIndex < 0 ? "添加路线" : "编辑路线");

        Button cancelBtn = findViewById(R.id.btn_cancel);
        cancelBtn.setVisibility(android.view.View.GONE);   // 取消文字 → 返回箭头
        LineIconButton backBtn = LineIconButton.sized(LineIconButton.back(this), 30);
        backBtn.setOnClickListener(v -> finish());
        LinearLayout topbar = (LinearLayout) cancelBtn.getParent();
        int idx = topbar.indexOfChild(cancelBtn);
        topbar.addView(backBtn, idx);
        findViewById(R.id.btn_save).setOnClickListener(v -> save());

        box = findViewById(R.id.form_box);
        fromEt = field("出发地 *", "如：上海虹桥站");
        fromEt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(20)});
        toEt = field("到达地 *", "如：杭州东站");
        toEt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(20)});

        // 时间行
        LinearLayout times = Util.hBox(this);
        times.setPadding(0, Util.dp(this, 12), 0, 0);
        LinearLayout depWrap = Util.vBox(this);
        depWrap.addView(boldLabel("出发时间"));
        depEt = input("如 09:00");
        depWrap.addView(depEt);
        LinearLayout arrWrap = Util.vBox(this);
        arrWrap.addView(boldLabel("到达时间"));
        arrEt = input("如 10:30");
        arrWrap.addView(arrEt);
        times.addView(depWrap, new LinearLayout.LayoutParams(0, -2, 1));
        times.addView(arrWrap, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(times);

        // 跨天
        LinearLayout crossWrap = Util.vBox(this);
        crossWrap.setPadding(0, Util.dp(this, 12), 0, 0);
        crossWrap.addView(boldLabel("跨天"));
        crossSp = spinner(crossWrap, new String[]{"当天到达", "跨 1 天 (+1)", "跨 2 天 (+2)", "跨 3 天 (+3)", "跨 4 天 (+4)"});
        box.addView(crossWrap);

        // 交通方式
        LinearLayout transWrap = Util.vBox(this);
        transWrap.setPadding(0, Util.dp(this, 12), 0, 0);
        transWrap.addView(boldLabel("交通方式"));
        List<String> transItems = new ArrayList<>();
        for (String[] t : Util.TRANS) transItems.add(t[1] + " " + t[0]);
        transSp = spinner(transWrap, transItems.toArray(new String[0]));
        box.addView(transWrap);

        priceEt = field("价格（元，可不填）", "如 73");
        priceEt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        vehicleEt = field("车次/航班（高铁/飞机）", "如 G1345、MU5101");
        vehicleEt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(30)});

        // 乘客与座位（网页版 pax-panel 入口）
        LinearLayout paxWrap = Util.vBox(this);
        paxWrap.setPadding(0, Util.dp(this, 12), 0, 0);
        paxWrap.addView(boldLabel("乘客与座位"));
        paxBtn = new Button(this);
        paxBtn.setText("👥 选择乘客/填座位（0 人） ›");
        Util.styleSoft(paxBtn, Util.ACCENT, Util.accentSoft(Util.ACCENT, this), this);
        paxBtn.setMinHeight(Util.dp(this, 40));
        paxBtn.setOnClickListener(v -> showPaxPanel());
        paxWrap.addView(paxBtn);
        box.addView(paxWrap);

        noteEt = field("备注（≤80 字）", "转乘/路况等信息，全字显示不省略");
        noteEt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});

        if (segIndex >= 0) load();
    }

    private void load() {
        JSONArray trips = Store.load(this);
        JSONObject trip = EditTripActivity.findTrip(trips, tripId);
        if (trip == null) { finish(); return; }
        JSONObject days = trip.optJSONObject("days");
        JSONObject day = days != null ? days.optJSONObject(date) : null;
        if (day == null) { finish(); return; }
        JSONArray segs = day.optJSONArray("segments");
        if (segs == null || segIndex >= segs.length()) { finish(); return; }
        JSONObject s = segs.optJSONObject(segIndex);
        if (s == null) { finish(); return; }
        fromEt.setText(s.optString("from"));
        toEt.setText(s.optString("to"));
        depEt.setText(s.optString("departTime"));
        arrEt.setText(s.optString("arriveTime"));
        priceEt.setText(s.optDouble("price", 0) > 0 ? Util.fmtMoney(s.optDouble("price", 0)) : "");
        vehicleEt.setText(s.optString("vehicleNo"));
        String trans = s.optString("transport");
        for (int i = 0; i < Util.TRANS.length; i++) {
            if (Util.TRANS[i][0].equals(trans)) transSp.setSelection(i);
        }
        int cd = s.optInt("crossDays", 0);
        if (cd > 4) cd = 4;
        if (cd > 0) crossSp.setSelection(cd);
        selPax.clear();
        for (String[] p : Util.normPax(s)) selPax.put(p[0], p[1]);
        updatePaxBtn();
        noteEt.setText(s.optString("notes"));
    }

    private void updatePaxBtn() {
        paxBtn.setText("👥 选择乘客/填座位（" + selPax.size() + " 人） ›");
    }

    /** 乘客与座位弹窗（对应网页版 pax-panel） */
    private void showPaxPanel() {
        JSONArray trips = Store.load(this);
        JSONObject trip = EditTripActivity.findTrip(trips, tripId);
        List<String> travelers = new ArrayList<>();
        if (trip != null) {
            JSONArray tr = trip.optJSONArray("travelers");
            if (tr != null) for (int i = 0; i < tr.length(); i++) travelers.add(tr.optString(i));
        }

        LinearLayout panel = Util.vBox(this);
        int pad = Util.dp(this, 18);
        panel.setPadding(pad, 0, pad, 0);
        TextView hint = Util.text(this, "点击名字选中/取消；选中的乘客可填座位号（可留空）", 12, Util.MUTE);
        panel.addView(hint);

        // 出行人 chips
        FlowLayout chips = new FlowLayout(this);
        chips.setGaps(6, 6, this);
        chips.setPadding(0, Util.dp(this, 6), 0, 0);
        final TextView noPax = Util.text(this, "出行计划还没有出行人，可直接在下方输入乘客名字", 12, Util.MUTE);
        if (travelers.isEmpty()) {
            chips.addView(noPax);
        }
        for (String n : travelers) {
            final TextView c = chipFor(n, selPax.containsKey(n));
            c.setOnClickListener(v -> {
                if (selPax.containsKey(n)) { selPax.remove(n); styleChip(c, false); }
                else { selPax.put(n, ""); styleChip(c, true); }
                renderSeats(panel);
            });
            chips.addView(c);
        }
        panel.addView(chips);

        // 自定义乘客
        LinearLayout custom = Util.hBox(this);
        custom.setPadding(0, Util.dp(this, 6), 0, 0);
        EditText customEt = new EditText(this);
        EditTripActivity.Style.input(customEt);
        customEt.setHint("乘客姓名（多个用、或,分隔）");
        customEt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(30)});
        custom.addView(customEt, new LinearLayout.LayoutParams(0, -2, 1));
        Button addBtn = new Button(this);
        addBtn.setText("＋ 添加");
        Util.styleSoft(addBtn, Util.ACCENT, Util.accentSoft(Util.ACCENT, this), this);
        LinearLayout.LayoutParams abp = new LinearLayout.LayoutParams(-2, -2);
        abp.leftMargin = Util.dp(this, 8);
        addBtn.setLayoutParams(abp);
        custom.addView(addBtn);
        panel.addView(custom);
        addBtn.setOnClickListener(v -> {
            // 支持一次填多人：「a、b」「a,b」「a，b」都拆成两个乘客
            for (final String n : customEt.getText().toString().split("[、,，]")) {
                final String nm = n.trim();
                if (nm.isEmpty() || selPax.containsKey(nm)) continue;
                selPax.put(nm, "");
                // 追加一个选中 chip
                final TextView c = chipFor(nm, true);
                c.setOnClickListener(v2 -> {
                    if (selPax.containsKey(nm)) { selPax.remove(nm); styleChip(c, false); }
                    else { selPax.put(nm, ""); styleChip(c, true); }
                    renderSeats(panel);
                });
                chips.addView(c);
            }
            customEt.setText("");
            renderSeats(panel);
        });

        // 座位区（选中乘客 → 自动生成座位行，可留空）
        renderSeats(panel);

        new AlertDialog.Builder(this)
                .setTitle("👥 乘客与座位")
                .setView(panel)
                .setPositiveButton("确定", (d, w) -> updatePaxBtn())
                .setNegativeButton("取消", null)
                .show();
    }

    /** 座位区：每个选中乘客一行（👤名字 + 座位输入 + ✕移除），对应网页版 pax-seat-row */
    private void renderSeats(LinearLayout panel) {
        LinearLayout seatsBox = panel.findViewWithTag("seatsBox");
        if (seatsBox == null) {
            seatsBox = Util.vBox(this);
            seatsBox.setTag("seatsBox");
            LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(-1, -2);
            sp2.topMargin = Util.dp(this, 6);
            seatsBox.setLayoutParams(sp2);
            panel.addView(seatsBox);
        } else {
            seatsBox.removeAllViews();
        }
        if (selPax.isEmpty()) {
            TextView empty = Util.text(this, "尚未选择乘客", 12.5f, Util.MUTE);
            seatsBox.addView(empty);
            return;
        }
        for (final Map.Entry<String, String> en : selPax.entrySet()) {
            LinearLayout row = Util.hBox(this);
            row.setBackground(Util.chipBg(0xFFFAFAFC, 10, this));
            row.setPadding(Util.dp(this, 10), Util.dp(this, 6), Util.dp(this, 8), Util.dp(this, 6));
            TextView name = Util.text(this, "👤 " + en.getKey(), 13, Util.INK);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(Util.dp(this, 96), -2);
            name.setLayoutParams(np);
            row.addView(name);
            EditText seatEt = new EditText(this);
            seatEt.setTextSize(13);
            seatEt.setTextColor(Util.INK);
            seatEt.setHint("座位号，如 3车5D");
            seatEt.setText(en.getValue());
            seatEt.setSingleLine(true);
            seatEt.setBackground(Util.chipBg(0xFFFFFFFF, 8, this));
            seatEt.setPadding(Util.dp(this, 8), Util.dp(this, 4), Util.dp(this, 8), Util.dp(this, 4));
            seatEt.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
                @Override public void onTextChanged(CharSequence c, int a, int b, int d) {}
                @Override public void afterTextChanged(Editable c) { en.setValue(c.toString()); }
            });
            row.addView(seatEt, new LinearLayout.LayoutParams(0, -2, 1));
            final String fName = en.getKey();
            Button rm = Util.delBtn(this);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
            rp.leftMargin = Util.dp(this, 4);
            rm.setLayoutParams(rp);
            rm.setOnClickListener(v -> {
                selPax.remove(fName);
                renderSeats(panel);
            });
            row.addView(rm);
            seatsBox.addView(row);
            LinearLayout.LayoutParams rr = new LinearLayout.LayoutParams(-1, -2);
            rr.topMargin = Util.dp(this, 5);
            row.setLayoutParams(rr);
        }
    }

    private TextView chipFor(String name, boolean sel) {
        TextView c = new TextView(this);
        c.setText(name);
        c.setTextSize(13);
        c.setTypeface(Typeface.DEFAULT_BOLD);
        styleChip(c, sel);
        c.setPadding(Util.dp(this, 12), Util.dp(this, 6), Util.dp(this, 12), Util.dp(this, 6));
        return c;
    }

    private void styleChip(TextView c, boolean sel) {
        if (sel) {
            c.setTextColor(0xFFFFFFFF);
            c.setBackground(Util.coverGradient(Util.ACCENT, 14, this));
        } else {
            c.setTextColor(Util.INK);
            c.setBackground(Util.cardStyle(this, 14));
        }
    }

    private void save() {
        String from = fromEt.getText().toString().trim();
        String to = toEt.getText().toString().trim();
        if (from.isEmpty() || to.isEmpty()) {
            Toast.makeText(this, "请填写出发地和到达地", Toast.LENGTH_SHORT).show();
            return;
        }
        String dep = Util.normalizeTime(depEt.getText().toString());
        String arr = Util.normalizeTime(arrEt.getText().toString());
        if (dep == null || arr == null) {
            Toast.makeText(this, "时间格式应为 09:00 这样的 HH:mm", Toast.LENGTH_SHORT).show();
            return;
        }
        int cross = crossSp.getSelectedItemPosition();
        if (cross == 0 && arr.compareTo(dep) < 0) cross = 1;

        double price = 0;
        try { price = Double.parseDouble(priceEt.getText().toString().trim()); } catch (Exception e) {}

        try {
            JSONObject s = new JSONObject();
            s.put("from", from);
            s.put("to", to);
            s.put("time", dep + "-" + arr);
            s.put("transport", Util.TRANS[transSp.getSelectedItemPosition()][0]);
            s.put("notes", noteEt.getText().toString().trim());
            s.put("departTime", dep);
            s.put("arriveTime", arr);
            if (cross > 0) s.put("crossDays", cross);
            if (price > 0) s.put("price", price);
            String vehicle = vehicleEt.getText().toString().trim();
            if (!vehicle.isEmpty()) s.put("vehicleNo", vehicle);
            // 乘客：对象数组 [{name, seat}]（与网页版一致）；座位归属乘客，不再写顶层 seat
            JSONArray paxArr = new JSONArray();
            for (Map.Entry<String, String> en : selPax.entrySet()) {
                JSONObject p = new JSONObject();
                p.put("name", en.getKey());
                p.put("seat", en.getValue());
                paxArr.put(p);
            }
            if (paxArr.length() > 0) s.put("passengers", paxArr);

            JSONArray trips = Store.load(this);
            JSONObject trip = EditTripActivity.findTrip(trips, tripId);
            if (trip == null) { finish(); return; }
            JSONObject days = trip.optJSONObject("days");
            JSONObject day = days != null ? days.optJSONObject(date) : null;
            if (day == null) { finish(); return; }
            JSONArray segs = day.optJSONArray("segments");
            if (segs == null) { segs = new JSONArray(); day.put("segments", segs); }
            if (segIndex >= 0 && segIndex < segs.length()) {
                segs.put(segIndex, s);   // 编辑已有段：原地替换，不动顺序
            } else {
                // 新增段：按出发时间插到正确位置（与网页版 placeSegByTime 同口径）；
                // 没填时间的排到末尾，其余段的顺序一律不变
                int ins = insertIndexByTime(segs, s.optString("departTime"));
                JSONArray out = new JSONArray();
                for (int i = 0; i < segs.length(); i++) {
                    if (i == ins) out.put(s);
                    out.put(segs.optJSONObject(i));
                }
                if (ins >= segs.length()) out.put(s);
                day.put("segments", out);
            }
            Store.save(this, trips);

            Intent it = new Intent();
            it.putExtra("segment", s.toString());
            it.putExtra("index", segIndex);
            setResult(RESULT_OK, it);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---- UI 构建辅助 ----
    /** 'HH:MM' → 分钟数；非法/为空返回 null。兼容不补零的旧值（'9:00'），故按数值比而非字符串比 */
    private static Integer hhmmToMin(String v) {
        if (v == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,2}):(\\d{2})$").matcher(v.trim());
        if (!m.matches()) return null;
        int h = Integer.parseInt(m.group(1)), mi = Integer.parseInt(m.group(2));
        return (h > 23 || mi > 59) ? null : h * 60 + mi;
    }

    /** 新增段按出发时间定位：返回应插入的下标。没填时间 → 末尾；
     *  无时间的既有段会被跳过，因此仍在末尾（与网页版 placeSegByTime 同规则） */
    private static int insertIndexByTime(JSONArray segs, String dep) {
        Integer t = hhmmToMin(dep);
        if (t == null) return segs.length();
        for (int i = 0; i < segs.length(); i++) {
            JSONObject x = segs.optJSONObject(i);
            Integer v = hhmmToMin(x != null ? x.optString("departTime") : null);
            if (v != null && v > t) return i;          // 第一个更晚的段之前
        }
        int last = -1;
        for (int i = 0; i < segs.length(); i++) {
            JSONObject x = segs.optJSONObject(i);
            Integer v = hhmmToMin(x != null ? x.optString("departTime") : null);
            if (v != null && v <= t) last = i;
        }
        return last + 1;   // 没有更晚的 → 放到最后一个「≤ 本段」的之后
    }

    private EditText field(String label, String hint) {
        LinearLayout wrap = Util.vBox(this);
        wrap.setPadding(0, Util.dp(this, 12), 0, 0);
        wrap.addView(boldLabel(label));
        EditText et = input(hint);
        wrap.addView(et);
        box.addView(wrap);
        return et;
    }

    private TextView boldLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(0xFF55556A);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private EditText input(String hint) {
        EditText et = new EditText(this);
        EditTripActivity.Style.input(et);
        et.setHint(hint);
        return et;
    }

    private Spinner spinner(LinearLayout wrap, String[] items) {
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = Util.dp(this, 6);
        sp.setLayoutParams(p);
        wrap.addView(sp);
        return sp;
    }
}