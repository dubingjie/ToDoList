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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private List<Task> tasks = new ArrayList<>();
    private List<Task> trashed = new ArrayList<>();
    private CoordinatorLayout root;
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
    private ReviewPage activeReview;
    private TaskAdapter homeAdapter;
    private MainViewModel vm;
    private View navView;
    private boolean fadeIn;
    private static final int EXPORT_BACKUP = 105, IMPORT_BACKUP = 106, EXPORT_POSTER = 107;
    private int reviewType, statsTab;
    private LocalDate reviewDate = LocalDate.now();
    private boolean reviewDetail, reviewReading;
    private boolean reviewBackRegistered;
    private android.window.OnBackInvokedCallback reviewBack;
    private LocalDate displayedToday = LocalDate.now();
    private boolean resumed;
    private final Runnable dayRefresh = () -> {
        if (!resumed) return;
        refreshDay();
        scheduleDayRefresh();
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override public void onCreate(Bundle state) {
        dark = resolveDark();
        setTheme(themeRes());
        super.onCreate(state);
        vm = new ViewModelProvider(this).get(MainViewModel.class);
        vm.tasks.observe(this, list -> { tasks = list; refreshContent(); });
        vm.trashed.observe(this, list -> { trashed = list; if (page.equals("回收站") || page.equals("设置")) render(); });
        if (state != null) pendingExportMonth = YearMonth.parse(state.getString("exportMonth", YearMonth.now().toString()));
        if (state != null) homeCalendarExpanded = state.getBoolean("homeCalendarExpanded", false);
        if (state != null) {
            reviewType = state.getInt("reviewType", 0); statsTab = state.getInt("statsTab", 0);
            reviewDate = LocalDate.parse(state.getString("reviewDate", LocalDate.now().toString()));
            reviewDetail = state.getBoolean("reviewDetail"); reviewReading = state.getBoolean("reviewReading");
        }
        if (state != null) { page = state.getString("page", "清单"); filter = state.getString("filter", "全部"); month = YearMonth.parse(state.getString("month")); selected = LocalDate.parse(state.getString("selected")); }
        if (page.equals("月历")) { page = "统计"; statsTab = 1; }
        Reminders.channel(this);
        colors(); render();
        taskChangedReceiver = new BroadcastReceiver() { @Override public void onReceive(Context context, Intent intent) {
            if (!refreshDay()) refreshContent();
            if (resumed) scheduleDayRefresh();
        } };
        IntentFilter taskFilter = new IntentFilter("com.example.todolist.TASKS_CHANGED");
        taskFilter.addAction(Intent.ACTION_DATE_CHANGED);
        taskFilter.addAction(Intent.ACTION_TIME_CHANGED);
        taskFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(taskChangedReceiver, taskFilter, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(taskChangedReceiver, taskFilter);
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (!refreshDay()) refreshContent();
        scheduleDayRefresh();
        TodoDb.IO.execute(() -> TodoWidget.updateAll(this));
    }
    @Override protected void onPause() { if (activeReview != null) activeReview.persist(); resumed = false; main.removeCallbacks(dayRefresh); super.onPause(); }
    private boolean refreshDay() {
        LocalDate today = LocalDate.now();
        if (today.equals(displayedToday)) return false;
        if (selected.equals(displayedToday)) { selected = today; month = YearMonth.from(today); savedScrollY = 0; }
        displayedToday = today;
        refreshContent();
        TodoDb.IO.execute(() -> TodoWidget.updateAll(this));
        return true;
    }
    private void scheduleDayRefresh() {
        main.removeCallbacks(dayRefresh);
        long now = System.currentTimeMillis();
        main.postDelayed(dayRefresh, Math.max(1, TaskDates.nextDayStart(now) - now + 100));
    }
    @Override protected void onSaveInstanceState(Bundle b) { if (activeReview != null) { activeReview.persist(); reviewDetail = activeReview.isDetail(); reviewReading = activeReview.isReading(); } super.onSaveInstanceState(b); b.putString("page", page); b.putString("filter", filter); b.putString("month", month.toString()); b.putString("selected", selected.toString()); b.putString("exportMonth", pendingExportMonth.toString()); b.putBoolean("homeCalendarExpanded", homeCalendarExpanded); b.putInt("reviewType", reviewType); b.putInt("statsTab", statsTab); b.putString("reviewDate", reviewDate.toString()); b.putBoolean("reviewDetail", reviewDetail); b.putBoolean("reviewReading", reviewReading); }
    @Override protected void onDestroy() { main.removeCallbacksAndMessages(null); if (taskChangedReceiver != null) unregisterReceiver(taskChangedReceiver); if (Build.VERSION.SDK_INT >= 33 && reviewBackRegistered) getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(reviewBack); super.onDestroy(); }
    // API 33+ uses the registered callback below; this fallback serves older phones.
    @SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { if (activeReview != null && activeReview.isDetail()) activeReview.showList(); else super.onBackPressed(); }
    private void updateReviewBack() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (reviewBack == null) reviewBack = () -> { if (activeReview != null && activeReview.isDetail()) activeReview.showList(); };
        boolean needed = page.equals("复盘") && activeReview != null && activeReview.isDetail();
        if (needed && !reviewBackRegistered) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, reviewBack);
        if (!needed && reviewBackRegistered) getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(reviewBack);
        reviewBackRegistered = needed;
    }
    private void colors() {
        bg = color(dark ? "#1B1611" : "#FAF4EA"); surface = color(dark ? "#292118" : "#FFFFFF");
        ink = color(dark ? "#F0E8DB" : "#3F3A33"); muted = color(dark ? "#AB9F8C" : "#A39A8B");
        border = color(dark ? "#3C342A" : "#EFE6D8");
        accent = color(paletteAccent());
        soft = blend(accent, surface, dark ? .22f : .12f);
    }
    private int darkMode() {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        if (!prefs.contains("darkMode")) prefs.edit().putInt("darkMode", prefs.getBoolean("dark", false) ? 2 : 0).apply();
        return prefs.getInt("darkMode", 0);
    }
    private boolean resolveDark() {
        int mode = darkMode();
        if (mode == 0) return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return mode == 2;
    }
    private String accentKey() { return getPreferences(MODE_PRIVATE).getString("accent", "amber"); }
    private String paletteAccent() {
        switch (accentKey()) {
            case "green": return dark ? "#A9C9A4" : "#6F8F6A";
            case "blue": return dark ? "#A9C4E0" : "#6E93BC";
            case "rose": return dark ? "#E8B4C0" : "#C4748A";
            case "violet": return dark ? "#C4B8E4" : "#8F7FBE";
            default: return dark ? "#EFBE93" : "#CE8A4E";
        }
    }
    private int themeRes() {
        switch (accentKey()) {
            case "green": return dark ? R.style.AppThemeDarkGreen : R.style.AppThemeLightGreen;
            case "blue": return dark ? R.style.AppThemeDarkBlue : R.style.AppThemeLightBlue;
            case "rose": return dark ? R.style.AppThemeDarkRose : R.style.AppThemeLightRose;
            case "violet": return dark ? R.style.AppThemeDarkViolet : R.style.AppThemeLightViolet;
            default: return dark ? R.style.AppThemeDark : R.style.AppThemeLight;
        }
    }
    private int color(String s) { return Color.parseColor(s); }
    private int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable box(int fill, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); return d; }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(Math.max(12, size)); v.setTextColor(color);
        UiStyle.typography(v, bold); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private void space(LinearLayout l, int h) { View v = new View(this); l.addView(v, new LinearLayout.LayoutParams(1, dp(h))); }
    private TextView button(String label, boolean filled, Runnable action) {
        TextView b = text(label, 14, filled ? (dark ? bg : Color.WHITE) : accent, true);
        b.setGravity(Gravity.CENTER); b.setPadding(dp(14), dp(12), dp(14), dp(12)); b.setMinHeight(dp(48));
        b.setBackground(UiStyle.press(this, filled ? accent : soft, 16, accent)); b.setOnClickListener(v -> action.run()); return b;
    }
    private void heading(String name, String sub) { body.addView(text(name, 30, ink, true)); space(body, 6); body.addView(text(sub, 13, muted, false)); space(body, 24); }
    private LinearLayout card() {
        LinearLayout l = column(); l.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable background = box(surface, 22); background.setStroke(dp(1), border);
        l.setBackground(background);
        l.setElevation(dp(1));
        if (Build.VERSION.SDK_INT >= 28) l.setOutlineAmbientShadowColor(color("#593A2E1E"));
        return l;
    }
    private void refreshContent() {
        if (homeAdapter != null && page.equals("清单")) homeAdapter.setData(tasks, selected); else render();
    }
    private void render() {
        if (activeReview != null) { reviewDetail = activeReview.isDetail(); reviewReading = activeReview.isReading(); activeReview.persist(); activeReview = null; }
        if (activeScroll != null) savedScrollY = activeScroll.getScrollY();
        homeAdapter = null;
        root = new CoordinatorLayout(this); root.setBackgroundColor(bg); root.setClipChildren(false);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        boolean list = page.equals("清单");
        boolean calendar = page.equals("统计") && statsTab == 1;
        LinearLayout shell = column(); shell.setBackgroundColor(bg);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        body = column();
        if (list) homeListPage(shell);
        else {
            body.setPadding(dp(calendar ? 4 : 16), dp(8), dp(calendar ? 4 : 16), dp(calendar ? 0 : 12));
            ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); activeScroll = scroll;
            scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> savedScrollY = y);
            if (calendar) shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
            else { scroll.addView(body); shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); scroll.post(() -> scroll.scrollTo(0, savedScrollY)); }
            if (page.equals("复盘")) reviewPage(); else if (calendar) { statisticsHeader(); calendarPage(); } else if (page.equals("统计")) statsPage(); else if (page.equals("回收站")) trashPage(); else settingsPage();
        }
        BottomNavigationView nav = new BottomNavigationView(this);
        nav.setBackgroundColor(surface);
        ColorStateList navTint = new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{accent, muted});
        nav.setItemIconTintList(navTint); nav.setItemTextColor(navTint);
        nav.setItemActiveIndicatorColor(ColorStateList.valueOf(soft));
        nav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        String[] tabs = {"清单", "复盘", "统计", "设置"};
        String[] icons = {"list", "review", "stats", "settings"};
        for (int i = 0; i < tabs.length; i++) nav.getMenu().add(0, i, i, tabs[i]).setIcon(UiStyle.icon(this, icons[i], muted, 24));
        nav.setSelectedItemId(Math.max(0, page.equals("回收站") ? 3 : Arrays.asList(tabs).indexOf(page)));
        nav.setOnItemSelectedListener(item -> {
            String tab = tabs[item.getItemId()];
            if (tab.equals(page)) return true;
            savedScrollY = 0; page = tab; fadeIn = true; render(); return true;
        });
        navView = nav;
        shell.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        if (list) {
            FloatingActionButton fab = new FloatingActionButton(this);
            fab.setImageDrawable(UiStyle.icon(this, "plus", Color.WHITE, 26));
            fab.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            fab.setBackgroundTintList(ColorStateList.valueOf(accent));
            fab.setContentDescription("添加待办事项"); fab.setOnClickListener(v -> editor(null));
            CoordinatorLayout.LayoutParams fp = new CoordinatorLayout.LayoutParams(-2, -2); fp.gravity = Gravity.END | Gravity.BOTTOM; fp.setMargins(0, 0, dp(22), dp(96)); root.addView(fab, fp);
        }
        if (page.equals("复盘")) {
            ReviewPage review = activeReview;
            TextView write = button("写复盘", true, () -> review.open(LocalDate.now(), false));
            write.setCompoundDrawables(UiStyle.icon(this, "review", dark ? bg : Color.WHITE, 18), null, null, null); write.setCompoundDrawablePadding(dp(8));
            write.setElevation(dp(2));
            CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams(-2, dp(48)); params.gravity = Gravity.END | Gravity.BOTTOM; params.setMargins(0, 0, dp(20), dp(92)); root.addView(write, params);
            review.modeChanged = () -> {
                reviewDetail = review.isDetail(); reviewReading = review.isReading();
                write.setVisibility(reviewDetail ? View.GONE : View.VISIBLE);
                updateReviewBack();
                if (activeScroll != null) activeScroll.scrollTo(0, 0);
            };
            write.setVisibility(review.isDetail() ? View.GONE : View.VISIBLE);
        }
        setContentView(root); root.requestApplyInsets(); updateReviewBack();
        if (fadeIn) { fadeIn = false; root.setAlpha(0f); root.animate().alpha(1f).setDuration(160).start(); }
    }
    private TextView homeAction(String label, Runnable action) {
        TextView view = button(label, false, action);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable background = box(soft, 14);
        background.setStroke(dp(1), border); view.setBackground(background);
        return view;
    }
    private void homeListPage(LinearLayout shell) {
        TaskAdapter.Listener listener = new TaskAdapter.Listener() {
            @Override public void onToggle(Task t) { toggle(t); }
            @Override public void onOpen(Task t) { editor(t); }
            @Override public void onMore(Task t) { actions(t); }
            @Override public void onSwipeComplete(Task t) {
                if (root != null) root.performHapticFeedback(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
                toggle(t);
            }
            @Override public void onSwipeDelete(Task t) { delete(t); }
            @Override public void onOpenStats() { savedScrollY = 0; page = "统计"; statsTab = 0; fadeIn = true; render(); }
        };
        homeAdapter = new TaskAdapter(this, dark, surface, ink, muted, accent, soft, border, this::homeHeaderView, listener);
        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setClipToPadding(false);
        list.setAdapter(homeAdapter);
        new ItemTouchHelper(new TaskAdapter.Swipe(homeAdapter, listener, this, dark, accent)).attachToRecyclerView(list);
        shell.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        homeAdapter.setData(tasks, selected);
    }
    private View homeHeaderView() {
        LinearLayout container = column(); container.setBackgroundColor(bg); container.setPadding(dp(16), dp(2), dp(16), 0);
        int todayBackground = getColor(dark ? R.color.calendar_today_background_dark : R.color.calendar_today_background);
        int circleSize = Math.max(dp(32), Math.min(dp(44), (getResources().getDisplayMetrics().widthPixels - dp(20)) / 7));
        LinearLayout header = row();
        header.setPadding(dp(8), dp(6), dp(8), dp(2));
        LinearLayout greeting = column();
        int hour = LocalTime.now().getHour();
        String hello = hour < 5 ? "夜深了" : hour < 12 ? "早上好" : hour < 14 ? "中午好" : hour < 18 ? "下午好" : "晚上好";
        greeting.addView(text(hello, 27, ink, true));
        LocalDate today = LocalDate.now();
        int pendingToday = 0;
        for (Task t : tasks) if (TaskDates.visibleInDateList(t, today) && t.completedAt == null) pendingToday++;
        String subtitle = selected.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA));
        if (selected.equals(today)) subtitle += pendingToday == 0 ? " · 今天没有待办" : " · 还有 " + pendingToday + " 件待办";
        TextView sub = text(subtitle, 13, muted, false);
        sub.setPadding(0, dp(4), 0, 0);
        greeting.addView(sub);
        header.addView(greeting, new LinearLayout.LayoutParams(0, dp(64), 1));
        TextView widget = text("⌂", 22, muted, false); widget.setGravity(Gravity.CENTER); widget.setContentDescription("添加到桌面");
        widget.setOnClickListener(v -> pinWidget());
        header.addView(widget, new LinearLayout.LayoutParams(dp(44), dp(52)));
        ImageButton expand = new ImageButton(this);
        expand.setImageResource(R.drawable.ic_calendar_expand);
        expand.setImageTintList(ColorStateList.valueOf(ink));
        expand.setScaleType(ImageView.ScaleType.CENTER);
        expand.setPadding(0, 0, 0, 0);
        expand.setRotation(homeCalendarExpanded ? 180f : 0f);
        expand.setBackground(new android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(soft), null, box(Color.WHITE, 26)));
        expand.setContentDescription(homeCalendarExpanded ? "折叠日历" : "展开日历");
        expand.setOnClickListener(v -> { homeCalendarExpanded = !homeCalendarExpanded; render(); });
        header.addView(expand, new LinearLayout.LayoutParams(dp(48), dp(52)));
        container.addView(header);

        LinearLayout weekdays = row();
        for (String w : new String[]{"日", "一", "二", "三", "四", "五", "六"}) {
            TextView day = text(w, 12, muted, true); day.setGravity(Gravity.CENTER);
            weekdays.addView(day, new LinearLayout.LayoutParams(0, dp(28), 1));
        }
        container.addView(weekdays);

        if (homeCalendarExpanded) {
            CalendarPager pager = new CalendarPager(this, delta -> {
                month = month.plusMonths(delta);
                selected = month.atDay(1);
                render();
            });
            for (int monthOffset = -1; monthOffset <= 1; monthOffset++) {
            YearMonth displayMonth = month.plusMonths(monthOffset);
            LocalDate first = displayMonth.atDay(1);
            LocalDate start = first.minusDays(first.getDayOfWeek().getValue() % 7);
            LinearLayout monthRows = column(); monthRows.setPadding(0, 0, 0, 0); monthRows.setBaselineAligned(false);
            for (int rowIndex = 0; rowIndex < 6; rowIndex++) {
                LinearLayout week = row(); week.setPadding(0, 0, 0, 0); week.setGravity(Gravity.CENTER); week.setBaselineAligned(false); week.setBackgroundColor(color(dark ? "#282219" : "#F3EDE2"));
                for (int col = 0; col < 7; col++) {
                    LocalDate date = start.plusDays(rowIndex * 7L + col);
                    boolean inMonth = YearMonth.from(date).equals(displayMonth);
                    int dayColor = !inMonth ? color(dark ? "#6B6154" : "#C4B9A6") : (date.equals(selected) ? accent : ink);
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
                    UiStyle.typography(cell, date.equals(selected) || !visibleDayTasks.isEmpty());
                    cell.setGravity(Gravity.CENTER); cell.setMaxLines(3); cell.setEllipsize(TextUtils.TruncateAt.END);
                    GradientDrawable gridBackground = box(date.equals(selected) ? accent : (visibleDayTasks.isEmpty() ? surface : soft), 0);
                    gridBackground.setStroke(dp(1), color(dark ? "#3C342A" : "#E9DFCC"));
                    cell.setBackground(gridBackground);
                    cell.setText(date.equals(LocalDate.now()) ? "今" : String.valueOf(date.getDayOfMonth()));
                    cell.setTextSize(15); cell.setTextColor(date.equals(selected) ? (dark ? bg : Color.WHITE) : dayColor);
                    int circleFill = date.equals(selected) ? accent : (date.equals(LocalDate.now()) ? todayBackground : Color.TRANSPARENT);
                    GradientDrawable circle = box(circleFill, 30); circle.setShape(GradientDrawable.OVAL);
                    cell.setBackground(circleFill == Color.TRANSPARENT ? null : circle);
                    cell.setOnClickListener(v -> { selected = date; month = YearMonth.from(date); render(); });
                    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(circleSize, circleSize); p.setMargins(dp(1), dp(1), dp(1), dp(1));
                    week.addView(cell, p);
                }
                monthRows.addView(week, new LinearLayout.LayoutParams(-1, dp(52)));
            }
            pager.addView(monthRows);
            }
            container.addView(pager, new LinearLayout.LayoutParams(-1, dp(312)));
        } else {
            LocalDate sunday = TaskDates.weekStartSunday(selected);
            LinearLayout week = row(); week.setGravity(Gravity.CENTER);
            for (int i = 0; i < 7; i++) {
                LocalDate date = sunday.plusDays(i);
                TextView cell = text(String.valueOf(date.getDayOfMonth()) + (MonthGrid.tasksOn(tasks, date).isEmpty() ? "" : "\n•"), 17, date.equals(selected) ? (dark ? bg : Color.WHITE) : ink, true);
                cell.setGravity(Gravity.CENTER); cell.setMaxLines(2); cell.setContentDescription(date.toString());
                if (date.equals(LocalDate.now())) cell.setText("今");
                GradientDrawable dayBackground = box(date.equals(selected) ? accent : surface, 22);
                if (date.equals(LocalDate.now())) dayBackground.setStroke(dp(2), accent);
                cell.setBackground(dayBackground);
                int circleFill = date.equals(selected) ? accent : (date.equals(LocalDate.now()) ? todayBackground : Color.TRANSPARENT);
                GradientDrawable circle = box(circleFill, 30); circle.setShape(GradientDrawable.OVAL);
                cell.setBackground(circleFill == Color.TRANSPARENT ? null : circle);
                if (!date.equals(selected) && date.equals(LocalDate.now())) cell.setTextColor(accent);
                cell.setOnClickListener(v -> { selected = date; month = YearMonth.from(date); render(); });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(circleSize, circleSize); p.setMargins(dp(1), dp(1), dp(1), dp(1));
                week.addView(cell, p);
            }
            container.addView(week);
        }
        return container;
    }
    private void empty(LinearLayout parent, String title, String subtitle) {
        LinearLayout c = card(); TextView icon = text("✓", 42, accent, false); icon.setGravity(Gravity.CENTER); c.addView(icon); space(c, 14);
        TextView h = text(title, 18, ink, true); h.setGravity(Gravity.CENTER); c.addView(h); space(c, 8);
        TextView sub = text(subtitle, 13, muted, false); sub.setGravity(Gravity.CENTER); c.addView(sub); parent.addView(c);
    }
    private String dateTime(long time) { return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm")); }
    private void actions(Task t) { new MaterialAlertDialogBuilder(this).setTitle(t.title).setItems(new String[]{"编辑事项", t.completedAt == null ? "标记完成" : "恢复为待办", "删除事项"}, (d, n) -> { if (n == 0) editor(t); else if (n == 1) toggle(t); else delete(t); }).show(); }
    private void toggle(Task t) {
        Task next = t.copy(); next.completedAt = t.completedAt == null ? System.currentTimeMillis() : null;
        save(next);
        if (next.completedAt != null && next.repeat != null && !next.repeat.isEmpty()) spawnNextRepeat(next);
    }
    private void spawnNextRepeat(Task t) {
        LocalDate day = TaskDates.date(t.taskDate == null ? t.createdAt : t.taskDate);
        long days = t.repeat.equals("weekly") ? 7 : 1;
        Task next = t.copy();
        next.id = 0; next.completedAt = null; next.createdAt = System.currentTimeMillis();
        next.taskDate = day.plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (next.reminderAt != null) next.reminderAt = Instant.ofEpochMilli(next.reminderAt).atZone(ZoneId.systemDefault()).toLocalDateTime().plusDays(days).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (next.dueAt != null) next.dueAt = Instant.ofEpochMilli(next.dueAt).atZone(ZoneId.systemDefault()).toLocalDateTime().plusDays(days).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (next.startAt != null) next.startAt = Instant.ofEpochMilli(next.startAt).atZone(ZoneId.systemDefault()).toLocalDateTime().plusDays(days).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        try {
            org.json.JSONArray steps = new org.json.JSONArray(next.substeps == null ? "[]" : next.substeps);
            for (int i = 0; i < steps.length(); i++) { org.json.JSONObject step = steps.optJSONObject(i); if (step != null) step.put("done", false); }
            next.substeps = steps.toString();
        } catch (org.json.JSONException e) { next.substeps = "[]"; }
        TodoDb.IO.execute(() -> {
            next.id = TodoDb.get(this).tasks().save(next); Reminders.schedule(this, next); TodoWidget.updateAll(this);
            main.post(() -> toast("已完成，已安排下一次"));
        });
    }
    private void save(Task t) {
        TodoDb.IO.execute(() -> {
            t.id = TodoDb.get(this).tasks().save(t); Reminders.schedule(this, t); TodoWidget.updateAll(this);
        });
    }
    private void delete(Task t) {
        TodoDb.IO.execute(() -> {
            TodoDb.get(this).tasks().moveToTrash(t.id, System.currentTimeMillis()); Reminders.cancel(this, t); TodoWidget.updateAll(this);
            main.post(() -> {
                if (isDestroyed() || root == null) return;
                String title = t.title.length() > 12 ? t.title.substring(0, 12) + "…" : t.title;
                Snackbar undo = Snackbar.make(root, "已删除「" + title + "」", Snackbar.LENGTH_LONG).setAction("撤销", v -> undoDelete(t));
                if (navView != null) undo.setAnchorView(navView);
                undo.show();
            });
        });
    }
    private void undoDelete(Task t) {
        TodoDb.IO.execute(() -> {
            TodoDb.get(this).tasks().restore(t.id);
            Task current = TodoDb.get(this).tasks().find(t.id);
            if (current != null) Reminders.schedule(this, current);
            TodoWidget.updateAll(this);
        });
    }
    private TextView timeField(LinearLayout form, String label, TextView clear) {
        LinearLayout line = row();
        line.setBackground(box(soft, 16));
        LinearLayout content = column();
        content.setPadding(dp(14), dp(10), dp(8), dp(10));
        content.addView(text(label, 12, muted, false));
        TextView value = text("未设置 · 点击选择", 14, accent, false);
        content.addView(value);
        content.setMinimumHeight(dp(56));
        line.addView(content, new LinearLayout.LayoutParams(0, -2, 1));
        clear.setGravity(Gravity.CENTER);
        clear.setContentDescription("清除" + label);
        line.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        form.addView(line, new LinearLayout.LayoutParams(-1, -2));
        return value;
    }
    private void onTimeFieldClick(TextView value, View.OnClickListener listener) {
        LinearLayout content = (LinearLayout) value.getParent();
        View line = (View) content.getParent();
        line.setOnClickListener(listener);
        content.setOnClickListener(listener);
        for (int i = 0; i < content.getChildCount(); i++) {
            content.getChildAt(i).setOnClickListener(listener);
        }
    }
    private LinearLayout chipRow(String[] labels, int selected, java.util.function.IntConsumer onSelect) {
        LinearLayout chips = row();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = text(labels[i], 13, index == selected ? accent : muted, index == selected);
            chip.setGravity(Gravity.CENTER);
            styleChip(chip, index == selected);
            chip.setOnClickListener(v -> {
                onSelect.accept(index);
                for (int j = 0; j < chips.getChildCount(); j++) styleChip((TextView) chips.getChildAt(j), j == index);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1);
            params.setMargins(dp(index == 0 ? 0 : 4), 0, dp(index == labels.length - 1 ? 0 : 4), 0);
            chips.addView(chip, params);
        }
        return chips;
    }
    private void styleChip(TextView chip, boolean active) {
        chip.setTextColor(active ? accent : muted);
        UiStyle.typography(chip, active);
        GradientDrawable background = box(active ? soft : Color.TRANSPARENT, 16);
        background.setStroke(dp(1), active ? accent : border);
        chip.setBackground(background);
    }
    private void editor(Task original) {
        Task draft = original == null ? new Task() : original.copy();
        if (original == null) draft.taskDate = selected.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (original == null) draft.tag = getPreferences(MODE_PRIVATE).getString("defaultTag", "学习");
        LocalDate taskDay = TaskDates.date(draft.taskDate == null ? draft.createdAt : draft.taskDate);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout sheet = column();
        sheet.setBackground(box(surface, 28));
        sheet.setPadding(dp(20), dp(10), dp(20), 0);
        sheet.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        View handle = new View(this); handle.setBackground(box(border, 3));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, handleParams); space(sheet, 12);
        LinearLayout headerRow = row();
        headerRow.addView(text(original == null ? "记下一件事" : "编辑事项", 20, ink, true), new LinearLayout.LayoutParams(0, -2, 1));
        headerRow.addView(text(taskDay.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)), 12, muted, false));
        sheet.addView(headerRow); space(sheet, 8);
        LinearLayout form = column();
        EditText title = new EditText(this); title.setHint("想完成什么？"); title.setText(draft.title); title.setTextSize(18); title.setMaxLines(4);
        title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        title.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        title.setBackgroundColor(Color.TRANSPARENT);
        form.addView(title);
        UiStyle.typography(title, false);
        space(form, 16); form.addView(text("优先级", 13, muted, true)); space(form, 6);
        form.addView(chipRow(new String[]{"低 · 不着急", "中 · 按计划", "高 · 优先处理"}, draft.priority, i -> draft.priority = i));
        space(form, 12); form.addView(text("分类标签", 13, muted, true));
        AutoCompleteTextView tag = new AutoCompleteTextView(this); tag.setSingleLine(); tag.setText(draft.tag); tag.setHint("学习 / 工作 / 生活，也可以自定义"); tag.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)}); tag.setAdapter(UiStyle.choices(this, android.R.layout.simple_dropdown_item_1line, new String[]{"学习", "工作", "生活"})); tag.setThreshold(0); tag.setOnFocusChangeListener((v, focused) -> { if (focused) tag.showDropDown(); }); form.addView(tag);
        UiStyle.typography(tag, false);
        space(form, 12); form.addView(text("重复", 13, muted, true)); space(form, 6);
        form.addView(chipRow(new String[]{"不重复", "每天重复", "每周重复"}, draft.repeat == null || draft.repeat.isEmpty() ? 0 : draft.repeat.equals("daily") ? 1 : 2, i -> draft.repeat = new String[]{"", "daily", "weekly"}[i]));
        space(form, 18);
        TextView clearDuration = text("×", 22, muted, false);
        TextView duration = timeField(form, "预计耗时", clearDuration);
        space(form, 8);
        TextView clearReminder = text("×", 22, muted, false);
        TextView reminder = timeField(form, "提醒时间", clearReminder);
        Runnable refreshTimes = () -> {
            duration.setText(draft.estimatedMinutes == null ? "未设置 · 点击选择" : TaskDates.durationLabel(draft.estimatedMinutes));
            reminder.setText(draft.reminderAt == null ? "不提醒 · 点击设置" : dateTime(draft.reminderAt));
            clearDuration.setVisibility(draft.estimatedMinutes == null ? View.GONE : View.VISIBLE);
            clearReminder.setVisibility(draft.reminderAt == null ? View.GONE : View.VISIBLE);
        };
        clearDuration.setOnClickListener(v -> { draft.estimatedMinutes = null; refreshTimes.run(); });
        clearReminder.setOnClickListener(v -> { draft.reminderAt = null; refreshTimes.run(); });
        refreshTimes.run();
        onTimeFieldClick(duration, v -> {
            LinearLayout pickers = row(); pickers.setPadding(dp(16), dp(8), dp(16), dp(8));
            NumberPicker hours = new NumberPicker(this); hours.setMinValue(0); hours.setMaxValue(99); hours.setWrapSelectorWheel(false);
            NumberPicker minutes = new NumberPicker(this); minutes.setMinValue(0); minutes.setMaxValue(59); minutes.setWrapSelectorWheel(false);
            int current = draft.estimatedMinutes == null ? 30 : draft.estimatedMinutes;
            hours.setValue(current / 60); minutes.setValue(current % 60);
            hours.setContentDescription("预计耗时小时"); minutes.setContentDescription("预计耗时分钟");
            pickers.addView(hours, new LinearLayout.LayoutParams(0, -2, 1)); pickers.addView(text("小时", 13, muted, false));
            pickers.addView(minutes, new LinearLayout.LayoutParams(0, -2, 1)); pickers.addView(text("分钟", 13, muted, false));
            AlertDialog picker = new MaterialAlertDialogBuilder(this).setTitle("预计耗时").setView(pickers).setNegativeButton("取消", null).setPositiveButton("确定", null).create();
            picker.setOnShowListener(shown -> picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                pickers.clearFocus(); int value = hours.getValue() * 60 + minutes.getValue();
                if (value == 0) { toast("预计耗时至少为 1 分钟"); return; }
                draft.estimatedMinutes = value; refreshTimes.run(); picker.dismiss();
            })); picker.show();
        });
        onTimeFieldClick(reminder, v -> {
            ZonedDateTime current = draft.reminderAt == null ? taskDay.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()) : Instant.ofEpochMilli(draft.reminderAt).atZone(ZoneId.systemDefault());
            if (draft.reminderAt == null && !current.isAfter(ZonedDateTime.now())) current = ZonedDateTime.now().plusHours(1);
            final ZonedDateTime initial = current;
            new DatePickerDialog(this, (picker, year, m, day) -> {
                TimePicker time = (TimePicker) getLayoutInflater().inflate(R.layout.start_time_picker, null);
                time.setIs24HourView(true);
                time.setHour(initial.getHour());
                time.setMinute(initial.getMinute());
                new MaterialAlertDialogBuilder(this).setTitle("提醒时间").setView(time)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确定", (confirmed, which) -> {
                        time.clearFocus();
                        draft.reminderAt = LocalDateTime.of(year, m + 1, day, time.getHour(), time.getMinute()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                        refreshTimes.run();
                    }).show();
            }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
        });
        space(form, 18); form.addView(text("子任务", 13, muted, true)); space(form, 4);
        LinearLayout stepsList = column(); form.addView(stepsList);
        List<EditText> stepTexts = new ArrayList<>();
        List<Boolean> stepDone = new ArrayList<>();
        java.util.function.BiConsumer<String, Boolean> addStep = (stepTitle, done) -> {
            int index = stepTexts.size();
            stepTexts.add(null); stepDone.add(done);
            LinearLayout stepRow = row();
            ImageButton check = new ImageButton(this);
            check.setImageDrawable(UiStyle.icon(this, done ? "check" : "circle", done ? accent : muted, 22));
            check.setBackground(UiStyle.press(this, Color.TRANSPARENT, 20, accent));
            check.setOnClickListener(v -> {
                int i = stepsList.indexOfChild(stepRow);
                boolean now = !stepDone.get(i); stepDone.set(i, now);
                check.setImageDrawable(UiStyle.icon(this, now ? "check" : "circle", now ? accent : muted, 22));
            });
            stepRow.addView(check, new LinearLayout.LayoutParams(dp(40), dp(44)));
            EditText stepText = new EditText(this); stepText.setTextSize(14); stepText.setSingleLine();
            stepText.setHint("子任务 " + (index + 1)); stepText.setText(stepTitle);
            stepText.setTextColor(ink); stepText.setHintTextColor(muted);
            stepText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
            UiStyle.typography(stepText, false);
            stepTexts.set(index, stepText);
            stepRow.addView(stepText, new LinearLayout.LayoutParams(0, -2, 1));
            TextView remove = text("×", 20, muted, false); remove.setGravity(Gravity.CENTER);
            remove.setContentDescription("删除子任务");
            remove.setOnClickListener(v -> { int i = stepsList.indexOfChild(stepRow); if (i >= 0) { stepsList.removeViewAt(i); stepTexts.remove(i); stepDone.remove(i); } });
            stepRow.addView(remove, new LinearLayout.LayoutParams(dp(40), dp(44)));
            stepsList.addView(stepRow);
        };
        try {
            org.json.JSONArray savedSteps = new org.json.JSONArray(draft.substeps == null ? "[]" : draft.substeps);
            for (int i = 0; i < savedSteps.length(); i++) {
                org.json.JSONObject step = savedSteps.optJSONObject(i);
                if (step != null) addStep.accept(step.optString("t"), step.optBoolean("done"));
            }
        } catch (org.json.JSONException ignored) {}
        TextView addStepButton = text("＋ 添加子任务", 13, accent, true);
        addStepButton.setGravity(Gravity.CENTER_VERTICAL);
        addStepButton.setOnClickListener(v -> addStep.accept("", false));
        form.addView(addStepButton, new LinearLayout.LayoutParams(-1, dp(44)));
        space(form, 12); form.addView(text("备注", 13, muted, true));
        EditText note = new EditText(this); note.setHint("补充说明（可选）"); note.setText(draft.note); note.setTextSize(14);
        note.setMinLines(2); note.setMaxLines(5); note.setGravity(Gravity.TOP);
        note.setTextColor(ink); note.setHintTextColor(muted);
        note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)}); form.addView(note);
        UiStyle.typography(note, false);
        space(form, 12); form.addView(text("按你设置的时间提醒；预计耗时与提醒时间可以分别设置。", 12, muted, false));
        ScrollView sc = new ScrollView(this); sc.addView(form);
        sheet.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
        space(sheet, 8);
        Runnable saveAttempt = () -> {
            if (title.getText().toString().trim().isEmpty()) { title.setError("先写一点内容吧"); return; }
            if (draft.reminderAt != null && draft.reminderAt <= System.currentTimeMillis() && (original == null || !Objects.equals(original.reminderAt, draft.reminderAt))) { toast("提醒时间需要晚于现在"); return; }
            draft.title = title.getText().toString().trim(); draft.tag = tag.getText().toString().trim();
            draft.note = note.getText().toString().trim();
            org.json.JSONArray steps = new org.json.JSONArray();
            for (int i = 0; i < stepTexts.size(); i++) {
                String stepTitle = stepTexts.get(i).getText().toString().trim();
                if (stepTitle.isEmpty()) continue;
                try { steps.put(new org.json.JSONObject().put("t", stepTitle).put("done", stepDone.get(i))); } catch (org.json.JSONException ignored) {}
            }
            draft.substeps = steps.toString();
            save(draft); dialog.dismiss(); if (draft.reminderAt != null) requestNotifications();
        };
        LinearLayout actions = row();
        actions.addView(button("取消", false, dialog::dismiss), new LinearLayout.LayoutParams(0, -2, 1));
        View actionsGap = new View(this);
        actions.addView(actionsGap, new LinearLayout.LayoutParams(dp(10), 1));
        actions.addView(button("保存", true, saveAttempt::run), new LinearLayout.LayoutParams(0, -2, 1));
        sheet.addView(actions); space(sheet, 14);
        dialog.setContentView(sheet);
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        com.google.android.material.bottomsheet.BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
        title.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { saveAttempt.run(); return true; }
            return false;
        });
        dialog.setOnShowListener(d -> title.postDelayed(() -> {
            title.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(title, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }, 200));
        dialog.show();
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
        MonthGrid grid = new MonthGrid(this, month, selected, tasks, dark, accent, (date, details) -> {
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
        AlertDialog.Builder dialog = new MaterialAlertDialogBuilder(this).setTitle(daySummary(date)).setNegativeButton("关闭", null);
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
    private void reviewPage() {
        activeReview = new ReviewPage(this, tasks, reviewType, reviewDate, ink, muted, accent, soft, surface,
            (type, date) -> { reviewType = type; reviewDate = date; });
        if (reviewDetail) activeReview.open(reviewDate, reviewReading);
        body.addView(activeReview, new LinearLayout.LayoutParams(-1, -2));
    }
    private void statisticsHeader() {
        TextView title = text("统计", 22, ink, true); title.setPadding(dp(statsTab == 1 ? 12 : 0), 0, 0, 0);
        body.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout tabs = row(); tabs.setPadding(dp(4), dp(4), dp(4), dp(4)); tabs.setBackground(box(soft, 18));
        String[] labels = {"概览", "月视图"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView tab = text(labels[i], 14, statsTab == i ? accent : muted, statsTab == i);
            tab.setGravity(Gravity.CENTER); tab.setBackground(UiStyle.press(this, statsTab == i ? surface : Color.TRANSPARENT, 14, accent));
            tab.setOnClickListener(v -> { if (activeScroll != null) activeScroll.scrollTo(0, 0); savedScrollY = 0; statsTab = index; render(); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1); params.setMargins(dp(2), 0, dp(2), 0); tabs.addView(tab, params);
        }
        body.addView(tabs); space(body, 12);
    }
    private void statsPage() {
        statisticsHeader();
        space(body, 4);
        body.addView(text("用简单数据看看最近的学习和工作进度", 13, muted, false));
        space(body, 20);
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(7);
        int weekDone = TaskDates.countAssigned(tasks, weekStart, weekEnd, true);
        int weekTotal = TaskDates.countAssigned(tasks, weekStart, weekEnd, false);
        int rate = weekTotal == 0 ? 0 : Math.min(100, Math.round(weekDone * 100f / weekTotal));
        LinearLayout summary = card();
        summary.addView(text("本周完成率", 13, muted, false)); space(summary, 4);
        summary.addView(text(weekTotal == 0 ? "暂无数据" : rate + "%", weekTotal == 0 ? 26 : 40, accent, true));
        summary.addView(text(weekTotal == 0 ? "本周还没有安排事项" : "已完成 " + weekDone + " / " + weekTotal + " 件", 14, ink, true));
        space(summary, 10);
        summary.addView(text("本月完成 " + TaskDates.count(tasks, today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1)) + " 件", 13, muted, false));
        int streak = streakDays();
        if (streak > 0) { space(summary, 6); summary.addView(text("已连续完成 " + streak + " 天", 13, accent, true)); }
        body.addView(summary); space(body, 24);

        body.addView(text("完成热力", 18, ink, true)); space(body, 10);
        Map<LocalDate, Integer> daily = new HashMap<>();
        for (Task t : tasks) if (t.deletedAt == null && t.completedAt != null) {
            LocalDate day = TaskDates.date(t.completedAt);
            daily.merge(day, 1, Integer::sum);
        }
        int[] levels = {soft, blend(accent, soft, .35f), blend(accent, soft, .7f), accent};
        LinearLayout heat = card();
        heat.addView(new HeatmapView(this, daily, levels, 18), new LinearLayout.LayoutParams(-1, -2));
        space(heat, 10);
        LinearLayout legend = row(); legend.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        legend.addView(text("少 ", 11, muted, false));
        for (int level : levels) { View block = new View(this); block.setBackground(box(level, 3)); LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(12), dp(12)); bp.setMargins(dp(2), 0, dp(2), 0); legend.addView(block, bp); }
        legend.addView(text(" 多", 11, muted, false));
        heat.addView(legend);
        body.addView(heat); space(body, 20);

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
        LinearLayout chartCard = card(); chartCard.addView(chart); body.addView(chartCard); space(body, 20);
        int totalCompleted = (int) tasks.stream().filter(t -> t.deletedAt == null && t.completedAt != null).count();
        body.addView(text("累计完成 " + totalCompleted + " 件", 13, muted, false));
        space(body, 8); body.addView(text("完成率按事项所属日期统计：本周事项中已完成的比例。七天图表与月视图按实际完成日期记录。", 12, muted, false));
        space(body, 24);
        TextView poster = button("🎨 生成本周分享海报", true, this::exportPoster);
        body.addView(poster, new LinearLayout.LayoutParams(-1, dp(52)));
        space(body, 24);
    }
    private void exportPoster() {
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("image/png").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, "周报海报-" + LocalDate.now() + ".png"), EXPORT_POSTER);
    }
    private int streakDays() {
        Set<LocalDate> days = new HashSet<>();
        for (Task t : tasks) if (t.deletedAt == null && t.completedAt != null) days.add(TaskDates.date(t.completedAt));
        LocalDate day = LocalDate.now();
        if (!days.contains(day)) day = day.minusDays(1);
        int streak = 0;
        while (days.contains(day)) { streak++; day = day.minusDays(1); }
        return streak;
    }
    private int blend(int a, int b, float fraction) {
        float inverse = 1f - fraction;
        return Color.argb(
            Math.round(Color.alpha(a) * fraction + Color.alpha(b) * inverse),
            Math.round(Color.red(a) * fraction + Color.red(b) * inverse),
            Math.round(Color.green(a) * fraction + Color.green(b) * inverse),
            Math.round(Color.blue(a) * fraction + Color.blue(b) * inverse));
    }
    private void settingsPageLegacy() {
        body.addView(text("设置", 20, ink, true)); space(body, 16);
        Switch theme = new Switch(this); theme.setText("深色模式"); theme.setTextColor(ink);
        theme.setChecked(dark); theme.setMinHeight(dp(56));
        theme.setOnCheckedChangeListener((b, on) -> { getPreferences(MODE_PRIVATE).edit().putBoolean("dark", on).apply(); recreate(); });
        body.addView(theme); settingDivider();
        boolean enabled = getSystemService(NotificationManager.class).areNotificationsEnabled();
        settingRow("待办提醒", enabled ? "已开启" : "未开启", () -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !getPreferences(MODE_PRIVATE).getBoolean("notificationRequested", false)) requestNotifications();
            else startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
        });
        settingRow("回收站", trashed.size() + " 项", () -> { page = "回收站"; render(); });
        settingRow("导出月历图片", "PNG", this::chooseExportMonth);
        space(body, 18); body.addView(text("一件一件  " + BuildConfig.VERSION_NAME, 12, muted, false));
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
        body.addView(text("设置", 22, ink, true), new LinearLayout.LayoutParams(-1, dp(48))); space(body, 12);
        body.addView(text("偏好设置", 12, muted, true)); space(body, 10);
        LinearLayout preferences = settingsGroup();
        settingRow(preferences, "深色模式", new String[]{"跟随系统", "浅色", "深色"}[darkMode()], "moon", this::chooseDarkMode); settingsLine(preferences);
        settingRow(preferences, "主题色", accentName(), "palette", this::chooseAccent); settingsLine(preferences);
        String defaultTag = getPreferences(MODE_PRIVATE).getString("defaultTag", "学习");
        settingRow(preferences, "默认分类", defaultTag, "tag", this::chooseDefaultTag); settingsLine(preferences);
        boolean enabled = getSystemService(NotificationManager.class).areNotificationsEnabled();
        settingRow(preferences, "待办提醒", enabled ? "已开启" : "未开启", "bell", () -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !getPreferences(MODE_PRIVATE).getBoolean("notificationRequested", false)) requestNotifications();
            else startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
        });
        body.addView(preferences);
        space(body, 24); body.addView(text("数据管理", 12, muted, true)); space(body, 10);
        LinearLayout data = settingsGroup();
        settingRow(data, "回收站", trashed.size() + " 项", "trash", () -> { page = "回收站"; render(); }); settingsLine(data);
        settingRow(data, "导出月视图", "PNG", "export", this::chooseExportMonth); settingsLine(data);
        settingRow(data, "备份数据", "JSON", "export", this::exportBackup); settingsLine(data);
        settingRow(data, "恢复备份", "", "import", this::importBackup); body.addView(data);
        space(body, 28); TextView version = text("一件一件 · " + BuildConfig.VERSION_NAME, 12, muted, false); version.setGravity(Gravity.CENTER); body.addView(version);
    }
    private LinearLayout settingsGroup() {
        LinearLayout group = column(); group.setPadding(dp(14), dp(2), dp(14), dp(2));
        GradientDrawable background = box(surface, 22); background.setStroke(dp(1), border);
        group.setBackground(background);
        group.setElevation(dp(1));
        if (Build.VERSION.SDK_INT >= 28) group.setOutlineAmbientShadowColor(color("#593A2E1E"));
        return group;
    }
    private TextView settingsLabel(String title, String icon) {
        TextView label = text(title, 15, ink, false);
        label.setCompoundDrawables(UiStyle.icon(this, icon, accent, 20), null, null, null); label.setCompoundDrawablePadding(dp(12)); return label;
    }
    private void settingsLine(LinearLayout group) {
        View line = new View(this); line.setBackgroundColor(border);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Math.max(1, dp(.5f))); params.leftMargin = dp(32); group.addView(line, params);
    }
    private void settingRow(LinearLayout group, String title, String value, String icon, Runnable action) {
        LinearLayout item = row(); item.setBackground(UiStyle.press(this, Color.TRANSPARENT, 10, accent));
        item.addView(settingsLabel(title, icon), new LinearLayout.LayoutParams(0, dp(58), 1));
        item.addView(text(value + "  ›", 12, muted, false)); item.setOnClickListener(v -> action.run()); group.addView(item);
    }
    private void chooseDarkMode() {
        String[] choices = {"跟随系统", "浅色", "深色"};
        new MaterialAlertDialogBuilder(this).setTitle("深色模式").setSingleChoiceItems(choices, darkMode(), (dialog, which) -> {
            getPreferences(MODE_PRIVATE).edit().putInt("darkMode", which).apply(); dialog.dismiss(); recreate();
        }).show();
    }
    private String accentName() {
        switch (accentKey()) {
            case "green": return "抹茶绿";
            case "blue": return "雾霾蓝";
            case "rose": return "豆沙粉";
            case "violet": return "香芋紫";
            default: return "焦糖杏";
        }
    }
    private void chooseAccent() {
        String[] keys = {"amber", "green", "blue", "rose", "violet"};
        String[] choices = {"● 焦糖杏", "● 抹茶绿", "● 雾霾蓝", "● 豆沙粉", "● 香芋紫"};
        int current = Math.max(0, Arrays.asList(keys).indexOf(accentKey()));
        new MaterialAlertDialogBuilder(this).setTitle("主题色").setSingleChoiceItems(choices, current, (dialog, which) -> {
            getPreferences(MODE_PRIVATE).edit().putString("accent", keys[which]).apply(); dialog.dismiss(); recreate();
        }).show();
    }
    private void chooseDefaultTag() {
        String[] choices = new String[]{"学习", "工作", "生活"};
        String current = getPreferences(MODE_PRIVATE).getString("defaultTag", "学习");
        int checked = Math.max(0, Arrays.asList(choices).indexOf(current));
        new MaterialAlertDialogBuilder(this).setTitle("默认分类").setSingleChoiceItems(choices, checked, (dialog, which) -> {
            getPreferences(MODE_PRIVATE).edit().putString("defaultTag", choices[which]).apply(); dialog.dismiss(); render();
        }).show();
    }
    private void trashPage() {
        LinearLayout trashHeader = row();
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setImageTintList(ColorStateList.valueOf(ink));
        back.setScaleType(ImageView.ScaleType.CENTER); back.setPadding(0, 0, 0, 0);
        back.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(soft), null, box(Color.WHITE, 24)));
        back.setContentDescription("返回设置");
        back.setOnClickListener(v -> { page = "设置"; render(); });
        trashHeader.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        trashHeader.addView(text("回收站", 20, ink, true), new LinearLayout.LayoutParams(0, dp(48), 1));
        boolean canClear = !trashed.isEmpty();
        TextView clear = text("清空", 14, canClear ? accent : muted, false);
        clear.setGravity(Gravity.CENTER); clear.setContentDescription("清空回收站");
        GradientDrawable clearBorder = box(Color.TRANSPARENT, 10);
        clearBorder.setStroke(dp(1), canClear ? accent : muted);
        android.graphics.drawable.InsetDrawable clearOutline = new android.graphics.drawable.InsetDrawable(clearBorder, 0, dp(6), 0, dp(6));
        clear.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(soft), clearOutline, clearOutline));
        clear.setOnClickListener(v -> emptyTrash());
        clear.setEnabled(canClear); clear.setAlpha(canClear ? 1f : .45f);
        trashHeader.addView(clear, new LinearLayout.LayoutParams(dp(56), dp(48)));
        body.addView(trashHeader); space(body, 12);
        if (trashed.isEmpty()) {
            space(body, 56);
            TextView emptyTitle = text("回收站为空", 17, ink, true); emptyTitle.setGravity(Gravity.CENTER); body.addView(emptyTitle);
            space(body, 8);
            TextView hint = text("删除的事项会暂存于此", 13, muted, false); hint.setGravity(Gravity.CENTER); body.addView(hint);
            return;
        }
        body.addView(text(trashed.size() + " 项 · 可恢复或彻底删除", 12, muted, false)); space(body, 12);
        for (Task t : trashed) {
            LinearLayout item = column(); item.setPadding(dp(16), dp(14), dp(16), dp(8)); item.setBackground(box(surface, 20));
            TextView title = text(t.title, 16, ink, false); title.setMaxLines(3); title.setEllipsize(TextUtils.TruncateAt.END); item.addView(title);
            space(item, 6); item.addView(text("删除于 " + dateTime(t.deletedAt), 11, muted, false)); space(item, 6);
            LinearLayout actions = row();
            actions.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            TextView restore = text("恢复", 13, accent, true); restore.setGravity(Gravity.CENTER); restore.setOnClickListener(v -> restoreTask(t));
            actions.addView(restore, new LinearLayout.LayoutParams(dp(64), dp(48)));
            TextView remove = text("彻底删除", 13, muted, false); remove.setGravity(Gravity.CENTER);
            remove.setOnClickListener(v -> new MaterialAlertDialogBuilder(this).setTitle("彻底删除这条事项？")
                .setMessage(t.title + "\n删除后无法恢复。").setNegativeButton("取消", null)
                .setPositiveButton("彻底删除", (d, w) -> {
                    TodoDb.IO.execute(() -> { Task current = TodoDb.get(this).tasks().find(t.id); if (current != null && current.deletedAt != null) TodoDb.get(this).tasks().delete(current); });
                }).show());
            actions.addView(remove, new LinearLayout.LayoutParams(dp(80), dp(48))); item.addView(actions);
            body.addView(item); space(body, 10);
        }
    }
    private void restoreTask(Task t) {
        TodoDb.IO.execute(() -> {
            Task current = TodoDb.get(this).tasks().find(t.id);
            if (current != null && current.deletedAt != null) {
                TodoDb.get(this).tasks().restore(t.id); current.deletedAt = null;
                Reminders.schedule(this, current); TodoWidget.updateAll(this);
            }
            main.post(() -> toast("已恢复事项"));
        });
    }
    private void emptyTrash() {
        new MaterialAlertDialogBuilder(this).setTitle("清空回收站？").setMessage("清空后所有删除事项将无法恢复。")
            .setNegativeButton("取消", null).setPositiveButton("清空", (d, w) -> TodoDb.IO.execute(() -> TodoDb.get(this).tasks().emptyTrash())).show();
    }
    private void chooseExportMonth() {
        LinearLayout pickers = row(); pickers.setPadding(dp(24), 0, dp(24), 0);
        NumberPicker year = new NumberPicker(this); year.setMinValue(1); year.setMaxValue(9999); year.setValue(month.getYear());
        NumberPicker m = new NumberPicker(this); m.setMinValue(1); m.setMaxValue(12); m.setValue(month.getMonthValue());
        pickers.addView(year, new LinearLayout.LayoutParams(0, -2, 1)); pickers.addView(m, new LinearLayout.LayoutParams(0, -2, 1));
        new MaterialAlertDialogBuilder(this).setTitle("选择导出的年份和月份").setView(pickers).setNegativeButton("取消", null)
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
                new MaterialAlertDialogBuilder(this).setTitle("当前桌面不支持一键添加")
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
    private void exportBackup() {
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, "一件一件备份-" + LocalDate.now() + ".json"), EXPORT_BACKUP);
    }
    private void importBackup() {
        new MaterialAlertDialogBuilder(this).setTitle("恢复备份？")
            .setMessage("备份文件中的事项和复盘会与现有数据合并，相同记录以备份为准。")
            .setNegativeButton("取消", null)
            .setPositiveButton("选择文件", (d, w) -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE), IMPORT_BACKUP))
            .show();
    }
    private org.json.JSONObject taskJson(Task t) throws org.json.JSONException {
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("id", t.id); o.put("title", t.title); o.put("tag", t.tag); o.put("priority", t.priority);
        o.put("createdAt", t.createdAt); o.put("repeat", t.repeat); o.put("note", t.note); o.put("substeps", t.substeps);
        if (t.dueAt != null) o.put("dueAt", (long) t.dueAt);
        if (t.startAt != null) o.put("startAt", (long) t.startAt);
        if (t.estimatedMinutes != null) o.put("estimatedMinutes", (int) t.estimatedMinutes);
        if (t.reminderAt != null) o.put("reminderAt", (long) t.reminderAt);
        if (t.taskDate != null) o.put("taskDate", (long) t.taskDate);
        if (t.completedAt != null) o.put("completedAt", (long) t.completedAt);
        if (t.deletedAt != null) o.put("deletedAt", (long) t.deletedAt);
        return o;
    }
    private Task parseTask(org.json.JSONObject o) {
        Task t = new Task();
        t.id = o.optLong("id"); t.title = o.optString("title"); t.tag = o.optString("tag", "学习");
        t.priority = o.optInt("priority", 1); t.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        t.repeat = o.optString("repeat"); t.note = o.optString("note"); t.substeps = o.optString("substeps", "[]");
        if (o.has("dueAt")) t.dueAt = o.optLong("dueAt");
        if (o.has("startAt")) t.startAt = o.optLong("startAt");
        if (o.has("estimatedMinutes")) t.estimatedMinutes = o.optInt("estimatedMinutes");
        if (o.has("reminderAt")) t.reminderAt = o.optLong("reminderAt");
        if (o.has("taskDate")) t.taskDate = o.optLong("taskDate");
        if (o.has("completedAt")) t.completedAt = o.optLong("completedAt");
        if (o.has("deletedAt")) t.deletedAt = o.optLong("deletedAt");
        return t;
    }
    private void writeBackup(Uri uri) {
        TodoDb.IO.execute(() -> {
            try {
                org.json.JSONObject backup = new org.json.JSONObject();
                backup.put("version", 1); backup.put("exportedAt", System.currentTimeMillis());
                org.json.JSONArray taskArray = new org.json.JSONArray();
                List<Task> all = TodoDb.get(this).tasks().all();
                all.addAll(TodoDb.get(this).tasks().trash());
                for (Task t : all) taskArray.put(taskJson(t));
                backup.put("tasks", taskArray);
                org.json.JSONArray reviewArray = new org.json.JSONArray();
                for (String prefix : new String[]{"0:", "1:", "2:"}) {
                    for (ReviewRecord r : TodoDb.get(this).reviews().list(prefix)) {
                        org.json.JSONObject o = new org.json.JSONObject();
                        o.put("id", r.id); o.put("goals", r.goals); o.put("answers", r.answers);
                        o.put("finished", r.finished); o.put("updatedAt", r.updatedAt); o.put("mood", r.mood);
                        reviewArray.put(o);
                    }
                }
                backup.put("reviews", reviewArray);
                try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt")) {
                    if (stream == null) throw new IOException("无法写入文件");
                    stream.write(backup.toString(2).getBytes("UTF-8"));
                }
                main.post(() -> toast("备份已保存 · " + taskArray.length() + " 条事项"));
            } catch (Exception e) { main.post(() -> toast("备份失败，请检查保存位置后重试")); }
        });
    }
    private void readBackup(Uri uri) {
        TodoDb.IO.execute(() -> {
            try {
                StringBuilder text = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), "UTF-8"))) {
                    String line; while ((line = reader.readLine()) != null) text.append(line);
                }
                org.json.JSONObject backup = new org.json.JSONObject(text.toString());
                org.json.JSONArray taskArray = backup.getJSONArray("tasks");
                List<Task> imported = new ArrayList<>();
                for (int i = 0; i < taskArray.length(); i++) imported.add(parseTask(taskArray.getJSONObject(i)));
                TodoDb.get(this).tasks().insertAll(imported);
                int reviewCount = 0;
                org.json.JSONArray reviewArray = backup.optJSONArray("reviews");
                if (reviewArray != null) for (int i = 0; i < reviewArray.length(); i++) {
                    org.json.JSONObject o = reviewArray.getJSONObject(i);
                    ReviewRecord r = new ReviewRecord();
                    r.id = o.optString("id"); r.goals = o.optString("goals", "[]"); r.answers = o.optString("answers", "[]");
                    r.finished = o.optBoolean("finished"); r.updatedAt = o.optLong("updatedAt"); r.mood = o.optInt("mood", -1);
                    if (!r.id.isEmpty()) { TodoDb.get(this).reviews().save(r); reviewCount++; }
                }
                for (Task t : imported) if (t.deletedAt == null && t.completedAt == null && t.reminderAt != null) Reminders.schedule(this, t);
                TodoWidget.updateAll(this);
                int taskCount = imported.size(); int reviews = reviewCount;
                main.post(() -> toast("已恢复 " + taskCount + " 条事项" + (reviews > 0 ? "、" + reviews + " 篇复盘" : "")));
            } catch (Exception e) { main.post(() -> toast("恢复失败，备份文件格式不正确")); }
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == EXPORT_BACKUP) { writeBackup(uri); return; }
        if (request == IMPORT_BACKUP) { readBackup(uri); return; }
        if (request == EXPORT_POSTER) {
            TodoDb.IO.execute(() -> {
                android.graphics.Bitmap bitmap = null;
                try {
                    bitmap = PosterImage.create(this, TodoDb.get(this).tasks().all(), bg, surface, ink, muted, accent, soft);
                    try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt")) {
                        if (stream == null || !bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException("无法写入图片");
                    }
                    main.post(() -> toast("海报已保存，去晒一晒吧"));
                } catch (Exception e) { main.post(() -> toast("生成失败，请检查保存位置后重试")); }
                finally { if (bitmap != null) bitmap.recycle(); }
            });
            return;
        }
        if (request != EXPORT_MONTH) return;
        YearMonth exporting = pendingExportMonth;
        TodoDb.IO.execute(() -> {
            android.graphics.Bitmap bitmap = null;
            try {
                bitmap = MonthImage.create(this, exporting, TodoDb.get(this).tasks().all());
                try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt")) {
                    if (stream == null || !bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException("无法写入图片");
                }
                main.post(() -> toast("月历图片已保存"));
            } catch (Exception e) { main.post(() -> toast("导出失败，请检查保存位置后重试")); }
            finally { if (bitmap != null) bitmap.recycle(); }
        });
    }
}
