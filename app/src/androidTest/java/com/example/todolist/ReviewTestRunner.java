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
            database(); navigation(); autosave();
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
        } finally { db.close(); context.deleteDatabase(name); }
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
        } finally {
            runOnMainSync(() -> activity.setContentView(new android.view.View(activity)));
            TodoDb.IO.submit(() -> {
                if (original != null) db.reviews().save(original);
                else db.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM reviews WHERE id = ?", new Object[]{key});
            }).get();
        }
    }
}
