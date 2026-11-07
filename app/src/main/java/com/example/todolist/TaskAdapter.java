package com.example.todolist;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import androidx.recyclerview.widget.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/** Home list: calendar header plus completed/pending sections, animated by DiffUtil. */
final class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    interface Listener {
        void onToggle(Task task);
        void onOpen(Task task);
        void onMore(Task task);
        void onSwipeComplete(Task task);
        void onSwipeDelete(Task task);
        void onOpenStats();
    }
    private static final int CALENDAR = 0, SECTION = 1, TASK = 2, EMPTY = 3, SPACER = 4, CARDS = 5;
    private static final class Row {
        final int type; final String key; final String text; final Task task;
        Row(int type, String key, String text, Task task) {
            this.type = type; this.key = key; this.text = text; this.task = task;
        }
    }
    private final Context context;
    private final boolean dark;
    private final int surface, ink, muted, accent, soft, border;
    private final Supplier<View> calendar;
    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();
    private List<Task> lastTasks = new ArrayList<>();
    private boolean first = true;
    private View calendarView;
    private FrameLayout calendarHolder;

    TaskAdapter(Context context, boolean dark, int surface, int ink, int muted, int accent, int soft, int border,
                Supplier<View> calendar, Listener listener) {
        this.context = context; this.dark = dark;
        this.surface = surface; this.ink = ink; this.muted = muted; this.accent = accent; this.soft = soft; this.border = border;
        this.calendar = calendar; this.listener = listener;
    }

    Task taskAt(int position) {
        if (position < 0 || position >= rows.size()) return null;
        Row row = rows.get(position);
        return row.type == TASK ? row.task : null;
    }

    static int[] substepsProgress(String json) {
        int done = 0, total = 0;
        try {
            JSONArray steps = new JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.optJSONObject(i);
                if (step == null) continue;
                total++;
                if (step.optBoolean("done")) done++;
            }
        } catch (JSONException ignored) {}
        return new int[]{done, total};
    }

    void setData(List<Task> tasks, LocalDate selected) {
        lastTasks = tasks;
        List<Row> next = new ArrayList<>();
        next.add(new Row(CALENDAR, "calendar", "", null));
        next.add(new Row(CARDS, "cards", cardsSummary(tasks), null));
        next.add(new Row(SECTION, "section-done", "🌷 " + selected.format(DateTimeFormatter.ofPattern("M月d日")) + " · 已完成", null));
        List<Task> completed = MonthGrid.tasksOn(tasks, selected);
        if (completed.isEmpty()) next.add(new Row(EMPTY, "empty-done", "当天暂无完成记录", null));
        for (Task t : completed) next.add(new Row(TASK, "task-" + t.id, "", t.copy()));
        boolean historical = selected.isBefore(LocalDate.now());
        next.add(new Row(SECTION, "section-pending", historical ? "🍂 未完成" : "🌱 待办", null));
        int pending = 0;
        for (Task t : tasks) {
            if (TaskDates.visibleInDateList(t, selected) && t.completedAt == null) {
                next.add(new Row(TASK, "task-" + t.id, "", t.copy())); pending++;
            }
        }
        if (pending == 0) next.add(new Row(EMPTY, "empty-pending", historical ? "没有未完成事项" : "今天很轻松，享受吧！", null));
        next.add(new Row(SPACER, "spacer", "", null));
        calendarView = calendar.get();
        if (calendarHolder != null) {
            calendarHolder.removeAllViews();
            calendarHolder.addView(calendarView, new FrameLayout.LayoutParams(-1, -2));
        }
        if (first) {
            first = false;
            rows.clear(); rows.addAll(next);
            notifyDataSetChanged();
            return;
        }
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return rows.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                return rows.get(oldPos).key.equals(next.get(newPos).key);
            }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                Row a = rows.get(oldPos), b = next.get(newPos);
                if (a.type != b.type) return false;
                if (a.type == TASK) return sameTask(a.task, b.task);
                if (a.type == CALENDAR || a.type == SPACER) return true;
                return a.text.equals(b.text);
            }
        });
        rows.clear(); rows.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    private static String cardsSummary(List<Task> tasks) {
        LocalDate today = LocalDate.now();
        int total = TaskDates.countAssigned(tasks, today, today.plusDays(1), false);
        int done = TaskDates.countAssigned(tasks, today, today.plusDays(1), true);
        int[] streak = streakInfo(tasks);
        return done + "/" + total + ":" + streak[0] + ":" + streak[1];
    }
    private static int[] streakInfo(List<Task> tasks) {
        Set<LocalDate> days = new HashSet<>();
        for (Task t : tasks) if (t.deletedAt == null && t.completedAt != null) days.add(TaskDates.date(t.completedAt));
        LocalDate day = LocalDate.now();
        if (!days.contains(day)) day = day.minusDays(1);
        int streak = 0;
        while (days.contains(day)) { streak++; day = day.minusDays(1); }
        int mask = 0;
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) if (days.contains(today.minusDays(6 - i))) mask |= 1 << i;
        return new int[]{streak, mask};
    }

    private static boolean sameTask(Task a, Task b) {
        return Objects.equals(a.title, b.title) && Objects.equals(a.tag, b.tag) && a.priority == b.priority
            && Objects.equals(a.completedAt, b.completedAt) && Objects.equals(a.estimatedMinutes, b.estimatedMinutes)
            && Objects.equals(a.reminderAt, b.reminderAt) && Objects.equals(a.taskDate, b.taskDate)
            && Objects.equals(a.repeat, b.repeat) && Objects.equals(a.note, b.note) && Objects.equals(a.substeps, b.substeps);
    }

    @Override public int getItemCount() { return rows.size(); }
    @Override public int getItemViewType(int position) { return rows.get(position).type; }

    @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case CALENDAR: {
                FrameLayout frame = new FrameLayout(context);
                frame.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerView.ViewHolder(frame) {};
            }
            case SECTION: {
                TextView view = text("", 16, ink, true);
                view.setPadding(dp(24), dp(12), dp(8), dp(6));
                view.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerView.ViewHolder(view) {};
            }
            case EMPTY: {
                LinearLayout box = new LinearLayout(context);
                box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);
                TextView emoji = text("", 26, muted, false); emoji.setTag("emoji"); emoji.setGravity(Gravity.CENTER);
                box.addView(emoji, new LinearLayout.LayoutParams(-1, -2));
                TextView label = text("", 13, muted, false); label.setTag("label"); label.setGravity(Gravity.CENTER);
                box.addView(label, new LinearLayout.LayoutParams(-1, -2));
                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(92));
                params.setMargins(dp(16), 0, dp(16), dp(6));
                box.setLayoutParams(params);
                return new RecyclerView.ViewHolder(box) {};
            }
            case SPACER: {
                View view = new View(context);
                view.setLayoutParams(new RecyclerView.LayoutParams(1, dp(84)));
                return new RecyclerView.ViewHolder(view) {};
            }
            case CARDS: {
                LinearLayout bento = new LinearLayout(context);
                bento.setOrientation(LinearLayout.VERTICAL); bento.setBaselineAligned(false);
                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
                params.setMargins(dp(16), dp(2), dp(16), dp(6));
                bento.setLayoutParams(params);
                LinearLayout topRow = new LinearLayout(context); topRow.setBaselineAligned(false);
                LinearLayout progress = dashCard();
                progress.addView(dashLabel("今日进度"));
                TextView progressValue = dashValue("0 / 0"); progressValue.setTag("progressValue");
                progress.addView(progressValue);
                ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
                bar.setTag("progressBar"); bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
                bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(soft));
                LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(6));
                barParams.setMargins(0, dp(8), 0, dp(6));
                progress.addView(bar, barParams);
                TextView progressSub = text("", 11, muted, false); progressSub.setTag("progressSub");
                progress.addView(progressSub);
                LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, -2, 2);
                leftParams.setMargins(0, 0, dp(5), 0);
                topRow.addView(progress, leftParams);
                LinearLayout streak = dashCard();
                streak.addView(dashLabel("连续打卡"));
                TextView streakValue = dashValue("0 天"); streakValue.setTag("streakValue");
                streak.addView(streakValue);
                LinearLayout dots = new LinearLayout(context); dots.setTag("dots");
                for (int i = 0; i < 7; i++) {
                    View dot = new View(context);
                    LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
                    dotParams.setMargins(dp(2), dp(10), dp(2), 0);
                    dots.addView(dot, dotParams);
                }
                streak.addView(dots);
                LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -2, 1);
                rightParams.setMargins(dp(5), 0, 0, 0);
                topRow.addView(streak, rightParams);
                bento.addView(topRow);
                return new RecyclerView.ViewHolder(bento) {};
            }
            default: return new RecyclerView.ViewHolder(taskView()) {};
        }
    }

    @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        switch (row.type) {
            case CALENDAR: {
                FrameLayout frame = (FrameLayout) holder.itemView;
                frame.removeAllViews();
                if (calendarView != null) frame.addView(calendarView, new FrameLayout.LayoutParams(-1, -2));
                calendarHolder = frame;
                break;
            }
            case SECTION: ((TextView) holder.itemView).setText(row.text); break;
            case CARDS: bindCards(holder.itemView); break;
            case EMPTY: {
                ((TextView) holder.itemView.findViewWithTag("emoji")).setText(
                    row.text.startsWith("没有匹配") ? "🔍" : row.text.equals("没有未完成事项") ? "🌙" : row.text.contains("完成") ? "🌤" : "🌿");
                ((TextView) holder.itemView.findViewWithTag("label")).setText(row.text);
                break;
            }
            case TASK: bindTask(holder.itemView, row.task); break;
        }
    }

    private LinearLayout dashCard() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(UiStyle.pressBordered(context, surface, 20, border, accent));
        card.setOnClickListener(v -> listener.onOpenStats());
        return card;
    }
    private TextView dashLabel(String label) {
        return text(label, 11, muted, false);
    }
    private TextView dashValue(String value) {
        TextView view = text(value, 26, ink, true);
        view.setPadding(0, dp(2), 0, 0);
        return view;
    }
    private void bindCards(View itemView) {
        LocalDate today = LocalDate.now();
        int total = TaskDates.countAssigned(lastTasks, today, today.plusDays(1), false);
        int done = TaskDates.countAssigned(lastTasks, today, today.plusDays(1), true);
        ((TextView) itemView.findViewWithTag("progressValue")).setText(done + " / " + total);
        ProgressBar bar = itemView.findViewWithTag("progressBar");
        bar.setMax(Math.max(1, total)); bar.setProgress(done);
        ((TextView) itemView.findViewWithTag("progressSub")).setText(
            total == 0 ? "今天还没有安排事项" : done == total ? "全部完成，太棒了！" : "已完成 " + Math.round(done * 100f / total) + "%");
        int[] streak = streakInfo(lastTasks);
        ((TextView) itemView.findViewWithTag("streakValue")).setText("🔥 " + streak[0] + " 天");
        LinearLayout dots = itemView.findViewWithTag("dots");
        for (int i = 0; i < dots.getChildCount(); i++) {
            boolean filled = (streak[1] & (1 << i)) != 0;
            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(filled ? accent : blend(accent, surface, .16f));
            dots.getChildAt(i).setBackground(dot);
        }
    }

    private View taskView() {
        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(8), dp(6), dp(8));
        card.setBackground(UiStyle.pressBordered(context, surface, 20, border, accent));
        View colorBar = new View(context); colorBar.setTag("bar");
        android.graphics.drawable.GradientDrawable barBg = new android.graphics.drawable.GradientDrawable();
        barBg.setCornerRadius(dp(3)); colorBar.setBackground(barBg);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(4), dp(36));
        barParams.setMargins(0, 0, dp(4), 0);
        card.addView(colorBar, barParams);
        ImageButton check = new ImageButton(context); check.setTag("check");
        check.setBackground(UiStyle.press(context, Color.TRANSPARENT, 22, accent));
        card.addView(check, new LinearLayout.LayoutParams(dp(44), dp(48)));
        LinearLayout content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL); content.setTag("content");
        card.addView(content, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton more = new ImageButton(context); more.setTag("more");
        more.setImageDrawable(UiStyle.icon(context, "more", muted, 22));
        more.setBackground(UiStyle.press(context, Color.TRANSPARENT, 22, accent));
        card.addView(more, new LinearLayout.LayoutParams(dp(44), dp(48)));
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
        params.setMargins(dp(16), 0, dp(16), dp(6));
        card.setLayoutParams(params);
        return card;
    }

    private void bindTask(View itemView, Task t) {
        View colorBar = itemView.findViewWithTag("bar");
        ((android.graphics.drawable.GradientDrawable) colorBar.getBackground().mutate()).setColor(tagColor(t.tag));
        colorBar.setAlpha(t.completedAt == null ? 1f : .35f);
        ImageButton check = itemView.findViewWithTag("check");
        LinearLayout content = itemView.findViewWithTag("content");
        ImageButton more = itemView.findViewWithTag("more");
        content.removeAllViews();
        TextView title = text(t.title, 16, t.completedAt == null ? ink : muted, false);
        title.setMaxLines(3); title.setEllipsize(TextUtils.TruncateAt.END);
        if (t.completedAt != null) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        content.addView(title); space(content, 2);
        LinearLayout meta = new LinearLayout(context); meta.setGravity(Gravity.CENTER_VERTICAL);
        int tagColor = tagColor(t.tag);
        TextView pill = text(t.tag.isEmpty() ? "未分类" : t.tag, 10, dark ? blend(tagColor, Color.WHITE, .35f) : tagColor, false);
        android.graphics.drawable.GradientDrawable pillBg = new android.graphics.drawable.GradientDrawable();
        pillBg.setColor(blend(tagColor, surface, dark ? .30f : .15f)); pillBg.setCornerRadius(dp(10));
        pill.setBackground(pillBg);
        pill.setPadding(dp(8), dp(2), dp(8), dp(2));
        meta.addView(pill);
        meta.addView(text("  " + new String[]{"低优先级", "中优先级", "高优先级"}[t.priority], 11,
            t.priority == 2 ? color(dark ? "#F0A89B" : "#C96A5A") : t.priority == 1 ? color(dark ? "#EFBE93" : "#B08A3E") : muted, false));
        content.addView(meta);
        String timing = t.repeat == null ? "" : t.repeat.equals("daily") ? "每天重复" : t.repeat.equals("weekly") ? "每周重复" : "";
        if (t.estimatedMinutes != null) timing += (timing.isEmpty() ? "" : " · ") + "预计 " + TaskDates.durationLabel(t.estimatedMinutes);
        if (t.reminderAt != null) {
            LocalDate taskDay = TaskDates.date(t.taskDate == null ? t.createdAt : t.taskDate);
            String reminder = TaskDates.date(t.reminderAt).equals(taskDay)
                ? Instant.ofEpochMilli(t.reminderAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                : dateTime(t.reminderAt);
            timing += (timing.isEmpty() ? "" : " · ") + "提醒 " + reminder;
        }
        if (!timing.isEmpty()) { space(content, 4); content.addView(text(timing, 12, muted, false)); }
        int[] progress = substepsProgress(t.substeps);
        if (progress[1] > 0) { space(content, 4); content.addView(text("子任务 " + progress[0] + "/" + progress[1], 12, progress[0] == progress[1] ? accent : muted, false)); }
        if (t.note != null && !t.note.isEmpty()) {
            space(content, 4);
            TextView note = text(t.note, 12, muted, false);
            note.setMaxLines(1); note.setEllipsize(TextUtils.TruncateAt.END);
            content.addView(note);
        }
        check.setScaleX(1f); check.setScaleY(1f);
        check.setImageDrawable(UiStyle.icon(context, t.completedAt == null ? "circle" : "checkfill", t.completedAt == null ? muted : accent, 24));
        check.setContentDescription(t.completedAt == null ? "完成：" + t.title : "恢复待办：" + t.title);
        check.setOnClickListener(v -> {
            v.performHapticFeedback(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
            v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(110).withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(new OvershootInterpolator(3f)).start()).start();
            v.postDelayed(() -> listener.onToggle(t), 180);
        });
        more.setContentDescription("编辑或删除：" + t.title);
        more.setOnClickListener(v -> listener.onMore(t));
        itemView.setOnClickListener(v -> listener.onOpen(t));
        itemView.setOnLongClickListener(v -> { listener.onMore(t); return true; });
    }

    private int dp(float value) { return UiStyle.dp(context, value); }
    private int color(String value) { return Color.parseColor(value); }
    private int tagColor(String tag) {
        switch (tag == null || tag.isEmpty() ? "" : tag) {
            case "学习": return color("#6E93BC");
            case "工作": return color("#8F7FBE");
            case "生活": return color("#6F8F6A");
            case "": return muted;
            default: {
                int[] palette = {color("#C4748A"), color("#CE8A4E"), color("#5E9A94"), color("#B08A3E")};
                return palette[Math.floorMod(tag.hashCode(), palette.length)];
            }
        }
    }
    private static int blend(int a, int b, float fraction) {
        float inverse = 1f - fraction;
        return Color.argb(
            Math.round(Color.alpha(a) * fraction + Color.alpha(b) * inverse),
            Math.round(Color.red(a) * fraction + Color.red(b) * inverse),
            Math.round(Color.green(a) * fraction + Color.green(b) * inverse),
            Math.round(Color.blue(a) * fraction + Color.blue(b) * inverse));
    }
    private String dateTime(long time) {
        return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"));
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value); view.setTextSize(Math.max(12, size)); view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        UiStyle.typography(view, bold);
        return view;
    }
    private void space(LinearLayout parent, int height) {
        parent.addView(new View(context), new LinearLayout.LayoutParams(1, dp(height)));
    }

    /** Swipe right to complete, left to delete, with colored background and icon revealed. */
    static final class Swipe extends ItemTouchHelper.Callback {
        private final TaskAdapter adapter;
        private final Listener listener;
        private final int completeColor, deleteColor, iconInset, corner;
        private final Drawable checkIcon, trashIcon;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        Swipe(TaskAdapter adapter, Listener listener, Context context, boolean dark, int accent) {
            this.adapter = adapter; this.listener = listener;
            completeColor = accent;
            deleteColor = Color.parseColor(dark ? "#A85B4C" : "#C26A5A");
            checkIcon = UiStyle.icon(context, "check", Color.WHITE, 22);
            trashIcon = UiStyle.icon(context, "trash", Color.WHITE, 22);
            iconInset = UiStyle.dp(context, 20);
            corner = UiStyle.dp(context, 20);
        }
        @Override public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            int position = viewHolder.getBindingAdapterPosition();
            Task task = position == RecyclerView.NO_POSITION ? null : adapter.taskAt(position);
            if (task == null) return makeMovementFlags(0, 0);
            return makeMovementFlags(0, ItemTouchHelper.LEFT | (task.completedAt == null ? ItemTouchHelper.RIGHT : 0));
        }
        @Override public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) { return false; }
        @Override public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            int position = viewHolder.getBindingAdapterPosition();
            Task task = position == RecyclerView.NO_POSITION ? null : adapter.taskAt(position);
            if (task == null) return;
            if (direction == ItemTouchHelper.LEFT) listener.onSwipeDelete(task); else listener.onSwipeComplete(task);
        }
        @Override public void onChildDraw(Canvas canvas, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                          float dX, float dY, int actionState, boolean isCurrentlyActive) {
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0) {
                View view = viewHolder.itemView;
                boolean complete = dX > 0;
                paint.setColor(complete ? completeColor : deleteColor);
                float left = complete ? view.getLeft() - dX : view.getRight();
                float right = complete ? view.getLeft() : view.getRight() - dX;
                rect.set(left, view.getTop(), right, view.getBottom());
                canvas.drawRoundRect(rect, corner, corner, paint);
                Drawable icon = complete ? checkIcon : trashIcon;
                int size = icon.getIntrinsicWidth();
                int top = view.getTop() + (view.getHeight() - size) / 2;
                int iconLeft = complete ? (int) (view.getLeft() - dX) + iconInset : (int) (view.getRight() - dX) - iconInset - size;
                icon.setAlpha((int) (255 * Math.min(1f, Math.abs(dX) / (size + iconInset * 2f))));
                icon.setBounds(iconLeft, top, iconLeft + size, top + size);
                icon.draw(canvas);
                icon.setAlpha(255);
            }
            super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }
}
