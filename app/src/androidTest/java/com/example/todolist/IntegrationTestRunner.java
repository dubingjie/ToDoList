package com.example.todolist;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.room.Room;
import java.util.*;

/** A dependency-free device smoke test runner. Uses a separate database for persistence tests. */
public class IntegrationTestRunner extends Instrumentation {
    private int passed;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            databaseSurvivesReopen(); migrationAndTrash(); monthLayoutAndExport(); widgetLayout(); uiCrud();
            result.putString("stream", "\nPASS: " + passed + " integration checks (Room migration/trash, month grid/export, widget layout, UI create/edit/complete/undo/delete/calendar/statistics/theme).\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) { result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(e)); finish(Activity.RESULT_CANCELED, result); }
    }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); passed++; }
    private void databaseSurvivesReopen() {
        Context c = getTargetContext(); String name = "integration-test.db"; c.deleteDatabase(name);
        TodoDb db = Room.databaseBuilder(c, TodoDb.class, name).build();
        Task t = new Task(); t.title = "持久化测试"; t.tag = "学习"; t.priority = 2; t.dueAt = System.currentTimeMillis() + 3600000;
        t.id = db.tasks().save(t); db.close();
        db = Room.databaseBuilder(c, TodoDb.class, name).build();
        Task saved = db.tasks().find(t.id); check(saved != null && saved.title.equals(t.title) && saved.dueAt.equals(t.dueAt), "Data must survive database close/reopen");
        saved.completedAt = System.currentTimeMillis(); db.tasks().save(saved); check(db.tasks().find(t.id).completedAt != null, "Complete must persist");
        db.tasks().delete(saved); check(db.tasks().all().isEmpty(), "Delete must persist"); db.close(); c.deleteDatabase(name);
    }
    private void migrationAndTrash() {
        Context c = getTargetContext(); String name = "migration-test.db"; c.deleteDatabase(name);
        android.database.sqlite.SQLiteDatabase old = c.openOrCreateDatabase(name, 0, null);
        old.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT, tag TEXT, priority INTEGER NOT NULL, createdAt INTEGER NOT NULL, dueAt INTEGER, completedAt INTEGER)");
        old.execSQL("INSERT INTO tasks VALUES (1, '旧版学习事项', '学习', 2, 1000, 5000, 3000)");
        old.setVersion(1); old.close();
        TodoDb db = Room.databaseBuilder(c, TodoDb.class, name).addMigrations(TodoDb.MIGRATION_1_2).build();
        try {
            Task migrated = db.tasks().find(1);
            check(migrated.title.equals("旧版学习事项") && migrated.completedAt == 3000 && migrated.deletedAt == null, "Migration must preserve old data");
            db.tasks().moveToTrash(1, 9000);
            check(db.tasks().all().isEmpty() && db.tasks().trash().size() == 1, "Trashed tasks must be excluded from normal queries");
            db.tasks().restore(1);
            check(db.tasks().all().size() == 1 && db.tasks().find(1).completedAt == 3000, "Restore must preserve completion date");
            db.tasks().moveToTrash(1, 10000); db.tasks().delete(db.tasks().find(1));
            check(db.tasks().trash().isEmpty() && db.tasks().find(1) == null, "Permanent deletion must remove row");
        } finally { db.close(); c.deleteDatabase(name); }
    }
    private void monthLayoutAndExport() throws Exception {
        List<Task> sample = new ArrayList<>();
        java.time.YearMonth month = java.time.YearMonth.of(2026, 8);
        String[] titles = {"复习英语单词", "完成数学练习", "阅读技术文档", "整理工作周报", "学习数据库事务", "完成项目复盘", "阅读一章书", "练习算法题", "归纳学习笔记"};
        for (int day : new int[]{1, 3, 7, 12, 19, 24, 31}) {
            int count = day == 19 ? 9 : day % 3 + 1;
            for (int i = 0; i < count; i++) {
                Task t = new Task(); t.id = sample.size() + 1; t.title = titles[i]; t.tag = i % 2 == 0 ? "学习" : "工作";
                t.completedAt = month.atDay(day).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + i;
                sample.add(t);
            }
        }
        runOnMainSync(() -> {
            MonthGrid grid = new MonthGrid(getTargetContext(), month, month.atDay(19), sample, false, (d, detail) -> {});
            int w = 1080, h = 1700;
            grid.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY));
            grid.layout(0, 0, w, h);
            check(grid.getChildCount() == 31 && grid.getChildAt(30).getBottom() == h - Math.round(getTargetContext().getResources().getDisplayMetrics().density), "Six-week month must fill height");
            check(grid.getChildAt(18).isSelected() && !grid.getChildAt(17).isSelected(), "Selected day must be explicit");
            android.widget.LinearLayout busy = (android.widget.LinearLayout) grid.getChildAt(18);
            android.widget.TextView plus = (android.widget.TextView) busy.getChildAt(busy.getChildCount() - 1);
            check(plus.getText().toString().startsWith("＋"), "Overflow must show plus at bottom");
            check(plus.getBottom() <= busy.getHeight(), "Overflow must stay inside cell");
            android.graphics.Bitmap preview = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            grid.draw(new android.graphics.Canvas(preview)); writeImage(preview, "month-grid-preview.png"); preview.recycle();
            grid.select(month.atDay(20)); check(grid.getChildAt(19).isSelected() && !grid.getChildAt(18).isSelected(), "Selection must move to clicked day");
        });
        android.graphics.Bitmap exported = MonthImage.create(month, sample);
        check(exported.getWidth() == 2100 && exported.getHeight() > 2500, "Export must be a high-resolution full month");
        writeImage(exported, "month-export-preview.png"); exported.recycle();
    }
    private void writeImage(android.graphics.Bitmap bitmap, String name) {
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null), name))) {
            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)) throw new AssertionError("PNG export failed");
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }
    private AccessibilityNodeInfo find(String text) {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        if (root == null) return null;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) if (node.getText() != null && node.getText().toString().equals(text)) return node;
        return null;
    }
    private AccessibilityNodeInfo await(String text) throws Exception {
        for (int n = 0; n < 50; n++) { AccessibilityNodeInfo node = find(text); if (node != null) return node; Thread.sleep(100); }
        throw new AssertionError("UI text not found: " + text);
    }
    private void click(String text) throws Exception {
        if (text.contains("璁颁笅")) { clickDescription("添加待办事项"); return; }
        if (text.contains("鏈堝巻")) { click("月视图"); return; }
        AccessibilityNodeInfo node = await(text);
        while (node != null && !node.isClickable()) node = node.getParent();
        if (node == null || !node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) throw new AssertionError("Cannot click " + text);
        Thread.sleep(250);
    }
    private void clickDescription(String description) throws Exception {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        AccessibilityNodeInfo found = findDescription(root, description);
        check(found != null, "Missing content description: " + description);
        while (found != null && !found.isClickable()) found = found.getParent();
        check(found != null && found.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Cannot click description: " + description);
        Thread.sleep(250);
    }
    private AccessibilityNodeInfo findDescription(AccessibilityNodeInfo node, String description) {
        if (node == null) return null;
        if (description.equals(String.valueOf(node.getContentDescription()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findDescription(node.getChild(i), description);
            if (found != null) return found;
        }
        return null;
    }
    private AccessibilityNodeInfo edit(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo match = edit(node.getChild(i)); if (match != null) return match; }
        return null;
    }
    private void uiCrud() throws Exception {
        String title = "Smoke-test-" + System.currentTimeMillis();
        Activity activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            click("＋  记下一件事"); await("保存");
            AccessibilityNodeInfo field = edit(getUiAutomation().getRootInActiveWindow());
            Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, title);
            check(field != null && field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "Title input must accept text");
            click("保存"); await(title);
            Task t = lookup(title); check(t != null, "UI save must write to Room");
            AccessibilityNodeInfo editNode = await(title); while (editNode != null && !editNode.isLongClickable()) editNode = editNode.getParent();
            if (editNode == null) throw new AssertionError("Task must support long press");
            editNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK); click("编辑事项");
            field = edit(getUiAutomation().getRootInActiveWindow());
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, title + "-edited");
            field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args); click("保存");
            title = title + "-edited"; await(title); check(lookup(title) != null, "UI edit must persist new content");
            click(title); await(title);
            check(lookup(title).completedAt != null, "UI click must complete task");
            click("▦  月历"); await("今天"); check(await(title) != null, "Calendar must display tasks on their completion day");
            click("☷  清单");
            check(find("撤销") == null && find("操作已保存") == null, "No undo bar should appear");
            click(title); Thread.sleep(400);
            check(lookup(title).completedAt == null, "Clicking completed task must restore active status");
            AccessibilityNodeInfo node = await(title); while (node != null && !node.isLongClickable()) node = node.getParent();
            check(node != null && node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK), "Long press must open actions");
            click("删除事项"); Thread.sleep(400); check(lookup(title) == null, "UI delete must remove task");
            check(TodoDb.get(getTargetContext()).tasks().find(t.id).deletedAt != null, "UI delete must move to recycle bin");
            click("☼  设置");
            check(find("添加到桌面") == null && find("备份与恢复") == null, "Settings must not duplicate widget or backup entries");
            click("回收站");
            AccessibilityNodeInfo trashItem = await(title).getParent();
            for (AccessibilityNodeInfo action : trashItem.findAccessibilityNodeInfosByText("恢复")) if ("恢复".contentEquals(action.getText())) { action.performAction(AccessibilityNodeInfo.ACTION_CLICK); break; }
            Thread.sleep(400);
            check(lookup(title) != null && lookup(title).deletedAt == null, "Recycle bin restore must return task to list");
            click("☷  清单");
            node = await(title); while (node != null && !node.isLongClickable()) node = node.getParent();
            node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK); click("删除事项");
            click("☼  设置"); click("回收站"); await(title);
            trashItem = await(title).getParent();
            for (AccessibilityNodeInfo action : trashItem.findAccessibilityNodeInfosByText("彻底删除")) if ("彻底删除".contentEquals(action.getText())) { action.performAction(AccessibilityNodeInfo.ACTION_CLICK); break; }
            click("彻底删除"); Thread.sleep(400);
            check(TodoDb.get(getTargetContext()).tasks().find(t.id) == null, "Confirmed permanent deletion must remove record");
            click("◷  统计"); check(await("最近七天") != null, "Statistics must be accessible");
            click("☼  设置"); click("深色模式"); Thread.sleep(500);
            check(getTargetContext().getSharedPreferences("MainActivity", Context.MODE_PRIVATE).getBoolean("dark", false), "Theme preference must persist");
            click("深色模式"); Thread.sleep(500); click("☷  清单");
        } finally {
            Task t = lookup(title); if (t != null) TodoDb.get(getTargetContext()).tasks().delete(t); for (Task removed : TodoDb.get(getTargetContext()).tasks().trash()) if (removed.title.equals(title)) TodoDb.get(getTargetContext()).tasks().delete(removed);
            runOnMainSync(activity::finish);
        }
    }
    private Task lookup(String title) { for (Task t : TodoDb.get(getTargetContext()).tasks().all()) if (t.title.equals(title)) return t; return null; }
    private void widgetLayout() {
        runOnMainSync(() -> {
            android.widget.RemoteViews widget = new android.widget.RemoteViews(getTargetContext().getPackageName(), R.layout.widget);
            android.widget.RemoteViews row = new android.widget.RemoteViews(getTargetContext().getPackageName(), R.layout.widget_row);
            row.setTextViewText(R.id.widget_text, "小组件检查"); widget.addView(R.id.widget_rows, row);
            android.view.View view = widget.apply(getTargetContext(), new android.widget.FrameLayout(getTargetContext()));
            check(view.findViewById(R.id.widget_check) != null, "Widget RemoteViews must inflate successfully");
            Task pending = new Task(); pending.id = 100; pending.title = "待完成任务";
            Task finished = new Task(); finished.id = 101; finished.title = "今天已完成"; finished.completedAt = System.currentTimeMillis();
            android.view.View ordered = TodoWidget.layout(getTargetContext(), java.util.Arrays.asList(pending, finished), 160).apply(getTargetContext(), new android.widget.FrameLayout(getTargetContext()));
            android.widget.LinearLayout orderedRows = ordered.findViewById(R.id.widget_rows);
            check(orderedRows.getChildCount() == 2, "Widget must keep completed rows visible");
            check(((android.widget.TextView) orderedRows.getChildAt(0).findViewById(R.id.widget_text)).getText().toString().equals("待完成任务"), "Pending rows must come first");
            check(((android.widget.TextView) orderedRows.getChildAt(1).findViewById(R.id.widget_check)).getText().toString().equals("✓"), "Completed rows must show a check mark");
            Task yesterday = new Task(); yesterday.title = "昨天完成"; yesterday.completedAt = java.time.LocalDate.now().minusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            Task todayTask = new Task(); todayTask.title = "今天完成"; todayTask.completedAt = System.currentTimeMillis();
            check(!TaskDates.visibleInTodayList(yesterday, java.time.LocalDate.now()), "Yesterday's completed task must leave today's list");
            check(TaskDates.visibleInTodayList(todayTask, java.time.LocalDate.now()), "Today's completed task must remain visible");
            check(MonthGrid.tasksOn(java.util.Arrays.asList(yesterday), java.time.LocalDate.now().minusDays(1)).size() == 1, "Hidden task must remain available in calendar history");
            List<Task> items = new ArrayList<>();
            for (int i = 0; i < 12; i++) { Task t = new Task(); t.id = i + 1; t.title = "待办 " + (i + 1) + "：整理今天的学习笔记"; items.add(t); }
            float density = getTargetContext().getResources().getDisplayMetrics().density;
            for (int width : new int[]{150, 330}) {
                int previousRows = -1;
                for (int height : new int[]{160, 240, 320}) {
                    android.view.View compact = TodoWidget.layout(getTargetContext(), items, height).apply(getTargetContext(), new android.widget.FrameLayout(getTargetContext()));
                    int w = Math.round(width * density), h = Math.round(height * density);
                    compact.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY));
                    compact.layout(0, 0, w, h);
                    android.widget.LinearLayout rows = compact.findViewById(R.id.widget_rows);
                    int count = rows.getChildCount();
                    check(count > previousRows, "Taller widgets must display more tasks");
                    check(count > 0 && rows.getChildAt(count - 1).getBottom() <= rows.getHeight(), "Rows must fit without footer clipping");
                    previousRows = count;
                    if (height == 240) {
                        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                        compact.draw(new android.graphics.Canvas(bitmap));
                        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null), "widget-" + width + ".png"))) {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                        } catch (java.io.IOException e) { throw new RuntimeException(e); }
                        bitmap.recycle();
                    }
                }
            }
        });
    }
}
