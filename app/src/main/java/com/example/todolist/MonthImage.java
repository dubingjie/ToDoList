package com.example.todolist;

import android.graphics.*;
import android.text.*;
import java.time.*;
import java.util.*;

/** High-resolution monthly overview; hidden entries are explicitly counted. */
public final class MonthImage {
    private MonthImage() {}
    public static Bitmap create(YearMonth month, java.util.List<Task> tasks) {
        int offset = month.atDay(1).getDayOfWeek().getValue() - 1;
        int weeks = (offset + month.lengthOfMonth() + 6) / 7;
        int width = 2100, margin = 28, top = 180, rowHeight = 420;
        Bitmap bitmap = Bitmap.createBitmap(width, top + weeks * rowHeight + 56, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap); c.drawColor(Color.WHITE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.rgb(32, 57, 45)); p.setTextSize(48); p.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText(month.getYear() + "年" + month.getMonthValue() + "月 · 完成记录", margin, 65, p);
        p.setTextSize(24); p.setTypeface(Typeface.DEFAULT);
        c.drawText("本月完成 " + TaskDates.count(tasks, month.atDay(1), month.plusMonths(1).atDay(1)) + " 件", margin, 108, p);
        float cell = (width - margin * 2f) / 7;
        String[] weekdays = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < 7; i++) c.drawText(weekdays[i], margin + i * cell + cell / 2 - 12, 153, p);
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            int index = offset + day - 1; float x = margin + index % 7 * cell, y = top + index / 7 * rowHeight;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(Color.rgb(213, 222, 215));
            c.drawRect(x + 2, y + 2, x + cell - 2, y + rowHeight - 2, p); p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(32, 57, 45)); p.setTextSize(28); p.setTypeface(Typeface.DEFAULT_BOLD); c.drawText(String.valueOf(day), x + 14, y + 37, p);
            java.util.List<Task> items = MonthGrid.tasksOn(tasks, month.atDay(day));
            for (int i = 0; i < Math.min(5, items.size()); i++) {
                float ty = y + 52 + i * 63;
                p.setColor(Color.rgb(232, 240, 231)); c.drawRoundRect(x + 9, ty, x + cell - 9, ty + 58, 5, 5, p);
                TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG); tp.setColor(Color.rgb(32, 57, 45)); tp.setTextSize(22);
                StaticLayout layout = StaticLayout.Builder.obtain(items.get(i).title, 0, items.get(i).title.length(), tp, (int) cell - 30)
                    .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build();
                c.save(); c.translate(x + 15, ty + 4); layout.draw(c); c.restore();
            }
            if (items.size() > 5) { p.setColor(Color.rgb(35, 103, 81)); p.setTextSize(24); c.drawText("＋" + (items.size() - 5) + " 件", x + 14, y + rowHeight - 18, p); }
        }
        p.setColor(Color.GRAY); p.setTextSize(20); p.setTypeface(Typeface.DEFAULT);
        c.drawText("一件一件 · 月历总览 · 每格最多显示 5 件，＋表示更多事项", margin, bitmap.getHeight() - 18, p);
        return bitmap;
    }
}
