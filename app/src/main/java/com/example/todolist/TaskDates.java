package com.example.todolist;

import java.time.*;
import java.util.List;

public final class TaskDates {
    private TaskDates() {}
    public static LocalDate date(long timestamp) { return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate(); }
    public static int count(List<Task> tasks, LocalDate start, LocalDate endExclusive) {
        int n = 0;
        for (Task t : tasks) if (t.deletedAt == null && t.completedAt != null) {
            LocalDate d = date(t.completedAt);
            if (!d.isBefore(start) && d.isBefore(endExclusive)) n++;
        }
        return n;
    }
    public static long reminderAt(long due, long now) { return Math.max(now + 1000, due - 15 * 60 * 1000); }
    /** Current list visibility is day-scoped; the row remains in Room for calendar/history. */
    public static boolean visibleInTodayList(Task task, LocalDate today) {
        return task.deletedAt == null && (task.completedAt == null || date(task.completedAt).equals(today));
    }
}
