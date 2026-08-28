package com.zcode.travelapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 添加/编辑一笔花销：分类（固定 6 类）/项目/金额/时间。
 */
public class EditExpenseActivity extends Activity {

    private EditText itemEt, amountEt, whenEt, noteEt;
    private Spinner catSp;
    private String tripId, date;
    private int expIndex; // -1 = 新建

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tripId = getIntent().getStringExtra("tripId");
        date = getIntent().getStringExtra("date");
        expIndex = getIntent().getIntExtra("index", -1);

        LinearLayout root = Util.vBox(this);
        root.setBackgroundColor(0xFFF4F4F7);
        setContentView(root);

        // 顶栏
        LinearLayout top = Util.hBox(this);
        top.setBackgroundColor(0xFFFFFFFF);
        Util.setHeight(top, 52, this);
        top.setPadding(Util.dp(this, 14), 0, Util.dp(this, 14), 0);
        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setTextSize(14);
        cancel.setBackgroundColor(0x00000000);
        cancel.setTextColor(0xFF55556A);
        cancel.setMinWidth(0); cancel.setMinHeight(0);
        cancel.setPadding(Util.dp(this, 8), 0, Util.dp(this, 8), 0);
        // 取消文字 → 返回箭头（先挂进顶栏再操作，避免 getParent()==null）
        top.addView(cancel);
        cancel.setVisibility(android.view.View.GONE);
        LineIconButton backBtn = LineIconButton.sized(LineIconButton.back(this), 30);
        backBtn.setOnClickListener(v -> finish());
        top.addView(backBtn, 0);
        TextView title = Util.text(this, expIndex < 0 ? "添加花销" : "编辑花销", 17, 0xFF20202E);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        Button save = new Button(this);
        save.setText("保存");
        save.setTextSize(14);
        save.setTextColor(0xFF4D96FF);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setBackgroundColor(0x00000000);
        save.setMinWidth(0); save.setMinHeight(0);
        save.setPadding(Util.dp(this, 8), 0, Util.dp(this, 8), 0);
        top.addView(save);
        root.addView(top);

        LinearLayout box = Util.vBox(this);
        box.setPadding(Util.dp(this, 16), Util.dp(this, 8), Util.dp(this, 16), Util.dp(this, 30));
        root.addView(box);

        // 分类
        LinearLayout catWrap = Util.vBox(this);
        catWrap.addView(boldLabel("分类"));
        catSp = new Spinner(this);
        String[] cats = new String[Util.CATS.length];
        for (int i = 0; i < Util.CATS.length; i++) cats[i] = Util.CATS[i][1] + " " + Util.CATS[i][0];
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cats);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        catSp.setAdapter(ad);
        catWrap.addView(catSp);
        box.addView(catWrap);

        itemEt = addField(box, "项目 *", "如：西湖边午餐（≤30字，全字显示）");
        itemEt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(30)});
        amountEt = addField(box, "金额（元）*", "数字，如 68");
        amountEt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        whenEt = addField(box, "时间（可不填）", "如 12:30");
        noteEt = addField(box, "备注（可不填，≤80字）", "如：和谁吃的、哪家店");
        noteEt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        whenEt.setHintTextColor(0xFFB0B0BB);

        save.setOnClickListener(v -> save());

        if (expIndex >= 0) load();
    }

    private void load() {
        JSONArray trips = Store.load(this);
        JSONObject trip = EditTripActivity.findTrip(trips, tripId);
        if (trip == null) { finish(); return; }
        JSONObject days = trip.optJSONObject("days");
        JSONObject day = days != null ? days.optJSONObject(date) : null;
        if (day == null) { finish(); return; }
        JSONArray exps = day.optJSONArray("expenses");
        if (exps == null || expIndex >= exps.length()) { finish(); return; }
        JSONObject e = exps.optJSONObject(expIndex);
        if (e == null) { finish(); return; }
        String cat = e.optString("category");
        for (int i = 0; i < Util.CATS.length; i++) {
            if (Util.CATS[i][0].equals(cat)) catSp.setSelection(i);
        }
        itemEt.setText(e.optString("item"));
        amountEt.setText(e.optDouble("amount", 0) > 0 ? Util.fmtMoney(e.optDouble("amount", 0)) : "");
        whenEt.setText(e.optString("when"));
        noteEt.setText(e.optString("note"));
    }

    private void save() {
        String item = itemEt.getText().toString().trim();
        if (item.isEmpty()) { Toast.makeText(this, "请填写项目", Toast.LENGTH_SHORT).show(); return; }
        double amount;
        try { amount = Double.parseDouble(amountEt.getText().toString().trim()); }
        catch (Exception e) { Toast.makeText(this, "请填写金额", Toast.LENGTH_SHORT).show(); return; }
        String when = Util.normalizeTime(whenEt.getText().toString());
        if (when == null) when = "";

        try {
            JSONObject e = new JSONObject();
            e.put("category", Util.CATS[catSp.getSelectedItemPosition()][0]);
            e.put("item", item);
            e.put("amount", amount);
            if (!when.isEmpty()) e.put("when", when);
            String note = noteEt.getText().toString().trim();
            if (!note.isEmpty()) e.put("note", note);

            JSONArray trips = Store.load(this);
            JSONObject trip = EditTripActivity.findTrip(trips, tripId);
            if (trip == null) { finish(); return; }
            JSONObject days = trip.optJSONObject("days");
            JSONObject day = days != null ? days.optJSONObject(date) : null;
            if (day == null) { finish(); return; }
            JSONArray exps = day.optJSONArray("expenses");
            if (exps == null) { exps = new JSONArray(); day.put("expenses", exps); }
            if (expIndex >= 0 && expIndex < exps.length()) exps.put(expIndex, e);
            else exps.put(e);
            Store.save(this, trips);

            Intent it = new Intent();
            it.putExtra("expense", e.toString());
            it.putExtra("index", expIndex);
            setResult(RESULT_OK, it);
            finish();
        } catch (Exception ex) {
            Toast.makeText(this, "保存失败：" + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private EditText addField(LinearLayout box, String label, String hint) {
        LinearLayout wrap = Util.vBox(this);
        wrap.setPadding(0, Util.dp(this, 12), 0, 0);
        wrap.addView(boldLabel(label));
        EditText et = new EditText(this);
        EditTripActivity.Style.input(et);
        et.setHint(hint);
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
}