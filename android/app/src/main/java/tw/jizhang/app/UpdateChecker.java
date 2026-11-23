package tw.jizhang.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 到 GitHub Releases 檢查新版，下載 APK 後交給系統安裝。資料不會受影響。 */
public final class UpdateChecker {
    static final String REPO = "s22643588-sketch/jizhang";
    private static final long AUTO_INTERVAL = 6L * 3600 * 1000;
    private static volatile boolean running = false;

    private UpdateChecker() {}

    public static void check(Activity a, boolean manual) {
        SharedPreferences p = a.getSharedPreferences("update", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!manual && now - p.getLong("lastCheck", 0) < AUTO_INTERVAL) return;
        if (running) return;
        running = true;
        p.edit().putLong("lastCheck", now).apply();
        if (manual) toast(a, "檢查更新中…");

        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "jizhang-app");
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                if (c.getResponseCode() != 200) {
                    if (manual) toast(a, "目前沒有可用的更新");
                    return;
                }
                String body;
                try (InputStream in = c.getInputStream()) {
                    body = new String(Bridge.readAll(in), StandardCharsets.UTF_8);
                }
                JSONObject j = new JSONObject(body);
                int latest = parseCode(j.optString("tag_name"));
                long current = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).getLongVersionCode();
                String apk = null;
                JSONArray assets = j.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject as = assets.getJSONObject(i);
                        if (as.optString("name").endsWith(".apk")) { apk = as.optString("browser_download_url"); break; }
                    }
                }
                if (latest > current && apk != null) {
                    final String url = apk;
                    final String notes = j.optString("body", "");
                    a.runOnUiThread(() -> ask(a, latest, notes, url));
                } else if (manual) {
                    toast(a, "已經是最新版本");
                }
            } catch (Exception e) {
                if (manual) toast(a, "無法連線檢查更新，請確認網路");
            } finally {
                running = false;
            }
        }).start();
    }

    private static int parseCode(String tag) {
        try { return Integer.parseInt(tag.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
    }

    private static void ask(Activity a, int code, String notes, String url) {
        if (a.isFinishing()) return;
        String msg = "新版本 1.0." + code + " 已推出。\n記帳資料會保留，不會被清掉。";
        if (notes != null && !notes.trim().isEmpty()) msg += "\n\n更新內容：\n" + notes.trim();
        new AlertDialog.Builder(a)
                .setTitle("有新版本")
                .setMessage(msg)
                .setPositiveButton("立即更新", (d, w) -> download(a, code, url))
                .setNegativeButton("稍後", null)
                .show();
    }

    private static void download(Activity a, int code, String url) {
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a)
                    .setTitle("需要允許安裝更新")
                    .setMessage("請在下一個畫面開啟「允許來自這個來源的應用程式」，返回後再按一次「檢查更新」。")
                    .setPositiveButton("前往設定", (d, w) -> {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + a.getPackageName())));
                        } catch (Exception e) { openBrowser(a, url); }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        try {
            DownloadManager dm = (DownloadManager) a.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url))
                    .setTitle("記帳小夥伴 更新")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(a, Environment.DIRECTORY_DOWNLOADS, "jizhang-" + code + ".apk");
            final long id = dm.enqueue(r);
            toast(a, "下載更新中…");
            BroadcastReceiver rc = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return;
                    try { ctx.unregisterReceiver(this); } catch (Exception ignored) { }
                    Uri uri = dm.getUriForDownloadedFile(id);
                    if (uri == null) { openBrowser(a, url); return; }
                    Intent i = new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { ctx.startActivity(i); } catch (Exception e) { openBrowser(a, url); }
                }
            };
            IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) a.getApplicationContext().registerReceiver(rc, f, Context.RECEIVER_EXPORTED);
            else a.getApplicationContext().registerReceiver(rc, f);
        } catch (Exception e) {
            openBrowser(a, url);
        }
    }

    private static void openBrowser(Activity a, String url) {
        try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
    }

    private static void toast(Activity a, String s) {
        a.runOnUiThread(() -> Toast.makeText(a, s, Toast.LENGTH_SHORT).show());
    }
}
