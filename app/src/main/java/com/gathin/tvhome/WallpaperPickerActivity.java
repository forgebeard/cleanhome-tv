package com.gathin.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Remote-friendly gallery of the Home wallpapers: every picture in the theme's wallpapers/ folder is
 * a 16:9 tile with its own thumbnail, so the owner picks with their eyes instead of reading "01.jpg".
 * Rows are plain LinearLayouts of a computed tile size rather than a GridView: a GridView needs a
 * columnWidth to lay 3 tiles per row out, and without one it stacked every tile in one column.
 */
public final class WallpaperPickerActivity extends Activity {

    private static final int KIND_ROTATION = 0;
    private static final int KIND_PHOTO = 1;
    private static final int KIND_NONE = 2;
    private static final int THUMB_WIDTH = 480;
    private static final int COLUMNS = 3;
    private static final int SIDE_DP = 72;
    private static final int GAP_DP = 18;

    private static final class Tile {
        final int kind;
        final File file;
        final int index;
        Bitmap thumb;

        Tile(int kind, File file, int index, Bitmap thumb) {
            this.kind = kind;
            this.file = file;
            this.index = index;
            this.thumb = thumb;
        }
    }

    private final List<Tile> tiles = new ArrayList<>();
    private final List<View> tileViews = new ArrayList<>();
    /** Thumbnails decode off the UI thread: ten 1920x1080 JPEGs on the main thread made the picker open late. */
    private final ExecutorService thumbs = Executors.newFixedThreadPool(2);
    private SharedPreferences choices;
    private String currentChoice;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_wallpaper_picker);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        currentChoice = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        buildTiles();
        buildRows();
        focusCurrent();
    }

    private void buildTiles() {
        File dir = Config.dir(this);
        List<File> images = dir == null ? new ArrayList<>()
                : new ArrayList<>(WallpaperRotation.images(new File(dir, "wallpapers")));
        tiles.add(new Tile(KIND_ROTATION, null, 0, null));
        for (int i = 0; i < images.size(); i++) {
            File file = images.get(i);
            tiles.add(new Tile(KIND_PHOTO, file, i + 1, null));
        }
        tiles.add(new Tile(KIND_NONE, null, 0, null));
    }

    /** A small copy for the grid: decoding the full 1920x1080 pictures for every tile would cost too much. */
    private static Bitmap decode(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= THUMB_WIDTH) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable t) {
            return null;
        }
    }

    private void buildRows() {
        LinearLayout rows = findViewById(R.id.wallpaperRows);
        int screenDp = getResources().getConfiguration().screenWidthDp;
        if (screenDp <= 0) {
            screenDp = (int) (getResources().getDisplayMetrics().widthPixels
                    / getResources().getDisplayMetrics().density);
        }
        int contentDp = screenDp - SIDE_DP * 2;
        int tileWidthDp = (contentDp - GAP_DP * (COLUMNS - 1)) / COLUMNS;
        int tileHeightDp = tileWidthDp * 9 / 16;
        int tileWidth = dp(tileWidthDp);
        int tileHeight = dp(tileHeightDp);
        int gap = dp(GAP_DP);

        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = gap;
                rows.addView(row, rowParams);
            }
            Tile tile = tiles.get(i);
            View view = getLayoutInflater().inflate(R.layout.item_wallpaper, row, false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(tileWidth, tileHeight);
            if (i % COLUMNS != COLUMNS - 1) params.rightMargin = gap;
            row.addView(view, params);
            bind(view, tile);
            tileViews.add(view);
        }
    }

    private void bind(View view, final Tile tile) {
        ImageView image = view.findViewById(R.id.wallpaperImage);
        TextView label = view.findViewById(R.id.wallpaperLabel);
        TextView check = view.findViewById(R.id.wallpaperCheck);
        if (tile.kind == KIND_PHOTO) {
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (tile.thumb != null) {
                image.setBackground(null);
                image.setImageBitmap(tile.thumb);
            } else {
                image.setImageDrawable(null);
                image.setBackgroundColor(0xFF1A2430);
                loadThumb(tile, image);
            }
            label.setText(getString(R.string.wallpaper_picker_photo, tile.index));
        } else {
            image.setImageDrawable(null);
            image.setBackgroundResource(tile.kind == KIND_ROTATION
                    ? R.drawable.wallpaper_special_rotation : R.drawable.wallpaper_special_none);
            label.setText(tile.kind == KIND_ROTATION
                    ? R.string.wallpaper_picker_rotation : R.string.wallpaper_picker_none);
        }
        boolean current = isCurrent(tile);
        view.setSelected(current);
        check.setVisibility(current ? View.VISIBLE : View.GONE);
        view.setOnFocusChangeListener((v, focused) -> {
            float scale = focused ? 1.05f : 1f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
        });
        view.setOnClickListener(v -> apply(tile));
    }

    private boolean isCurrent(Tile tile) {
        if (tile.kind == KIND_ROTATION) return currentChoice == null;
        if (tile.kind == KIND_NONE) return "none".equals(currentChoice);
        return tile.file != null && tile.file.getName().equals(currentChoice);
    }

    private void focusCurrent() {
        int wanted = 0;
        for (int i = 0; i < tiles.size(); i++) {
            if (isCurrent(tiles.get(i))) wanted = i;
        }
        final View view = tileViews.get(wanted);
        ScrollView scroll = findViewById(R.id.wallpaperScroll);
        scroll.post(() -> view.requestFocus());
    }

    @Override
    protected void onDestroy() {
        thumbs.shutdownNow();
        super.onDestroy();
    }

    private void apply(Tile tile) {
        SharedPreferences.Editor editor = choices.edit();
        if (tile.kind == KIND_ROTATION) {
            editor.remove(Config.WALLPAPER_CHOICE_KEY);
        } else if (tile.kind == KIND_NONE) {
            editor.putString(Config.WALLPAPER_CHOICE_KEY, "none");
        } else {
            editor.putString(Config.WALLPAPER_CHOICE_KEY, tile.file.getName());
        }
        editor.putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1);
        editor.apply();
        setResult(RESULT_OK);
        Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
        finish();
    }

    /** One background decode per picture; the tile shows a placeholder until its thumbnail is ready. */
    private void loadThumb(final Tile tile, final ImageView image) {
        thumbs.execute(() -> {
            final Bitmap bitmap = decode(tile.file);
            if (bitmap == null) return;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    bitmap.recycle();
                    return;
                }
                tile.thumb = bitmap;
                image.setBackground(null);
                image.setImageBitmap(bitmap);
            });
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
