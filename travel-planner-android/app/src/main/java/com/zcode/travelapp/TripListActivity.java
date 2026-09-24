package com.zcode.travelapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 行程列表（首页）：展示/新建/编辑/删除大行程；数据导出/导入（与电脑版互通）。
 * 视觉：封面渐变卡（随行程主题色）＋ 胶囊按钮 ＋ 白卡描边。
 */
public class TripListActivity extends Activity {

    private static final int REQ_EDIT_TRIP = 1;
    private static final int REQ_IMPORT = 2;
    private static final int REQ_EXPORT = 3;

    private JSONArray trips;
    private LinearLayout listBox;
    private TextView emptyTip;
    private ScrollView listSv;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        trips = Store.load(this);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = Util.vBox(this);
        root.setBackgroundColor(Util.BG);
        setContentView(root);

        // ---- 顶栏：白底 + 标题 + 新建（紧凑小胶囊，与详情页 PDF 按钮同款） ----
        LinearLayout top = Util.hBox(this);
        top.setBackgroundColor(0xFFFFFFFF);
        top.setPadding(Util.dp(this, 16), Util.dp(this, 8), Util.dp(this, 12), Util.dp(this, 8));
        TextView title = Util.text(this, "我的行程", 18, Util.INK);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        // 右侧四个手绘图标：＋新建 / 导出 / 导入 / 设置（中心线对齐，紧凑）
        LineIconButton add = LineIconButton.sized(LineIconButton.plus(this), 36);
        add.setOnClickListener(v -> openEditTrip(null));
        LineIconButton exp = LineIconButton.sized(LineIconButton.export(this), 36);
        exp.setOnClickListener(v -> exportData());
        LineIconButton imp = LineIconButton.sized(LineIconButton.import_(this), 36);
        imp.setOnClickListener(v -> importData());
        LineIconButton set = LineIconButton.sized(LineIconButton.gear(this), 36);
        set.setOnClickListener(v -> openSettings());
        LinearLayout iconBox = Util.actionGroup(this, add, exp, imp, set);
        top.addView(iconBox, new LinearLayout.LayoutParams(-2, -2));
        root.addView(top);



