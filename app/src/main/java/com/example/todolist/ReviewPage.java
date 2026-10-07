package com.example.todolist;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.*;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiConsumer;

/** Date-scoped goals and reflection, independently saved from task records. */
final class ReviewPage extends LinearLayout {
    private final int ink, muted, accent, soft, surface;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Task> tasks;
    private final BiConsumer<Integer, LocalDate> navigation;
    private final Runnable saveDraft = this::persist;
    private int type, generation;
    private LocalDate start;
    private ReviewRecord record;
    private JSONArray goals = new JSONArray();
    private final List<EditText> answers = new ArrayList<>();
    private TextView status;
    private boolean dirty;
    private boolean detail, reading;
    Runnable modeChanged = () -> {};
    private static final String[][] QUESTIONS = {
        {"今天最有收获的是什么？", "哪里卡住了，为什么？", "明天准备怎样调整？"},
        {"本周目标进展如何？", "哪些方法有效，值得继续？", "主要问题是什么？", "下周优先改善什么？"},
        {"这个月有哪些实际成果？", "哪些目标没达到，原因是什么？", "哪些做法值得保留？", "下个月重点是什么？"}
    };

    ReviewPage(Context context, List<Task> tasks, int type, LocalDate date,
               int ink, int muted, int accent, int soft, int surface,
               BiConsumer<Integer, LocalDate> navigation) {
        super(context); setOrientation(VERTICAL);
        this.tasks = tasks; this.type = type; this.start = ReviewPeriod.start(type, date);
        this.ink = ink; this.muted = muted; this.accent = accent; this.soft = soft; this.surface = surface;
        this.navigation = navigation;
        load();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(Math.max(12, size)); view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL); UiStyle.typography(view, bold); return view;
    }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private void gap(LinearLayout parent, int size) { parent.addView(new android.view.View(getContext()), new LayoutParams(1, dp(size))); }
    private LinearLayout row() { LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL); return row; }
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(getContext()); card.setOrientation(VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16)); card.setBackground(background(surface, 18)); return card;
    }
    private TextView action(String title, Runnable click) {
        boolean primary = title.equals("完成复盘");
        int primaryInk = (surface & 0xffffff) == 0xffffff ? android.graphics.Color.WHITE : android.graphics.Color.parseColor("#141E19");
        TextView view = text(title, 13, primary ? primaryInk : accent, true); view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(8), dp(12), dp(8)); view.setMinHeight(dp(48));
        view.setBackground(UiStyle.press(getContext(), primary ? accent : soft, 14, accent)); view.setOnClickListener(v -> click.run()); return view;
    }
    private void change(int newType, LocalDate date) {
        persist(); type = newType; start = ReviewPeriod.start(type, date); navigation.accept(type, start); load();
    }
    boolean isDetail() { return detail; }
    boolean isReading() { return reading; }
    void open(LocalDate date, boolean read) {
        persist(); detail = true; reading = read;
        change(type, date); modeChanged.run();
    }
    void showList() {
        persist(); detail = false; reading = false;
        change(type, LocalDate.now()); modeChanged.run();
    }
    private String dateTitle(LocalDate date) {
        if (type == 2) return date.format(DateTimeFormatter.ofPattern("yyyy年M月"));
        String pattern = date.getYear() == LocalDate.now().getYear() ? "M月d日" : "yyyy年M月d日";
        if (type == 1) return date.format(DateTimeFormatter.ofPattern(pattern)) + "—" + date.plusDays(6).format(DateTimeFormatter.ofPattern(date.plusDays(6).getYear() == date.getYear() ? "M月d日" : "yyyy年M月d日"));
        return date.format(DateTimeFormatter.ofPattern(pattern + " · EEE", Locale.CHINA));
    }
    private void load() {
        int token = ++generation; record = null; status = null; dirty = false; answers.clear(); removeAllViews();
        if (detail) {
            LinearLayout header = row();
            ImageButton back = new ImageButton(getContext()); back.setImageResource(R.drawable.ic_back);
            back.setImageTintList(android.content.res.ColorStateList.valueOf(ink)); back.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            back.setContentDescription("返回复盘列表"); back.setOnClickListener(v -> showList());
            header.addView(back, new LayoutParams(dp(48), dp(48)));
            header.addView(text(new String[]{"每日复盘", "每周复盘", "每月复盘"}[type], 18, ink, true), new LayoutParams(0, dp(48), 1));
            if (reading) {
                TextView edit = text("编辑", 14, accent, true); edit.setGravity(Gravity.CENTER);
                edit.setOnClickListener(v -> { persist(); reading = false; load(); }); header.addView(edit, new LayoutParams(dp(56), dp(48)));
            }
            ImageButton more = new ImageButton(getContext()); more.setImageDrawable(UiStyle.icon(getContext(), "more", muted, 22));
            more.setBackground(UiStyle.press(getContext(), android.graphics.Color.TRANSPARENT, 14, accent));
            more.setContentDescription("复盘更多操作");
            more.setOnClickListener(v -> {
                if (record == null) return;
                new AlertDialog.Builder(getContext()).setItems(new String[]{"删除这篇复盘"}, (dialog, which) -> confirmDelete()).show();
            }); header.addView(more, new LayoutParams(dp(48), dp(48)));
            addView(header);
            TextView date = text(dateTitle(start) + (reading ? "" : "  ▾"), 12, muted, false); date.setPadding(dp(8), 0, dp(8), 0);
            if (!reading) {
                date.setContentDescription("选择复盘日期");
                date.setOnClickListener(v -> new DatePickerDialog(getContext(), (p, y, m, d) -> open(LocalDate.of(y, m + 1, d), false), start.getYear(), start.getMonthValue() - 1, start.getDayOfMonth()).show());
            }
            addView(date, new LayoutParams(-1, dp(40))); gap(this, 12);
        } else {
        addView(text("复盘", 22, ink, true), new LayoutParams(-1, dp(48)));
        LinearLayout tabs = row(); tabs.setPadding(dp(4), dp(4), dp(4), dp(4)); tabs.setBackground(background(soft, 14));
        String[] labels = {"每日", "每周", "每月"};
        for (int i = 0; i < 3; i++) {
            final int target = i;
            TextView tab = action(labels[i], () -> change(target, LocalDate.now()));
            tab.setTextColor(i == type ? accent : muted);
            tab.setBackground(UiStyle.press(getContext(), i == type ? surface : android.graphics.Color.TRANSPARENT, 10, accent));
            LayoutParams params = new LayoutParams(0, dp(38), 1); params.setMargins(dp(2), 0, dp(2), 0); tab.setMinHeight(0); tabs.addView(tab, params);
        }
        addView(tabs); gap(this, 16);
        }
        TextView loading = text("正在读取记录…", 13, muted, false); addView(loading);
        String key = ReviewPeriod.key(type, start);
        TodoDb.IO.execute(() -> {
            ReviewRecord existing = TodoDb.get(getContext()).reviews().find(key);
            List<ReviewRecord> history = detail ? Collections.emptyList() : TodoDb.get(getContext()).reviews().list(type + ":");
            handler.post(() -> {
                if (token != generation) return;
                removeView(loading); record = existing == null ? new ReviewRecord() : existing; record.id = key;
                goals = parse(record.goals);
                if (detail) buildContent(); else buildList(history);
            });
        });
    }
    private JSONArray parse(String json) { try { return new JSONArray(json == null ? "[]" : json); } catch (JSONException e) { return new JSONArray(); } }
    private void confirmDelete() {
        if (record == null) return;
        String key = record.id;
        new AlertDialog.Builder(getContext()).setTitle("删除这篇复盘？")
            .setMessage("复盘内容删除后无法恢复，任务记录和周、月目标会保留。")
            .setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> {
                if (record == null || !record.id.equals(key)) return;
                persist(); int token = ++generation; record = null; dirty = false; answers.clear();
                removeAllViews(); addView(text("正在删除…", 13, muted, false));
                TodoDb.IO.execute(() -> {
                    TodoDb.get(getContext()).reviews().clearContent(key);
                    handler.post(() -> { if (generation == token) showList(); });
                });
            }).show();
    }
    private void buildList(List<ReviewRecord> history) {
        if (type != 0) {
            LinearLayout targets = card(); LinearLayout header = row();
            header.addView(text(type == 1 ? "本周目标" : "本月目标", 15, ink, true), new LayoutParams(0, dp(40), 1));
            TextView edit = text("编辑", 13, accent, true); edit.setGravity(Gravity.CENTER); edit.setOnClickListener(v -> editGoals()); header.addView(edit, new LayoutParams(dp(48), dp(40))); targets.addView(header);
            if (goals.length() == 0) targets.addView(text("先定下 1～3 个重点目标", 12, muted, false));
            for (int i = 0; i < goals.length(); i++) {
                JSONObject goal = goals.optJSONObject(i); if (goal == null) continue;
                TextView title = text((goal.optInt("state") == 1 ? "✓ " : "· ") + goal.optString("title"), 13, ink, false); title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END); targets.addView(title); gap(targets, 4);
            }
            addView(targets); gap(this, 18);
        }
        int shown = 0;
        for (ReviewRecord note : history) {
            JSONArray content = parse(note.answers); StringBuilder summary = new StringBuilder();
            for (int i = 0; i < content.length(); i++) { String value = content.optString(i).trim(); if (!value.isEmpty()) { if (summary.length() > 0) summary.append("  ·  "); summary.append(value.replace('\n', ' ')); } }
            if (summary.length() == 0 && !note.finished) continue;
            LocalDate date;
            try { date = LocalDate.parse(note.id.substring(2)); } catch (RuntimeException e) { continue; }
            LinearLayout item = card();
            LinearLayout header = row(); header.addView(text(dateTitle(date), 15, ink, true), new LayoutParams(0, -2, 1));
            if (!note.finished) { TextView draft = text("草稿", 11, accent, false); draft.setPadding(dp(8), dp(4), dp(8), dp(4)); draft.setBackground(background(soft, 8)); header.addView(draft); } item.addView(header); gap(item, 10);
            TextView excerpt = text(summary.toString(), 13, muted, false); excerpt.setLineSpacing(dp(4), 1f); excerpt.setMaxLines(2); excerpt.setEllipsize(TextUtils.TruncateAt.END); item.addView(excerpt);
            item.setBackground(UiStyle.press(getContext(), surface, 18, accent));
            item.setContentDescription("查看复盘：" + dateTitle(date)); item.setOnClickListener(v -> open(date, note.finished));
            addView(item); gap(this, 10); shown++;
        }
        if (shown == 0) { gap(this, 36); TextView empty = text("还没有复盘记录", 15, ink, true); empty.setGravity(Gravity.CENTER); addView(empty); gap(this, 8); TextView hint = text("点击「写复盘」，记录一点收获和想法", 12, muted, false); hint.setGravity(Gravity.CENTER); addView(hint); }
        gap(this, 84);
    }
    private void buildContent() {
        if (type != 0) {
            LinearLayout targetCard = card();
            LinearLayout heading = row(); heading.addView(text(type == 1 ? "本周重点目标" : "本月重点目标", 16, ink, true), new LayoutParams(0, dp(48), 1));
            if (!reading) heading.addView(action("编辑目标", this::editGoals)); targetCard.addView(heading);
            if (goals.length() == 0) targetCard.addView(text("写下 1～3 个希望达到的结果，也可以直接复盘。", 13, muted, false));
            for (int i = 0; i < goals.length(); i++) {
                JSONObject goal = goals.optJSONObject(i); if (goal == null) continue;
                String[] labels = {"进行中", "已达成", "未达成"}; int state = Math.max(0, Math.min(2, goal.optInt("state")));
                TextView title = text((i + 1) + ". " + goal.optString("title"), 15, ink, true); targetCard.addView(title); gap(targetCard, 4);
                String approach = goal.optString("approach"); if (!approach.isEmpty()) { targetCard.addView(text(approach, 12, muted, false)); gap(targetCard, 4); }
                targetCard.addView(text(labels[state], 12, state == 1 ? accent : muted, false)); gap(targetCard, 10);
            }
            if (!reading) targetCard.addView(action("选择带入上期未达成目标", this::carryGoals)); addView(targetCard); gap(this, 14);
        }
        LinearLayout facts = card();
        LocalDate end = ReviewPeriod.next(type, start, 1);
        int completed = TaskDates.count(tasks, start, end), unfinished = 0;
        StringBuilder pending = new StringBuilder(), done = new StringBuilder();
        for (Task task : tasks) {
            if (task.deletedAt != null) continue;
            LocalDate assigned = TaskDates.date(task.taskDate == null ? task.createdAt : task.taskDate);
            if (task.completedAt == null && !assigned.isBefore(start) && assigned.isBefore(end)) {
                unfinished++; pending.append("□ ").append(task.title).append('\n');
            }
            if (task.completedAt != null && !TaskDates.date(task.completedAt).isBefore(start) && TaskDates.date(task.completedAt).isBefore(end)) done.append("✓ ").append(task.title).append('\n');
        }
        TextView execution = text("已完成 " + completed + " 件 · 未完成 " + unfinished + " 件" + (completed + unfinished > 0 ? "  ›" : ""), 12, muted, false);
        facts.addView(execution);
        if (completed + unfinished > 0) {
            final String details = (done.length() == 0 ? "" : "已完成\n" + done + "\n") + (pending.length() == 0 ? "" : "未完成\n" + pending);
            facts.setOnClickListener(v -> new AlertDialog.Builder(getContext()).setTitle("本期任务记录").setMessage(details.trim()).setPositiveButton("关闭", null).show());
        }
        addView(facts); gap(this, 18);
        LinearLayout heading = row(); heading.addView(text(reading ? "复盘内容" : "收获与想法", 17, ink, true), new LayoutParams(0, dp(40), 1));
        status = text(record.finished ? "已完成复盘" : record.updatedAt == 0 ? "尚未填写" : "草稿已保存", 12, muted, false); heading.addView(status); addView(heading);
        if (!reading) addView(text("可以跳过问题，填写内容会自动保存。", 12, muted, false)); gap(this, 12);
        JSONArray saved = parse(record.answers);
        for (int i = 0; i < QUESTIONS[type].length; i++) {
            addView(text((i + 1) + ". " + QUESTIONS[type][i], 14, ink, true)); gap(this, 6);
            if (reading) {
                String value = saved.optString(i).trim();
                TextView answer = text(value.isEmpty() ? "未填写" : value, 14, value.isEmpty() ? muted : ink, false);
                answer.setPadding(dp(16), dp(14), dp(16), dp(14)); answer.setBackground(background(surface, 14)); answer.setLineSpacing(dp(5), 1f);
                answer.setTextIsSelectable(true); addView(answer); gap(this, 18); continue;
            }
            EditText answer = new EditText(getContext()); answer.setTextSize(14); answer.setTextColor(ink); answer.setHintTextColor(muted);
            answer.setHint("写一点想法…"); answer.setGravity(Gravity.TOP); answer.setMinLines(2); answer.setMaxLines(6);
            answer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            answer.setPadding(dp(16), dp(14), dp(16), dp(14)); answer.setBackground(background(surface, 14)); UiStyle.typography(answer, false); answer.setLineSpacing(dp(5), 1f);
            answer.setText(saved.optString(i)); answers.add(answer); addView(answer, new LayoutParams(-1, -2)); gap(this, 16);
            answer.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
                public void onTextChanged(CharSequence s, int st, int before, int count) { record.finished = false; changed(); }
                public void afterTextChanged(Editable e) {}
            });
        }
        if (!reading) addView(action("完成复盘", () -> {
            boolean hasContent = false; for (EditText answer : answers) if (!answer.getText().toString().trim().isEmpty()) hasContent = true;
            if (!hasContent) { Toast.makeText(getContext(), "先写一点复盘内容吧", Toast.LENGTH_SHORT).show(); return; }
            record.finished = true; dirty = true; persist(); showList();
        }));
    }
    private void changed() {
        dirty = true; if (status != null) status.setText("正在保存…");
        handler.removeCallbacks(saveDraft); handler.postDelayed(saveDraft, 400);
    }
    void persist() {
        handler.removeCallbacks(saveDraft);
        if (record == null || !dirty) return;
        JSONArray content = new JSONArray(); for (EditText answer : answers) content.put(answer.getText().toString());
        ReviewRecord snapshot = new ReviewRecord(); snapshot.id = record.id; snapshot.goals = goals.toString(); snapshot.answers = answers.isEmpty() ? record.answers : content.toString();
        snapshot.finished = record.finished; snapshot.updatedAt = System.currentTimeMillis();
        dirty = false; record.goals = snapshot.goals; record.answers = snapshot.answers; record.updatedAt = snapshot.updatedAt;
        String key = snapshot.id;
        TodoDb.IO.execute(() -> {
            TodoDb.get(getContext()).reviews().save(snapshot);
            handler.post(() -> { if (record != null && record.id.equals(key) && !dirty && status != null) status.setText(snapshot.finished ? "已完成复盘" : "草稿已保存"); });
        });
    }
    private void editGoals() {
        LinearLayout form = new LinearLayout(getContext()); form.setOrientation(VERTICAL); form.setPadding(dp(20), dp(8), dp(20), dp(8));
        List<EditText> titles = new ArrayList<>(), approaches = new ArrayList<>(); List<Spinner> states = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            JSONObject goal = goals.optJSONObject(i);
            EditText title = new EditText(getContext()); title.setTextSize(15); title.setHint("目标 " + (i + 1) + "：想达到什么结果？"); title.setText(goal == null ? "" : goal.optString("title")); form.addView(title); titles.add(title);
            UiStyle.typography(title, false);
            EditText approach = new EditText(getContext()); approach.setTextSize(13); approach.setHint("准备怎么做（可选）"); approach.setText(goal == null ? "" : goal.optString("approach")); form.addView(approach); approaches.add(approach);
            UiStyle.typography(approach, false);
            Spinner state = new Spinner(getContext()); state.setAdapter(UiStyle.choices(getContext(), android.R.layout.simple_spinner_dropdown_item, new String[]{"进行中", "已达成", "未达成"})); state.setSelection(goal == null ? 0 : Math.max(0, Math.min(2, goal.optInt("state")))); form.addView(state, new LayoutParams(-1, dp(48))); states.add(state); gap(form, 12);
        }
        ScrollView scroll = new ScrollView(getContext()); scroll.addView(form);
        new AlertDialog.Builder(getContext()).setTitle("重点目标 · 最多 3 项").setView(scroll).setNegativeButton("取消", null).setPositiveButton("保存", (d, w) -> {
            JSONArray updated = new JSONArray();
            for (int i = 0; i < 3; i++) {
                String title = titles.get(i).getText().toString().trim(); if (title.isEmpty()) continue;
                JSONObject goal = new JSONObject(); try { goal.put("title", title); goal.put("approach", approaches.get(i).getText().toString().trim()); goal.put("state", states.get(i).getSelectedItemPosition()); } catch (JSONException ignored) {}
                updated.put(goal);
            }
            goals = updated; dirty = true; persist(); load();
        }).show();
    }
    private void carryGoals() {
        String key = record.id; int token = generation;
        String previousKey = ReviewPeriod.key(type, ReviewPeriod.next(type, start, -1));
        TodoDb.IO.execute(() -> {
            ReviewRecord previous = TodoDb.get(getContext()).reviews().find(previousKey);
            handler.post(() -> {
                if (token != generation || record == null || !record.id.equals(key)) return;
                List<JSONObject> available = new ArrayList<>(); JSONArray old = parse(previous == null ? null : previous.goals);
                for (int i = 0; i < old.length(); i++) { JSONObject goal = old.optJSONObject(i); if (goal != null && goal.optInt("state") != 1) available.add(goal); }
                if (available.isEmpty()) { Toast.makeText(getContext(), "上期没有可带入的目标", Toast.LENGTH_SHORT).show(); return; }
                String[] labels = new String[available.size()]; for (int i = 0; i < labels.length; i++) labels[i] = available.get(i).optString("title"); boolean[] checked = new boolean[labels.length];
                new AlertDialog.Builder(getContext()).setTitle("选择继续的目标").setMultiChoiceItems(labels, checked, (d, i, on) -> checked[i] = on).setNegativeButton("取消", null).setPositiveButton("带入", (d, w) -> {
                    int count = 0; for (boolean on : checked) if (on) count++;
                    if (goals.length() + count > 3) { Toast.makeText(getContext(), "最多保留 3 个重点目标，请先编辑现有目标", Toast.LENGTH_LONG).show(); return; }
                    for (int i = 0; i < checked.length; i++) if (checked[i]) { JSONObject goal = new JSONObject(); try { goal.put("title", labels[i]); goal.put("approach", available.get(i).optString("approach")); goal.put("state", 0); } catch (JSONException ignored) {} goals.put(goal); }
                    dirty = true; persist(); load();
                }).show();
            });
        });
    }
    @Override protected void onDetachedFromWindow() { persist(); generation++; super.onDetachedFromWindow(); }
}
