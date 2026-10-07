package com.example.todolist;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.room.Room;

/** Isolated storage migration and screen navigation checks for reviews. */
public class ReviewTestRunner extends Instrumentation {
    private int checks;
    private MainActivity activity;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            database(); monthCapacity(); widgetCapacity(); navigation(); widgetClickChecks(); taskEditing(); autosave();
            result.putString("stream", "\nPASS: " + checks + " review checks (migration, persistence, period isolation, navigation).\n"); finish(Activity.RESULT_OK, result);
        } catch (Throwable e) { result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(e)); finish(Activity.RESULT_CANCELED, result); }
    }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private void database() {
        Context context = getTargetContext(); String name = "review-feature-test.db";
        context.deleteDatabase(name);
        android.database.sqlite.SQLiteDatabase old = context.openOrCreateDatabase(name, 0, null);
        old.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT, tag TEXT, priority INTEGER NOT NULL, createdAt INTEGER NOT NULL, dueAt INTEGER, startAt INTEGER, taskDate INTEGER, completedAt INTEGER, deletedAt INTEGER)");
        old.execSQL("INSERT INTO tasks VALUES (1, 'preserved', 'work', 2, 1000, NULL, NULL, 1000, NULL, NULL)");
        old.setVersion(4); old.close();
        TodoDb db = Room.databaseBuilder(context, TodoDb.class, name).addMigrations(TodoDb.MIGRATION_4_5).build();
        try {
            check("preserved".equals(db.tasks().find(1).title), "Upgrade must preserve tasks");
            ReviewRecord record = new ReviewRecord(); record.id = "1:2026-09-28"; record.goals = "[{\"title\":\"practice\",\"state\":1}]"; record.answers = "[\"reflection\"]";
            db.reviews().save(record); db.close();
            db = Room.databaseBuilder(context, TodoDb.class, name).build();
            ReviewRecord saved = db.reviews().find(record.id);
            check(saved != null && saved.answers.equals(record.answers) && saved.goals.equals(record.goals), "Draft and goals must survive reopening");
            saved.finished = true; db.reviews().save(saved);
            check(db.reviews().find(record.id).finished, "Completion status must persist");
            check(db.reviews().find("0:2026-09-28") == null && db.reviews().find("1:2026-10-05") == null, "Period records must be independent");
            db.reviews().clearContent(record.id);
            ReviewRecord cleared = db.reviews().find(record.id);
            check(cleared.answers.equals("[]") && !cleared.finished && cleared.updatedAt == 0, "Deleting reflection clears content and status");
            check(cleared.goals.equals(record.goals) && db.tasks().find(1) != null, "Deleting reflection preserves goals and tasks");
        } finally { db.close(); context.deleteDatabase(name); }
    }
    private void monthCapacity() {
        runOnMainSync(() -> {
            java.time.YearMonth month = java.time.YearMonth.of(2026, 10);
            java.util.List<Task> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < 9; i++) {
                Task task = new Task(); task.title = "事项" + (i + 1);
                task.completedAt = month.atDay(i < 3 ? 7 : 8).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + i;
                tasks.add(task);
            }
            Context context = getTargetContext();
            MonthGrid grid = new MonthGrid(context, month, month.atDay(7), tasks, false, (d, detail) -> {});
            int width = UiStyle.dp(context, 392), height = UiStyle.dp(context, 500);
            grid.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY));
            grid.layout(0, 0, width, height);
            android.widget.LinearLayout day = (android.widget.LinearLayout) grid.getChildAt(6);
            int shown = 0; boolean overflow = false;
            for (int i = 1; i < day.getChildCount(); i++) if (day.getChildAt(i) instanceof android.widget.TextView) {
                String value = ((android.widget.TextView) day.getChildAt(i)).getText().toString();
                if (value.startsWith("事项")) shown++; if (value.startsWith("＋")) overflow = true;
            }
            check(shown == 3 && !overflow, "Three tasks fit a normal month cell without premature overflow");
            android.widget.LinearLayout busy = (android.widget.LinearLayout) grid.getChildAt(7);
            android.widget.TextView more = (android.widget.TextView) busy.getChildAt(busy.getChildCount() - 1);
            check(more.getText().toString().matches("＋[1-6]件") && more.getBottom() <= busy.getHeight(), "Actual overflow shows a clear count inside the cell");
            check(more.getWidth() > 0 && more.getHeight() > 0, "Overflow label must have visible dimensions: " + more.getWidth() + "x" + more.getHeight());
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
            grid.draw(new android.graphics.Canvas(bitmap));
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null), "month-capacity-check.png"))) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
            } catch (java.io.IOException e) { throw new RuntimeException(e); } finally { bitmap.recycle(); }
        });
    }
    private void widgetCapacity() {
        runOnMainSync(() -> {
            java.util.List<Task> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < 30; i++) { Task task = new Task(); task.id = i + 1; task.title = "小组件事项 " + (i + 1); task.priority = i % 3; tasks.add(task); }
            for (float scale : new float[]{1f, 1.5f, 2f}) {
                android.content.res.Configuration config = new android.content.res.Configuration(getTargetContext().getResources().getConfiguration());
                config.fontScale = scale; Context context = getTargetContext().createConfigurationContext(config);
                for (int height : new int[]{120, 160, 240, 360}) {
                    android.view.View widget = TodoWidget.layout(context, tasks, (float) height).apply(context, new android.appwidget.AppWidgetHostView(context));
                    int widthPixels = UiStyle.dp(context, 160), heightPixels = UiStyle.dp(context, height);
                    widget.measure(android.view.View.MeasureSpec.makeMeasureSpec(widthPixels, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(heightPixels, android.view.View.MeasureSpec.EXACTLY));
                    widget.layout(0, 0, widthPixels, heightPixels);
                    android.widget.ListView rows = widget.findViewById(R.id.widget_rows);
                    int rowHeight = context.getResources().getDimensionPixelSize(R.dimen.widget_row_height);
                    check(rows.getCount() == tasks.size(), "Widget must retain every task at any reported height");
                    check(rows.isVerticalScrollBarEnabled() && !rows.isScrollbarFadingEnabled(), "Widget has a persistent vertical scroll indicator");
                    check(rows.getChildCount() > 0 && rows.getChildAt(rows.getChildCount() - 1).getBottom() >= rows.getHeight() - rowHeight,
                        "Widget uses actual list height without empty task slots at height=" + height + ", fontScale=" + scale);
                    if (scale == 1f && height == 360) {
                        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(widthPixels, heightPixels, android.graphics.Bitmap.Config.ARGB_8888);
                        widget.draw(new android.graphics.Canvas(bitmap));
                        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null), "widget-capacity-check.png"))) {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
                        } catch (java.io.IOException e) { throw new RuntimeException(e); } finally { bitmap.recycle(); }
                    }
                }
                android.view.View resized = TodoWidget.layout(context, tasks, 120f).apply(context, new android.appwidget.AppWidgetHostView(context));
                resized.measure(android.view.View.MeasureSpec.makeMeasureSpec(UiStyle.dp(context, 160), android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(UiStyle.dp(context, 360), android.view.View.MeasureSpec.EXACTLY));
                resized.layout(0, 0, resized.getMeasuredWidth(), resized.getMeasuredHeight());
                android.widget.ListView resizedRows = resized.findViewById(R.id.widget_rows);
                check(resizedRows.getChildCount() > 3, "A larger launcher allocation displays more tasks even with stale reported height");
                resizedRows.setSelection(tasks.size() - 1);
                resizedRows.measure(android.view.View.MeasureSpec.makeMeasureSpec(resizedRows.getWidth(), android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(resizedRows.getHeight(), android.view.View.MeasureSpec.EXACTLY));
                resizedRows.layout(resizedRows.getLeft(), resizedRows.getTop(), resizedRows.getRight(), resizedRows.getBottom());
                check(resizedRows.getLastVisiblePosition() == tasks.size() - 1, "Scrolling can reach the final task");
            }
        });
    }
    private AccessibilityNodeInfo await(String text) throws Exception {
        for (int i = 0; i < 60; i++) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) if (text.contentEquals(node.getText() == null ? "" : node.getText())) return node;
            Thread.sleep(100);
        }
        throw new AssertionError("Missing UI: " + text);
    }
    private void click(String text) throws Exception {
        await(text);
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        boolean clicked = false;
        if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
            if (text.contentEquals(node.getText() == null ? "" : node.getText()) && node.isClickable()) {
                clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK); break;
            }
        }
        check(clicked, "Click " + text); waitForIdleSync();
    }
    private void navigation() throws Exception {
        activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        click("复盘"); check(await("每日") != null && await("写复盘") != null, "Daily notes list must load");
        click("写复盘"); check(await("收获与想法") != null, "Writing button must open template");
        runOnMainSync(() -> { activity.onBackPressed(); });
        click("每周"); check(await("本周目标") != null, "Weekly goals must appear");
        click("每月"); check(await("本月目标") != null, "Monthly goals must appear");
        click("统计"); check(await("概览") != null, "Statistics overview must load");
        click("月视图"); check(await("查看当天 ›") != null, "Combined month view must load");
        click("设置"); check(await("默认分类") != null, "Settings must remain available");
    }
    private android.widget.EditText firstAnswer(android.view.View view) {
        if (view instanceof android.widget.EditText) return (android.widget.EditText) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { android.widget.EditText answer = firstAnswer(group.getChildAt(i)); if (answer != null) return answer; }
        }
        return null;
    }
    private void widgetClickChecks() throws Exception {
        Context context = getTargetContext(); TodoDb db = TodoDb.get(context);
        long id = -900000001L; Task original = TodoDb.IO.submit(() -> db.tasks().find(id)).get();
        Task task = new Task(); task.id = id; task.title = "isolated widget click check";
        task.taskDate = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        TodoDb.IO.submit(() -> db.tasks().save(task)).get();
        try {
            runOnMainSync(() -> {
                android.appwidget.AppWidgetHostView host = new android.appwidget.AppWidgetHostView(activity);
                for (android.appwidget.AppWidgetProviderInfo info : android.appwidget.AppWidgetManager.getInstance(activity).getInstalledProviders()) {
                    if (info.provider.getClassName().equals(TodoWidget.class.getName())) { host.setAppWidget(900001, info); break; }
                }
                host.addView(TodoWidget.layout(activity, java.util.Collections.singletonList(task), 160f).apply(activity, host));
                activity.setContentView(host);
            });
            boolean[] ready = {false};
            for (int i = 0; i < 30; i++) {
                runOnMainSync(() -> ready[0] = described(activity.getWindow().getDecorView(), "完成：" + task.title) != null);
                if (ready[0]) break; Thread.sleep(100);
            }
            check(ready[0], "Collection checkbox must be rendered in host");
            runOnMainSync(() -> {
                android.view.View decor = activity.getWindow().getDecorView();
                described(decor, "完成：" + task.title).performClick();
            });
            boolean completed = false;
            for (int i = 0; i < 30; i++) {
                completed = TodoDb.IO.submit(() -> db.tasks().find(id).completedAt != null).get();
                if (completed) break; Thread.sleep(100);
            }
            check(completed, "Collection checkbox must dispatch and persist completion");
            Task done = TodoDb.IO.submit(() -> db.tasks().find(id)).get();
            Task pending = new Task(); pending.id = id + 1; pending.title = "pending"; pending.priority = 0;
            done.priority = 2;
            java.util.List<Task> ordered = TodoWidget.visibleTasks(java.util.Arrays.asList(done, pending));
            check(ordered.size() == 2 && ordered.get(1).id == done.id, "Completed widget task remains visible after unfinished tasks");
        } finally {
            runOnMainSync(() -> activity.setContentView(new android.view.View(activity)));
            TodoDb.IO.submit(() -> { if (original != null) db.tasks().save(original); else db.tasks().delete(task); }).get();
        }
    }
    private void taskEditing() throws Exception {
        Task task = new Task(); task.title = "isolated task interaction";
        android.widget.LinearLayout[] host = new android.widget.LinearLayout[1];
        runOnMainSync(() -> {
            try {
                host[0] = new android.widget.LinearLayout(activity); host[0].setOrientation(android.widget.LinearLayout.VERTICAL);
                java.lang.reflect.Method add = MainActivity.class.getDeclaredMethod("addTask", android.widget.LinearLayout.class, Task.class);
                add.setAccessible(true); add.invoke(activity, host[0], task); activity.setContentView(host[0]);
                host[0].getChildAt(0).performClick();
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        check(await("编辑事项") != null && task.completedAt == null, "Task row opens editor without completing");
        click("取消");
    }
    private android.view.View described(android.view.View view, String label) {
        if (label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { android.view.View found = described(group.getChildAt(i), label); if (found != null) return found; }
        }
        return null;
    }
    private android.widget.TextView completionButton(android.view.View view) {
        if (view instanceof android.widget.TextView && "完成复盘".contentEquals(((android.widget.TextView) view).getText())) return (android.widget.TextView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { android.widget.TextView button = completionButton(group.getChildAt(i)); if (button != null) return button; }
        }
        return null;
    }
    private void autosave() throws Exception {
        java.time.LocalDate date = java.time.LocalDate.of(2099, 12, 30);
        String key = ReviewPeriod.key(0, date); Context context = getTargetContext();
        TodoDb db = TodoDb.get(context); ReviewRecord original = TodoDb.IO.submit(() -> db.reviews().find(key)).get();
        ReviewPage[] page = new ReviewPage[1];
        try {
            runOnMainSync(() -> {
                page[0] = new ReviewPage(activity, java.util.Collections.emptyList(), 0, date,
                    0xFF20392D, 0xFF7A887D, 0xFF29664B, 0xFFE9EFE1, 0xFFFFFFFF, (t, d) -> {});
                activity.setContentView(page[0]);
                page[0].open(date, false);
            });
            await("收获与想法");
            runOnMainSync(() -> { firstAnswer(page[0]).setText("device draft check"); page[0].persist(); });
            ReviewRecord draft = TodoDb.IO.submit(() -> db.reviews().find(key)).get();
            check(draft != null && draft.answers.contains("device draft check") && !draft.finished, "Typed answers must save as a draft");
            runOnMainSync(() -> completionButton(page[0]).performClick());
            check(TodoDb.IO.submit(() -> db.reviews().find(key)).get().finished, "Completion button must save completed status");
            check(!page[0].isDetail(), "Completing reflection must return to notes list");
            runOnMainSync(() -> page[0].open(date, true));
            await("复盘内容");
            check(firstAnswer(page[0]) == null, "Completed record opens in reading mode");
            click("编辑"); await("收获与想法");
            runOnMainSync(() -> {
                firstAnswer(page[0]).setText("edited immediately before leaving");
                activity.setContentView(new android.view.View(activity));
            });
            ReviewRecord edited = TodoDb.IO.submit(() -> db.reviews().find(key)).get();
            check(edited.answers.contains("edited immediately before leaving") && !edited.finished, "Leaving must flush edits before debounce fires");
            runOnMainSync(() -> { activity.setContentView(page[0]); page[0].open(date, false); });
            await("收获与想法");
            runOnMainSync(() -> described(page[0], "复盘更多操作").performClick());
            click("删除这篇复盘"); click("取消");
            check(TodoDb.IO.submit(() -> db.reviews().find(key)).get().answers.contains("edited immediately"), "Cancel deletion keeps reflection");
            runOnMainSync(() -> described(page[0], "复盘更多操作").performClick());
            click("删除这篇复盘"); click("删除");
            await("每日");
            check(!page[0].isDetail() && TodoDb.IO.submit(() -> db.reviews().find(key)).get().answers.equals("[]"), "Confirm deletion clears reflection and returns to list");
        } finally {
            runOnMainSync(() -> activity.setContentView(new android.view.View(activity)));
            TodoDb.IO.submit(() -> {
                if (original != null) db.reviews().save(original);
                else db.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM reviews WHERE id = ?", new Object[]{key});
            }).get();
        }
    }
}
