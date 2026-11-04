package com.aicoach.adjust;

import com.aicoach.adjust.Repacker.Item;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepackerTest {

    /** A Thursday; Saturday 7 Nov is the buffer day. */
    static final LocalDate FROM = LocalDate.of(2026, 11, 5);
    static final LocalDate FAR = FROM.plusDays(60);

    @Test
    void fillsEachDayInOrderAndPullsWorkEarlier() {
        List<Item> items = List.of(new Item(1, 40), new Item(2, 40), new Item(3, 40), new Item(4, 30));

        Repacker.Result result = Repacker.repack(items, FROM, 90, DayOfWeek.SATURDAY, FAR);

        // 40 + 40 fit in 90; the third starts Friday with the 30 after it
        assertThat(result.dates()).containsEntry(1L, FROM).containsEntry(2L, FROM)
                .containsEntry(3L, FROM.plusDays(1)).containsEntry(4L, FROM.plusDays(1));
        assertThat(result.overflow()).isZero();
    }

    @Test
    void skipsTheBufferDayAndGivesALongTaskADayToItself() {
        List<Item> items = List.of(new Item(1, 60), new Item(2, 60), new Item(3, 150), new Item(4, 30));

        Repacker.Result result = Repacker.repack(items, FROM, 60, DayOfWeek.SATURDAY, FAR);

        assertThat(result.dates()).containsEntry(1L, FROM).containsEntry(2L, FROM.plusDays(1))
                .containsEntry(3L, FROM.plusDays(3)).containsEntry(4L, FROM.plusDays(4));
    }

    @Test
    void workPastTheDeadlineGoesOnTheDeadline() {
        List<Item> items = List.of(new Item(1, 60), new Item(2, 60), new Item(3, 60));

        Repacker.Result result = Repacker.repack(items, FROM, 60, null, FROM.plusDays(1));

        assertThat(result.dates()).containsEntry(3L, FROM.plusDays(1));
        assertThat(result.overflow()).isEqualTo(1);
    }
}
