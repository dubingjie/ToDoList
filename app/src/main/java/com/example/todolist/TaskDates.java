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
    /** A completion rate uses one assigned-date cohort for both numerator and denominator. */
    public static int countAssigned(List<Task> tasks, LocalDate start, LocalDate endExclusive, boolean completedOnly) {
        int count = 0;
        for (Task task : tasks) {
            if (task.deletedAt != null || (completedOnly && task.completedAt == null)) continue;
            LocalDate assigned = date(task.taskDate == null ? task.createdAt : task.taskDate);
            if (!assigned.isBefore(start) && assigned.isBefore(endExclusive)) count++;
        }
        return count;
    }
    /** Current list visibility is day-scoped; the row remains in Room for calendar/history. */
    public static boolean visibleInTodayList(Task task, LocalDate today) {
        return visibleInDateList(task, today);
    }
    public static boolean visibleInDateList(Task task, LocalDate day) {
        if (task.deletedAt != null) return false;
        if (task.completedAt != null) return date(task.completedAt).equals(day);
        return date(task.taskDate == null ? task.createdAt : task.taskDate).equals(day);
    }
    public static long nextDayStart(long now) {
        return Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
            .plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
