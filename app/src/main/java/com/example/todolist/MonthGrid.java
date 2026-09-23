package com.example.todolist;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

/** Seven columns fill the available height; every day owns its overflow affordance. */
public final class MonthGrid extends ViewGroup {
    public interface Listener { void selected(LocalDate date, boolean showDetails); }
    private final List<DayCell> days = new ArrayList<>();
    private final int offset, weeks;
    private final boolean dark;
    private final Listener listener;
    private LocalDate selected;
    private final int ink, accent, border;
    public MonthGrid(Context c, YearMonth month, LocalDate selected, List<Task> tasks, boolean dark, Listener listener) {
        super(c); this.selected = selected; this.dark = dark; this.listener = listener;
        ink = Color.parseColor(dark ? "#E7EEE6" : "#20392D");
        accent = Color.parseColor(dark ? "#A4D7AD" : "#236751");
        border = Color.parseColor(dark ? "#405448" : "#D5DED7");
        setBackgroundColor(Color.parseColor(dark ? "#1D2B24" : "#FFFFFF"));
        offset = month.atDay(1).getDayOfWeek().getValue() - 1;
        weeks = (offset + month.lengthOfMonth() + 6) / 7;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            DayCell cell = new DayCell(c, date, tasksOn(tasks, date));
            days.add(cell); addView(cell);
        }
    }
    static List<Task> tasksOn(List<Task> tasks, LocalDate date) {
        List<Task> result = new ArrayList<>();
        for (Task t : tasks) if (t.deletedAt == null && t.completedAt != null && TaskDates.date(t.completedAt).equals(date)) result.add(t);
        result.sort(Comparator.comparingLong((Task t) -> t.completedAt).thenComparingLong(t -> t.id));
        return result;
    }
    public void select(LocalDate date) { selected = date; for (DayCell cell : days) cell.style(); }
    private int dp(float x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        for (int i = 0; i < days.size(); i++) {
            int index = offset + i, col = index % 7, row = index / 7;
            int width = (col + 1) * w / 7 - col * w / 7;
            int height = (row + 1) * h / weeks - row * h / weeks;
            days.get(i).prepare(Math.max(1, height));
            days.get(i).measure(MeasureSpec.makeMeasureSpec(Math.max(1, width), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(1, height), MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < days.size(); i++) {
            int index = offset + i, col = index % 7, row = index / 7;
            days.get(i).layout(col * getWidth() / 7, row * getHeight() / weeks, (col + 1) * getWidth() / 7, (row + 1) * getHeight() / weeks);
        }
    }
    private void choose(LocalDate date, boolean detail) { select(date); listener.selected(date, detail); }
    private class DayCell extends LinearLayout {
        final LocalDate date; final List<Task> tasks; int preparedHeight = -1;
        TextView number;
        DayCell(Context c, LocalDate date, List<Task> tasks) {
            super(c); this.date = date; this.tasks = tasks; setOrientation(VERTICAL);
            setPadding(dp(3), dp(3), dp(3), dp(3));
            setContentDescription(date + "，已完成 " + tasks.size() + " 件");
            setOnClickListener(v -> choose(date, false));
            setOnLongClickListener(v -> { choose(date, true); return true; });
        }
        TextView label(String value, int size) {
            TextView v = new TextView(getContext()); v.setText(value); v.setTextSize(size); v.setTextColor(ink);
            v.setIncludeFontPadding(false); return v;
        }
        void prepare(int height) {
            if (height == preparedHeight) return;
            preparedHeight = height; removeAllViews();
            float scale = getResources().getConfiguration().fontScale;
            int head = dp(24 * scale), item = dp(34 * scale), more = dp(20 * scale);
            number = label(date.equals(LocalDate.now()) ? "今" : String.valueOf(date.getDayOfMonth()), 13);
            number.setGravity(Gravity.CENTER); number.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            LinearLayout.LayoutParams numberLayout = new LinearLayout.LayoutParams(head, head); numberLayout.gravity = Gravity.CENTER_HORIZONTAL;
            addView(number, numberLayout);
            int available = Math.max(0, height - dp(6) - head);
            boolean overflow = tasks.size() * item > available;
            int shown = Math.min(tasks.size(), Math.max(0, (available - (overflow ? more : 0)) / item));
            for (int i = 0; i < shown; i++) {
                Task task = tasks.get(i);
                TextView title = label(task.title, 10); title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END);
                title.setGravity(Gravity.CENTER_VERTICAL); title.setPadding(dp(2), 0, dp(2), 0);
                GradientDrawable tint = new GradientDrawable();
                tint.setColor(Color.parseColor(dark ? "#334B3D" : ("工作".equals(task.tag) ? "#E4EDF7" : "#E8F0E7"))); tint.setCornerRadius(dp(3));
                title.setBackground(tint); title.setContentDescription(task.title);
                title.setOnClickListener(v -> choose(date, true));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, item - dp(3)); p.bottomMargin = dp(3); addView(title, p);
            }
            View spacer = new View(getContext()); addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
            if (shown < tasks.size()) {
                TextView plus = label("＋" + (tasks.size() - shown), 12); plus.setGravity(Gravity.CENTER); plus.setTextColor(accent);
                plus.setContentDescription(date + "，还有 " + (tasks.size() - shown) + " 件，查看全部");
                plus.setOnClickListener(v -> choose(date, true)); addView(plus, new LinearLayout.LayoutParams(-1, more));
            }
            style();
        }
        void style() {
            boolean active = date.equals(selected); setSelected(active);
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(Color.TRANSPARENT); shape.setStroke(dp(1), Color.parseColor(dark ? "#2B3A31" : "#E5EDE1")); setBackground(shape);
            if (number != null) {
                GradientDrawable chip = new GradientDrawable(); chip.setShape(GradientDrawable.OVAL);
                boolean today = date.equals(LocalDate.now());
                int todayBackground = getContext().getColor(dark ? R.color.calendar_today_background_dark : R.color.calendar_today_background);
                chip.setColor(active ? accent : todayBackground); number.setBackground(active || today ? chip : null);
                number.setTextColor(active ? (dark ? Color.parseColor("#121C18") : Color.WHITE) : date.equals(LocalDate.now()) ? accent : ink);
            }
        }
    }
}
