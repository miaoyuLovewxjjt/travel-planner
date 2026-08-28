package com.zcode.travelapp;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

/**
 * 新建/编辑大行程（名称/表情/日期范围/预算/出行人/备注）。
 * 编辑时只改动已知字段，未知字段（行李清单等）原样保留。
 */
public class EditTripActivity extends Activity {

    private LinearLayout box;
    private EditText nameEt, emojiEt, startEt, endEt, budgetEt, travelersEt, notesEt;
    private String tripId;   // null = 新建
    private LocalDate startD, endD;
    private String color = "#4D96FF"; // 主题色（网页版 PRESET_COLORS）

    /** 网页版 PRESET_COLORS 10 预设 */
    private static final String[] PRESET_COLORS = {
            "#FF6B6B", "#FF9A5A", "#FFC24B", "#4ECB71", "#4D96FF",
            "#7B5CF0", "#F0619B", "#0FB5AE", "#B58A5A", "#5A6B8C"
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_edit_trip);

        tripId = getIntent().getStringExtra("tripId");
        TextView title = findViewById(R.id.title_tv);
        title.setText(tripId == null ? "新建行程" : "编辑行程");

        Button cancelBtn = findViewById(R.id.btn_cancel);
        cancelBtn.setText("");               // 取消文字 → 返回箭头
        cancelBtn.setBackgroundColor(0x00000000);
        cancelBtn.setMinWidth(0); cancelBtn.setMinHeight(0);
        cancelBtn.setPadding(0, 0, 0, 0);
        LineIconButton backBtn = LineIconButton.sized(LineIconButton.back(this), 36);
        backBtn.setOnClickListener(v -> finish());
        LinearLayout topbar = (LinearLayout) cancelBtn.getParent();
        int idx = topbar.indexOfChild(cancelBtn);
        topbar.removeViewAt(idx);
        topbar.addView(backBtn, idx);

        box = findViewById(R.id.form_box);
        nameEt = field("名称 *", "例如：十一国庆 上海→千岛湖", false, false);
        emojiEt = field("封面表情", "可填一个 emoji，如 🏝️", false, false);

        TextView dateLabel = new TextView(this);
        dateLabel.setText("起止日期（含首尾）");
        dateLabel.setTextSize(13);
        dateLabel.setTextColor(0xFF55556A);
        dateLabel.setTypeface(Typeface.DEFAULT_BOLD);
        dateLabel.setPadding(0, Util.dp(this, 12), 0, 0);
        box.addView(dateLabel);

        startEt = dateField("开始日期");
        endEt = dateField("结束日期");
        startEt.setOnClickListener(v -> pickDate(true));
        endEt.setOnClickListener(v -> pickDate(false));

        budgetEt = field("总预算（元，可不填）", "数字，如 5000", false, true);
        travelersEt = field("出行人（逗号分隔）", "如：我、爸爸、妈妈", false, false);
        // 主题色（网页版 PRESET_COLORS 10 预设）
        LinearLayout colorWrap = Util.vBox(this);
        colorWrap.setPadding(0, Util.dp(this, 12), 0, 0);
        colorWrap.addView(fieldLabel("主题色"));
        // 色板自动换行（与电脑版一致：10 色分两行展示）
        FlowLayout swatches = new FlowLayout(this);
        swatches.setGaps(10, 8, this);
        swatches.setPadding(0, Util.dp(this, 6), 0, 0);
        for (int cIdx = 0; cIdx < PRESET_COLORS.length; cIdx++) {
            final String fc = PRESET_COLORS[cIdx];
            final int ci = cIdx;
            // 纯色圆点（无文字）；选中 = 深色描边 + 放大 1.1（对齐电脑版 color-dot：sel 边框深色+scale(1.1)）
            Button sw = new Button(this);
            sw.setText("");
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setColor(Util.parseColor(fc, 0xFF4D96FF));
            g.setCornerRadius(Util.dp(this, 18));
            g.setStroke(fc.equalsIgnoreCase(color) ? Util.dp(this, 3) : 0, 0xFF1F2230);
            sw.setBackground(g);
            sw.setScaleX(fc.equalsIgnoreCase(color) ? 1.1f : 1f);
            sw.setScaleY(fc.equalsIgnoreCase(color) ? 1.1f : 1f);
            LinearLayout.LayoutParams sp_ = new LinearLayout.LayoutParams(Util.dp(this, 36), Util.dp(this, 36));
            sp_.rightMargin = Util.dp(this, 12);
            sw.setLayoutParams(sp_);
            sw.setStateListAnimator(null);
            swatches.addView(sw);
            sw.setOnClickListener(v -> {
                color = fc;
                for (int i = 0; i < swatches.getChildCount(); i++) {
                    Button b2 = (Button) swatches.getChildAt(i);
                    android.graphics.drawable.GradientDrawable bg =
                            (android.graphics.drawable.GradientDrawable) ((Button) swatches.getChildAt(i)).getBackground();
                    boolean sel = PRESET_COLORS[i].equalsIgnoreCase(color);
                    bg.setStroke(sel ? Util.dp(this, 3) : 0, 0xFF1F2230);
                    b2.setScaleX(sel ? 1.1f : 1f);
                    b2.setScaleY(sel ? 1.1f : 1f);
                }
            });
        }
        colorWrap.addView(swatches);
        box.addView(colorWrap);
        notesEt = field("备注（≤80字）", "旅行主题、注意事项…", false, false);
        notesEt.setSingleLine(false);
        notesEt.setMinLines(3);
        notesEt.setGravity(Gravity.TOP);

