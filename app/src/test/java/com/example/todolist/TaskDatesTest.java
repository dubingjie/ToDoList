package com.example.todolist;

import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class TaskDatesTest {
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
}
