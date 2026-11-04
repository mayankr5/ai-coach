package com.aicoach.replan;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks the dates a coach message mentions against the facts it was given. Models, small ones especially,
 * invent dates or call tomorrow "today"; a message that does is sent back with the problem explained.
 * Only dates are checked, not the rest of the wording.
 */
final class MessageDates {

    private static final String MONTH = "(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|June?|July?"
            + "|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\.?";
    private static final Pattern ISO = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");
    private static final Pattern MONTH_DAY = Pattern.compile(
            "\\b" + MONTH + "\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b(?:,?\\s+(\\d{4})\\b)?");
    private static final Pattern DAY_MONTH = Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?" + MONTH + "(?![a-z])(?:,?\\s+(\\d{4})\\b)?");
    /** Full names in any case; short forms only capitalised, so "sat" or "sun" in a sentence don't count. */
    private static final Pattern WEEKDAY = Pattern.compile("\\b(?:(?i:(mon|tues|wednes|thurs|fri|satur|sun)day)"
            + "|(Mon|Tue|Tues|Wed|Thu|Thur|Thurs|Fri|Sat|Sun))\\b");
    private static final Pattern TODAY = Pattern.compile("(?i)\\btoday\\b");
    /** Text allowed between "today" and a date for them to mean the same day, as in "today (Nov 5)". */
    private static final int MAX_GAP = 12;

    private record Mention(int start, int end, String text, LocalDate date, DayOfWeek weekday) {
        boolean isDay(LocalDate day) {
            return date != null ? date.equals(day) : weekday == day.getDayOfWeek();
        }
    }

    private MessageDates() {
    }

    /** @param known dates the message may mention: today and every date in the facts */
    static List<String> problems(String message, LocalDate today, Set<LocalDate> known) {
        List<String> problems = new ArrayList<>();
        List<Mention> mentions = new ArrayList<>();
        findDates(message, today, mentions, problems);
        findWeekdays(message, mentions);

        Set<DayOfWeek> knownWeekdays = known.stream().map(LocalDate::getDayOfWeek).collect(Collectors.toSet());
        DayLabels labels = new DayLabels(today);
        for (Mention m : mentions) {
            if (m.date() != null && !known.contains(m.date())) {
                problems.add("You mentioned \"" + m.text() + "\", which is not a date in the facts; "
                        + "only mention dates listed there");
            } else if (m.date() == null && !knownWeekdays.contains(m.weekday())) {
                problems.add("You mentioned \"" + m.text() + "\", but no date in the facts is on that weekday");
            }
        }

        Matcher t = TODAY.matcher(message);
        while (t.find()) {
            for (Mention m : mentions) {
                if (!m.isDay(today) && nextTo(message, t.start(), t.end(), m)) {
                    String day = m.date() != null ? labels.of(m.date()) : "\"" + m.text() + "\"";
                    problems.add("You called " + day + " \"today\", but today is " + labels.of(today)
                            + "; plan changes start tomorrow");
                }
            }
        }
        return problems.stream().distinct().toList();
    }

    private static void findDates(String message, LocalDate today, List<Mention> mentions, List<String> problems) {
        Matcher iso = ISO.matcher(message);
        while (iso.find()) {
            add(mentions, problems, iso, today, Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)),
                    Integer.parseInt(iso.group(3)));
        }
        Matcher md = MONTH_DAY.matcher(message);
        while (md.find()) {
            add(mentions, problems, md, today, year(md.group(3)), month(md.group(1)), Integer.parseInt(md.group(2)));
        }
        Matcher dm = DAY_MONTH.matcher(message);
        while (dm.find()) {
            add(mentions, problems, dm, today, year(dm.group(3)), month(dm.group(2)), Integer.parseInt(dm.group(1)));
        }
    }

    private static void findWeekdays(String message, List<Mention> mentions) {
        Matcher w = WEEKDAY.matcher(message);
        while (w.find()) {
            int start = w.start();
            // "Thu 5 Nov" is checked as a date already
            if (mentions.stream().anyMatch(m -> m.date() != null && m.start() > start && m.start() - w.end() <= 2)) {
                continue;
            }
            String name = w.group(1) != null ? w.group(1) : w.group(2);
            mentions.add(new Mention(start, w.end(), w.group(), null, weekday(name)));
        }
    }

    private static void add(List<Mention> mentions, List<String> problems, Matcher m, LocalDate today,
                            Integer year, int month, int day) {
        LocalDate date = year != null ? date(year, month, day) : closestToToday(today, month, day);
        if (date == null) {
            problems.add("\"" + m.group() + "\" is not a real date");
        } else {
            mentions.add(new Mention(m.start(), m.end(), m.group(), date, null));
        }
    }

    /** "Nov 5" with no year: the matching date nearest to today, so late December can mention early January. */
    private static LocalDate closestToToday(LocalDate today, int month, int day) {
        LocalDate best = null;
        for (int year = today.getYear() - 1; year <= today.getYear() + 1; year++) {
            LocalDate d = date(year, month, day);
            if (d != null && (best == null || Math.abs(d.toEpochDay() - today.toEpochDay())
                    < Math.abs(best.toEpochDay() - today.toEpochDay()))) {
                best = d;
            }
        }
        return best;
    }

    private static LocalDate date(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** "today (Nov 5)", "today, Thursday", "Nov 5 is today": only brackets, commas, "is" or "on" in between. */
    private static boolean nextTo(String message, int todayStart, int todayEnd, Mention m) {
        String gap;
        if (m.start() >= todayEnd) {
            gap = message.substring(todayEnd, m.start());
        } else if (m.end() <= todayStart) {
            gap = message.substring(m.end(), todayStart);
        } else {
            return false;
        }
        return gap.length() <= MAX_GAP
                && gap.replaceAll("(?i)\\b(is|on)\\b", "").replaceAll("[\\s(),:\\-–—]", "").isEmpty();
    }

    private static Integer year(String text) {
        return text == null ? null : Integer.parseInt(text);
    }

    private static int month(String text) {
        String prefix = text.substring(0, 3).toUpperCase(Locale.ROOT);
        return Arrays.stream(Month.values()).filter(m -> m.name().startsWith(prefix)).findFirst()
                .orElseThrow().getValue();
    }

    private static DayOfWeek weekday(String text) {
        String prefix = text.substring(0, 3).toUpperCase(Locale.ROOT);
        return Arrays.stream(DayOfWeek.values()).filter(d -> d.name().startsWith(prefix)).findFirst().orElseThrow();
    }
}
