package com.example.todolist;

import android.Manifest;
import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import android.annotation.SuppressLint;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private List<Task> tasks = new ArrayList<>();
    private List<Task> trashed = new ArrayList<>();
    private FrameLayout root;
    private LinearLayout body;
    private String page = "清单", filter = "全部";
    private YearMonth month = YearMonth.now();
    private LocalDate selected = LocalDate.now();
    private boolean dark;
    private boolean homeCalendarExpanded;
    private int bg, surface, ink, muted, accent, soft, border;
    private static final int EXPORT_MONTH = 103;
    private YearMonth pendingExportMonth = YearMonth.now();
    private BroadcastReceiver taskChangedReceiver;
    private int savedScrollY;
    private ScrollView activeScroll;
    private int monthTransition;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override public void onCreate(Bundle state) {
        dark = getPreferences(MODE_PRIVATE).getBoolean("dark", false);
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        if (state != null) pendingExportMonth = YearMonth.parse(state.getString("exportMonth", YearMonth.now().toString()));
        if (state != null) homeCalendarExpanded = state.getBoolean("homeCalendarExpanded", false);
        if (state != null) { page = state.getString("page", "清单"); filter = state.getString("filter", "全部"); month = YearMonth.parse(state.getString("month")); selected = LocalDate.parse(state.getString("selected")); }
        Reminders.channel(this);
        colors(); render();
        taskChangedReceiver = new BroadcastReceiver() { @Override public void onReceive(Context context, Intent intent) { reload(); } };
        IntentFilter taskFilter = new IntentFilter("com.example.todolist.TASKS_CHANGED");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(taskChangedReceiver, taskFilter, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(taskChangedReceiver, taskFilter);
    }
    @Override protected void onResume() { super.onResume(); reload(); }
    @Override protected void onSaveInstanceState(Bundle b) { super.onSaveInstanceState(b); b.putString("page", page); b.putString("filter", filter); b.putString("month", month.toString()); b.putString("selected", selected.toString()); b.putString("exportMonth", pendingExportMonth.toString()); b.putBoolean("homeCalendarExpanded", homeCalendarExpanded); }
    @Override protected void onDestroy() { main.removeCallbacksAndMessages(null); if (taskChangedReceiver != null) unregisterReceiver(taskChangedReceiver); super.onDestroy(); }
    private void colors() {
        bg = color(dark ? "#121C18" : "#F7F8F2"); surface = color(dark ? "#1D2B24" : "#FFFFFF");
        ink = color(dark ? "#E7EEE6" : "#20392D"); muted = color(dark ? "#A0B0A5" : "#7A887D");
        accent = color(dark ? "#A4D7AD" : "#29664B"); soft = color(dark ? "#30483A" : "#E9EFE1"); border = color(dark ? "#34463A" : "#E6EAE0");
    }
    private int color(String s) { return Color.parseColor(s); }
    private int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable box(int fill, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); return d; }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private void space(LinearLayout l, int h) { View v = new View(this); l.addView(v, new LinearLayout.LayoutParams(1, dp(h))); }
    private TextView button(String label, boolean filled, Runnable action) {
        TextView b = text(label, 14, filled ? (dark ? bg : Color.WHITE) : accent, true);
        b.setGravity(Gravity.CENTER); b.setPadding(dp(14), dp(12), dp(14), dp(12)); b.setMinHeight(dp(48));
        b.setBackground(box(filled ? accent : soft, 16)); b.setOnClickListener(v -> action.run()); return b;
    }
    private void heading(String name, String sub) { body.addView(text(name, 30, ink, true)); space(body, 6); body.addView(text(sub, 13, muted, false)); space(body, 24); }
    private LinearLayout card() { LinearLayout l = column(); l.setPadding(dp(20), dp(20), dp(20), dp(20)); l.setBackground(box(surface, 24)); return l; }
    private void reload() {
        TodoDb.IO.execute(() -> { List<Task> all = TodoDb.get(this).tasks().all(); List<Task> trash = TodoDb.get(this).tasks().trash(); main.post(() -> { if (!isDestroyed()) { tasks = all; trashed = trash; render(); } }); });
    }
    private void render() {
        if (activeScroll != null) savedScrollY = activeScroll.getScrollY();
        root = new FrameLayout(this); root.setBackgroundColor(bg); root.setClipChildren(false);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        boolean list = page.equals("清单");
        boolean calendar = page.equals("月历");
        LinearLayout shell = column(); shell.setBackgroundColor(bg);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout brand = row(); brand.setPadding(dp(list ? 16 : 24), dp(list ? 4 : 14), dp(list ? 16 : 24), dp(list ? 0 : 12));
        if (list) {
            brand.addView(text(LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)), 12, muted, false), new LinearLayout.LayoutParams(0, dp(48), 1));
            brand.addView(homeAction("添加到桌面", this::pinWidget));
        } else {
            TextView logo = text("✓  一件一件", 18, accent, true); brand.addView(logo, new LinearLayout.LayoutParams(0, dp(42), 1));
        }
        if (!calendar && !list) shell.addView(brand);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); activeScroll = scroll;
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> savedScrollY = y);
        body = column(); body.setPadding(dp(calendar ? 4 : 16), dp(list ? 2 : 8), dp(calendar ? 4 : 16), dp(calendar ? 0 : 12));
        if (calendar) shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        else { scroll.addView(body); shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); scroll.post(() -> scroll.scrollTo(0, savedScrollY)); }
        if (list) homePage(); else if (calendar) calendarPage(); else if (page.equals("统计")) statsPage(); else if (page.equals("回收站")) trashPage(); else settingsPage();
        LinearLayout nav = row(); nav.setPadding(dp(12), dp(list ? 4 : 10), dp(12), dp(list ? 4 : 12)); nav.setBackgroundColor(surface);
        for (String tab : new String[]{"清单", "月历", "统计", "设置"}) {
            String navLabel = tab.equals("月历") ? "月视图" : tab;
            boolean active = tab.equals(page) || (tab.equals("设置") && page.equals("回收站"));
            TextView b = text((tab.equals("清单") ? "☷  " : tab.equals("月历") ? "▦  " : tab.equals("统计") ? "◷  " : "☼  ") + tab, 14, active ? accent : muted, active);
            if (tab.equals("鏈堝巻")) b.setText("月视图");
            b.setGravity(Gravity.CENTER); b.setBackground(box(active ? soft : surface, 14)); b.setOnClickListener(v -> { page = tab; render(); }); nav.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        shell.addView(nav);
        if (list) {
            TextView fab = text("＋", 34, Color.WHITE, false); fab.setGravity(Gravity.CENTER); fab.setElevation(dp(8));
            fab.setContentDescription("添加待办事项"); fab.setBackground(box(accent, 32)); fab.setOnClickListener(v -> editor(null));
            FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.END | Gravity.BOTTOM); fp.setMargins(0, 0, dp(22), dp(86)); root.addView(fab, fp);
        }
        setContentView(root); root.requestApplyInsets();
    }
    private TextView homeAction(String label, Runnable action) {
        TextView view = button(label, false, action);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable background = box(soft, 12);
        background.setStroke(dp(1), border); view.setBackground(background);
        return view;
    }
    private void homePage() {
        body.setBackgroundColor(surface);
        int circleSize = Math.max(dp(32), Math.min(dp(44), (getResources().getDisplayMetrics().widthPixels - dp(20)) / 7));
        LinearLayout header = row();
        header.setPadding(dp(8), dp(4), dp(8), dp(2));
        TextView menu = text("☷", 28, ink, false); menu.setGravity(Gravity.CENTER); menu.setContentDescription("清单菜单");
        header.addView(menu, new LinearLayout.LayoutParams(dp(56), dp(52)));
        TextView title = text(month.format(DateTimeFormatter.ofPattern("yyyy.MM")), 23, ink, true); title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView widget = text("⌂", 24, ink, false); widget.setGravity(Gravity.CENTER); widget.setContentDescription("添加到桌面");
        widget.setOnClickListener(v -> pinWidget());
        header.addView(widget, new LinearLayout.LayoutParams(dp(44), dp(52)));
        TextView expand = text(homeCalendarExpanded ? "⌃" : "⌄", 26, ink, false); expand.setGravity(Gravity.CENTER);
        expand.setContentDescription(homeCalendarExpanded ? "折叠日历" : "展开日历");
        expand.setOnClickListener(v -> { homeCalendarExpanded = !homeCalendarExpanded; render(); });
        header.addView(expand, new LinearLayout.LayoutParams(dp(56), dp(52)));
        if (header.getChildCount() >= 3) {
            header.removeViewAt(0);
            header.removeViewAt(1);
            TextView widgetButton = homeAction("添加到桌面", this::pinWidget);
            widgetButton.setContentDescription("添加到桌面");
            header.addView(widgetButton, 1, new LinearLayout.LayoutParams(dp(104), dp(44)));
        }
        body.addView(header);

        LinearLayout weekdays = row();
        for (String w : new String[]{"日", "一", "二", "三", "四", "五", "六"}) {
            TextView day = text(w, 12, muted, true); day.setGravity(Gravity.CENTER);
            weekdays.addView(day, new LinearLayout.LayoutParams(0, dp(28), 1));
        }
        body.addView(weekdays);

        if (homeCalendarExpanded) {
            LocalDate first = month.atDay(1);
            LocalDate start = first.minusDays(first.getDayOfWeek().getValue() % 7);
            LinearLayout monthRows = column(); monthRows.setPadding(0, 0, 0, 0); monthRows.setBaselineAligned(false);
            for (int rowIndex = 0; rowIndex < 6; rowIndex++) {
                LinearLayout week = row(); week.setPadding(0, 0, 0, 0); week.setGravity(Gravity.CENTER); week.setBaselineAligned(false); week.setBackgroundColor(color(dark ? "#26362C" : "#F5F8F2"));
                for (int col = 0; col < 7; col++) {
                    LocalDate date = start.plusDays(rowIndex * 7L + col);
                    boolean inMonth = YearMonth.from(date).equals(month);
                    int dayColor = !inMonth ? color(dark ? "#526158" : "#B7C0B8") : (date.equals(selected) ? accent : ink);
                    TextView cell = text(String.valueOf(date.getDayOfMonth()) + (MonthGrid.tasksOn(tasks, date).isEmpty() ? "" : "\n•"), 15, dayColor, true);
                    cell.setGravity(Gravity.CENTER); cell.setMaxLines(2); cell.setContentDescription(date.toString());
                    if (date.equals(LocalDate.now())) cell.setText("今\n" + date.getDayOfMonth());
                    GradientDrawable dayBackground = box(date.equals(selected) ? soft : surface, 0);
                    dayBackground.setStroke(dp(date.equals(LocalDate.now()) ? 2 : 1), date.equals(LocalDate.now()) ? accent : border);
                    cell.setBackground(dayBackground); cell.setElevation(0);
                    List<Task> visibleDayTasks = MonthGrid.tasksOn(tasks, date);
                    StringBuilder taskLabel = new StringBuilder(date.equals(LocalDate.now()) ? "今" : String.valueOf(date.getDayOfMonth()));
                    int taskLines = 0;
                    for (Task task : visibleDayTasks) {
                        if (taskLines++ >= 2) { taskLabel.append("\n…"); break; }
                        taskLabel.append("\n").append(task.title);
                    }
                    cell.setText(taskLabel.toString()); cell.setTextColor(date.equals(selected) ? Color.WHITE : dayColor); cell.setTextSize(visibleDayTasks.isEmpty() ? 15 : 10);
                    cell.setTypeface(Typeface.DEFAULT, date.equals(selected) || !visibleDayTasks.isEmpty() ? Typeface.BOLD : Typeface.NORMAL);
                    cell.setGravity(Gravity.CENTER); cell.setMaxLines(3); cell.setEllipsize(TextUtils.TruncateAt.END);
                    GradientDrawable gridBackground = box(date.equals(selected) ? accent : (visibleDayTasks.isEmpty() ? surface : soft), 0);
                    gridBackground.setStroke(dp(1), color(dark ? "#34463A" : "#DCE6DA"));
                    cell.setBackground(gridBackground);
                    cell.setText(date.equals(LocalDate.now()) ? "今" : String.valueOf(date.getDayOfMonth()));
                    cell.setTextSize(15); cell.setTextColor(date.equals(selected) ? Color.WHITE : dayColor);
                    int circleFill = date.equals(selected) ? accent : (date.equals(LocalDate.now()) ? soft : Color.TRANSPARENT);
                    GradientDrawable circle = box(circleFill, 30); circle.setShape(GradientDrawable.OVAL);
                    cell.setBackground(circleFill == Color.TRANSPARENT ? null : circle);
                    cell.setOnClickListener(v -> { selected = date; month = YearMonth.from(date); render(); });
                    cell.setOnTouchListener(new View.OnTouchListener() {
                        float downX;
                        @Override public boolean onTouch(View v, MotionEvent e) {
                            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { downX = e.getX(); return false; }
                            if (e.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(e.getX() - downX) > dp(72)) {
                                swipeMonth(e.getX() < downX ? 1 : -1, monthRows); return true;
                            }
                            return false;
                        }
                    });
                    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(circleSize, circleSize); p.setMargins(dp(1), dp(1), dp(1), dp(1));
                    week.addView(cell, p);
                }
                monthRows.addView(week, new LinearLayout.LayoutParams(-1, dp(52)));
            }
            monthRows.setOnTouchListener(new View.OnTouchListener() {
                float downX;
                @Override public boolean onTouch(View v, MotionEvent e) {
                    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { downX = e.getX(); return true; }
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(e.getX() - downX) > dp(72)) {
                        swipeMonth(e.getX() < downX ? 1 : -1, v); return true;
                    }
                    return true;
                }
            });
            body.addView(monthRows, new LinearLayout.LayoutParams(-1, dp(312)));
            if (monthTransition != 0) {
                int direction = monthTransition; monthTransition = 0;
                monthRows.setTranslationX(direction * getResources().getDisplayMetrics().widthPixels);
                monthRows.animate().translationX(0).setDuration(220).start();
            }
        } else {
            LocalDate sunday = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() % 7);
            LinearLayout week = row(); week.setGravity(Gravity.CENTER);
            for (int i = 0; i < 7; i++) {
                LocalDate date = sunday.plusDays(i);
                TextView cell = text(String.valueOf(date.getDayOfMonth()) + (MonthGrid.tasksOn(tasks, date).isEmpty() ? "" : "\n•"), 17, date.equals(selected) ? Color.WHITE : ink, true);
                cell.setGravity(Gravity.CENTER); cell.setMaxLines(2); cell.setContentDescription(date.toString());
                if (date.equals(LocalDate.now())) cell.setText("今");
                GradientDrawable dayBackground = box(date.equals(selected) ? accent : surface, 22);
                if (date.equals(LocalDate.now())) dayBackground.setStroke(dp(2), accent);
                cell.setBackground(dayBackground); cell.setElevation(date.equals(selected) ? dp(4) : 0);
                int circleFill = date.equals(selected) ? accent : (date.equals(LocalDate.now()) ? soft : Color.TRANSPARENT);
                GradientDrawable circle = box(circleFill, 30); circle.setShape(GradientDrawable.OVAL);
                cell.setBackground(circleFill == Color.TRANSPARENT ? null : circle);
                if (!date.equals(selected) && date.equals(LocalDate.now())) cell.setTextColor(accent);
                cell.setOnClickListener(v -> { selected = date; month = YearMonth.from(date); render(); });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(circleSize, circleSize); p.setMargins(dp(1), dp(1), dp(1), dp(1));
                week.addView(cell, p);
            }
            body.addView(week);
        }
        TextView completedHeader = text(selected.format(DateTimeFormatter.ofPattern("M月d日")) + " · 已完成", 16, ink, true);
        completedHeader.setPadding(dp(8), dp(10), dp(8), dp(6)); body.addView(completedHeader);
        if (body.getChildCount() > 0) body.removeViewAt(body.getChildCount() - 1);
        List<Task> completed = MonthGrid.tasksOn(tasks, selected); int completedCount = 0;
        for (Task t : completed) { addTask(body, t); completedCount++; }
        if (completedCount == 0) { TextView empty = text("当天暂无完成记录", 13, muted, false); empty.setGravity(Gravity.CENTER); body.addView(empty, new LinearLayout.LayoutParams(-1, dp(54))); }
        if (selected.equals(LocalDate.now())) {
            TextView pendingHeader = text("待办", 16, ink, true); pendingHeader.setPadding(dp(8), dp(12), dp(8), dp(6)); body.addView(pendingHeader);
            int pending = 0;
            for (Task t : tasks) if (t.deletedAt == null && t.completedAt == null) { addTask(body, t); pending++; }
            if (pending == 0) { TextView empty = text("暂无待办", 13, muted, false); empty.setGravity(Gravity.CENTER); body.addView(empty, new LinearLayout.LayoutParams(-1, dp(54))); }
        }
    }
    private void swipeMonth(int delta, View current) {
        if (monthTransition != 0) return;
        current.animate().translationX(-delta * getResources().getDisplayMetrics().widthPixels).setDuration(180).withEndAction(() -> {
            month = delta > 0 ? month.plusMonths(1) : month.minusMonths(1);
            selected = month.atDay(1); monthTransition = delta; render();
        }).start();
    }
    private LinearLayout listControls() {
        LinearLayout controls = column(); controls.setPadding(dp(16), dp(4), dp(16), dp(6));
        LinearLayout line = row();
        LinearLayout filters = row();
        for (String name : new String[]{"全部", "待完成", "已完成"}) {
            TextView b = text(name, 14, filter.equals(name) ? accent : muted, filter.equals(name));
            b.setGravity(Gravity.CENTER); b.setBackground(box(filter.equals(name) ? soft : bg, 8));
            b.setOnClickListener(v -> { filter = name; render(); });
            filters.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1));
        }
        line.addView(filters, new LinearLayout.LayoutParams(0, -2, 1));
        TextView add = homeAction("＋  记下一件事", () -> editor(null));
        boolean wrap = getResources().getConfiguration().screenWidthDp < 360 || getResources().getConfiguration().fontScale > 1.2f;
        if (wrap) {
            controls.addView(line); space(controls, 4); controls.addView(add);
        } else {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2); p.setMargins(dp(10), 0, 0, 0);
            line.addView(add, p); controls.addView(line);
        }
        return controls;
    }
    private void listPage() {
        int count = 0;
        for (Task t : tasks) {
            if (!TaskDates.visibleInTodayList(t, LocalDate.now())) continue;
            if ((filter.equals("待完成") && t.completedAt != null) || (filter.equals("已完成") && t.completedAt == null)) continue;
            addTask(body, t); count++;
        }
        if (count == 0) {
            TextView empty = text(filter.equals("已完成") ? "暂无已完成事项" : filter.equals("待完成") ? "暂无待完成事项" : "暂无事项", 13, muted, false);
            empty.setGravity(Gravity.CENTER); body.addView(empty, new LinearLayout.LayoutParams(-1, dp(64)));
        }
    }
    private void empty(LinearLayout parent, String title, String subtitle) {
        LinearLayout c = card(); TextView icon = text("✓", 42, accent, false); icon.setGravity(Gravity.CENTER); c.addView(icon); space(c, 14);
        TextView h = text(title, 18, ink, true); h.setGravity(Gravity.CENTER); c.addView(h); space(c, 8);
        TextView sub = text(subtitle, 13, muted, false); sub.setGravity(Gravity.CENTER); c.addView(sub); parent.addView(c);
    }
    private String dateTime(long time) { return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm")); }
    private void addTask(LinearLayout parent, Task t) {
        boolean compact = page.equals("清单") || page.equals("月历");
        LinearLayout c = row(); c.setPadding(dp(compact ? 4 : 12), dp(compact ? 6 : 14), dp(compact ? 4 : 12), dp(compact ? 6 : 14)); c.setBackground(box(surface, compact ? 8 : 18));
        TextView check = text(t.completedAt == null ? "○" : "✓", 26, t.completedAt == null ? muted : accent, true); check.setGravity(Gravity.CENTER); check.setContentDescription(t.completedAt == null ? "完成：" + t.title : "恢复待办：" + t.title);
        c.addView(check, new LinearLayout.LayoutParams(dp(44), dp(compact ? 48 : 52))); check.setOnClickListener(v -> toggle(t));
        LinearLayout content = column(); TextView title = text(t.title, 16, t.completedAt == null ? ink : muted, !compact); title.setMaxLines(3); title.setEllipsize(TextUtils.TruncateAt.END);
        if (t.completedAt != null) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG); content.addView(title); space(content, compact ? 2 : 6);
        String detail = (t.tag.isEmpty() ? "未分类" : t.tag) + "  ·  " + new String[]{"低优先级", "中优先级", "高优先级"}[t.priority];
        TextView meta = text(detail, 11, t.priority == 2 ? color(dark ? "#F4A39B" : "#BC6053") : t.priority == 1 ? color(dark ? "#E4C48A" : "#9A7A36") : muted, false); content.addView(meta);
        if (t.dueAt != null) { space(content, 5); content.addView(text((t.completedAt == null && t.dueAt < System.currentTimeMillis() ? "已逾期 · " : "截止 · ") + dateTime(t.dueAt), 11, muted, false)); }
        c.addView(content, new LinearLayout.LayoutParams(0, -2, 1));
        TextView edit = text("⋯", 24, muted, true); edit.setGravity(Gravity.CENTER); edit.setContentDescription("编辑或删除：" + t.title); edit.setOnClickListener(v -> actions(t)); c.addView(edit, new LinearLayout.LayoutParams(dp(44), dp(48)));
        c.setOnClickListener(v -> toggle(t)); c.setOnLongClickListener(v -> { actions(t); return true; });
        c.setOnTouchListener(new View.OnTouchListener() {
            float x, y; boolean moved;
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: x = e.getX(); y = e.getY(); moved = false; break;
                    case MotionEvent.ACTION_MOVE:
                        float delta = e.getX() - x;
                        if (Math.abs(delta) > dp(24) && Math.abs(delta) > Math.abs(e.getY() - y) * 1.5f) { moved = true; v.getParent().requestDisallowInterceptTouchEvent(true); v.setTranslationX(delta * .4f); }
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setTranslationX(0); v.getParent().requestDisallowInterceptTouchEvent(false);
                        if (moved) { if (e.getX() - x < -dp(72)) delete(t); else if (e.getX() - x > dp(72) && t.completedAt == null) toggle(t); return true; }
                        break;
                    case MotionEvent.ACTION_CANCEL: v.setTranslationX(0); break;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 0, 0, dp(compact ? 4 : 10)); parent.addView(c, p);
    }
    private void actions(Task t) { new AlertDialog.Builder(this).setTitle(t.title).setItems(new String[]{"编辑事项", t.completedAt == null ? "标记完成" : "恢复为待办", "删除事项"}, (d, n) -> { if (n == 0) editor(t); else if (n == 1) toggle(t); else delete(t); }).show(); }
    private void toggle(Task t) { Task next = t.copy(); next.completedAt = t.completedAt == null ? System.currentTimeMillis() : null; save(next); }
    private void save(Task t) {
        TodoDb.IO.execute(() -> {
            t.id = TodoDb.get(this).tasks().save(t); Reminders.schedule(this, t); TodoWidget.updateAll(this);
            main.post(this::reload);
        });
    }
    private void delete(Task t) {
        TodoDb.IO.execute(() -> { TodoDb.get(this).tasks().moveToTrash(t.id, System.currentTimeMillis()); Reminders.cancel(this, t); TodoWidget.updateAll(this); main.post(this::reload); });
    }
    private void editor(Task original) {
        Task draft = original == null ? new Task() : original.copy();
        if (original == null) draft.tag = getPreferences(MODE_PRIVATE).getString("defaultTag", "学习");
        LinearLayout form = column(); form.setPadding(dp(24), dp(8), dp(24), dp(8));
        EditText title = new EditText(this); title.setHint("想完成什么？"); title.setText(draft.title); title.setTextSize(18); title.setMaxLines(4); title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)}); form.addView(title);
        space(form, 16); form.addView(text("优先级", 13, muted, true));
        Spinner priority = new Spinner(this); ArrayAdapter<String> choices = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"● 低 · 不着急", "● 中 · 按计划", "● 高 · 优先处理"}); priority.setAdapter(choices); priority.setSelection(draft.priority); form.addView(priority, new LinearLayout.LayoutParams(-1, dp(52)));
        space(form, 12); form.addView(text("分类标签", 13, muted, true));
        AutoCompleteTextView tag = new AutoCompleteTextView(this); tag.setSingleLine(); tag.setText(draft.tag); tag.setHint("学习 / 工作 / 生活，也可以自定义"); tag.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)}); tag.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new String[]{"学习", "工作", "生活"})); tag.setThreshold(0); tag.setOnFocusChangeListener((v, focused) -> { if (focused) tag.showDropDown(); }); form.addView(tag);
        space(form, 18);
        TextView due = button(draft.dueAt == null ? "＋  设置截止时间（可选）" : "截止 " + dateTime(draft.dueAt), false, () -> {});
        TextView start = button(draft.startAt == null ? "设置开始时间（可选）" : "开始 " + dateTime(draft.startAt), false, () -> {});
        start.setOnClickListener(v -> {
            ZonedDateTime current = draft.startAt == null ? ZonedDateTime.now().plusMinutes(15) : Instant.ofEpochMilli(draft.startAt).atZone(ZoneId.systemDefault());
            new DatePickerDialog(this, (picker, year, m, day) -> new TimePickerDialog(this, (time, hour, minute) -> {
                draft.startAt = LocalDateTime.of(year, m + 1, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); start.setText("开始 " + dateTime(draft.startAt));
            }, current.getHour(), current.getMinute(), true).show(), current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()).show();
        }); form.addView(start); space(form, 8);
        form.addView(button("清除开始时间", false, () -> { draft.startAt = null; start.setText("设置开始时间（可选）"); })); space(form, 12);
        due.setOnClickListener(v -> {
            ZonedDateTime current = draft.dueAt == null ? ZonedDateTime.now().plusHours(1) : Instant.ofEpochMilli(draft.dueAt).atZone(ZoneId.systemDefault());
            new DatePickerDialog(this, (picker, year, m, day) -> new TimePickerDialog(this, (time, hour, minute) -> {
                draft.dueAt = LocalDateTime.of(year, m + 1, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); due.setText("截止 " + dateTime(draft.dueAt));
            }, current.getHour(), current.getMinute(), true).show(), current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()).show();
        }); form.addView(due); space(form, 8);
        form.addView(button("清除截止时间", false, () -> { draft.dueAt = null; due.setText("＋  设置截止时间（可选）"); }));
        space(form, 12); form.addView(text("提前约 15 分钟提醒；系统省电可能使提醒延迟。", 11, muted, false));
        ScrollView sc = new ScrollView(this); sc.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(original == null ? "记下一件事" : "编辑事项").setView(sc).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            if (title.getText().toString().trim().isEmpty()) { title.setError("先写一点内容吧"); return; }
            if (draft.dueAt != null && draft.dueAt <= System.currentTimeMillis() && (original == null || !Objects.equals(original.dueAt, draft.dueAt))) { toast("截止时间需要晚于现在"); return; }
            if (draft.startAt != null && draft.startAt <= System.currentTimeMillis() && (original == null || !Objects.equals(original.startAt, draft.startAt))) { toast("开始时间需要晚于现在"); return; }
            if (draft.startAt != null && draft.dueAt != null && draft.startAt >= draft.dueAt) { toast("开始时间需要早于预计完成时间"); return; }
            draft.title = title.getText().toString().trim(); draft.tag = tag.getText().toString().trim(); draft.priority = priority.getSelectedItemPosition();
            save(draft); dialog.dismiss(); if (draft.startAt != null || draft.dueAt != null) requestNotifications();
        })); dialog.show();
    }
    private void calendarPage() {
        LinearLayout controls = row();
        TextView label = text(month.getYear() + "年" + month.getMonthValue() + "月", 17, ink, true);
        controls.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView previous = text("‹", 24, muted, false); previous.setGravity(Gravity.CENTER); previous.setContentDescription("上个月");
        previous.setOnClickListener(v -> { month = month.minusMonths(1); selected = month.atDay(1); render(); });
        controls.addView(previous, new LinearLayout.LayoutParams(dp(40), dp(48)));
        TextView next = text("›", 24, muted, false); next.setGravity(Gravity.CENTER); next.setContentDescription("下个月");
        next.setOnClickListener(v -> { month = month.plusMonths(1); selected = month.atDay(1); render(); });
        controls.addView(next, new LinearLayout.LayoutParams(dp(40), dp(48)));
        controls.addView(homeAction("今天", () -> { month = YearMonth.now(); selected = LocalDate.now(); render(); }));
        body.addView(controls);
        LinearLayout week = row();
        for (String w : new String[]{"一", "二", "三", "四", "五", "六", "日"}) {
            TextView v = text(w, 12, muted, false); v.setGravity(Gravity.CENTER);
            week.addView(v, new LinearLayout.LayoutParams(0, dp(28), 1));
        }
        body.addView(week);
        TextView dayStatus = text("", 13, ink, true);
        MonthGrid grid = new MonthGrid(this, month, selected, tasks, dark, (date, details) -> {
            selected = date;
            dayStatus.setText(daySummary(date));
            if (details) dayDetails(date);
        });
        body.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout footer = row();
        dayStatus.setText(daySummary(selected)); footer.addView(dayStatus, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView detail = text("查看当天 ›", 13, accent, true); detail.setGravity(Gravity.CENTER);
        detail.setOnClickListener(v -> dayDetails(selected)); footer.addView(detail, new LinearLayout.LayoutParams(dp(96), dp(44)));
        body.addView(footer);
    }
    private String daySummary(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("M月d日")) + " · 完成 " + MonthGrid.tasksOn(tasks, date).size() + " 件";
    }
    private void dayDetails(LocalDate date) {
        List<Task> completed = MonthGrid.tasksOn(tasks, date);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(daySummary(date)).setNegativeButton("关闭", null);
        if (completed.isEmpty()) dialog.setMessage("当天暂无完成记录");
        else {
            String[] titles = new String[completed.size()];
            for (int i = 0; i < completed.size(); i++) titles[i] = completed.get(i).title;
            dialog.setItems(titles, (d, which) -> actions(completed.get(which)));
        }
        dialog.show();
    }
    private void statsPageLegacy() {
        heading("小小坚持，慢慢累积", "每一件完成的事，都是向前的一步。");
        LocalDate today = LocalDate.now(), week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LinearLayout totals = card(); totals.addView(text("本周完成", 13, muted, false)); space(totals, 8); totals.addView(text(TaskDates.count(tasks, week, week.plusWeeks(1)) + " 件", 42, accent, true));
        space(totals, 16); totals.addView(text("本月 " + TaskDates.count(tasks, today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1)) + " 件   ·   累计 " + tasks.stream().filter(t -> t.completedAt != null).count() + " 件", 15, ink, true)); body.addView(totals); space(body, 24);
        body.addView(text("最近七天", 18, ink, true)); space(body, 16);
        for (int i = 6; i >= 0; i--) { LocalDate d = today.minusDays(i); int n = TaskDates.count(tasks, d, d.plusDays(1)); LinearLayout line = row(); line.addView(text(d.format(DateTimeFormatter.ofPattern("MM/dd")), 13, muted, false), new LinearLayout.LayoutParams(dp(58), dp(36)));
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setProgressTintList(ColorStateList.valueOf(accent)); bar.setMax(Math.max(5, tasks.size())); bar.setProgress(n); line.addView(bar, new LinearLayout.LayoutParams(0, dp(12), 1)); TextView count = text("  " + n + " 件", 13, ink, true); line.addView(count); body.addView(line); }
        space(body, 24); body.addView(text("统计按完成时间计算；恢复为待办后不计入完成数量。", 12, muted, false));
    }
    private void statsPage() {
        heading("完成情况", "用简单数据看看最近的学习和工作进度");
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(7);
        int weekDone = TaskDates.count(tasks, weekStart, weekEnd);
        Set<Long> weeklyWork = new HashSet<>();
        for (Task t : tasks) if (t.deletedAt == null) {
            boolean createdThisWeek = !TaskDates.date(t.createdAt).isBefore(weekStart) && TaskDates.date(t.createdAt).isBefore(weekEnd);
            boolean completedThisWeek = t.completedAt != null && !TaskDates.date(t.completedAt).isBefore(weekStart) && TaskDates.date(t.completedAt).isBefore(weekEnd);
            if (createdThisWeek || completedThisWeek) weeklyWork.add(t.id);
        }
        int weekTotal = weeklyWork.size();
        int rate = weekTotal == 0 ? 0 : Math.min(100, Math.round(weekDone * 100f / weekTotal));
        LinearLayout summary = card();
        summary.addView(text("本周完成率", 13, muted, false)); space(summary, 4);
        summary.addView(text(rate + "%", 40, accent, true));
        summary.addView(text(weekDone + " / " + weekTotal + " 件", 14, ink, true));
        space(summary, 10);
        summary.addView(text("本月完成 " + TaskDates.count(tasks, today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1)) + " 件", 13, muted, false));
        body.addView(summary); space(body, 24);

        body.addView(text("最近七天", 18, ink, true)); space(body, 10);
        int[] counts = new int[7]; int max = 1;
        for (int i = 0; i < 7; i++) { LocalDate d = today.minusDays(6 - i); counts[i] = TaskDates.count(tasks, d, d.plusDays(1)); max = Math.max(max, counts[i]); }
        LinearLayout chart = row(); chart.setGravity(Gravity.BOTTOM); chart.setPadding(dp(4), 0, dp(4), 0);
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.minusDays(6 - i);
            LinearLayout barColumn = column(); barColumn.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            TextView value = text(String.valueOf(counts[i]), 11, ink, true); value.setGravity(Gravity.CENTER);
            int barHeight = dp(18 + Math.round(72f * counts[i] / max));
            View bar = new View(this); bar.setBackground(box(counts[i] == 0 ? soft : accent, 8));
            TextView label = text(d.format(DateTimeFormatter.ofPattern("MM/dd")), 10, muted, false); label.setGravity(Gravity.CENTER);
            barColumn.addView(value, new LinearLayout.LayoutParams(-1, dp(22)));
            barColumn.addView(bar, new LinearLayout.LayoutParams(dp(22), barHeight));
            barColumn.addView(label, new LinearLayout.LayoutParams(-1, dp(28)));
            chart.addView(barColumn, new LinearLayout.LayoutParams(0, dp(128), 1));
        }
        body.addView(chart); space(body, 20);
        int totalCompleted = (int) tasks.stream().filter(t -> t.deletedAt == null && t.completedAt != null).count();
        body.addView(text("累计完成 " + totalCompleted + " 件", 13, muted, false));
        space(body, 8); body.addView(text("完成率按本周新建事项计算；历史完成记录仍保留在月视图中。", 12, muted, false));
    }
    private void settingsPageLegacy() {
        body.addView(text("设置", 20, ink, true)); space(body, 16);
        Switch theme = new Switch(this); theme.setText("深色模式"); theme.setTextColor(ink);
        theme.setChecked(dark); theme.setMinHeight(dp(56));
        theme.setOnCheckedChangeListener((b, on) -> { getPreferences(MODE_PRIVATE).edit().putBoolean("dark", on).apply(); recreate(); });
        body.addView(theme); settingDivider();
        boolean enabled = getSystemService(NotificationManager.class).areNotificationsEnabled();
        settingRow("截止提醒", enabled ? "已开启" : "未开启", () -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !getPreferences(MODE_PRIVATE).getBoolean("notificationRequested", false)) requestNotifications();
            else startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
        });
        settingRow("回收站", trashed.size() + " 项", () -> { page = "回收站"; render(); });
        settingRow("导出月历图片", "PNG", this::chooseExportMonth);
        space(body, 18); body.addView(text("一件一件  2.3", 12, muted, false));
    }
    private void settingDivider() {
        View line = new View(this); line.setBackgroundColor(border); body.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }
    private void settingRow(String title, String value, Runnable action) {
        LinearLayout item = row();
        item.addView(text(title, 16, ink, false), new LinearLayout.LayoutParams(0, dp(60), 1));
        item.addView(text(value + "  ›", 13, muted, false));
        item.setOnClickListener(v -> action.run()); body.addView(item); settingDivider();
    }
    private void settingsPage() {
        body.addView(text("设置", 22, ink, true)); space(body, 16);
        body.addView(text("偏好设置", 13, muted, true)); space(body, 4);
        Switch theme = new Switch(this); theme.setText("深色模式"); theme.setTextColor(ink); theme.setChecked(dark); theme.setMinHeight(dp(56));
        theme.setOnCheckedChangeListener((b, on) -> { getPreferences(MODE_PRIVATE).edit().putBoolean("dark", on).apply(); recreate(); });
        body.addView(theme); settingDivider();
        String defaultTag = getPreferences(MODE_PRIVATE).getString("defaultTag", "学习");
        settingRow("默认分类", defaultTag, () -> chooseDefaultTag());
        boolean enabled = getSystemService(NotificationManager.class).areNotificationsEnabled();
        settingRow("截止提醒", enabled ? "已开启" : "未开启", () -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !getPreferences(MODE_PRIVATE).getBoolean("notificationRequested", false)) requestNotifications();
            else startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
        });
        space(body, 18); body.addView(text("数据管理", 13, muted, true)); space(body, 4);
        settingRow("回收站", trashed.size() + " 项", () -> { page = "回收站"; render(); });
        settingRow("导出月视图", "PNG", this::chooseExportMonth);
        space(body, 18); TextView version = text("一件一件 · 2.3", 12, muted, false); version.setGravity(Gravity.CENTER); body.addView(version);
    }
    private void chooseDefaultTag() {
        String[] choices = new String[]{"学习", "工作", "生活"};
        String current = getPreferences(MODE_PRIVATE).getString("defaultTag", "学习");
        int checked = Math.max(0, Arrays.asList(choices).indexOf(current));
        new AlertDialog.Builder(this).setTitle("默认分类").setSingleChoiceItems(choices, checked, (dialog, which) -> {
            getPreferences(MODE_PRIVATE).edit().putString("defaultTag", choices[which]).apply(); dialog.dismiss(); render();
        }).show();
    }
    private void trashPage() {
        body.addView(homeAction("‹ 返回设置", () -> { page = "设置"; render(); })); space(body, 12);
        body.addView(text("回收站", 20, ink, true)); space(body, 6);
        body.addView(text("删除的事项会保留在这里，直到你彻底删除。", 12, muted, false)); space(body, 14);
        if (trashed.isEmpty()) { body.addView(text("回收站为空", 14, muted, false)); return; }
        for (Task t : trashed) {
            LinearLayout item = column(); item.setPadding(0, dp(8), 0, dp(8));
            item.addView(text(t.title, 16, ink, false));
            item.addView(text("删除于 " + dateTime(t.deletedAt), 11, muted, false));
            LinearLayout actions = row();
            actions.addView(homeAction("恢复", () -> restoreTask(t)));
            TextView remove = text("彻底删除", 13, muted, false); remove.setPadding(dp(20), 0, dp(8), 0);
            remove.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("彻底删除这条事项？")
                .setMessage(t.title + "\n删除后无法恢复。").setNegativeButton("取消", null)
                .setPositiveButton("彻底删除", (d, w) -> {
                    TodoDb.IO.execute(() -> { Task current = TodoDb.get(this).tasks().find(t.id); if (current != null && current.deletedAt != null) TodoDb.get(this).tasks().delete(current); main.post(this::reload); });
                }).show());
            actions.addView(remove, new LinearLayout.LayoutParams(-2, dp(48))); item.addView(actions);
            body.addView(item); settingDivider();
        }
    }
    private void restoreTask(Task t) {
        TodoDb.IO.execute(() -> {
            Task current = TodoDb.get(this).tasks().find(t.id);
            if (current != null && current.deletedAt != null) {
                TodoDb.get(this).tasks().restore(t.id); current.deletedAt = null;
                Reminders.schedule(this, current); TodoWidget.updateAll(this);
            }
            main.post(() -> { toast("已恢复事项"); reload(); });
        });
    }
    private void chooseExportMonth() {
        LinearLayout pickers = row(); pickers.setPadding(dp(24), 0, dp(24), 0);
        NumberPicker year = new NumberPicker(this); year.setMinValue(1); year.setMaxValue(9999); year.setValue(month.getYear());
        NumberPicker m = new NumberPicker(this); m.setMinValue(1); m.setMaxValue(12); m.setValue(month.getMonthValue());
        pickers.addView(year, new LinearLayout.LayoutParams(0, -2, 1)); pickers.addView(m, new LinearLayout.LayoutParams(0, -2, 1));
        new AlertDialog.Builder(this).setTitle("选择导出的年份和月份").setView(pickers).setNegativeButton("取消", null)
            .setPositiveButton("保存图片", (d, w) -> {
                pickers.clearFocus(); pendingExportMonth = YearMonth.of(year.getValue(), m.getValue());
                startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("image/png").addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_TITLE, "完成月历-" + pendingExportMonth + ".png"), EXPORT_MONTH);
            }).show();
    }
    private void pinWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        try {
            if (!manager.isRequestPinAppWidgetSupported()) {
                new AlertDialog.Builder(this).setTitle("当前桌面不支持一键添加")
                    .setMessage("手机当前使用的桌面没有提供直接添加小组件的入口。可以更新系统桌面，或切换到支持此功能的桌面后再试。")
                    .setPositiveButton("知道了", null).show();
                return;
            }
            // An accepted request is not a confirmed addition: the launcher owns confirmation.
            if (!manager.requestPinAppWidget(new ComponentName(this, TodoWidget.class), null, null)) {
                toast("桌面未接受添加请求，请稍后重试");
            }
        } catch (IllegalStateException | SecurityException e) {
            toast("暂时无法添加，请保持应用在前台，并检查系统桌面是否允许添加小组件");
        }
    }
    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationRequested", true).apply(); requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 20);
        }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) { super.onRequestPermissionsResult(request, permissions, results); if (request == 20 && results.length > 0 && results[0] != PackageManager.PERMISSION_GRANTED) toast("事项已保存；可在设置中开启提醒通知"); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT_MONTH || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData(); YearMonth exporting = pendingExportMonth;
        TodoDb.IO.execute(() -> {
            android.graphics.Bitmap bitmap = null;
            try {
                bitmap = MonthImage.create(exporting, TodoDb.get(this).tasks().all());
                try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt")) {
                    if (stream == null || !bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException("无法写入图片");
                }
                main.post(() -> toast("月历图片已保存"));
            } catch (Exception e) { main.post(() -> toast("导出失败，请检查保存位置后重试")); }
            finally { if (bitmap != null) bitmap.recycle(); }
        });
    }
}
