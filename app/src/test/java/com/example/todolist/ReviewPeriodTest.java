package com.example.todolist;

import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class ReviewPeriodTest {
    @Test public void weekSharesOneRecordAcrossYearBoundary() {
        assertEquals(ReviewPeriod.key(1, LocalDate.of(2026, 12, 31)), ReviewPeriod.key(1, LocalDate.of(2027, 1, 3)));
        assertNotEquals(ReviewPeriod.key(1, LocalDate.of(2027, 1, 3)), ReviewPeriod.key(1, LocalDate.of(2027, 1, 4)));
    }
    @Test public void monthAndDayAreIndependentPeriods() {
        LocalDate date = LocalDate.of(2028, 2, 29);
        assertEquals(LocalDate.of(2028, 3, 1), ReviewPeriod.next(2, ReviewPeriod.start(2, date), 1));
        assertNotEquals(ReviewPeriod.key(0, date), ReviewPeriod.key(2, date));
        assertNotEquals(ReviewPeriod.key(0, date), ReviewPeriod.key(0, date.minusDays(1)));
    }
}
