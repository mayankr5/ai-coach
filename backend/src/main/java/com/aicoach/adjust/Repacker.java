package com.aicoach.adjust;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays tasks out again from a start day, in series order, filling each working day up to the daily time.
 * Unlike the catch-up scheduler it also pulls tasks earlier: it runs after the user adds daily time or drops
 * work, so the freed time gets used. Buffer days stay empty; a task longer than a day gets a day to itself;
 * work that doesn't fit before the deadline goes on the deadline.
 */
final class Repacker {

    record Item(long id, int minutes) {
    }

    /** @param dates the new date of every item, by id */
    record Result(Map<Long, LocalDate> dates, int overflow) {
    }

    private Repacker() {
    }

    /** @param items in series order */
    static Result repack(List<Item> items, LocalDate from, int minutesPerDay, DayOfWeek bufferDay, LocalDate deadline) {
        Map<Long, LocalDate> dates = new HashMap<>();
        int overflow = 0;
        LocalDate day = workingDayFrom(from, bufferDay);
        int used = 0;
        for (Item item : items) {
            if (used > 0 && used + item.minutes() > minutesPerDay) {
                day = workingDayFrom(day.plusDays(1), bufferDay);
                used = 0;
            }
            if (day.isAfter(deadline)) {
                dates.put(item.id(), deadline);
                overflow++;
                continue;
            }
            dates.put(item.id(), day);
            used += item.minutes();
        }
        return new Result(dates, overflow);
    }

    private static LocalDate workingDayFrom(LocalDate day, DayOfWeek bufferDay) {
        return day.getDayOfWeek() == bufferDay ? day.plusDays(1) : day;
    }
}