        if (startD == null) startD = LocalDate.now();
        if (endD == null) endD = startD.plusDays(1);
        startEt.setText(startD.format(Util.FMT_DATE));
        endEt.setText(endD.format(Util.FMT_DATE));

        Button save = findViewById(R.id.btn_save);
        save.setOnClickListener(v -> save());

        if (tripId != null) loadTrip();
    }

    /** 标签+输入框组合加入表单，返回输入框 */
    private EditText field(String label, String hint, boolean unused, boolean number) {
        LinearLayout wrap = Util.vBox(this);
        wrap.setPadding(0, Util.dp(this, 12), 0, 0);
        wrap.addView(fieldLabel(label));
        EditText et = new EditText(this);
        Style.input(et);
        et.setHint(hint);
        if (number) et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        wrap.addView(et);
        box.addView(wrap);
        return et;
    }

    private TextView fieldLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(0xFF55556A);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private EditText dateField(String hint) {
        EditText et = new EditText(this);
        Style.input(et);
        et.setHint(hint);
        et.setFocusable(false); // 点按弹出日期选择器，不弹键盘
        box.addView(et);
        return et;
    }

    private void pickDate(boolean isStart) {
        LocalDate base = isStart ? startD : endD;
        new DatePickerDialog(this, (dp, y, m, d) -> {
            LocalDate picked = LocalDate.of(y, m + 1, d);
            if (isStart) {
                startD = picked;
                if (endD.isBefore(startD)) endD = startD; // 结束不早于开始
            } else {
                endD = picked;
                if (endD.isBefore(startD)) startD = endD;
            }
            startEt.setText(startD.format(Util.FMT_DATE));
            endEt.setText(endD.format(Util.FMT_DATE));
        }, base.getYear(), base.getMonthValue() - 1, base.getDayOfMonth()).show();
    }

    private void updateSwatch(android.widget.ImageView dot, android.graphics.drawable.GradientDrawable g, String c) {
        g.setStroke(c.equalsIgnoreCase(color) ? Util.dp(this, 3) : 0, 0xFFFFFFFF);
    }

    private void loadTrip() {
        JSONArray trips = Store.load(this);
        JSONObject t = findTrip(trips, tripId);
        if (t == null) { finish(); return; }
        nameEt.setText(t.optString("name"));
        emojiEt.setText(t.optString("emoji"));
        try { startD = LocalDate.parse(t.optString("startDate")); } catch (Exception e) {}
        try { endD = LocalDate.parse(t.optString("endDate")); } catch (Exception e) {}
        startEt.setText(startD.format(Util.FMT_DATE));
        endEt.setText(endD.format(Util.FMT_DATE));
        budgetEt.setText(t.optDouble("budget", 0) > 0 ? Util.fmtMoney(t.optDouble("budget", 0)) : "");
        StringBuilder sb = new StringBuilder();
        JSONArray tr = t.optJSONArray("travelers");
        if (tr != null) for (int i = 0; i < tr.length(); i++) { if (i > 0) sb.append("、"); sb.append(tr.optString(i)); }
        travelersEt.setText(sb.toString());
        notesEt.setText(t.optString("notes"));
        String tc = t.optString("color", "#4D96FF");
        if (tc.length() == 7) color = tc;
    }

    static JSONObject findTrip(JSONArray trips, String id) {
        for (int i = 0; i < trips.length(); i++) {
            JSONObject t = trips.optJSONObject(i);
            if (t != null && t.optString("id").equals(id)) return t;
        }
        return null;
    }

    private void save() {
        String name = nameEt.getText().toString().trim();
        if (name.isEmpty()) { Toast.makeText(this, "请填写行程名称", Toast.LENGTH_SHORT).show(); return; }
        if (startD == null || endD == null || endD.isBefore(startD)) {
            Toast.makeText(this, "日期范围不正确", Toast.LENGTH_SHORT).show(); return;
        }
        JSONArray trips = Store.load(this);
        JSONArray travelers = new JSONArray();
        for (String s : travelersEt.getText().toString().split("[,，]")) {
            s = s.trim();
            if (!s.isEmpty()) travelers.put(s);
        }
        double budget = 0;
        try { budget = Double.parseDouble(budgetEt.getText().toString().trim()); } catch (Exception e) {}
        String emoji = emojiEt.getText().toString().trim();
        if (emoji.isEmpty()) emoji = "🗺️";

        try {
            if (tripId == null) {
                JSONObject t = new JSONObject();
                t.put("id", "t" + System.currentTimeMillis());
                t.put("name", name);
                t.put("emoji", emoji);
                t.put("startDate", startD.format(Util.FMT_DATE));
                t.put("endDate", endD.format(Util.FMT_DATE));
                t.put("travelers", travelers);
                t.put("budget", budget);
                t.put("color", color);
                t.put("notes", notesEt.getText().toString().trim());
                t.put("wall", "none");
                t.put("days", emptyDays(startD, endD));
                trips.put(t);
            } else {
                JSONObject t = findTrip(trips, tripId);
                if (t == null) { finish(); return; }
                boolean rangeChanged = !t.optString("startDate").equals(startD.format(Util.FMT_DATE))
                        || !t.optString("endDate").equals(endD.format(Util.FMT_DATE));
                t.put("name", name);
                t.put("emoji", emoji);
                t.put("startDate", startD.format(Util.FMT_DATE));
                t.put("endDate", endD.format(Util.FMT_DATE));
                t.put("travelers", travelers);
                t.put("budget", budget);
                t.put("notes", notesEt.getText().toString().trim());
                t.put("color", color);
                if (rangeChanged) {
                    JSONObject days = t.optJSONObject("days");
                    if (days == null) { days = new JSONObject(); t.put("days", days); }
                    // 扩范围补空天；缩范围保留已有数据不删（与网页版一致）
                    for (LocalDate d = startD; !d.isAfter(endD); d = d.plusDays(1)) {
                        String k = d.format(Util.FMT_DATE);
                        if (!days.has(k)) days.put(k, emptyDay());
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Store.save(this, trips);
        setResult(RESULT_OK);
        finish();
    }

    static JSONObject emptyDay() {
        JSONObject day = new JSONObject();
        try {
            day.put("notes", "");
            JSONObject lodge = new JSONObject();
            lodge.put("type", "酒店");
            lodge.put("name", ""); lodge.put("location", ""); lodge.put("pricePerNight", 0); lodge.put("notes", "");
            day.put("lodging", lodge);
            day.put("travelers", new JSONArray());
            day.put("expenses", new JSONArray());
            day.put("segments", new JSONArray());
            day.put("pins", new JSONArray());
        } catch (Exception e) {}
        return day;
    }

    private static JSONObject emptyDays(LocalDate start, LocalDate end) {
        JSONObject days = new JSONObject();
        try {
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                days.put(d.format(Util.FMT_DATE), emptyDay());
            }
        } catch (Exception e) {}
        return days;
    }

    /** 表单输入框样式（统一圆角浅底） */
    static final class Style {
        static void input(EditText et) {
            et.setTextSize(15);
            et.setTextColor(0xFF20202E);
            et.setHintTextColor(0xFFB0B0BB);
            et.setBackground(Util.chipBg(0xFFF0F0F5, 12, et.getContext()));
            et.setPadding(Util.dp(et.getContext(), 12), Util.dp(et.getContext(), 10),
                    Util.dp(et.getContext(), 12), Util.dp(et.getContext(), 10));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            p.topMargin = Util.dp(et.getContext(), 6);
            et.setLayoutParams(p);
        }
    }
}