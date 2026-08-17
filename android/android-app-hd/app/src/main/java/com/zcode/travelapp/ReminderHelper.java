package com.zcode.travelapp;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * 提醒调度：将页面保存的提醒同步到系统闹钟（AlarmManager + 通知栏）。
 * 全量重调度：先取消已记录的所有闹钟，再按最新列表逐个设置。
 */
public class ReminderHelper {

    private static final String PREFS = "reminders";
    private static final String KEY_IDS = "scheduled_ids";
    public static final String CHANNEL_ID = "travel_reminder";

    /** 创建通知渠道（Android 8+） */
    public static void ensureNotificationChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "行程提醒",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("每日行程定时提醒（抢票、出发等）");
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    /** 全量调度提醒：json = [{"id","title","ts"}] */
    public static void scheduleAll(Context ctx, String json) {
        ensureNotificationChannel(ctx);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // 1) 取消旧闹钟
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> old = sp.getStringSet(KEY_IDS, new HashSet<>());
        for (String id : old) {
            try {
                PendingIntent pi = buildPi(ctx, id);
                am.cancel(pi);
            } catch (Exception ignored) {
            }
        }

        // 2) 调度新的
        Set<String> now = new HashSet<>();
        try {
            JSONArray arr = new JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("id", "r" + i);
                String title = o.optString("title", "行程提醒");
                long ts = o.optLong("ts", 0);
                if (ts <= System.currentTimeMillis()) continue; // 过期的不调度
                now.add(id);
                Intent intent = new Intent(ctx, ReminderReceiver.class);
                intent.putExtra("title", title);
                PendingIntent pi = buildPi(ctx, id, intent, title);
                try {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ts, pi);
                } catch (Exception e) {
                    // 精确闹钟权限不可用时降级为非精确
                    am.set(AlarmManager.RTC_WAKEUP, ts, pi);
                }
            }
        } catch (Exception ignored) {
        }
        sp.edit().putStringSet(KEY_IDS, now).apply();
    }

    private static PendingIntent buildPi(Context ctx, String id) {
        return buildPi(ctx, id, new Intent(ctx, ReminderReceiver.class), "");
    }

    private static PendingIntent buildPi(Context ctx, String id, Intent intent, String title) {
        int requestCode = id.hashCode();
        intent.setAction("reminder_" + id);
        return PendingIntent.getBroadcast(ctx, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
