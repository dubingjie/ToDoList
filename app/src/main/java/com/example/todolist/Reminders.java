package com.example.todolist;

import android.app.*;
import android.content.*;
import android.net.Uri;

public final class Reminders {
    public static final String CHANNEL = "task_due";
    public static void channel(Context c) {
        c.getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "截止时间提醒", NotificationManager.IMPORTANCE_DEFAULT));
    }
    public static void schedule(Context c, Task t) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction("REMIND").setData(Uri.parse("todo://reminder/" + t.id)).putExtra("id", t.id);
        PendingIntent p = PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarms = c.getSystemService(AlarmManager.class);
        alarms.cancel(p);
        c.getSystemService(NotificationManager.class).cancel("task-" + t.id, 1);
        long now = System.currentTimeMillis();
        long trigger = t.startAt != null ? t.startAt : (t.dueAt == null ? 0 : TaskDates.reminderAt(t.dueAt, now));
        if (t.deletedAt == null && t.completedAt == null && trigger > now) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, p);
        }
    }
    public static void cancel(Context c, Task t) { Task done = t.copy(); done.completedAt = System.currentTimeMillis(); schedule(c, done); }
}
