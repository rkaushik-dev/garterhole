package io.github.rkaushikdev.garterhole.core;

import static io.github.rkaushikdev.garterhole.core.RowParse.parseRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.CallChanges.Called;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CallChangesTest {

    @Nested
    class Ringing {

        @Test
        void swapsTheCalledBellBehindTheOneItFollows() {
            List<Called> rung = CallChanges.ring(Row.rounds(6), "2 to 3, 4 to 5");
            assertThat(rung).extracting(Called::row).containsExactly(parseRow("132456"), parseRow("132546"));
            assertThat(rung.get(0).call()).isEqualTo("2 to 3");
        }

        @Test
        void namesTheRowsThatHaveNames() {
            List<Called> rung = CallChanges.ring(Row.rounds(6), "2 to 3; 4 to 5\n2 to 5");
            assertThat(rung.get(2).row()).isEqualTo(parseRow("135246"));
            assertThat(rung.get(2).name()).contains("Queens");
            assertThat(rung.get(0).name()).isEmpty();
        }

        @Test
        void callsABellInSecondsToLead() {
            assertThat(CallChanges.apply(parseRow("132456"), "3 lead")).isEqualTo(parseRow("312456"));
            assertThat(CallChanges.apply(parseRow("132456"), "3 to lead")).isEqualTo(parseRow("312456"));
            assertThat(CallChanges.apply(Row.rounds(6), "2 follow 3")).isEqualTo(parseRow("132456"));
        }

        @Test
        void ringsNothingForNoCalls() {
            assertThat(CallChanges.ring(Row.rounds(6), " , ")).isEmpty();
            assertThat(CallChanges.ring(Row.rounds(6), null)).isEmpty();
        }
    }

    @Nested
    class Refusing {

        @Test
        void refusesACallWhenTheBellToFollowIsNotStraightBehind() {
            assertThatThrownBy(() -> CallChanges.apply(Row.rounds(6), "2 to 4"))
                    .isInstanceOf(NotationException.class)
                    .hasMessageContaining("4 is not straight behind 2");
            assertThatThrownBy(() -> CallChanges.apply(Row.rounds(6), "6 to 1"))
                    .hasMessageContaining("1 is not straight behind 6");
        }

        @Test
        void refusesALeadForABellThatIsNotInSeconds() {
            assertThatThrownBy(() -> CallChanges.apply(Row.rounds(6), "4 lead")).hasMessageContaining("not in seconds");
        }

        @Test
        void refusesSomethingThatIsNotACall() {
            assertThatThrownBy(() -> CallChanges.apply(Row.rounds(6), "go")).hasMessageContaining("not a call change");
            assertThatThrownBy(() -> CallChanges.apply(Row.rounds(6), "2 to 9"))
                    .hasMessageContaining("not a bell on 6");
            assertThatThrownBy(() -> CallChanges.ring(null, "2 to 3")).hasMessageContaining("a Row is needed");
        }
    }

    @Nested
    class CallsBetween {

        @Test
        void callsRoundsToQueensAndBackAgain() {
            Row queens = NamedRows.queens(6);
            List<String> there = CallChanges.callsBetween(Row.rounds(6), queens);
            assertThat(there).containsExactly("2 to 3", "4 to 5", "2 to 5");
            List<Called> rung = CallChanges.ring(Row.rounds(6), String.join(", ", there));
            assertThat(rung.get(rung.size() - 1).row()).isEqualTo(queens);
            List<String> back = CallChanges.callsBetween(queens, Row.rounds(6));
            assertThat(CallChanges.ring(queens, String.join(", ", back))
                            .get(back.size() - 1)
                            .row())
                    .isEqualTo(Row.rounds(6));
        }

        @Test
        void takesAsManyCallsAsThereArePairsTheWrongWayRound() {
            assertThat(CallChanges.callsBetween(Row.rounds(8), NamedRows.backrounds(8)))
                    .hasSize(28);
            assertThat(CallChanges.callsBetween(Row.rounds(8), Row.rounds(8))).isEmpty();
        }

        @Test
        void callsTheTrebleOffTheLeadWhenItHasToMove() {
            assertThat(CallChanges.callsBetween(Row.rounds(4), parseRow("2134")))
                    .containsExactly("2 lead");
        }

        @Test
        void refusesRowsAtDifferentStages() {
            assertThatThrownBy(() -> CallChanges.callsBetween(Row.rounds(6), Row.rounds(8)))
                    .isInstanceOf(ValidationException.class);
        }
    }
}
