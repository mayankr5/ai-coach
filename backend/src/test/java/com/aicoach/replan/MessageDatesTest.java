package com.aicoach.replan;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MessageDatesTest {

    /** A Wednesday; the facts mention tomorrow, Saturday and the deadline. */
    static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
    static final Set<LocalDate> KNOWN = Set.of(TODAY, TODAY.plusDays(1), TODAY.plusDays(3), LocalDate.of(2027, 1, 2));

    @Test
    void datesFromTheFactsPass() {
        assertThat(problems("Great work today. Tomorrow (Thu 5 Nov) starts with the rest of the setup.")).isEmpty();
        assertThat(problems("Finish it on Thursday, Nov 5, then extra time on Sat 7 Nov.")).isEmpty();
        assertThat(problems("Your deadline is 2 January 2027; the 7th November and 2026-11-07 are busy.")).isEmpty();
        assertThat(problems("You sat down today and did it.")).isEmpty();
    }

    @Test
    void tomorrowCalledTodayIsRejected() {
        assertThat(problems("27 minutes have been added to your plan today (Nov 5) to finish the setup."))
                .containsExactly("You called tomorrow (Thu 5 Nov) \"today\", but today is today (Wed 4 Nov); "
                        + "plan changes start tomorrow");
        assertThat(problems("Thursday is today, so start early.")).hasSize(1);
    }

    @Test
    void datesNotInTheFactsAreRejected() {
        assertThat(problems("Your lesson moved to Nov 9.")).singleElement().asString().contains("\"Nov 9\"");
        assertThat(problems("See you on Friday!")).singleElement().asString().contains("no date in the facts");
        assertThat(problems("Finish it on 2026-11-31.")).containsExactly("\"2026-11-31\" is not a real date");
    }

    @Test
    void yearIsTheOneClosestToToday() {
        // "2 Jan" in November means next January
        assertThat(problems("Keep going until Jan 2.")).isEmpty();
    }

    private static List<String> problems(String message) {
        return MessageDates.problems(message, TODAY, KNOWN);
    }
}
