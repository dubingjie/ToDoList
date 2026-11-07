package com.example.todolist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.time.LocalDate;
import java.util.Map;

/** GitHub-style completion heatmap: columns are weeks (Sunday on top), newest at bottom-right. */
final class HeatmapView extends View {
    private final Map<LocalDate, Integer> counts;
    private final int[] levels;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int weeks;
    private final LocalDate start;
    private float cell, gap;

    HeatmapView(Context context, Map<LocalDate, Integer> counts, int[] levelColors, int weeks) {
        super(context);
        this.counts = counts; this.levels = levelColors; this.weeks = weeks;
        LocalDate today = LocalDate.now();
        this.start = TaskDates.weekStartSunday(today).minusWeeks(weeks - 1L);
        gap = UiStyle.dp(context, 3);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        cell = (width - (weeks - 1) * gap) / weeks;
        setMeasuredDimension(width, Math.round(cell * 7 + gap * 6));
    }

    @Override protected void onDraw(Canvas canvas) {
        LocalDate today = LocalDate.now();
        float radius = cell * .28f;
        for (int week = 0; week < weeks; week++) {
            for (int day = 0; day < 7; day++) {
                LocalDate date = start.plusDays(week * 7L + day);
                if (date.isAfter(today)) continue;
                Integer count = counts.get(date);
                int level = count == null || count == 0 ? 0 : count == 1 ? 1 : count <= 3 ? 2 : 3;
                paint.setColor(levels[level]);
                float left = week * (cell + gap), top = day * (cell + gap);
                rect.set(left, top, left + cell, top + cell);
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
        }
    }
}
