package com.aicoach.replan;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writes dates the way people say them, relative to the user's today: "tomorrow (Thu 5 Nov)", "Sat 7 Nov".
 * Models copy these far more reliably than ISO dates, and the dates written are remembered, so a message
 * can later be checked against exactly the dates it was given.
 */
final class DayLabels {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_WITH_YEAR = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

    private final LocalDate today;
    private final Set<LocalDate> written = new HashSet<>();

    DayLabels(LocalDate today) {
        this.today = today;
        written.add(today);
    }

    String of(LocalDate date) {
        written.add(date);
        String day = date.format(date.getYear() == today.getYear() ? DAY : DAY_WITH_YEAR);
        if (date.equals(today)) return "today (" + day + ")";
        if (date.equals(today.plusDays(1))) return "tomorrow (" + day + ")";
        if (date.equals(today.minusDays(1))) return "yesterday (" + day + ")";
        return day;
    }

    String of(Collection<LocalDate> dates) {
        return dates.stream().map(this::of).collect(Collectors.joining(", "));
    }

    LocalDate today() {
        return today;
    }

    /** Every date labelled so far, today included. */
    Set<LocalDate> written() {
        return Set.copyOf(written);
    }
}
