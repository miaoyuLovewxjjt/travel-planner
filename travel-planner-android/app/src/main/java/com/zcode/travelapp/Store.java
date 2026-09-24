package com.zcode.travelapp;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 数据存取：trips.json 保存在应用私有目录（无需权限）。
 * 与网页版 trips.json 完全同构；未知字段（行李清单、地图坐标等）原样保留，导出后电脑版可直接导入。
 */
final class Store {
    private static final String TAG = "TravelApp";
    private static final String FILE = "trips.json";

    /** 首次启动的示例数据（与网页版 seed 同构） */
    private static final String SEED = "{\"trips\":[{\"id\":\"test1a018cfbe31\",\"name\":\"2026 国庆新疆之旅\",\"emoji\":\"👌👌👌\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-10-06\",\"travelers\":[\"韩梅梅\",\"李雷\"],\"budget\":32000,\"color\":\"#B58A5A\",\"wall\":\"gradient\",\"notes\":\"\",\"days\":{\"2026-09-25\":{\"notes\":\"1、阿勒泰站    --- 乌鲁木齐 K车火车需要提前【9.29-15 = 9.14】买票\",\"lodging\":{\"name\":\"出租房\",\"location\":\"出租房\",\"pricePerNight\":\"0\",\"notes\":\"出租房中住宿一晚\",\"_snap\":{\"name\":\"芳怡园\",\"location\":\"芳怡园\",\"pricePerNight\":\"0\",\"notes\":\"出租房中住宿一晚\",\"type\":\"其他\"},\"type\":\"其他\"},\"travelers\":[],\"expenses\":[{\"category\":\"购物\",\"item\":\"必备物品查漏补缺\",\"amount\":\"100\",\"when\":\"\"}],\"segments\":[{\"from\":\"北京南\",\"to\":\"出租屋\",\"transport\":\"地铁\",\"notes\":\"\",\"departTime\":\"20:45\",\"time\":\"20:45-22:30\",\"arriveTime\":\"22:30\",\"vehicleNo\":\"\",\"price\":\"6\"}],\"pins\":[],\"weather\":{\"cond\":\"晴\",\"low\":10,\"high\":20}},\"2026-09-26\":{\"notes\":\"1、雪都机场 --- 禾木村  行程需要提前安排包车\",\"lodging\":{\"name\":\"禾木\",\"location\":\"大床房\",\"pricePerNight\":\"1355\",\"notes\":\"含早餐\",\"_snap\":{\"name\":\"禾木百年老屋\",\"location\":\"迷你小木屋榻榻米亲子房B\",\"pricePerNight\":\"1355\",\"notes\":\"含早餐\"}},\"travelers\":[],\"expenses\":[{\"category\":\"餐饮\",\"item\":\"全天餐饮占位\",\"amount\":\"300\",\"when\":\"\",\"note\":\"\",\"_snap\":{\"category\":\"餐饮\",\"item\":\"全天餐饮占位\",\"amount\":\"300\",\"when\":\"\",\"note\":\"预估\"}},{\"category\":\"其他\",\"item\":\"娱乐占位\",\"amount\":\"200\",\"when\":\"\"},{\"category\":\"门票\",\"item\":\"禾木村48h门票*2\",\"amount\":\"104\",\"when\":\"\",\"_snap\":{\"category\":\"门票\",\"item\":\"禾木村门票*2\",\"amount\":\"104\",\"when\":\"\"}}],\"segments\":[{\"from\":\"出租屋\",\"to\":\"北京首都机场T3\",\"transport\":\"打车\",\"notes\":\"携程已预约送机 车牌号\",\"departTime\":\"09:40\",\"time\":\"09:40-10:00\",\"arriveTime\":\"10:00\",\"price\":\"50\",\"_snap\":{\"from\":\"出租屋\",\"to\":\"北京首都机场T3\",\"transport\":\"打车\",\"notes\":\"携程已预约送机 车牌号\",\"departTime\":\"10:40\",\"time\":\"10:40-11:20\",\"arriveTime\":\"11:20\",\"price\":\"91\"}},{\"from\":\"北京首都T3\",\"to\":\"阿勒泰雪都\",\"transport\":\"飞机\",\"notes\":\"含餐 等待值机开放\",\"departTime\":\"10:30\",\"time\":\"10:30-14:20\",\"arriveTime\":\"14:20\",\"vehicleNo\":\"\",\"price\":\"2968\",\"passengers\":[{\"name\":\"韩梅梅\",\"seat\":\"待值机\"},{\"name\":\"李雷\",\"seat\":\"待值机\"}],\"_snap\":{\"from\":\"北京首都T3\",\"to\":\"阿勒泰雪都\",\"transport\":\"飞机\",\"notes\":\"含餐 等待值机开放\",\"departTime\":\"10:30\",\"time\":\"10:30-14:20\",\"arriveTime\":\"14:20\",\"vehicleNo\":\"\",\"price\":\"2968\",\"passengers\":[{\"name\":\"徐\",\"seat\":\"待值机\"},{\"name\":\"苗\",\"seat\":\"待值机\"}]}},{\"from\":\"阿勒泰雪都机场\",\"to\":\"禾木村\",\"transport\":\"打车\",\"notes\":\"需要提前预约好\",\"departTime\":\"11:30\",\"time\":\"11:30-16:30\",\"arriveTime\":\"16:30\",\"price\":\"600\"}],\"pins\":[]},\"2026-09-27\":{\"notes\":\"1、禾木村 --- 喀纳斯老村 行程需要提前安排（预期下午出发）\\n2、本日：上午禾木村游玩、下午喀纳斯游玩\",\"lodging\":{\"name\":\"喀纳斯\",\"location\":\"双床房\",\"pricePerNight\":\"3037\",\"notes\":\"含早\",\"_snap\":{\"name\":\"喀纳斯清·一水云间酒店\",\"location\":\"月精·观景双床房\",\"pricePerNight\":\"2837\",\"notes\":\"喀纳斯湖边，含早\"}},\"travelers\":[],\"expenses\":[{\"category\":\"餐饮\",\"item\":\"全天餐饮占位\",\"amount\":\"300\",\"when\":\"\"},{\"category\":\"门票\",\"item\":\"喀纳斯老村48h*2套票\",\"amount\":\"500\",\"when\":\"\",\"note\":\"包含钓鱼台\"},{\"category\":\"其他\",\"item\":\"喀纳斯骑马*2\",\"amount\":\"600\",\"when\":\"\"}],\"segments\":[{\"from\":\"禾木老村\",\"to\":\"喀纳斯老村\",\"transport\":\"打车\",\"notes\":\"需要提前安排\",\"departTime\":\"14:30\",\"time\":\"14:30-16:00\",\"arriveTime\":\"16:00\",\"price\":\"400\"}],\"pins\":[]},\"2026-09-28\":{\"notes\":\"1、需要安排同酒店换房\",\"lodging\":{\"name\":\"喀纳斯\",\"location\":\"双床房\",\"pricePerNight\":\"3037\",\"notes\":\"含早\",\"_snap\":{\"name\":\"喀纳斯清·一水云间酒店\",\"location\":\"追风·观景大床房\",\"pricePerNight\":\"2914\",\"notes\":\"喀纳斯湖边，同酒店换房，含早\"}},\"travelers\":[],\"expenses\":[{\"category\":\"餐饮\",\"item\":\"全天餐饮占位\",\"amount\":\"300\",\"when\":\"\"}],\"segments\":[],\"pins\":[]},\"2026-09-29\":{\"notes\":\"1、喀纳斯老村 --- 阿勒泰站 行程需要提前安排（预期下午出发）\\n2、阿勒泰站    --- 乌鲁木齐 火车\",\"travelers\":[],\"expenses\":[{\"category\":\"餐饮\",\"item\":\"全天餐饮占位\",\"amount\":\"300\",\"when\":\"\",\"_snap\":{\"category\":\"餐饮\",\"item\":\"全天餐饮价格占位\",\"amount\":\"300\",\"when\":\"\"}}],\"segments\":[{\"from\":\"阿勒泰站\",\"to\":\"乌鲁木齐站\",\"transport\":\"高铁\",\"notes\":\"\",\"departTime\":\"23:38\",\"time\":\"23:38-08:58\",\"arriveTime\":\"08:58\",\"crossDays\":1,\"vehicleNo\":\"\",\"price\":\"500\",\"passengers\":[{\"name\":\"韩梅梅\",\"seat\":\"\"},{\"name\":\"李雷\",\"seat\":\"\"}],\"_snap\":{\"from\":\"阿勒泰站\",\"to\":\"乌鲁木齐站\",\"transport\":\"高铁\",\"notes\":\"\",\"departTime\":\"23:38\",\"time\":\"23:38-08:58\",\"arriveTime\":\"08:58\",\"crossDays\":1,\"vehicleNo\":\"\",\"price\":\"500\",\"passengers\":[{\"name\":\"韩梅梅\",\"seat\":\"下铺\"},{\"name\":\"李雷\",\"seat\":\"下铺\"}]}}],\"pins\":[],\"lodging\":{\"type\":\"酒店\",\"name\":\"阿勒泰住宿\",\"location\":\"阿勒泰车站\",\"pricePerNight\":\"1200\",\"notes\":\"含早\",\"_snap\":{\"type\":\"酒店\",\"name\":\"阿勒泰住宿\",\"location\":\"\",\"pricePerNight\":\"1200\",\"notes\":\"\"}},\"weather\":{\"cond\":\"晴\",\"low\":-10,\"high\":10}},\"2026-09-30\":{\"notes\":\"\",\"lodging\":{\"name\":\"赛里木湖酒店\",\"location\":\"\",\"pricePerNight\":\"1200\",\"notes\":\"不含早，有停车场和充电桩\",\"_snap\":{\"name\":\"赛里木湖洲际酒店\",\"location\":\"\",\"pricePerNight\":\"1392\",\"notes\":\"不含早，有停车场和充电桩\"}},\"travelers\":[],\"expenses\":[{\"category\":\"门票\",\"item\":\"赛里木湖人车门票·48h\",\"amount\":\"360\",\"when\":\"\",\"note\":\"\",\"_snap\":{\"category\":\"门票\",\"item\":\"赛里木湖人车门票\",\"amount\":\"360\",\"when\":\"\",\"note\":\"\"}},{\"category\":\"餐饮\",\"item\":\"全天餐饮占位\",\"amount\":\"350\",\"when\":\"\"}],\"segments\":[{\"from\":\"乌鲁木齐站\",\"to\":\"伊宁站\",\"transport\":\"高铁\",\"notes\":\"\",\"departTime\":\"09:10\",\"time\":\"09:10-14:50\",\"arriveTime\":\"14:50\",\"vehicleNo\":\"\",\"price\":\"264\",\"passengers\":[{\"name\":\"韩梅梅\",\"seat\":\"二等座\"},{\"name\":\"李雷\",\"seat\":\"二等座\"}],\"_snap\":{\"from\":\"乌鲁木齐站\",\"to\":\"伊宁站\",\"transport\":\"高铁\",\"notes\":\"\",\"departTime\":\"10:05\",\"time\":\"10:05-15:04\",\"arriveTime\":\"15:04\",\"vehicleNo\":\"C845\",\"price\":\"256\",\"passengers\":[{\"name\":\"徐\",\"seat\":\"二等座\"},{\"name\":\"苗\",\"seat\":\"二等座\"}]}},{\"from\":\"伊宁站\",\"to\":\"赛里木湖洲际酒店\",\"transport\":\"自驾\",\"notes\":\"油钱、电费另算，租车自驾截至到10.2晚上还车\",\"departTime\":\"15:30\",\"time\":\"15:30-20:00\",\"arriveTime\":\"20:00\",\"price\":\"1797\"}],\"pins\":[]},\"2026-10-01\":{\"notes\":\"1、赛里木湖 --- 特克斯 自驾\",\"lodging\":{\"name\":\"特克斯\",\"location\":\"八卦城\",\"pricePerNight\":\"420\",\"notes\":\"含早\",\"_snap\":{\"name\":\"特克斯\",\"location\":\"八卦城\",\"pricePerNight\":\"450\",\"notes\":\"含早\"}},\"travelers\":[],\"expenses\":[{\"category\":\"餐饮\",\"item\":\"全天餐饮占位\",\"amount\":\"300\",\"when\":\"\"},{\"category\":\"其他\",\"item\":\"加油费用\",\"amount\":\"500\",\"when\":\"\"}],\"segments\":[],\"pins\":[]},\"2026-10-02\":{\"notes\":\"\",\"lodging\":{\"name\":\"特克斯\",\"location\":\"八卦城\",\"pricePerNight\":\"420\",\"notes\":\"含早餐\",\"_snap\":{\"name\":\"特克斯天鹅驿站\",\"location\":\"八卦城中心太极店\",\"pricePerNight\":\"450\",\"notes\":\"含早餐\"}},\"travelers\":[],\"expenses\":[{\"category\":\"餐饮\",\"item\":\"全天餐饮占位\",\"amount\":\"300\",\"when\":\"\"},{\"category\":\"其他\",\"item\":\"还车手续费\",\"amount\":\"200\",\"when\":\"\"}],\"segments\":[],\"pins\":[]},\"2026-10-03\":{\"notes\":\"1、特克斯八卦城 --- 琼库什台 行程需要提前安排包车\",\"lodging\":{\"name\":\"琼库什台\",\"location\":\"琼库什台\",\"pricePerNight\":\"740\",\"notes\":\"含早\",\"_snap\":{\"name\":\"琼库什台阅木山居民宿\",\"location\":\"新疆特克斯县喀拉达拉镇琼库什台村塔西巴扎113-1号\",\"pricePerNight\":\"678\",\"notes\":\"含早\",\"type\":\"民宿\"},\"type\":\"民宿\"},\"travelers\":[],\"expenses\":[{\"category\":\"餐饮\",\"item\":\"全天餐饮占位\",\"amount\":\"300\",\"when\":\"\"}],\"segments\":[],\"pins\":[]},\"2026-10-04\":{\"notes\":\"\",\"lodging\":{\"name\":\"琼库什台\",\"location\":\"琼库什台\",\"pricePerNight\":\"740\",\"notes\":\"含早\",\"_snap\":{\"name\":\"琼库什台阅木山居民宿\",\"location\":\"新疆特克斯县喀拉达拉镇琼库什台村塔西巴扎113-1号\",\"pricePerNight\":\"678\",\"notes\":\"含早\"}},\"travelers\":[],\"expenses\":[{\"category\":\"餐饮\",\"item\":\"全天餐饮占位\",\"amount\":\"300\",\"when\":\"\"}],\"segments\":[],\"pins\":[]},\"2026-10-05\":{\"notes\":\"1、琼库 --- 伊宁站 行程需要提前安排打车\\n2、伊宁 --- 乌鲁木齐\",\"lodging\":{\"name\":\"\",\"location\":\"\",\"pricePerNight\":0,\"notes\":\"\"},\"travelers\":[],\"expenses\":[{\"category\":\"餐饮\",\"item\":\"全天餐饮占位\",\"amount\":\"300\",\"when\":\"\"},{\"category\":\"其他\",\"item\":\"琼库骑马*2\",\"amount\":\"700\",\"when\":\"\"}],\"segments\":[{\"from\":\"琼库什台\",\"to\":\"伊宁站\",\"transport\":\"打车\",\"notes\":\"需提前安排\",\"departTime\":\"16:00\",\"time\":\"16:00-21:00\",\"arriveTime\":\"21:00\",\"price\":\"800\"},{\"from\":\"伊宁站\",\"to\":\"乌鲁木齐站\",\"transport\":\"高铁\",\"notes\":\"\",\"departTime\":\"23:50\",\"time\":\"23:50-06:24\",\"arriveTime\":\"06:24\",\"crossDays\":1,\"vehicleNo\":\"\",\"price\":\"\",\"passengers\":[],\"_snap\":{\"from\":\"伊宁站\",\"to\":\"乌鲁木齐站\",\"transport\":\"高铁\",\"notes\":\"\",\"departTime\":\"23:50\",\"time\":\"23:50-06:24\",\"arriveTime\":\"06:24\",\"crossDays\":1,\"vehicleNo\":\"\",\"price\":\"\",\"passengers\":[{\"name\":\"徐\",\"seat\":\"下铺\"},{\"name\":\"苗\",\"seat\":\"下铺\"}]}}],\"pins\":[]},\"2026-10-06\":{\"notes\":\"\",\"lodging\":{\"name\":\"\",\"location\":\"\",\"pricePerNight\":0,\"notes\":\"\"},\"travelers\":[],\"expenses\":[{\"category\":\"购物\",\"item\":\"乌鲁木齐特产购买\",\"amount\":\"500\",\"when\":\"\"},{\"category\":\"餐饮\",\"item\":\"全天餐饮占位\",\"amount\":\"300\",\"when\":\"\"}],\"segments\":[{\"from\":\"乌鲁木齐天山\",\"to\":\"北京\",\"transport\":\"飞机\",\"notes\":\"\",\"departTime\":\"19:05\",\"time\":\"19:05-23:00\",\"arriveTime\":\"23:00\",\"vehicleNo\":\"\",\"price\":\"5800\",\"passengers\":[{\"name\":\"韩梅梅\",\"seat\":\"\"},{\"name\":\"李雷\",\"seat\":\"\"}],\"_snap\":{\"from\":\"乌鲁木齐天山\",\"to\":\"天津滨海T2\",\"transport\":\"飞机\",\"notes\":\"无餐，已提前预约51J 51K，等待值机中\",\"departTime\":\"19:05\",\"time\":\"19:05-23:00\",\"arriveTime\":\"23:00\",\"vehicleNo\":\"新海航·GS7836\",\"price\":\"4738\",\"passengers\":[{\"name\":\"徐\",\"seat\":\"\"},{\"name\":\"苗\",\"seat\":\"\"}]}}],\"pins\":[]}},\"packing\":[{\"id\":\"pkmszh5iktu00ao\",\"category\":\"证件\",\"name\":\"二人身份证\",\"importance\":\"必需品\",\"status\":\"已携带\",\"notes\":\"\",\"bag\":\"bagmsze0mwa4e92l1\"},{\"id\":\"pkmszpemq9oxht8\",\"category\":\"证件\",\"name\":\"驾驶证\",\"importance\":\"必需品\",\"status\":\"已携带\",\"notes\":\"\",\"bag\":\"bagmsze0mwaact770\"},{\"id\":\"pkmszq31gnx6hro\",\"category\":\"电子设备\",\"name\":\"大疆pocket4\",\"importance\":\"必需品\",\"status\":\"已携带\",\"notes\":\"\",\"bag\":\"bagmsze0mwaact770\"},{\"id\":\"pkmszq3ead76vz5\",\"category\":\"电子设备\",\"name\":\"充电宝\",\"importance\":\"必需品\",\"status\":\"已携带\",\"notes\":\"\",\"bag\":\"bagmsze0mwa4e92l1\"},{\"id\":\"pkmszq3lp1jrsg1\",\"category\":\"药品\",\"name\":\"晕车药\",\"importance\":\"必需品\",\"status\":\"未携带\",\"notes\":\"\",\"bag\":null},{\"id\":\"pkmszq3slcr4m3w\",\"category\":\"药品\",\"name\":\"止泻药\",\"importance\":\"必需品\",\"status\":\"未携带\",\"notes\":\"\",\"bag\":null},{\"id\":\"pkmszq4e3f7rbpa\",\"category\":\"食品\",\"name\":\"巧克力\",\"importance\":\"必需品\",\"status\":\"已携带\",\"notes\":\"\",\"bag\":\"bagmsze0mwaact770\"},{\"id\":\"pkmszq4yth5wa2s\",\"category\":\"电子设备\",\"name\":\"手机充电器\",\"importance\":\"必需品\",\"status\":\"已携带\",\"notes\":\"\",\"bag\":\"bagmsze0mwa4e92l1\"},{\"id\":\"pkmszq5npxwwz2p\",\"category\":\"药品\",\"name\":\"颈椎贴\",\"importance\":\"必需品\",\"status\":\"未携带\",\"notes\":\"\",\"bag\":null},{\"id\":\"pkmszqa1vd3xkps\",\"category\":\"电子设备\",\"name\":\"耳机\",\"importance\":\"必需品\",\"status\":\"未携带\",\"notes\":\"\",\"bag\":null},{\"id\":\"pkmszqac2ezfswa\",\"category\":\"电子设备\",\"name\":\"工作笔记本\",\"importance\":\"必需品\",\"status\":\"未携带\",\"notes\":\"\",\"bag\":null}],\"packingOrder\":[\"pkmszh5iktu00ao\",\"pkmszpemq9oxht8\",\"pkmszq31gnx6hro\",\"pkmszq3ead76vz5\",\"pkmszq3lp1jrsg1\",\"pkmszq3slcr4m3w\",\"pkmszq4e3f7rbpa\",\"pkmszq4yth5wa2s\",\"pkmszq5npxwwz2p\",\"pkmszqa1vd3xkps\",\"pkmszqac2ezfswa\"],\"luggageBags\":[{\"id\":\"bagmsze0mwaact770\",\"name\":\"1号行李箱\",\"icon\":\"🧳\"},{\"id\":\"bagmsze0mwa4e92l1\",\"name\":\"2号手提袋\",\"icon\":\"👜\"},{\"id\":\"bagmsze0mwa31qe02\",\"name\":\"3号随身携带\",\"icon\":\"🎒\"}]}]}";

