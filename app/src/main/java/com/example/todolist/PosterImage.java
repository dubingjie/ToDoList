package com.example.todolist;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;

/** 3:4 share poster (Xiaohongshu-friendly) with the week's completion summary. */
final class PosterImage {
    private PosterImage() {}

    static Bitmap create(Context context, java.util.List<Task> tasks, int bg, int surface, int ink, int muted, int accent, int soft) {
        int width = 1080, height = 1440;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(bg);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pad = 84, y = pad;

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int weekDone = TaskDates.countAssigned(tasks, weekStart, weekStart.plusDays(7), true);
        int weekTotal = TaskDates.countAssigned(tasks, weekStart, weekStart.plusDays(7), false);
        int rate = weekTotal == 0 ? 0 : Math.min(100, Math.round(weekDone * 100f / weekTotal));
        int monthDone = TaskDates.count(tasks, today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1));
        java.util.Set<LocalDate> days = new java.util.HashSet<>();
        for (Task t : tasks) if (t.deletedAt == null && t.completedAt != null) days.add(TaskDates.date(t.completedAt));
        LocalDate streakDay = today;
        if (!days.contains(streakDay)) streakDay = streakDay.minusDays(1);
        int streak = 0;
        while (days.contains(streakDay)) { streak++; streakDay = streakDay.minusDays(1); }

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(UiStyle.font(context, true));
        paint.setColor(accent); paint.setTextSize(52);
        canvas.drawText("一件一件", pad, y + 48, paint);
        paint.setTypeface(UiStyle.font(context, false));
        paint.setColor(muted); paint.setTextSize(30);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(weekStart.format(DateTimeFormatter.ofPattern("M月d日")) + " — " + weekStart.plusDays(6).format(DateTimeFormatter.ofPattern("M月d日")), width - pad, y + 46, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        y += 110;

        // Summary card
        RectF card = new RectF(pad, y, width - pad, y + 330);
        paint.setColor(surface);
        canvas.drawRoundRect(card, 44, 44, paint);
        paint.setColor(muted); paint.setTextSize(30);
        canvas.drawText("本周完成率", pad + 56, y + 76, paint);
        paint.setTypeface(UiStyle.font(context, true));
        paint.setColor(accent); paint.setTextSize(130);
        canvas.drawText(weekTotal == 0 ? "—" : rate + "%", pad + 52, y + 212, paint);
        paint.setTypeface(UiStyle.font(context, false));
        paint.setColor(ink); paint.setTextSize(34);
        canvas.drawText(weekTotal == 0 ? "本周还没有安排事项" : "已完成 " + weekDone + " / " + weekTotal + " 件", pad + 56, y + 268, paint);
        paint.setColor(muted); paint.setTextSize(28);
        canvas.drawText("本月完成 " + monthDone + " 件" + (streak > 0 ? "   ·   连续完成 " + streak + " 天" : ""), pad + 56, y + 312, paint);
        y += 330 + 36;

        // Heatmap card
        Map<LocalDate, Integer> daily = new HashMap<>();
        for (Task t : tasks) if (t.deletedAt == null && t.completedAt != null) daily.merge(TaskDates.date(t.completedAt), 1, Integer::sum);
        int[] levels = {soft, blend(accent, soft, .35f), blend(accent, soft, .7f), accent};
        paint.setColor(ink); paint.setTextSize(36);
        paint.setTypeface(UiStyle.font(context, true));
        canvas.drawText("完成热力", pad, y + 40, paint);
        y += 64;
        int weeks = 18;
        float gap = 8, cell = (width - pad * 2 - 56 * 2 - (weeks - 1) * gap) / weeks;
        float heatHeight = cell * 7 + gap * 6 + 96;
        card = new RectF(pad, y, width - pad, y + heatHeight);
        paint.setColor(surface);
        canvas.drawRoundRect(card, 44, 44, paint);
        LocalDate start = TaskDates.weekStartSunday(today).minusWeeks(weeks - 1L);
        for (int week = 0; week < weeks; week++) {
            for (int day = 0; day < 7; day++) {
                LocalDate date = start.plusDays(week * 7L + day);
                if (date.isAfter(today)) continue;
                Integer count = daily.get(date);
                int level = count == null || count == 0 ? 0 : count == 1 ? 1 : count <= 3 ? 2 : 3;
                paint.setColor(levels[level]);
                float left = pad + 56 + week * (cell + gap), top = y + 48 + day * (cell + gap);
                canvas.drawRoundRect(new RectF(left, top, left + cell, top + cell), cell * .3f, cell * .3f, paint);
            }
        }
        y += heatHeight + 36;

        // Seven-day bars card
        paint.setColor(ink); paint.setTextSize(36);
        paint.setTypeface(UiStyle.font(context, true));
        canvas.drawText("最近七天", pad, y + 40, paint);
        y += 64;
        float barsHeight = 240;
        card = new RectF(pad, y, width - pad, y + barsHeight);
        paint.setColor(surface);
        canvas.drawRoundRect(card, 44, 44, paint);
        int[] counts = new int[7]; int max = 1;
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.minusDays(6 - i);
            counts[i] = TaskDates.count(tasks, d, d.plusDays(1));
            max = Math.max(max, counts[i]);
        }
        float slot = (width - pad * 2 - 56 * 2) / 7f;
        paint.setTypeface(UiStyle.font(context, false));
        for (int i = 0; i < 7; i++) {
            float cx = pad + 56 + slot * i + slot / 2;
            float barHeight = 20 + 96f * counts[i] / max;
            paint.setColor(counts[i] == 0 ? soft : accent);
            canvas.drawRoundRect(new RectF(cx - 22, y + 166 - barHeight, cx + 22, y + 166), 16, 16, paint);
            paint.setColor(ink); paint.setTextSize(26);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.valueOf(counts[i]), cx, y + 166 - barHeight - 14, paint);
            paint.setColor(muted); paint.setTextSize(24);
            canvas.drawText(today.minusDays(6 - i).format(DateTimeFormatter.ofPattern("MM/dd")), cx, y + 208, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        // Footer
        paint.setColor(muted); paint.setTextSize(30);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(UiStyle.font(context, false));
        canvas.drawText("每一件完成的事，都是向前的一步", width / 2f, height - 48, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        return bitmap;
    }

    private static int blend(int a, int b, float fraction) {
        float inverse = 1f - fraction;
        return android.graphics.Color.argb(
            Math.round(android.graphics.Color.alpha(a) * fraction + android.graphics.Color.alpha(b) * inverse),
            Math.round(android.graphics.Color.red(a) * fraction + android.graphics.Color.red(b) * inverse),
            Math.round(android.graphics.Color.green(a) * fraction + android.graphics.Color.green(b) * inverse),
            Math.round(android.graphics.Color.blue(a) * fraction + android.graphics.Color.blue(b) * inverse));
    }
}
