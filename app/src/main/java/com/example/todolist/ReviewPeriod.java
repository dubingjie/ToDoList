package com.example.todolist;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;

final class ReviewPeriod {
    static LocalDate start(int type, LocalDate date) {
        if (type == 1) return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (type == 2) return date.withDayOfMonth(1);
        return date;
    }
    static LocalDate next(int type, LocalDate start, int delta) {
        return type == 1 ? start.plusWeeks(delta) : type == 2 ? start.plusMonths(delta) : start.plusDays(delta);
    }
    static String key(int type, LocalDate date) { return type + ":" + start(type, date); }
}
