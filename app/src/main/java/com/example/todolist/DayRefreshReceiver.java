package com.example.todolist;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Refresh date-scoped widgets even when the activity is closed. */
public final class DayRefreshReceiver extends BroadcastReceiver {
    static void schedule(Context context) {
        PendingIntent nextDay = PendingIntent.getBroadcast(context, 0,
            new Intent(context, DayRefreshReceiver.class).setAction("com.example.todolist.NEW_DAY"),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        context.getSystemService(AlarmManager.class).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, TaskDates.nextDayStart(System.currentTimeMillis()), nextDay);
    }

    @Override public void onReceive(Context context, Intent intent) {
        PendingResult result = goAsync();
        TodoDb.IO.execute(() -> {
            try {
                TodoWidget.updateAll(context);
                context.sendBroadcast(new Intent("com.example.todolist.TASKS_CHANGED")
                    .setPackage(context.getPackageName()));
            } finally { result.finish(); }
        });
    }
}
