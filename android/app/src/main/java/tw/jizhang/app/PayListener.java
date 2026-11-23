package tw.jizhang.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.text.DecimalFormat;

/** 監聽付款通知，解析後放進 AutoStore，並跳一則「已自動記帳」通知。 */
public class PayListener extends NotificationListenerService {
    static final String CHANNEL = "auto";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            handle(sbn);
        } catch (Throwable ignored) {
            // 任何一則通知格式異常都不能讓服務掛掉
        }
    }

    private void handle(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null) return;
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        Bundle ex = n.extras;
        if (ex == null) return;

        String title = str(ex.getCharSequence(Notification.EXTRA_TITLE));
        CharSequence big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        String body = str(big != null ? big : ex.getCharSequence(Notification.EXTRA_TEXT));
        String sub = str(ex.getCharSequence(Notification.EXTRA_SUB_TEXT));
        String text = (title + " " + body + " " + sub).replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) return;

        Parser.Result r = Parser.parse(text);
        if (r == null) return;

        String label = appLabel(pkg);
        boolean added = AutoStore.onDetected(this, pkg, label, text, r, sbn.getKey(), sbn.getPostTime());
        if (added) {
            notifyRecorded(label, r);
            MainActivity.ping();
        }
    }

    private static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence l = pm.getApplicationLabel(ai);
            if (l != null && l.length() > 0) return l.toString();
        } catch (Exception ignored) { }
        return pkg;
    }

    private void notifyRecorded(String label, Parser.Result r) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        ensureChannel(nm);
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String amt = new DecimalFormat("#,##0.##").format(r.amount);
        String what = r.merchant.isEmpty() ? label : r.merchant;
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(("in".equals(r.type) ? "自動記帳 +$" : "自動記帳 −$") + amt)
                .setContentText(what + " · 點我檢查分類")
                .setContentIntent(pi)
                .setAutoCancel(true);
        nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), b.build());
    }

    static void ensureChannel(NotificationManager nm) {
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "自動記帳", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("偵測到付款並自動記帳時通知你");
            nm.createNotificationChannel(ch);
        }
    }
}
