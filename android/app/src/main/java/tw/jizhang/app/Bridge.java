package tw.jizhang.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** 網頁（window.Android）可以呼叫的原生功能。 */
public class Bridge {
    private final MainActivity a;

    Bridge(MainActivity a) { this.a = a; }

    // ---------- 資料鏡像：每次存檔都寫一份到 App 私有檔案，localStorage 遺失時可還原 ----------
    private File dataFile() { return new File(a.getFilesDir(), "data.json"); }

    @JavascriptInterface
    public synchronized String loadData() {
        File f = dataFile();
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    @JavascriptInterface
    public synchronized boolean saveData(String json) {
        if (json == null || json.isEmpty()) return false;
        File tmp = new File(a.getFilesDir(), "data.json.tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            return false;
        }
        File f = dataFile();
        // 保留上一版，萬一寫壞還有一份
        if (f.exists()) {
            File bak = new File(a.getFilesDir(), "data.prev.json");
            bak.delete();
            f.renameTo(bak);
        }
        return tmp.renameTo(f);
    }

    // ---------- 自動記帳 ----------
    @JavascriptInterface
    public String pullPending() { return AutoStore.drain(a); }

    @JavascriptInterface
    public String getSources() { return AutoStore.sources(a); }

    @JavascriptInterface
    public void setSourceEnabled(String pkg, boolean on) { AutoStore.setEnabled(a, pkg, on); }

    @JavascriptInterface
    public boolean hasNotifAccess() {
        String flat = Settings.Secure.getString(a.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(a.getPackageName());
    }

    @JavascriptInterface
    public void openNotifSettings() {
        a.runOnUiThread(() -> safeStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
    }

    @JavascriptInterface
    public void openAppInfo() {
        a.runOnUiThread(() -> safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + a.getPackageName()))));
    }

    @JavascriptInterface
    public void openBatterySettings() {
        a.runOnUiThread(() -> {
            if (!safeStart(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
                safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + a.getPackageName())));
            }
        });
    }

    // ---------- 更新 ----------
    @JavascriptInterface
    public String version() {
        try {
            PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public void checkUpdate() { UpdateChecker.check(a, true); }

    // ---------- 匯出檔案到「下載」資料夾 ----------
    @JavascriptInterface
    public boolean saveFile(String name, String content, String mime) {
        try {
            ContentResolver cr = a.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime == null ? "application/octet-stream" : mime.split(";")[0]);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return false;
            try (OutputStream out = cr.openOutputStream(uri)) {
                if (out == null) return false;
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean safeStart(Intent i) {
        try {
            a.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }
}
