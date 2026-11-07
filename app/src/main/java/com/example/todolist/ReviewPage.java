package com.example.todolist;

import android.app.DatePickerDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.*;
import android.view.Gravity;
import android.view.View;
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
    private static final String[] MOOD_EMOJI = {"😄", "🙂", "😐", "😔", "😤"};
    private static final String[] MOOD_COLORS = {"#E8B04B", "#6F8F6A", "#A39A8B", "#6E93BC", "#C96A5A"};
    private static final String[] PROMPTS = {
        "今天最值得庆祝的一件小事是什么？",
        "今天有谁让你心存感激？",
        "今天学到的最有用的一个东西是？",
        "如果可以重做今天的一件事，你会改什么？",
        "今天哪个瞬间让你感到平静？",
        "今天克服了什么小困难？",
        "明天最想做好的一件事是？",
        "今天有什么想法值得记下来？",
        "这周的自己，最想被夸哪一点？",
        "今天放下了什么执念？",
        "今天身体感觉怎么样？",
        "今天哪顿饭最香？",
        "今天帮助了谁，或被谁帮助？",
        "今天最意外的收获是？"
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
        card.setPadding(dp(18), dp(16), dp(18), dp(16)); card.setBackground(background(surface, 22));
        card.setElevation(dp(2));
        if (android.os.Build.VERSION.SDK_INT >= 28) card.setOutlineAmbientShadowColor(android.graphics.Color.parseColor("#593A2E1E"));
        return card;
    }
    private TextView action(String title, Runnable click) {
        boolean primary = title.equals("完成复盘");
        int primaryInk = (surface & 0xffffff) == 0xffffff ? android.graphics.Color.WHITE : android.graphics.Color.parseColor("#141E19");
        TextView view = text(title, 13, primary ? primaryInk : accent, true); view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(8), dp(12), dp(8)); view.setMinHeight(dp(48));
        view.setBackground(UiStyle.press(getContext(), primary ? accent : soft, 16, accent)); view.setOnClickListener(v -> click.run()); return view;
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
                new MaterialAlertDialogBuilder(getContext()).setItems(new String[]{"删除这篇复盘"}, (dialog, which) -> confirmDelete()).show();
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
        LinearLayout tabs = row(); tabs.setPadding(dp(4), dp(4), dp(4), dp(4)); tabs.setBackground(background(soft, 18));
        String[] labels = {"每日", "每周", "每月"};
        for (int i = 0; i < 3; i++) {
            final int target = i;
            TextView tab = action(labels[i], () -> change(target, LocalDate.now()));
            tab.setTextColor(i == type ? accent : muted);
            tab.setBackground(UiStyle.press(getContext(), i == type ? surface : android.graphics.Color.TRANSPARENT, 14, accent));
            LayoutParams params = new LayoutParams(0, dp(38), 1); params.setMargins(dp(2), 0, dp(2), 0); tab.setMinHeight(0); tabs.addView(tab, params);
        }
        addView(tabs); gap(this, 16);
        }
        TextView loading = text("正在读取记录…", 13, muted, false); addView(loading);
        String key = ReviewPeriod.key(type, start);
        TodoDb.IO.execute(() -> {
            ReviewRecord existing = TodoDb.get(getContext()).reviews().find(key);
            List<ReviewRecord> history = detail ? Collections.emptyList() : TodoDb.get(getContext()).reviews().list(type + ":");
            List<ReviewRecord> monthReviews = Collections.emptyList();
            ReviewRecord pastToday = null;
            if (type == 0 && !detail) {
                monthReviews = TodoDb.get(getContext()).reviews().list("0:" + YearMonth.now());
                LocalDate today = LocalDate.now();
                for (int y = 1; y <= 3 && pastToday == null; y++) {
                    ReviewRecord past = TodoDb.get(getContext()).reviews().find("0:" + today.minusYears(y));
                    if (past != null && past.finished) pastToday = past;
                }
            }
            List<ReviewRecord> monthData = monthReviews;
            ReviewRecord pastData = pastToday;
            handler.post(() -> {
                if (token != generation) return;
                removeView(loading); record = existing == null ? new ReviewRecord() : existing; record.id = key;
                goals = parse(record.goals);
                if (detail) buildContent(); else buildList(history, monthData, pastData);
            });
        });
    }
    private JSONArray parse(String json) { try { return new JSONArray(json == null ? "[]" : json); } catch (JSONException e) { return new JSONArray(); } }
    private void confirmDelete() {
        if (record == null) return;
        String key = record.id;
        new MaterialAlertDialogBuilder(getContext()).setTitle("删除这篇复盘？")
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
    private void buildList(List<ReviewRecord> history, List<ReviewRecord> monthReviews, ReviewRecord pastToday) {
        if (type == 0) {
            LinearLayout bento = row(); bento.setBaselineAligned(false);
            YearMonth ym = YearMonth.now();
            Map<Integer, ReviewRecord> byDay = new HashMap<>();
            for (ReviewRecord r : monthReviews) {
                if (!r.finished) continue;
                try { byDay.put(LocalDate.parse(r.id.substring(2)).getDayOfMonth(), r); } catch (RuntimeException ignored) {}
            }
            LinearLayout monthCard = card();
            LinearLayout.LayoutParams monthParams = new LayoutParams(0, -2, 1);
            monthParams.setMargins(0, 0, dp(5), 0);
            monthCard.addView(text("本月复盘", 13, muted, false)); gap(monthCard, 4);
            monthCard.addView(text(byDay.size() + " 天", 24, ink, true)); gap(monthCard, 10);
            int lead = ym.atDay(1).getDayOfWeek().getValue() % 7;
            int cells = lead + ym.lengthOfMonth();
            int rowCount = (cells + 6) / 7;
            int dayNumber = 0;
            for (int r = 0; r < rowCount; r++) {
                LinearLayout weekRow = row();
                for (int c = 0; c < 7; c++) {
                    int index = r * 7 + c;
                    View cell = new View(getContext());
                    GradientDrawable cellBg = new GradientDrawable();
                    cellBg.setCornerRadius(dp(3));
                    if (index >= lead && dayNumber < ym.lengthOfMonth()) {
                        dayNumber++;
                        ReviewRecord record = byDay.get(dayNumber);
                        cellBg.setColor(record == null ? soft : record.mood >= 0 ? android.graphics.Color.parseColor(MOOD_COLORS[record.mood]) : accent);
                        if (dayNumber == LocalDate.now().getDayOfMonth()) cellBg.setStroke(dp(1), accent);
                    } else cellBg.setColor(android.graphics.Color.TRANSPARENT);
                    cell.setBackground(cellBg);
                    LayoutParams cellParams = new LayoutParams(0, dp(10), 1);
                    cellParams.setMargins(dp(1), dp(1), dp(1), dp(1));
                    weekRow.addView(cell, cellParams);
                }
                monthCard.addView(weekRow);
            }
            bento.addView(monthCard, monthParams);
            LinearLayout streakCard = card();
            LinearLayout.LayoutParams streakParams = new LayoutParams(0, -2, 1);
            streakParams.setMargins(dp(5), 0, 0, 0);
            Set<LocalDate> finishedDays = new HashSet<>();
            for (ReviewRecord r : history) {
                if (!r.finished) continue;
                try { finishedDays.add(LocalDate.parse(r.id.substring(2))); } catch (RuntimeException ignored) {}
            }
            LocalDate cursor = LocalDate.now();
            if (!finishedDays.contains(cursor)) cursor = cursor.minusDays(1);
            int streak = 0;
            while (finishedDays.contains(cursor)) { streak++; cursor = cursor.minusDays(1); }
            streakCard.addView(text("连续复盘", 13, muted, false)); gap(streakCard, 4);
            streakCard.addView(text("🔥 " + streak + " 天", 24, ink, true)); gap(streakCard, 10);
            streakCard.addView(text(streak > 0 ? "保持记录，看见自己的变化" : "从今天开始第一篇", 11, muted, false));
            bento.addView(streakCard, streakParams);
            addView(bento); gap(this, 12);
            LinearLayout pastCard = card();
            pastCard.addView(text("那年今日", 13, muted, false)); gap(pastCard, 6);
            if (pastToday != null) {
                LocalDate pastDate = LocalDate.parse(pastToday.id.substring(2));
                StringBuilder excerpt = new StringBuilder();
                JSONArray pastAnswers = parse(pastToday.answers);
                for (int i = 0; i < pastAnswers.length() && excerpt.length() < 48; i++) {
                    String value = pastAnswers.optString(i).trim();
                    if (!value.isEmpty()) { if (excerpt.length() > 0) excerpt.append("  "); excerpt.append(value.replace('\n', ' ')); }
                }
                TextView pastText = text(pastDate.getYear() + "年的今天 · " + (excerpt.length() == 0 ? "（有记录）" : excerpt), 13, ink, false);
                pastText.setMaxLines(2); pastText.setEllipsize(TextUtils.TruncateAt.END);
                pastCard.addView(pastText);
                pastCard.setOnClickListener(v -> open(pastDate, true));
            } else {
                pastCard.addView(text("从今天的复盘开始，明年今天回来看看 🌱", 13, muted, false));
            }
            addView(pastCard); gap(this, 12);
            LinearLayout promptCard = card();
            promptCard.addView(text("💡 今日一问", 13, muted, false)); gap(promptCard, 6);
            promptCard.addView(text(PROMPTS[LocalDate.now().getDayOfYear() % PROMPTS.length], 15, ink, true)); gap(promptCard, 8);
            promptCard.addView(text("点这里，写下今天的答案 →", 12, accent, true));
            promptCard.setBackground(UiStyle.press(getContext(), surface, 22, accent));
            promptCard.setOnClickListener(v -> open(LocalDate.now(), false));
            addView(promptCard); gap(this, 18);
        }
        if (type != 0) {
            LinearLayout targets = card(); LinearLayout header = row();
            header.addView(text(type == 1 ? "本周目标" : "本月目标", 15, ink, true), new LayoutParams(0, dp(40), 1));
            if (goals.length() > 0) header.addView(text(goalProgress(), 12, muted, false));
            TextView edit = text("编辑", 13, accent, true); edit.setGravity(Gravity.CENTER); edit.setOnClickListener(v -> editGoals()); header.addView(edit, new LayoutParams(dp(48), dp(40))); targets.addView(header);
            if (goals.length() == 0) targets.addView(text("先定下 1～3 个重点目标", 12, muted, false));
            for (int i = 0; i < goals.length(); i++) {
                JSONObject goal = goals.optJSONObject(i); if (goal == null) continue;
                addGoalTitle(targets, goal, false); gap(targets, 6);
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
            if (note.mood >= 0 && note.mood < MOOD_EMOJI.length) header.addView(text(MOOD_EMOJI[note.mood], 16, ink, false), new LayoutParams(dp(28), dp(28)));
            if (!note.finished) { TextView draft = text("草稿", 11, accent, false); draft.setPadding(dp(8), dp(4), dp(8), dp(4)); draft.setBackground(background(soft, 8)); header.addView(draft); }
            else { TextView completed = text("✓", 16, accent, true); completed.setGravity(Gravity.CENTER); completed.setContentDescription("已完成复盘"); header.addView(completed, new LayoutParams(dp(24), dp(24))); }
            item.addView(header); gap(item, 10);
            TextView excerpt = text(summary.toString(), 13, muted, false); excerpt.setLineSpacing(dp(4), 1f); excerpt.setMaxLines(2); excerpt.setEllipsize(TextUtils.TruncateAt.END); item.addView(excerpt);
            item.setBackground(UiStyle.press(getContext(), surface, 22, accent));
            item.setContentDescription("查看复盘：" + dateTitle(date)); item.setOnClickListener(v -> open(date, note.finished));
            addView(item); gap(this, 10); shown++;
        }
        if (shown == 0) { gap(this, 36); TextView empty = text("还没有复盘记录", 15, ink, true); empty.setGravity(Gravity.CENTER); addView(empty); gap(this, 8); TextView hint = text("点击「写复盘」，记录一点收获和想法", 12, muted, false); hint.setGravity(Gravity.CENTER); addView(hint); }
        gap(this, 84);
    }
    private void buildContent() {
        if (type != 0) {
            LinearLayout targetCard = card();
            LinearLayout heading = row(); LinearLayout titleGroup = new LinearLayout(getContext()); titleGroup.setOrientation(VERTICAL);
            titleGroup.addView(text(type == 1 ? "本周重点目标" : "本月重点目标", 16, ink, true));
            if (goals.length() > 0) titleGroup.addView(text(goalProgress(), 12, muted, false));
            heading.addView(titleGroup, new LayoutParams(0, dp(48), 1));
            if (!reading) heading.addView(action("编辑目标", this::editGoals)); targetCard.addView(heading);
            if (goals.length() == 0) targetCard.addView(text("写下 1～3 个希望达到的结果，也可以直接复盘。", 13, muted, false));
            for (int i = 0; i < goals.length(); i++) {
                JSONObject goal = goals.optJSONObject(i); if (goal == null) continue;
                String[] labels = {"进行中", "已达成", "未达成"}; int state = Math.max(0, Math.min(2, goal.optInt("state")));
                addGoalTitle(targetCard, goal, true); gap(targetCard, 4);
                String approach = goal.optString("approach"); if (!approach.isEmpty()) { targetCard.addView(text(approach, 12, muted, false)); gap(targetCard, 4); }
                targetCard.addView(text(labels[state], 12, state == 1 ? accent : muted, false)); gap(targetCard, 10);
            }
            if (!reading) targetCard.addView(action("选择带入上期未达成目标", this::carryGoals)); addView(targetCard); gap(this, 14);
        }
        if (type == 0) {
            LinearLayout moodCard = card();
            moodCard.addView(text(reading ? "那天的心情" : "今天的心情", 13, muted, false)); gap(moodCard, 8);
            LinearLayout moodRow = row();
            for (int i = 0; i < MOOD_EMOJI.length; i++) {
                final int index = i;
                TextView emoji = text(MOOD_EMOJI[i], 24, ink, false); emoji.setGravity(Gravity.CENTER);
                if (!reading) emoji.setOnClickListener(v -> {
                    record.mood = record.mood == index ? -1 : index;
                    styleMoodRow(moodRow);
                    dirty = true; persist();
                });
                moodRow.addView(emoji, new LayoutParams(0, dp(48), 1));
            }
            moodCard.addView(moodRow);
            styleMoodRow(moodRow);
            addView(moodCard); gap(this, 14);
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
            facts.setOnClickListener(v -> new MaterialAlertDialogBuilder(getContext()).setTitle("本期任务记录").setMessage(details.trim()).setPositiveButton("关闭", null).show());
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
    private String goalProgress() {
        int done = 0; for (int i = 0; i < goals.length(); i++) { JSONObject goal = goals.optJSONObject(i); if (goal != null && goal.optInt("state") == 1) done++; }
        return "已达成 " + done + "/" + goals.length();
    }
    private void addGoalTitle(LinearLayout parent, JSONObject goal, boolean bold) {
        int state = Math.max(0, Math.min(2, goal.optInt("state")));
        boolean dark = (surface & 0xffffff) != 0xffffff;
        int tint = state == 1 ? soft : android.graphics.Color.parseColor(dark ? (state == 0 ? "#2D3C49" : "#343C35") : (state == 0 ? "#EDF1F7" : "#EFF0EB"));
        LinearLayout line = row(); TextView marker = text(state == 1 ? "✓" : state == 0 ? "•" : "—", 12, state == 1 ? accent : muted, true);
        marker.setGravity(Gravity.CENTER); marker.setBackground(background(tint, 7));
        marker.setContentDescription(new String[]{"进行中", "已达成", "未达成"}[state]);
        line.addView(marker, new LayoutParams(dp(22), dp(22)));
        TextView title = text(goal.optString("title"), bold ? 15 : 13, ink, bold); title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams params = new LayoutParams(0, -2, 1); params.leftMargin = dp(8); line.addView(title, params); parent.addView(line);
    }
    private void styleMoodRow(LinearLayout moodRow) {
        for (int i = 0; i < moodRow.getChildCount(); i++) {
            TextView emoji = (TextView) moodRow.getChildAt(i);
            boolean selected = record != null && record.mood == i;
            GradientDrawable background = background(selected ? soft : android.graphics.Color.TRANSPARENT, 14);
            if (selected) background.setStroke(dp(1), accent);
            emoji.setBackground(background);
        }
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
        snapshot.finished = record.finished; snapshot.updatedAt = System.currentTimeMillis(); snapshot.mood = record.mood;
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
        new MaterialAlertDialogBuilder(getContext()).setTitle("重点目标 · 最多 3 项").setView(scroll).setNegativeButton("取消", null).setPositiveButton("保存", (d, w) -> {
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
                new MaterialAlertDialogBuilder(getContext()).setTitle("选择继续的目标").setMultiChoiceItems(labels, checked, (d, i, on) -> checked[i] = on).setNegativeButton("取消", null).setPositiveButton("带入", (d, w) -> {
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
