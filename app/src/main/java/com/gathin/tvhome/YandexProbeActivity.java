package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** On-TV diagnostic: try exported Yandex music/TV entry points without ADB or privileged access. */
public final class YandexProbeActivity extends Activity {
    private LinearLayout list;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 24, 28, 24);
        root.setBackgroundColor(Color.rgb(17, 21, 31));
        TextView title = line("Яндекс: проверка запуска", 24);
        root.addView(title);
        status = line("Только просмотр и запуск доступных экранов. Системные приложения не изменяются.", 15);
        root.addView(status);
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        discover();
    }

    private TextView line(String value, int size) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(Color.WHITE);
        v.setPadding(10, 12, 10, 12);
        return v;
    }

    private void discover() {
        PackageManager pm = getPackageManager();
        List<String> found = new ArrayList<>();
        try {
            for (PackageInfo pkg : pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)) {
                String packageName = pkg.packageName;
                String low = packageName.toLowerCase(Locale.ROOT);
                if (!(low.contains("yandex") || low.contains("yaos"))) continue;
                if (pkg.activities == null) continue;
                for (ActivityInfo activity : pkg.activities) {
                    if (!activity.exported || !activity.enabled) continue;
                    String name = activity.name.toLowerCase(Locale.ROOT);
                    if (!(name.contains("music") || name.contains("radio") || name.contains("live")
                            || name.contains("channel") || name.contains("player") || name.contains("tv"))) continue;
                    found.add(packageName + "/" + activity.name);
                }
            }
        } catch (Exception e) {
            status.setText("Не удалось прочитать список: " + e.getClass().getSimpleName());
            return;
        }
        Collections.sort(found);
        status.setText("Доступных кандидатов: " + found.size()
                + ". Выбери экран кнопкой OK. Если экран открылся — проверь кнопку Назад.");
        if (found.isEmpty()) {
            list.addView(line("Подходящих экспортируемых Activity не найдено. Это не доказывает, что запуск невозможен.", 17));
        }
        for (String candidate : found) {
            int slash = candidate.indexOf('/');
            String pkg = candidate.substring(0, slash);
            String cls = candidate.substring(slash + 1);
            TextView button = line(pkg + "\n" + cls, 15);
            button.setFocusable(true);
            button.setClickable(true);
            button.setBackgroundResource(android.R.drawable.btn_default);
            button.setTextColor(Color.BLACK);
            button.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setClassName(pkg, cls);
                    startActivity(intent);
                    status.setText("Отправлен запуск: " + candidate);
                } catch (Exception e) {
                    String message = "Не запустилось: " + e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : " — " + e.getMessage());
                    status.setText(message);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = 8;
            list.addView(button, lp);
        }
    }
}
