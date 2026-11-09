package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cold start used to be one thread doing everything in a row. This is the part that can run beside the main thread:
 * two threads load each TV app's name and banner (about 9 ms of PNG decoding per tile, a hundred ms in all) and
 * build the fonts the layout asks for. The first tile build takes what is ready; whatever is not, it loads itself,
 * exactly as before.
 */
final class StartupWarmup {
    private static final int[] FONTS = {R.string.font_body, R.string.font_regular, R.string.font_light,
            R.string.font_extralight, R.string.font_medium, R.string.font_display};

    /** One app's name and banner, loaded off the main thread. A null banner means the app has none worth showing. */
    static final class Warm {
        final String label;
        final Drawable banner;

        Warm(String label, Drawable banner) {
            this.label = label;
            this.banner = banner;
        }
    }

    private static final ConcurrentHashMap<String, Warm> WARM = new ConcurrentHashMap<>();
    private static volatile boolean released = true;

    private StartupWarmup() {}

    static void start(Context context) {
        Context app = context.getApplicationContext();
        WARM.clear();
        released = false;
        // Each preferences file reads itself from disk on its own thread once asked for; ask now, not when it is needed.
        app.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        app.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> new Thread(runnable, "home-warmup"));
        pool.execute(() -> fonts(app));
        pool.execute(() -> {
            try {
                PackageManager pm = app.getPackageManager();
                List<ResolveInfo> apps = pm.queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
                for (ResolveInfo ri : apps) {
                    if (ri.activityInfo == null || ri.activityInfo.packageName.equals(app.getPackageName())) continue;
                    pool.execute(() -> load(app, pm, ri));
                }
            } catch (Throwable ignored) {
                // the main thread builds the same tiles itself
            } finally {
                pool.shutdown();
            }
        });
    }

    /** What the warm-up loaded for this app, if it got there first. Each entry is handed out once. */
    static Warm take(ResolveInfo ri) {
        return WARM.remove(key(ri));
    }

    /** Called after Home's first tile build: the adapter holds the artwork now. */
    static void release() {
        released = true;
        WARM.clear();
    }

    private static String key(ResolveInfo ri) {
        return ri.activityInfo.packageName + "/" + ri.activityInfo.name;
    }

    private static void fonts(Context app) {
        try {
            for (int font : FONTS) UiFont.get(app, font);
        } catch (Throwable ignored) {
            // the layout builds them itself
        }
    }

    private static void load(Context app, PackageManager pm, ResolveInfo ri) {
        try {
            String label = String.valueOf(ri.loadLabel(pm));
            Drawable banner = AppAdapter.loadBanner(app, pm, ri);
            if (!released) WARM.put(key(ri), new Warm(label, banner));
        } catch (Throwable ignored) {
            // the main thread loads this one itself
        }
    }
}
