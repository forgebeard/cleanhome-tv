package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Remote-friendly settings owned by the launcher. Cards per setting, segment pills for small choices,
 * and a live miniature of Home on the right so every change is visible before leaving the screen.
 */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private static final int[] PREVIEW_COLORS = {
            0xFFFFB04A, 0xFFFF5C5C, 0xFF4B8DFF, 0xFFA78BFA, 0xFF38E0C4};
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    private TextView wallpaperChoice;
    private ImageView wallpaperThumb;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeValue;
    private TextView openOnStartValue;
    private View advancedRow;
    /** Preview caches: decoding the wallpaper again on every key press was the only real cost of this screen. */
    private File previewFile;
    private Bitmap previewBitmap;
    private LinearLayout previewStack;
    private LinearLayout previewDock;
    private int previewColumns = -1;
    private int previewRows = -1;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderRow = findViewById(R.id.reorderRow);
        reorderRow.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        // No empty caption: with both rows off the whole "Apps" group has nothing to show.
        findViewById(R.id.appsSection).setVisibility(
                config.launcherSettingsButton || config.openOnStartChoice ? View.VISIBLE : View.GONE);
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
            reverseRow(clockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperThumb = findViewById(R.id.wallpaperThumb);
        findViewById(R.id.wallpaperRow).setOnClickListener(v ->
                open(new Intent(this, WallpaperPickerActivity.class)));
        animationsChoice = findViewById(R.id.animationsChoice);
        findViewById(R.id.animationsRow).setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedRow = findViewById(R.id.advancedRow);
        advancedRow.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartValue = findViewById(R.id.openOnStartValue);
        if (config.openOnStartChoice) {
            findViewById(R.id.openOnStartRow).setVisibility(View.VISIBLE);
            findViewById(R.id.openOnStartRow).setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeValue = findViewById(R.id.followThemeValue);
        findViewById(R.id.followThemeRow).setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(Config.CLOCK_FORMAT_KEY).remove(OpenOnStart.KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            clockFormat = current.clockFormat;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.reorderRow).setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        findViewById(R.id.wifiNameRow).setOnClickListener(v -> requestPermissions(new String[]{
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION}, 4411));
        findViewById(R.id.defaultHomeRow).setOnClickListener(v ->
                open(new Intent(this, HomeSettingsActivity.class)));
        findViewById(R.id.tvSettingsRow).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        findViewById(R.id.doneButton).setOnClickListener(v -> finish());
        refresh();
        if (config.launcherSettingsButton) reorderRow.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        animationsChoice.setText(animations ? R.string.on : R.string.off);
        animationsChoice.setSelected(animations);
        advancedChoice.setText(advancedEffects ? R.string.on : R.string.off);
        advancedChoice.setSelected(advancedEffects);
        advancedRow.setEnabled(animations);
        advancedRow.setAlpha(animations ? 1f : 0.55f);
        wallpaperChoice.setText(wallpaperState());
        boolean needsLocation = getPackageManager().checkPermission(
                android.Manifest.permission.ACCESS_FINE_LOCATION, getPackageName())
                != PackageManager.PERMISSION_GRANTED;
        findViewById(R.id.wifiNameRow).setVisibility(needsLocation ? View.VISIBLE : View.GONE);
        openOnStartValue.setText(openOnStartLabel());
        boolean onTvChoices = hasOnTvChoices();
        followThemeValue.setText(onTvChoices
                ? R.string.settings_follow_theme_reset : R.string.settings_follow_theme_current);
        findViewById(R.id.followThemeRow).setAlpha(onTvChoices ? 1f : 0.6f);
        refreshPreview();
    }

    /** "Wallpaper: picture 3 of 10", "all pictures, rotating" or "off", for the settings row. */
    private CharSequence wallpaperState() {
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        if (current == null) return getString(R.string.wallpaper_state_rotation);
        if ("none".equals(current)) return getString(R.string.wallpaper_state_none);
        File dir = Config.dir(this);
        List<File> images = dir == null ? new ArrayList<>()
                : new ArrayList<>(WallpaperRotation.images(new File(dir, "wallpapers")));
        int index = indexOf(images, current) + 1;
        return index <= 0 ? getString(R.string.wallpaper_state, current)
                : getString(R.string.wallpaper_state_picture, index, images.size());
    }

    /** The picture Home shows now: the chosen file, or the first one while they rotate. */
    private File currentWallpaperFile() {
        File dir = Config.dir(this);
        if (dir == null) return null;
        List<File> images = WallpaperRotation.images(new File(dir, "wallpapers"));
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        if (current != null && !"none".equals(current)) {
            for (File file : images) {
                if (file.getName().equals(current)) return file;
            }
        }
        if (!images.isEmpty()) return images.get(0);
        File single = new File(dir, Config.WALLPAPER_NAME);
        return single.isFile() ? single : null;
    }

    private static Bitmap decode(File file, int targetWidth) {
        if (file == null) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The miniature Home: wallpaper, the dock with its panel opacity, and the grid rows under it. */
    private void refreshPreview() {
        File file = currentWallpaperFile();
        boolean changed = file == null ? previewFile != null : !file.equals(previewFile);
        if (changed || previewBitmap == null) {
            previewFile = file;
            previewBitmap = decode(file, 640);
            wallpaperThumb.setImageBitmap(decode(file, 96));
            ImageView wallpaper = findViewById(R.id.previewWallpaper);
            if (previewBitmap != null) wallpaper.setImageBitmap(previewBitmap);
            else wallpaper.setImageDrawable(null);
        }
        FrameLayout screen = findViewById(R.id.previewScreen);
        if (previewStack == null || previewColumns != columns || previewRows != rowsBelowDock) {
            buildPreview(screen);
        } else {
            previewDock.setAlpha(0.35f + 0.65f * dockOpacity / 100f);
        }
    }

    /** Builds the miniature once per layout shape; column and row changes rebuild it, opacity does not. */
    private void buildPreview(FrameLayout screen) {
        screen.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        previewColumns = columns;
        previewRows = rowsBelowDock;

        TextView clock = new TextView(this);
        clock.setText(DateFormat.getTimeFormat(this).format(new Date()));
        clock.setTextSize(22);
        clock.setTextColor(0xFFF4F8FB);
        FrameLayout.LayoutParams clockParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clockParams.gravity = Gravity.TOP | Gravity.START;
        clockParams.leftMargin = (int) (14 * d);
        clockParams.topMargin = (int) (12 * d);
        screen.addView(clock, clockParams);

        TextView date = new TextView(this);
        date.setText(new SimpleDateFormat("d MMMM", Locale.getDefault()).format(new Date()));
        date.setTextSize(9);
        date.setTextColor(0xB3FFFFFF);
        FrameLayout.LayoutParams dateParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateParams.gravity = Gravity.TOP | Gravity.START;
        dateParams.leftMargin = (int) (14 * d);
        dateParams.topMargin = (int) (40 * d);
        screen.addView(date, dateParams);

        previewStack = new LinearLayout(this);
        previewStack.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams stackParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stackParams.gravity = Gravity.BOTTOM;
        stackParams.leftMargin = (int) (10 * d);
        stackParams.rightMargin = (int) (10 * d);
        stackParams.bottomMargin = (int) (10 * d);
        screen.addView(previewStack, stackParams);

        for (int row = 0; row < rowsBelowDock; row++) {
            LinearLayout rowView = previewRow(d, 16);
            rowView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            previewStack.addView(rowView);
        }

        previewDock = new LinearLayout(this);
        previewDock.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setColor(0x33FFFFFF);
        dockBg.setCornerRadius(9 * d);
        previewDock.setBackground(dockBg);
        int pad = (int) (6 * d);
        previewDock.setPadding(pad, pad, pad, pad);
        previewDock.setAlpha(0.35f + 0.65f * dockOpacity / 100f);
        for (int i = 0; i < columns; i++) {
            previewDock.addView(previewTile(d, PREVIEW_COLORS[i % PREVIEW_COLORS.length], 20, 6));
        }
        previewDock.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        previewStack.addView(previewDock);
    }

    private LinearLayout previewRow(float d, int heightDp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < columns; i++) row.addView(previewTile(d, 0x26FFFFFF, heightDp, 4));
        return row;
    }

    private View previewTile(float d, int color, int heightDp, int radiusDp) {
        View tile = new View(this);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radiusDp * d);
        tile.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, (int) (heightDp * d), 1f);
        int margin = (int) (3 * d);
        params.setMargins(margin, margin, margin, margin);
        tile.setLayoutParams(params);
        return tile;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (choices != null) refresh();
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.CLOCK_FORMAT_KEY) || choices.contains(OpenOnStart.KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
