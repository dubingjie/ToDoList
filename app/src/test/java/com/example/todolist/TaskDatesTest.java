package com.example.todolist;

import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class TaskDatesTest {
    @Test public void weeklyRateUsesAssignedDatesForBothCounts() {
        LocalDate start = LocalDate.of(2026, 10, 5), end = start.plusWeeks(1);
        Task assigned = completed(end.plusDays(1));
        assigned.createdAt = start.minusWeeks(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assigned.taskDate = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Task pending = new Task(); pending.taskDate = assigned.taskDate;
        Task otherWeek = completed(start); otherWeek.taskDate = assigned.createdAt;
        Task deleted = assigned.copy(); deleted.deletedAt = 1L;
        Task legacy = new Task(); legacy.createdAt = start.plusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Task exclusiveEnd = new Task(); exclusiveEnd.taskDate = end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<Task> tasks = Arrays.asList(assigned, pending, otherWeek, deleted, legacy, exclusiveEnd);
        assertEquals(3, TaskDates.countAssigned(tasks, start, end, false));
        assertEquals(1, TaskDates.countAssigned(tasks, start, end, true));
        assigned.completedAt = null;
        assertEquals(0, TaskDates.countAssigned(tasks, start, end, true));
        assertEquals(0, TaskDates.countAssigned(Collections.emptyList(), start, end, false));
    }
    private Task completed(LocalDate date) {
        Task t = new Task(); t.completedAt = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); return t;
    }
    @Test public void statisticsUseCompletionDateAndExclusiveEnd() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        Task pending = new Task(); pending.dueAt = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertEquals(2, TaskDates.count(Arrays.asList(completed(start.minusDays(1)), completed(start), completed(start.plusDays(29)), completed(start.plusMonths(1)), pending), start, start.plusMonths(1)));
    }
    @Test public void undoCompletionRemovesTaskFromStatistics() {
        LocalDate date = LocalDate.of(2026, 9, 18); Task t = completed(date);
        assertEquals(1, TaskDates.count(Collections.singletonList(t), date, date.plusDays(1)));
        t.completedAt = null;
        assertEquals(0, TaskDates.count(Collections.singletonList(t), date, date.plusDays(1)));
    }
    @Test public void remindersAreFifteenMinutesEarlyUnlessDeadlineIsClose() {
        long now = 100000000L;
        assertEquals(now + 45 * 60 * 1000, TaskDates.reminderAt(now + 60 * 60 * 1000, now));
        assertEquals(now + 1000, TaskDates.reminderAt(now + 5 * 60 * 1000, now));
    }
    @Test public void taskCopyPreservesUndoSnapshot() {
        Task t = new Task(); t.id = 9; t.title = "完成文档"; t.dueAt = 1234L;
        Task previous = t.copy(); t.title = "新标题"; t.completedAt = 999L;
        assertEquals("完成文档", previous.title); assertNull(previous.completedAt); assertEquals(Long.valueOf(1234), previous.dueAt); assertEquals(9, previous.id);
    }
    @Test public void completedRowsExpireFromCurrentListButRemainHistorical() {
        LocalDate today = LocalDate.now();
        Task yesterday = completed(today.minusDays(1));
        Task todayTask = completed(today);
        assertFalse(TaskDates.visibleInTodayList(yesterday, today));
        assertTrue(TaskDates.visibleInTodayList(todayTask, today));
        assertEquals(1, TaskDates.count(Collections.singletonList(yesterday), today.minusDays(1), today));
    }
    @Test public void oldUnfinishedTaskRemainsInHistoryAndDoesNotRollOver() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Task task = new Task();
        task.createdAt = yesterday.atTime(16, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertTrue(TaskDates.visibleInDateList(task, yesterday));
        assertFalse(TaskDates.visibleInTodayList(task, yesterday.plusDays(1)));
        task.deletedAt = System.currentTimeMillis();
        assertFalse(TaskDates.visibleInDateList(task, yesterday));
    }
    @Test public void midnightRefreshUsesNextLocalCalendarDay() {
        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"));
            ZoneId zone = ZoneId.systemDefault();
            // The spring clock change makes this calendar day 23 hours long.
            long now = LocalDate.of(2026, 3, 8).atStartOfDay(zone).toInstant().toEpochMilli();
            long expected = LocalDate.of(2026, 3, 9).atStartOfDay(zone).toInstant().toEpochMilli();
            assertEquals(expected, TaskDates.nextDayStart(now));
            assertEquals(23 * 60 * 60 * 1000L, expected - now);
        } finally { java.util.TimeZone.setDefault(previous); }
    }
    @Test public void unfinishedTaskIsLimitedToItsAssignedDay() {
        LocalDate today = LocalDate.now();
        Task task = new Task();
        task.taskDate = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertTrue(TaskDates.visibleInDateList(task, today));
        assertFalse(TaskDates.visibleInDateList(task, today.plusDays(1)));
    }
}
