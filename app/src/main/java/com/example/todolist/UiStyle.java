package com.example.todolist;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.view.View;
import android.view.ViewGroup;

/** Shared, quiet typography and stroke icons for the app's four pages. */
final class UiStyle {
    private static Typeface regularFont, titleFont;
    private UiStyle() {}
    static synchronized Typeface font(Context context, boolean bold) {
        if (regularFont == null) {
            regularFont = context.getResources().getFont(R.font.book_font);
            titleFont = Typeface.create(regularFont, Typeface.BOLD);
        }
        return bold ? titleFont : regularFont;
    }
    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    static void typography(TextView view, boolean bold) {
        view.setIncludeFontPadding(false);
        view.setTypeface(font(view.getContext(), bold));
        view.setLineSpacing(dp(view.getContext(), 2), 1f);
    }
    static ArrayAdapter<String> choices(Context context, int layout, String[] labels) {
        return new ArrayAdapter<String>(context, layout, labels) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                if (view instanceof TextView) typography((TextView) view, false); return view;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                if (view instanceof TextView) typography((TextView) view, false); return view;
            }
        };
    }
    static GradientDrawable shape(Context context, int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(context, radius)); return shape;
    }
    static Drawable press(Context context, int fill, int radius, int accent) {
        return new RippleDrawable(ColorStateList.valueOf((accent & 0x00ffffff) | 0x18000000),
            shape(context, fill, radius), shape(context, Color.WHITE, radius));
    }
    static Drawable icon(Context context, String name, int color, int size) {
        return new StrokeIcon(name, color, dp(context, size));
    }
    private static final class StrokeIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String name;
        private final int size;
        StrokeIcon(String name, int color, int size) {
            this.name = name; this.size = size;
            paint.setColor(color); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.7f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            setBounds(0, 0, size, size);
        }
        private void path(Canvas c, float... points) {
            Path path = new Path(); path.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]); c.drawPath(path, paint);
        }
        @Override public void draw(Canvas canvas) {
            int save = canvas.save(); Rect bounds = getBounds();
            canvas.translate(bounds.left, bounds.top); canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
            switch (name) {
                case "list":
                    canvas.drawRoundRect(4, 3, 20, 21, 3, 3, paint);
                    path(canvas, 7, 8, 8, 9, 10, 7); path(canvas, 13, 8, 17, 8);
                    path(canvas, 7, 14, 8, 15, 10, 13); path(canvas, 13, 14, 17, 14); break;
                case "review":
                    path(canvas, 13, 4, 5, 4, 5, 20, 19, 20, 19, 12);
                    path(canvas, 10, 14, 11, 10, 18, 3, 21, 6, 14, 13, 10, 14); break;
                case "stats":
                    path(canvas, 4, 4, 4, 20, 21, 20); path(canvas, 8, 16, 8, 12);
                    path(canvas, 13, 16, 13, 8); path(canvas, 18, 16, 18, 5); break;
                case "settings":
                    path(canvas, 4, 6, 20, 6); path(canvas, 4, 12, 20, 12); path(canvas, 4, 18, 20, 18);
                    canvas.drawCircle(9, 6, 2.2f, paint); canvas.drawCircle(16, 12, 2.2f, paint); canvas.drawCircle(9, 18, 2.2f, paint); break;
                case "plus": path(canvas, 12, 5, 12, 19); path(canvas, 5, 12, 19, 12); break;
                case "check": canvas.drawCircle(12, 12, 8, paint); path(canvas, 8, 12, 11, 15, 16, 9); break;
                case "circle": canvas.drawCircle(12, 12, 8, paint); break;
                case "more":
                    paint.setStyle(Paint.Style.FILL); for (int x = 6; x <= 18; x += 6) canvas.drawCircle(x, 12, 1.2f, paint); paint.setStyle(Paint.Style.STROKE); break;
                case "trash":
                    path(canvas, 4, 6, 20, 6); path(canvas, 9, 6, 9, 3, 15, 3, 15, 6);
                    path(canvas, 6, 6, 7, 21, 17, 21, 18, 6); path(canvas, 10, 10, 10, 17); path(canvas, 14, 10, 14, 17); break;
                case "export":
                    path(canvas, 5, 13, 5, 20, 19, 20, 19, 13); path(canvas, 12, 15, 12, 3); path(canvas, 8, 7, 12, 3, 16, 7); break;
                case "bell":
                    path(canvas, 5, 17, 7, 14, 7, 9); canvas.drawArc(7, 4, 17, 14, 180, 180, false, paint);
                    path(canvas, 17, 9, 17, 14, 19, 17, 5, 17); canvas.drawArc(10, 18, 14, 22, 0, 180, false, paint); break;
                case "tag": path(canvas, 3, 4, 12, 4, 21, 13, 13, 21, 3, 11, 3, 4); canvas.drawCircle(8, 8, 1, paint); break;
                case "moon":
                    Path moon = new Path(); moon.moveTo(17, 3); moon.cubicTo(4, 0, 0, 17, 12, 21); moon.cubicTo(18, 23, 22, 17, 21, 13); moon.cubicTo(13, 17, 9, 7, 17, 3); canvas.drawPath(moon, paint); break;
            }
            canvas.restoreToCount(save);
        }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
