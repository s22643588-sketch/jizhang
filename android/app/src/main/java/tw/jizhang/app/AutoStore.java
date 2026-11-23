package tw.jizhang.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** 存放自動記帳的暫存紀錄與來源 App 設定（App 沒開時也能先記下來）。 */
public final class AutoStore {
    private static final String PREF = "auto";
    private static final String K_PENDING = "pending";
    private static final String K_SOURCES = "sources";
    private static final String K_RECENT = "recent";

    /** 聊天／簡訊類 App：常出現「付款」字眼但不是真的付款，預設不自動記。 */
    static final Set<String> MESSAGING = new HashSet<>(Arrays.asList(
            "jp.naver.line.android", "com.facebook.orca", "com.whatsapp", "org.telegram.messenger",
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
            "com.discord", "com.instagram.android", "com.tencent.mm", "com.google.android.gm"));

    private AutoStore() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static JSONObject obj(SharedPreferences p, String k) {
        try { return new JSONObject(p.getString(k, "{}")); } catch (JSONException e) { return new JSONObject(); }
    }

    private static JSONArray arr(SharedPreferences p, String k) {
        try { return new JSONArray(p.getString(k, "[]")); } catch (JSONException e) { return new JSONArray(); }
    }

    /**
     * 收到一則看起來是付款的通知。
     * @return true 代表已加入待記帳清單（來源已開啟且不是重複通知）
     */
    public static synchronized boolean onDetected(Context c, String pkg, String label, String text,
                                                  Parser.Result r, String key, long time) {
        SharedPreferences p = sp(c);
        JSONObject sources = obj(p, K_SOURCES);
        JSONObject recent = obj(p, K_RECENT);
        try {
            boolean msg = MESSAGING.contains(pkg);
            // LINE 裡的 LINE Pay 通知例外：標題或內容有 LINE Pay 才算
            boolean linePay = "jp.naver.line.android".equals(pkg) && text.contains("LINE Pay");
            JSONObject s = sources.optJSONObject(pkg);
            if (s == null) {
                s = new JSONObject();
                s.put("label", label);
                s.put("enabled", !msg || linePay);
                s.put("msg", msg);
                s.put("count", 0);
            }
            if (linePay && !s.optBoolean("lineChecked")) { s.put("enabled", true); s.put("lineChecked", true); }
            s.put("label", label);
            s.put("count", s.optInt("count") + 1);
            s.put("sample", text.length() > 140 ? text.substring(0, 140) + "…" : text);
            s.put("last", time);
            sources.put(pkg, s);

            boolean enabled = s.optBoolean("enabled");
            if (msg && "jp.naver.line.android".equals(pkg) && !linePay) enabled = false;

            // 去除重複：同一則通知更新、或同 App 同金額 3 分鐘內
            long now = System.currentTimeMillis();
            String k1 = "k:" + key + ":" + r.amount;
            String k2 = "a:" + pkg + ":" + r.amount + ":" + r.type;
            boolean dup = recent.has(k1) || (recent.has(k2) && now - recent.optLong(k2) < 180_000);
            recent.put(k1, now);
            recent.put(k2, now);
            pruneRecent(recent, now);

            boolean added = false;
            if (enabled && !dup) {
                JSONArray pending = arr(p, K_PENDING);
                JSONObject o = new JSONObject();
                o.put("pkg", pkg);
                o.put("label", label);
                o.put("amt", r.amount);
                o.put("type", r.type);
                o.put("merchant", r.merchant);
                o.put("text", text.length() > 300 ? text.substring(0, 300) : text);
                o.put("time", time);
                pending.put(o);
                p.edit().putString(K_PENDING, pending.toString()).apply();
                added = true;
            }
            p.edit().putString(K_SOURCES, sources.toString()).putString(K_RECENT, recent.toString()).apply();
            return added;
        } catch (JSONException e) {
            return false;
        }
    }

    private static void pruneRecent(JSONObject recent, long now) {
        Iterator<String> it = recent.keys();
        Set<String> drop = new HashSet<>();
        while (it.hasNext()) {
            String k = it.next();
            if (now - recent.optLong(k) > 86_400_000L) drop.add(k);
        }
        for (String k : drop) recent.remove(k);
    }

    /** 網頁端取走待記帳清單（取走後清空）。 */
    public static synchronized String drain(Context c) {
        SharedPreferences p = sp(c);
        String s = p.getString(K_PENDING, "[]");
        p.edit().putString(K_PENDING, "[]").commit();
        return s;
    }

    public static synchronized String sources(Context c) {
        JSONObject sources = obj(sp(c), K_SOURCES);
        JSONArray out = new JSONArray();
        Iterator<String> it = sources.keys();
        try {
            while (it.hasNext()) {
                String pkg = it.next();
                JSONObject s = sources.getJSONObject(pkg);
                JSONObject o = new JSONObject();
                o.put("pkg", pkg);
                o.put("label", s.optString("label", pkg));
                o.put("enabled", s.optBoolean("enabled"));
                o.put("msg", s.optBoolean("msg"));
                o.put("count", s.optInt("count"));
                o.put("sample", s.optString("sample"));
                o.put("last", s.optLong("last"));
                out.put(o);
            }
        } catch (JSONException ignored) { }
        return out.toString();
    }

    public static synchronized void setEnabled(Context c, String pkg, boolean on) {
        SharedPreferences p = sp(c);
        JSONObject sources = obj(p, K_SOURCES);
        JSONObject s = sources.optJSONObject(pkg);
        if (s == null) return;
        try {
            s.put("enabled", on);
            sources.put(pkg, s);
        } catch (JSONException ignored) { }
        p.edit().putString(K_SOURCES, sources.toString()).apply();
    }
}