    private Store() {}

    static File dataFile(Context c) { return new File(c.getFilesDir(), FILE); }

    /** 读取 trips 数组；文件不存在时写入示例数据并返回 */
    static JSONArray load(Context c) {
        File f = dataFile(c);
        try {
            if (!f.exists()) {
                JSONArray seed = new JSONObject(SEED).getJSONArray("trips");
                save(c, seed);
                return seed;
            }
            JSONArray trips = new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8))
                    .getJSONArray("trips");
            // 内置行程迁移：旧版示例行程（上海 demo）有脏数据 → 移除并以新版内置示例补充（仅触发一次）
            boolean hasOldDemo = false;
            for (int i = 0; i < trips.length(); i++) {
                if ("demo-2026".equals(trips.optJSONObject(i).optString("id"))) { hasOldDemo = true; break; }
            }
            if (hasOldDemo) {
                JSONArray nt = new JSONArray();
                for (int i = 0; i < trips.length(); i++) {
                    JSONObject t = trips.optJSONObject(i);
                    if (t != null && !"demo-2026".equals(t.optString("id"))) nt.put(t);
                }
                JSONObject builtin = new JSONObject(SEED).getJSONArray("trips").optJSONObject(0);
                boolean has = false;
                for (int i = 0; i < nt.length(); i++) {
                    if (builtin.optString("id").equals(nt.optJSONObject(i).optString("id"))) { has = true; break; }
                }
                if (!has) nt.put(builtin);
                trips = nt;
                save(c, trips);
            }
            // 日期归一化：手工编辑/外部导入可能带来 "2026-9-30" 这类非补零值，会让天序号、周末、
            // PDF 全部算错 → 补零并写回（与网页版 loadData 的 normTrips 同源，自愈一次）
            boolean dateFixed = false;
            for (int i = 0; i < trips.length(); i++) {
                JSONObject t = trips.optJSONObject(i);
                if (t == null) continue;
                String s = t.optString("startDate"), e = t.optString("endDate");
                String ns = Util.normDate(s), ne = Util.normDate(e);
                if (!ns.equals(s)) { t.put("startDate", ns); dateFixed = true; }
                if (!ne.equals(e)) { t.put("endDate", ne); dateFixed = true; }
            }
            if (dateFixed) save(c, trips);
            // 名字一次性自愈：把「a、b」这种一条塞了多人的 出行人/乘客 拆开（与网页版 splitTripNames 同源）
            // 单独 try 包住：万一出问题也不能让整次 load 失败把行程读成空
            boolean nameFixed = false;
            try { nameFixed = splitNamesInTrips(trips); }
            catch (Exception ex) { Log.e(TAG, "split names failed", ex); }
            if (nameFixed) save(c, trips);
            return trips;
        } catch (Exception e) {
            Log.e(TAG, "load trips failed", e);
            return new JSONArray();
        }
    }

    // ===================== 名字一次性拆分（历史数据自愈） =====================

    /** 名字分隔符：顿号/半角逗号/全角逗号一视同仁（与网页版 splitNames 同口径）。
     *  显示时乘客是用「、」拼的，从显示里复制过来粘进输入框也要能正确拆开 */
    static String[] splitNames(String v) {
        if (v == null) return new String[0];
        String[] parts = v.trim().split("[、,，]");
        int n = 0;
        for (String p : parts) if (!p.trim().isEmpty()) n++;
        String[] out = new String[n];
        int i = 0;
        for (String p : parts) { String t = p.trim(); if (!t.isEmpty()) out[i++] = t; }
        return out;
    }

    /** 字符串数组字段（travelers）按分隔符拆开 + 去重保序；只有确实含分隔符才动 */
    private static boolean fixNameArray(JSONObject obj, String key) throws Exception {
        JSONArray arr = obj.optJSONArray(key);
        if (arr == null || arr.length() == 0) return false;
        boolean need = false;
        for (int i = 0; i < arr.length(); i++) {
            if (splitNames(arr.optString(i)).length > 1) { need = true; break; }
        }
        if (!need) return false;                       // 没分隔符 → 一个字节都不动
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (int i = 0; i < arr.length(); i++) {
            for (String s : splitNames(arr.optString(i))) out.add(s);
        }
        JSONArray na = new JSONArray();
        for (String s : out) na.put(s);
        obj.put(key, na);
        return true;
    }

    private static String paxName(Object o) {
        if (o instanceof JSONObject) return ((JSONObject) o).optString("name");
        return o == null ? "" : String.valueOf(o);
    }

    private static String paxSeat(Object o) {
        return o instanceof JSONObject ? ((JSONObject) o).optString("seat") : "";
    }

    /** 一次性自愈：把 出行人/乘客 里含分隔符的名字拆开。
     *  乘客带座位时座位归第一个拆出的名字（不丢信息）；返回是否有改动 */
    private static boolean splitNamesInTrips(JSONArray trips) throws Exception {
        boolean changed = false;
        for (int i = 0; i < trips.length(); i++) {
            JSONObject t = trips.optJSONObject(i);
            if (t == null) continue;
            if (fixNameArray(t, "travelers")) changed = true;
            JSONObject days = t.optJSONObject("days");
            if (days == null) continue;
            java.util.Iterator<String> it = days.keys();
            while (it.hasNext()) {
                JSONObject day = days.optJSONObject(it.next());
                if (day == null) continue;
                if (fixNameArray(day, "travelers")) changed = true;
                JSONArray segs = day.optJSONArray("segments");
                if (segs == null) continue;
                for (int j = 0; j < segs.length(); j++) {
                    JSONObject seg = segs.optJSONObject(j);
                    if (seg == null) continue;
                    JSONArray ps = seg.optJSONArray("passengers");
                    if (ps == null || ps.length() == 0) continue;
                    boolean need = false;
                    for (int k = 0; k < ps.length(); k++) {
                        if (splitNames(paxName(ps.opt(k))).length > 1) { need = true; break; }
                    }
                    if (!need) continue;
                    JSONArray out = new JSONArray();
                    java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
                    for (int k = 0; k < ps.length(); k++) {
                        Object o = ps.opt(k);
                        String seat = paxSeat(o);
                        String[] parts = splitNames(paxName(o));
                        for (int q = 0; q < parts.length; q++) {
                            if (!seen.add(parts[q])) continue;
                            JSONObject np = new JSONObject();
                            np.put("name", parts[q]);
                            np.put("seat", q == 0 ? seat : "");
                            out.put(np);
                        }
                    }
                    seg.put("passengers", out);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** 保存 { "trips": [...] }，原子写入（先写临时文件再改名） */
    static void save(Context c, JSONArray trips) {
        try {
            JSONObject root = new JSONObject();
            root.put("trips", trips);
            File f = dataFile(c);
            File tmp = new File(f.getParentFile(), FILE + ".tmp");
            try (OutputStream os = new FileOutputStream(tmp)) {
                os.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(f)) {
                // 改名失败（罕见）则直接覆盖
                try (OutputStream os = new FileOutputStream(f)) {
                    os.write(root.toString().getBytes(StandardCharsets.UTF_8));
                }
                tmp.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "save trips failed", e);
        }
    }
}