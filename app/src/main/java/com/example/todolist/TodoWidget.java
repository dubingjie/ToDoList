package com.example.todolist;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Paint;
import android.util.SizeF;
import android.widget.RemoteViews;
import java.util.*;

public class TodoWidget extends AppWidgetProvider {
    public static final Class<?>[] PROVIDERS = {TodoWidget.class, NarrowMediumWidget.class, NarrowTallWidget.class, WideSmallWidget.class, WideMediumWidget.class, WideTallWidget.class};
    public static final String[] SIZE_LABELS = {"窄版 · 矮款（2 × 2）", "窄版 · 中高（2 × 3）", "窄版 · 加高（2 × 4）", "宽版 · 矮款（4 × 2）", "宽版 · 中高（4 × 3）", "宽版 · 加高（4 × 4）"};
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c); }
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle b) { refresh(c); }
    private void refresh(Context c) {
        PendingResult result = goAsync();
        TodoDb.IO.execute(() -> { try { updateAll(c); } finally { result.finish(); } });
    }
    @Override public void onReceive(Context c, Intent i) {
        if ("COMPLETE".equals(i.getAction())) {
            PendingResult result = goAsync();
            TodoDb.IO.execute(() -> {
                try {
                    Task t = TodoDb.get(c).tasks().find(i.getLongExtra("id", -1));
                    if (t != null && t.deletedAt == null) {
                        if (t.completedAt == null) {
                            t.completedAt = System.currentTimeMillis(); Reminders.cancel(c, t);
                        } else {
                            t.completedAt = null; Reminders.schedule(c, t);
                        }
                        TodoDb.get(c).tasks().save(t);
                    }
                    updateAll(c);
                    c.sendBroadcast(new Intent("com.example.todolist.TASKS_CHANGED").setPackage(c.getPackageName()));
                } finally { result.finish(); }
            });
        } else super.onReceive(c, i);
    }
    // Called on the database executor only; RemoteViews never reads the database on the UI thread.
    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        List<Integer> ids = new ArrayList<>();
        for (Class<?> provider : PROVIDERS) for (int id : m.getAppWidgetIds(new ComponentName(c, provider))) ids.add(id);
        if (ids.isEmpty()) return;
        // DAO order already places unfinished items first; completed items remain visible at the bottom.
        List<Task> all = TodoDb.get(c).tasks().all();
        List<Task> visible = new ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (Task task : all) if (TaskDates.visibleInTodayList(task, today)) visible.add(task);
        for (int id : ids) {
            Bundle options = m.getAppWidgetOptions(id);
            if (Build.VERSION.SDK_INT >= 31) {
                ArrayList<SizeF> sizes = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
                if (sizes != null && !sizes.isEmpty()) {
                    Map<SizeF, RemoteViews> layouts = new LinkedHashMap<>();
                    for (SizeF size : sizes) {
                        if (size.getWidth() > 0 && size.getHeight() > 0) layouts.put(size, layout(c, visible, size.getHeight()));
                        if (layouts.size() == 16) break;
                    }
                    if (!layouts.isEmpty()) { m.updateAppWidget(id, new RemoteViews(layouts)); continue; }
                }
            }
            int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160);
            int maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight);
            m.updateAppWidget(id, new RemoteViews(layout(c, visible, minHeight), layout(c, visible, maxHeight)));
        }
    }
    static RemoteViews layout(Context c, List<Task> visible, float height) {
            PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
            v.setOnClickPendingIntent(R.id.widget_title, open);
            v.setOnClickPendingIntent(R.id.widget_footer, open);
            v.removeAllViews(R.id.widget_rows);
            // Match the XML's sp dimensions, including Android's large-font conversion.
            android.util.DisplayMetrics metrics = c.getResources().getDisplayMetrics();
            float rowHeight = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 32, metrics) / metrics.density;
            float header = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 24, metrics) / metrics.density;
            float footer = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 18, metrics) / metrics.density;
            int limit = Math.max(0, (int) ((height - 20 - header - footer) / rowHeight));
            int shown = Math.min(limit, visible.size());
            int completedShown = 0;
            for (int x = 0; x < shown; x++) {
                Task t = visible.get(x);
                RemoteViews row = new RemoteViews(c.getPackageName(), R.layout.widget_row);
                row.setTextViewText(R.id.widget_text, t.title);
                boolean completed = t.completedAt != null;
                if (completed) {
                    completedShown++;
                    row.setTextViewText(R.id.widget_check, "✓");
                    row.setInt(R.id.widget_text, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG);
                    row.setContentDescription(R.id.widget_check, "恢复待办：" + t.title);
                } else {
                    row.setTextViewText(R.id.widget_check, "□");
                    row.setContentDescription(R.id.widget_check, "完成：" + t.title);
                }
                row.setOnClickPendingIntent(R.id.widget_text, open);
                Intent click = new Intent(c, TodoWidget.class).setAction("COMPLETE").setData(Uri.parse("todo://complete/" + t.id)).putExtra("id", t.id);
                row.setOnClickPendingIntent(R.id.widget_check, PendingIntent.getBroadcast(c, 0, click, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                v.addView(R.id.widget_rows, row);
            }
            int unfinished = 0;
            for (Task task : visible) if (task.completedAt == null) unfinished++;
            v.setTextViewText(R.id.widget_footer, visible.isEmpty() ? "今天很轻松，享受吧！" : "显示 " + shown + "/" + visible.size() + " 件 · 已完成 " + (visible.size() - unfinished));
            return v;
    }
}
