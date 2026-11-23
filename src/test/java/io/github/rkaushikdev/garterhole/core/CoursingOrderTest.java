package io.github.rkaushikdev.garterhole.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CoursingOrderTest {

    private static CoursingOrder of(String row) {
        return CoursingOrder.of(RowParse.parseRow(row));
    }

    @Nested
    class Reading {

        @Test
        void readsRoundsAsTheNaturalCoursingOrder() {
            assertThat(CoursingOrder.natural(8)).hasToString("8753246");
            assertThat(CoursingOrder.natural(6)).hasToString("65324");
            assertThat(CoursingOrder.natural(7)).hasToString("753246");
            assertThat(CoursingOrder.natural(10).bells()).containsExactly(10, 9, 7, 5, 3, 2, 4, 6, 8);
        }

        @Test
        void givesEveryLeadHeadOfAPlainCourseTheSameOrder() {
            for (String head : List.of("13527486", "15738264", "17856342", "18674523", "16482735", "14263857")) {
                assertThat(of(head)).isEqualTo(CoursingOrder.natural(8));
            }
        }

        @Test
        void showsWhatACallDidToTheOrder() {
            assertThat(of("12348567")).hasToString("8324576");
            assertThat(of("13425678")).hasToString("8754326");
        }

        @Test
        void refusesARowWithoutTheTrebleLeading() {
            assertThatThrownBy(() -> of("21345678")).hasMessageContaining("treble leading");
            assertThatThrownBy(() -> CoursingOrder.of(null)).hasMessageContaining("a Row is needed");
        }

        @Test
        void cannotBeChangedFromOutside() {
            assertThatThrownBy(() -> CoursingOrder.natural(8).bells().add(9))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class TenorsTogether {

        @Test
        void holdsForRoundsAndForAHeadThatOnlyMovesTheLittleBells() {
            assertThat(CoursingOrder.natural(8).tenorsTogether(2)).isTrue();
            assertThat(of("13425678").tenorsTogether(2)).isTrue();
            assertThat(CoursingOrder.natural(10).tenorsTogether(4)).isTrue();
        }

        @Test
        void failsWhenACallPartsTheTenors() {
            assertThat(of("12348567").tenorsTogether(2)).isFalse();
            assertThat(of("12345687").tenorsTogether(2)).isFalse();
        }

        @Test
        void looksAtAsManyTenorsAsItIsAsked() {
            CoursingOrder sixAway = of("12345867");
            assertThat(sixAway.tenorsTogether(2)).isTrue();
            assertThat(sixAway.tenorsTogether(3)).isFalse();
            assertThat(CoursingOrder.natural(8).tenorsTogether(7)).isTrue();
        }

        @Test
        void refusesACountThatMakesNoSense() {
            assertThatThrownBy(() -> CoursingOrder.natural(8).tenorsTogether(1))
                    .hasMessageContaining("1 tenors cannot be looked at on 8 bells");
            assertThatThrownBy(() -> CoursingOrder.natural(8).tenorsTogether(8))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class WithoutTenors {

        @Test
        void writesTheOrderTheWayACompositionTableDoes() {
            assertThat(CoursingOrder.natural(8).withoutTenors(2)).isEqualTo("53246");
            assertThat(of("13425678").withoutTenors(2)).isEqualTo("54326");
            assertThat(CoursingOrder.natural(8).withoutTenors(3)).isEqualTo("5324");
            assertThat(CoursingOrder.natural(6).withoutTenors(2)).isEqualTo("324");
        }

        @Test
        void leavesNothingWhenEveryBellIsATenor() {
            assertThat(CoursingOrder.natural(8).withoutTenors(7)).isEmpty();
        }

        @Test
        void refusesToHideTenorsThatAreParted() {
            assertThatThrownBy(() -> of("12348567").withoutTenors(2)).hasMessageContaining("tenors are parted");
        }
    }

    @Nested
    class OddAndEvenBells {

        @Test
        void splitsAStageIntoItsOddAndEvenBells() {
            assertThat(Bells.oddBells(7)).containsExactly(1, 3, 5, 7);
            assertThat(Bells.evenBells(7)).containsExactly(2, 4, 6);
            assertThat(Bells.evenBells(1)).isEmpty();
        }
    }
}
