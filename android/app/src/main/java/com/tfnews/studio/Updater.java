package com.tfnews.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks GitHub Releases for a newer APK, notifies, downloads and opens the installer. */
public class Updater {
    static final String API = "https://api.github.com/repos/taiful666/TF-NEWS-STUDIO/releases/latest";
    static final String CH = "updates";
    static final int NOTIF_ID = 7001;

    public static class Info {
        int code;
        String name, url, notes;
    }

    static long currentCode(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    static String currentName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Network call, run off the main thread. Returns null when there is no newer version. */
    static Info fetchNewer(Context c) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(API).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(20000);
        h.setRequestProperty("Accept", "application/vnd.github+json");
        h.setRequestProperty("User-Agent", "TF-News-Studio-App");
        InputStream in = h.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        h.disconnect();
        JSONObject j = new JSONObject(bo.toString("UTF-8"));
        String tag = j.optString("tag_name", "");
        Matcher m = Pattern.compile("(\\d+)$").matcher(tag);
        if (!m.find()) return null;
        int code = Integer.parseInt(m.group(1));
        if (code <= currentCode(c)) return null;
        JSONArray as = j.optJSONArray("assets");
        String url = null;
        if (as != null) {
            for (int i = 0; i < as.length(); i++) {
                JSONObject a = as.getJSONObject(i);
                if (a.optString("name").endsWith(".apk")) {
                    url = a.optString("browser_download_url");
                    break;
                }
            }
        }
        if (url == null) return null;
        Info inf = new Info();
        inf.code = code;
        inf.name = "1.0." + code;
        inf.url = url;
        inf.notes = j.optString("body", "");
        return inf;
    }