        // ---- 列表区 ----
        listSv = new ScrollView(this);
        listSv.setFillViewport(true);
        listBox = Util.vBox(this);
        listBox.setPadding(Util.dp(this, 16), Util.dp(this, 14), Util.dp(this, 16), Util.dp(this, 30));
        emptyTip = Util.text(this, "", 14, Util.MUTE);
        emptyTip.setGravity(Gravity.CENTER);
        emptyTip.setPadding(0, Util.dp(this, 70), 0, 0);
        listSv.addView(listBox);
        root.addView(listSv, new LinearLayout.LayoutParams(-1, -1));
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 详情页是普通 startActivity 打开的，改完账目/路线返回时不会走 onActivityResult，
        // 而本页只在 onCreate 读过一次盘 → 卡片上的花费/结余会停在旧值。
        // 与详情页同款处理（TripDetailActivity.onActivityResult）：回前台就强制从磁盘重载再渲染。
        trips = Store.load(this);
        int keepY = listSv.getScrollY(); // 重渲染会重置滚动位置，保住用户的浏览位置
        render();
        listSv.scrollTo(0, keepY);
    }

    private void render() {
        listBox.removeAllViews();
        if (trips.length() == 0) {
            emptyTip.setText("🗺️ 还没有行程\n\n点右上角「＋ 新建」开始规划");
            listBox.addView(emptyTip);
            return;
        }
        for (int i = 0; i < trips.length(); i++) {
            JSONObject t = trips.optJSONObject(i);
            if (t == null) continue;
            listBox.addView(tripCard(t, i));
            View gap = new View(this);
            gap.setBackgroundColor(0x00000000);
            Util.setHeight(gap, 14, this);
            listBox.addView(gap);
        }
    }

    /** 行程卡片：渐变封面（emoji＋名称＋日期）＋ 白底正文（出行人/花费＋操作按钮） */
    private View tripCard(JSONObject t, int index) {
        final int accent = Util.parseColor(t.optString("color"), Util.ACCENT);
        final String emoji = t.optString("emoji", "🗺️");

        LinearLayout card = Util.vBox(this);
        card.setBackground(Util.cardStyle(this, 22));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = Util.dp(this, 0);
        card.setLayoutParams(cp);

        // ---- 渐变封面 ----
        LinearLayout cover = Util.vBox(this);
        cover.setBackground(Util.coverGradient(accent, 22, this));
        cover.setPadding(Util.dp(this, 16), Util.dp(this, 16), Util.dp(this, 16), Util.dp(this, 14));
        LinearLayout coverTop = Util.hBox(this);
        TextView emojiTv = Util.text(this, emoji, 26, 0xFFFFFFFF);
        coverTop.addView(emojiTv);
        TextView nameTv = Util.text(this, t.optString("name", "未命名行程"), 17, 0xFFFFFFFF);
        nameTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        nameTv.setPadding(Util.dp(this, 10), 0, 0, 0);
        coverTop.addView(nameTv, new LinearLayout.LayoutParams(0, -2, 1));
        cover.addView(coverTop);

        String dates = Util.displayDate(t.optString("startDate")) + " → " + Util.displayDate(t.optString("endDate"));
        TextView dateTv = Util.text(this, "📅 " + dates, 12.5f, 0xE6FFFFFF);
        LinearLayout.LayoutParams dateP2 = new LinearLayout.LayoutParams(-1, -2);
        dateP2.topMargin = Util.dp(this, 10);
        cover.addView(dateTv, dateP2);

        int days = Util.dayKeys(t).size();
        String travelers = join(t.optJSONArray("travelers"));
        if (!travelers.isEmpty() || days > 0) {
            StringBuilder sb = new StringBuilder();
            if (days > 0) sb.append("🗓️ 共 ").append(days).append(" 天");
            if (!travelers.isEmpty()) {
                if (sb.length() > 0) sb.append("  ·  ");
                sb.append("👥 ").append(travelers);
            }
            TextView meta = Util.text(this, sb.toString(), 12, 0xD9FFFFFF);
            LinearLayout.LayoutParams metaP = new LinearLayout.LayoutParams(-1, -2);
            metaP.topMargin = Util.dp(this, 8);
            cover.addView(meta, metaP);
        }
        card.addView(cover);

        // ---- 白底正文：花费 ----
        LinearLayout body = Util.vBox(this);
        body.setPadding(Util.dp(this, 16), Util.dp(this, 14), Util.dp(this, 12), Util.dp(this, 12));
        card.addView(body);

        // ---- 底部：文字行（FlowLayout 完整显示不省略，超长自动换行）＋ 编辑/删除按钮独立一行（右对齐，永不挤压） ----
        double spent = Util.totalSpent(t);
        double budget = t.optDouble("budget", 0);
        boolean over = budget > 0 && spent > budget;
        LinearLayout bottom = Util.vBox(this);
        bottom.setPadding(0, Util.dp(this, 10), 0, 0);
        FlowLayout txtWrap = new FlowLayout(this);
        txtWrap.setGaps(6, 4, this);
        TextView spentTv = Util.text(this, "💰 已花费 ¥" + Util.fmtMoney(spent), 14, accent);
        spentTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        txtWrap.addView(spentTv);
        if (budget > 0) {
            TextView budgetTv = Util.text(this, "／ 预算 ¥" + Util.fmtMoney(budget), 14,
                    over ? 0xFFE5484D : 0xFF6B6E80);
            budgetTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            txtWrap.addView(budgetTv);
            if (over) {
                TextView overTv = Util.text(this, "⚠️超支", 14, 0xFFE5484D);
                overTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                txtWrap.addView(overTv);
            }
        }
        bottom.addView(txtWrap);
        LinearLayout btnRow = Util.hBox(this);
        btnRow.setGravity(Gravity.END); // 按钮独立一行、右对齐
        LineIconButton edit = LineIconButton.edit(this);
        LineIconButton del = LineIconButton.del(this);
        LinearLayout group = Util.actionGroup(this, edit, del);
        btnRow.addView(group);
        bottom.addView(btnRow);
        body.addView(bottom);

        // 整卡可点进入详情（网页版：点击卡片即进入）
        card.setOnClickListener(v -> {
            Intent it = new Intent(this, TripDetailActivity.class);
            it.putExtra("tripId", t.optString("id"));
            startActivity(it);
        });
        edit.setOnClickListener(v -> openEditTrip(t));
        del.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("删除行程")
                    .setMessage("确定删除「" + t.optString("name") + "」？该操作不可恢复。")
                    .setPositiveButton("删除", (d, w) -> {
                        trips.remove(index);
                        Store.save(this, trips);
                        render();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        return card;
    }

    private String join(JSONArray arr) {
        if (arr == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            if (i > 0) sb.append("、");
            sb.append(arr.optString(i));
        }
        return sb.toString();
    }


    /** 设置面板：与电脑版 ⚙️ 一致的数据管理（导出/导入/数据说明） */
    private void openSettings() {
        String[] items = {
                "📤 导出数据（备份/迁移）",
                "📥 导入数据（合并：同名覆盖+新增）",
                "💾 数据位置：" + Store.dataFile(this).getAbsolutePath(),
                "🔁 与电脑网页版 JSON 互通（导出→电脑导入）",
        };
        new AlertDialog.Builder(this)
                .setTitle("⚙️ 设置")
                .setItems(items, (d, which) -> {
                    if (which == 0) exportData();
                    else if (which == 1) importData();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void openEditTrip(JSONObject trip) {
        Intent it = new Intent(this, EditTripActivity.class);
        if (trip != null) it.putExtra("tripId", trip.optString("id"));
        startActivityForResult(it, REQ_EDIT_TRIP);
    }

    private void importData() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        // 用 */* 显示所有文件：部分手机文件管理器对 .json 不识别为 application/json 会隐藏文件
        it.setType("*/*");
        it.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json", "text/plain", "text/json", "application/octet-stream"});
        startActivityForResult(it, REQ_IMPORT);
    }

    private void exportData() {
        Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("*/*");
        it.putExtra(Intent.EXTRA_TITLE, "trips.json");
        startActivityForResult(it, REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        try {
            Uri uri = data.getData();
            if (req == REQ_EDIT_TRIP) {
                trips = Store.load(this); // 编辑页已写盘
                render();
            } else if (req == REQ_IMPORT) {
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    byte[] buf = new byte[in.available()];
                    int n = in.read(buf);
                    String json = new String(buf, 0, n, StandardCharsets.UTF_8);
                    JSONObject root = new JSONObject(json);
                    JSONArray arr = root.getJSONArray("trips");
                    if (arr.length() == 0) throw new Exception("文件里没有行程");
                    // 合并模式：同名行程（id 相同）用文件版覆盖，新行程追加；原有数据不丢
                    int added = 0, updated = 0;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject incoming = arr.optJSONObject(i);
                        if (incoming == null) continue;
                        String id = incoming.optString("id");
                        boolean replaced = false;
                        for (int j = 0; j < trips.length(); j++) {
                            JSONObject mine = trips.optJSONObject(j);
                            if (mine != null && id.length() > 0 && id.equals(mine.optString("id"))) {
                                trips.put(j, incoming);
                                replaced = true;
                                updated++;
                                break;
                            }
                        }
                        if (!replaced) {
                            trips.put(incoming);
                            added++;
                        }
                    }
                    Store.save(this, trips);
                    render();
                    Toast.makeText(this, "导入完成：新增 " + added + " 个、更新 " + updated
                            + " 个，原有行程已保留", Toast.LENGTH_LONG).show();
                }
            } else if (req == REQ_EXPORT) {
                JSONObject root = new JSONObject();
                root.put("trips", trips);
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(root.toString().getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "已导出 trips.json", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "操作失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}