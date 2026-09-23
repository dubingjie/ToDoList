package com.example.todolist;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        PendingResult pending = goAsync();
        TodoDb.IO.execute(() -> {
            try {
                Reminders.channel(c);
                if (!"REMIND".equals(i.getAction())) {
                    for (Task t : TodoDb.get(c).tasks().all()) Reminders.schedule(c, t);
                    TodoWidget.updateAll(c);
                    return;
                }
                Task t = TodoDb.get(c).tasks().find(i.getLongExtra("id", -1));
                if (t == null || t.deletedAt != null || t.completedAt != null || (t.startAt == null && t.dueAt == null)) return;
                if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
                PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                long reminderAt = t.startAt != null ? t.startAt : t.dueAt;
                String reminderLabel = t.startAt != null ? "开始时间 " : "截止时间 ";
                Notification n = new Notification.Builder(c, Reminders.CHANNEL).setSmallIcon(R.drawable.ic_check).setContentTitle("待办提醒 · " + t.title)
                    .setContentText(reminderLabel + java.time.format.DateTimeFormatter.ofPattern("MM月d日 HH:mm").format(java.time.Instant.ofEpochMilli(reminderAt).atZone(java.time.ZoneId.systemDefault())))
                    .setContentIntent(open).setAutoCancel(true).build();
                c.getSystemService(NotificationManager.class).notify("task-" + t.id, 1, n);
            } finally { pending.finish(); }
        });
    }
}