    static void channel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CH) == null) {
            NotificationChannel ch = new NotificationChannel(CH, "অ্যাপ আপডেট", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("নতুন সংস্করণ এলে জানাবে");
            nm.createNotificationChannel(ch);
        }
    }

    /** Shows a system notification once per new version. */
    static void notifyUpdate(Context c, Info inf) {
        SharedPreferences sp = c.getSharedPreferences("upd", Context.MODE_PRIVATE);
        if (sp.getInt("notified", 0) >= inf.code) return;
        channel(c);
        Intent open = new Intent(c, MainActivity.class);
        open.putExtra("doUpdate", true);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification nt = new Notification.Builder(c, CH)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("TF News Studio — নতুন আপডেট")
                .setContentText("সংস্করণ " + inf.name + " এসেছে। আপডেট করতে চাপুন।")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try {
            c.getSystemService(NotificationManager.class).notify(NOTIF_ID, nt);
            sp.edit().putInt("notified", inf.code).apply();
        } catch (SecurityException ignored) {
        }
    }

    /** Called from the app: check, and ask the user. manual=true also says "already latest". */
    static void checkInApp(final Activity a, final boolean manual, final boolean autoStart) {
        new Thread(() -> {
            Info inf = null;
            String err = null;
            try {
                inf = fetchNewer(a);
            } catch (Exception e) {
                err = e.getMessage();
            }
            final Info f = inf;
            final String fe = err;
            a.runOnUiThread(() -> {
                if (a.isFinishing()) return;
                if (f == null) {
                    if (manual) Toast.makeText(a, fe != null ? "আপডেট দেখা যায়নি — ইন্টারনেট দেখুন" : "আপনি সর্বশেষ সংস্করণ (" + currentName(a) + ") ব্যবহার করছেন", Toast.LENGTH_LONG).show();
                    return;
                }
                notifyUpdate(a, f);
                if (autoStart) {
                    download(a, f);
                    return;
                }
                new AlertDialog.Builder(a)
                        .setTitle("নতুন আপডেট এসেছে")
                        .setMessage("সংস্করণ " + f.name + " পাওয়া যাচ্ছে (এখন আছে " + currentName(a) + ")।\n\nআপনার সব কাজ, ছবি আর সেটিংস থেকে যাবে।")
                        .setPositiveButton("এখনই আপডেট করুন", (d, w) -> download(a, f))
                        .setNegativeButton("পরে", null)
                        .show();
            });
        }).start();
    }

    static File pendingApk;

    static void download(final Activity a, final Info inf) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (24 * a.getResources().getDisplayMetrics().density);
        box.setPadding(p, p / 2, p, 0);
        final TextView tv = new TextView(a);
        tv.setText("ডাউনলোড হচ্ছে… ০%");
        final ProgressBar pb = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        box.addView(tv);
        box.addView(pb);
        final AlertDialog dlg = new AlertDialog.Builder(a).setTitle("আপডেট " + inf.name).setView(box).setCancelable(false).show();
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                File dir = new File(a.getCacheDir(), "updates");
                dir.mkdirs();
                File out = new File(dir, "TF-News-Studio-" + inf.code + ".apk");
                String u = inf.url;
                HttpURLConnection h = null;
                for (int hop = 0; hop < 6; hop++) {
                    h = (HttpURLConnection) new URL(u).openConnection();
                    h.setInstanceFollowRedirects(false);
                    h.setConnectTimeout(20000);
                    h.setReadTimeout(30000);
                    h.setRequestProperty("User-Agent", "TF-News-Studio-App");
                    int code = h.getResponseCode();
                    if (code >= 300 && code < 400 && h.getHeaderField("Location") != null) {
                        u = new URL(new URL(u), h.getHeaderField("Location")).toString();
                        h.disconnect();
                        continue;
                    }
                    break;
                }
                long total = h.getContentLengthLong();
                InputStream in = h.getInputStream();
                FileOutputStream fo = new FileOutputStream(out);
                byte[] buf = new byte[65536];
                long got = 0;
                int n, last = -1;
                while ((n = in.read(buf)) > 0) {
                    fo.write(buf, 0, n);
                    got += n;
                    if (total > 0) {
                        final int pct = (int) (got * 100 / total);
                        if (pct != last) {
                            last = pct;
                            ui.post(() -> {
                                pb.setProgress(pct);
                                tv.setText("ডাউনলোড হচ্ছে… " + toBn(pct) + "%");
                            });
                        }
                    }
                }
                fo.close();
                in.close();
                h.disconnect();
                ui.post(() -> {
                    dlg.dismiss();
                    install(a, out);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    dlg.dismiss();
                    Toast.makeText(a, "ডাউনলোড হয়নি: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    static void install(Activity a, File apk) {
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            pendingApk = apk;
            new AlertDialog.Builder(a)
                    .setTitle("একবার অনুমতি দিন")
                    .setMessage("আপডেট বসানোর জন্য পরের পাতায় \"Allow from this source / এই উৎস থেকে অনুমতি দিন\" চালু করুন, তারপর ফিরে আসুন। এটা শুধু একবারই লাগবে।")
                    .setPositiveButton("ঠিক আছে", (d, w) -> {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
                        } catch (Exception e) {
                            a.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                        }
                    })
                    .setNegativeButton("বাতিল", null)
                    .show();
            return;
        }
        pendingApk = null;
        Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".files", apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        a.startActivity(i);
    }

    /** After the user returns from the "allow install" settings page. */
    static void resumePending(Activity a) {
        if (pendingApk != null && pendingApk.exists() && a.getPackageManager().canRequestPackageInstalls()) {
            install(a, pendingApk);
        }
    }

    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == 42) return;
        JobInfo ji = new JobInfo.Builder(42, new ComponentName(c, UpdateJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(6L * 60 * 60 * 1000)
                .setPersisted(true)
                .build();
        js.schedule(ji);
    }

    static String toBn(int v) {
        String s = String.valueOf(v);
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(ch >= '0' && ch <= '9' ? (char) ('০' + (ch - '0')) : ch);
        return b.toString();
    }
}
