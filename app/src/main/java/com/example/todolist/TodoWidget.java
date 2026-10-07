package com.example.todolist;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Paint;
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
        if ("WIDGET_ITEM".equals(i.getAction()) && i.getBooleanExtra("open", false)) {
            c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        if ("COMPLETE".equals(i.getAction()) || "WIDGET_ITEM".equals(i.getAction())) {
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
        DayRefreshReceiver.schedule(c);
        // DAO order already places unfinished items first; completed items remain visible at the bottom.
        List<Task> visible = visibleTasks(TodoDb.get(c).tasks().all());
        for (int id : ids) {
            m.updateAppWidget(id, layout(c, visible, id));
            if (Build.VERSION.SDK_INT < 31) m.notifyAppWidgetViewDataChanged(id, R.id.widget_rows);
        }
    }
    static List<Task> visibleTasks(List<Task> all) {
        List<Task> visible = new ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (Task task : all) if (TaskDates.visibleInTodayList(task, today)) visible.add(task);
        visible.sort(Comparator
            .comparing((Task task) -> task.completedAt != null)
            .thenComparing((Task task) -> task.priority, Comparator.reverseOrder())
            .thenComparingLong(task -> task.createdAt)
            .thenComparingLong(task -> task.id));
        return visible;
    }
    // Compatibility entry point for previews: height no longer limits the dataset.
    static RemoteViews layout(Context c, List<Task> visible, float height) {
        return layout(c, visible, AppWidgetManager.INVALID_APPWIDGET_ID);
    }
    private static RemoteViews layout(Context c, List<Task> visible, int widgetId) {
            PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
            v.setOnClickPendingIntent(R.id.widget_title, open);
            v.setOnClickPendingIntent(R.id.widget_footer, open);
            Intent template = new Intent(c, TodoWidget.class).setAction("WIDGET_ITEM").setData(Uri.parse("todo://widget/" + widgetId));
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            v.setPendingIntentTemplate(R.id.widget_rows, PendingIntent.getBroadcast(c, widgetId, template, flags));
            if (Build.VERSION.SDK_INT >= 31) {
                RemoteViews.RemoteCollectionItems.Builder items = new RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1);
                for (Task task : visible) items.addItem(task.id, item(c, task));
                v.setRemoteAdapter(R.id.widget_rows, items.build());
            } else {
                Intent service = new Intent(c, WidgetListService.class).setData(Uri.parse("todo://widget-list/" + widgetId));
                v.setRemoteAdapter(R.id.widget_rows, service);
            }
            int unfinished = 0;
            for (Task task : visible) if (task.completedAt == null) unfinished++;
            v.setTextViewText(R.id.widget_footer, visible.isEmpty() ? "今天很轻松，享受吧！" : "共 " + visible.size() + " 件 · 已完成 " + (visible.size() - unfinished));
            return v;
    }
    static RemoteViews item(Context c, Task t) {
                RemoteViews row = new RemoteViews(c.getPackageName(), R.layout.widget_row);
                row.setTextViewText(R.id.widget_text, t.title);
                boolean completed = t.completedAt != null;
                if (completed) {
                    row.setTextViewText(R.id.widget_check, "✓");
                    row.setTextColor(R.id.widget_check, 0xFF60736B);
                    row.setInt(R.id.widget_check, "setBackgroundResource", R.drawable.widget_check_completed);
                    row.setInt(R.id.widget_text, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG);
                    row.setContentDescription(R.id.widget_check, "恢复待办：" + t.title);
                } else {
                    row.setTextViewText(R.id.widget_check, "");
                    int checkBackground = t.priority >= 2 ? R.drawable.widget_check_high : t.priority == 1 ? R.drawable.widget_check_medium : R.drawable.widget_check_low;
                    int checkColor = t.priority >= 2 ? 0xFFE05252 : t.priority == 1 ? 0xFFE39A3B : 0xFF4B86C5;
                    row.setTextColor(R.id.widget_check, checkColor);
                    row.setInt(R.id.widget_check, "setBackgroundResource", checkBackground);
                    row.setContentDescription(R.id.widget_check, "完成：" + t.title);
                }
                row.setOnClickFillInIntent(R.id.widget_text, new Intent().putExtra("open", true));
                row.setOnClickFillInIntent(R.id.widget_check, new Intent().putExtra("id", t.id));
                return row;
    }
}
