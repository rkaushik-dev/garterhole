package io.github.rkaushikdev.garterhole.tower;

import static io.github.rkaushikdev.garterhole.tower.Assignment.assignBells;
import static io.github.rkaushikdev.garterhole.tower.Assignment.bellsOpenTo;
import static io.github.rkaushikdev.garterhole.tower.Assignment.ringingBells;
import static io.github.rkaushikdev.garterhole.tower.Ringer.ringer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.tower.Assignment.Request;
import io.github.rkaushikdev.garterhole.tower.CompetencyRegister.Level;
import io.github.rkaushikdev.garterhole.tower.Ringer.Details;
import io.github.rkaushikdev.garterhole.tower.Ringer.Role;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AssignmentTest {

    private static final String TITLE = "Plain Bob Minor";

    /** A tower whose bells get steadily heavier. */
    private static Tower tower(int bells) {
        return new Tower(
                "Test",
                IntStream.rangeClosed(1, bells)
                        .mapToObj(number -> new Bell(number, 200 * number))
                        .toList());
    }

    private static Tower tower() {
        return tower(6);
    }

    private static Ringer member(String id) {
        return ringer(id, Details.NONE.withId(id));
    }

    private static Ringer member(String id, double heaviest) {
        return ringer(id, Details.NONE.withId(id).withHeaviestBellPounds(heaviest));
    }

    private static List<Ringer> band() {
        return List.of(
                ringer("alice", Details.NONE.withId("alice").withRoles(Role.CONDUCTOR)),
                member("bob"),
                member("carol"),
                member("dan"),
                member("erin"),
                member("frank"));
    }

    private static List<Ringer> quiet() {
        return band().stream().map(each -> member(each.id())).toList();
    }

    @Nested
    class RingingBells {

        @Test
        void listsTheBellsAnEvenStageNeeds() {
            assertThat(ringingBells(Request.of(tower(), band(), 6))).containsExactly(1, 2, 3, 4, 5, 6);
        }

        @Test
        void addsACoverForAnOddStageWhenThereIsABellSpare() {
            assertThat(ringingBells(Request.of(tower(), band(), 5))).containsExactly(1, 2, 3, 4, 5, 6);
        }

        @Test
        void ringsAnOddStageWithoutACoverWhenThereIsNoBellSpare() {
            assertThat(ringingBells(Request.of(tower(5), band(), 5))).containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        void ringsTheBackBellsWhenAskedAndCoversWithTheTenor() {
            assertThat(ringingBells(Request.of(tower(8), band(), 6).onTheBackBells()))
                    .containsExactly(3, 4, 5, 6, 7, 8);
            assertThat(ringingBells(Request.of(tower(8), band(), 5).onTheBackBells()))
                    .containsExactly(3, 4, 5, 6, 7, 8);
            assertThat(Request.of(tower(8), band(), 6).ring()).isEqualTo(Assignment.Ring.FRONT);
        }

        @Test
        void putsTheBandOnTheBackSixOfAnEight() {
            Assignment.Result result =
                    Assignment.assignBells(Request.of(tower(8), band(), 6).onTheBackBells());
            assertThat(result.assignment().keySet()).containsExactly(3, 4, 5, 6, 7, 8);
        }

        @Test
        void keepsTheBackBellsWhenTheMethodAndLevelAreGiven() {
            Request request = Request.of(tower(8), band(), 6)
                    .onTheBackBells()
                    .withLevel(CompetencyRegister.Level.INSIDE)
                    .requiringConductor();
            assertThat(request.ring()).isEqualTo(Assignment.Ring.BACK);
        }

        @Test
        void refusesAStageTheTowerCannotRing() {
            assertThatThrownBy(() -> ringingBells(Request.of(tower(), band(), 8)))
                    .hasMessageContaining("cannot ring 8 bells");
        }

        @Test
        void refusesARequestItCannotUse() {
            assertThatThrownBy(() -> ringingBells(null)).hasMessageContaining("needs a request object");
            assertThatThrownBy(() -> ringingBells(Request.of(null, band(), 6))).hasMessageContaining("needs a Tower");
            assertThatThrownBy(() -> ringingBells(Request.of(tower(), List.of(), 6)))
                    .hasMessageContaining("needs a band");
            assertThatThrownBy(() -> ringingBells(Request.of(tower(), Arrays.asList((Ringer) null), 6)))
                    .hasMessageContaining("must be a Ringer");
        }
    }

    @Nested
    class BellsOpenTo {

        @Test
        void opensEveryBellToARingerWithNoLimits() {
            assertThat(bellsOpenTo(Request.of(tower(), band(), 6), member("alice")))
                    .containsExactly(1, 2, 3, 4, 5, 6);
        }

        @Test
        void closesTheHeavyBellsToARingerWhoCannotTakeThem() {
            assertThat(bellsOpenTo(Request.of(tower(), band(), 6), member("bob", 600)))
                    .containsExactly(1, 2, 3);
        }

        @Test
        void closesEveryBellToARingerWhoCannotRingTheMethod() {
            CompetencyRegister competency = new CompetencyRegister().record("alice", TITLE, Level.CONFIDENT);
            Request request = Request.of(tower(), band(), 6).withMethod(TITLE, competency);
            assertThat(bellsOpenTo(request, member("bob"))).isEmpty();
            assertThat(bellsOpenTo(request, member("alice"))).hasSize(6);
        }
    }

    @Nested
    class AssignBells {

        @Test
        void putsABandWithNoLimitsOnTheBells() {
            Map<Integer, String> assignment =
                    assignBells(Request.of(tower(), band(), 6)).assignment();
            assertThat(assignment).hasSize(6);
            assertThat(new HashSet<>(assignment.values())).hasSize(6);
        }

        @Test
        void keepsTheBellsInOrder() {
            assertThat(assignBells(Request.of(tower(), band(), 6)).assignment().keySet())
                    .containsExactly(1, 2, 3, 4, 5, 6);
        }

        @Test
        void respectsTheWeightARingerCanTake() {
            List<Ringer> limited = band().stream()
                    .map(each -> each.id().equals("frank") ? member("frank", 250) : each)
                    .toList();
            assertThat(assignBells(Request.of(tower(), limited, 6)).assignment())
                    .containsEntry(1, "frank");
        }

        @Test
        void backtracksWhenAGreedyChoiceWouldFail() {
            List<Ringer> tight = List.of(
                    member("a", 1200),
                    member("b", 1200),
                    member("c", 1200),
                    member("d", 1200),
                    member("e", 1200),
                    member("f"));
            assertThat(assignBells(Request.of(tower(), tight, 6)).assignment()).containsEntry(6, "f");
        }

        @Test
        void namesAConductorWhoIsOnTheBells() {
            assertThat(assignBells(Request.of(tower(), band(), 6)).conductor()).hasValue("alice");
        }

        @Test
        void reportsNoConductorWhenNobodyConducts() {
            assertThat(assignBells(Request.of(tower(), quiet(), 6)).conductor()).isEmpty();
        }

        @Test
        void refusesWhenAConductorIsRequiredAndThereIsNone() {
            assertThatThrownBy(() -> assignBells(Request.of(tower(), quiet(), 6).requiringConductor()))
                    .hasMessageContaining("nobody on the bells can conduct");
        }

        @Test
        void refusesWhenThereAreNotEnoughRingers() {
            assertThatThrownBy(() -> assignBells(Request.of(tower(), band().subList(0, 3), 6)))
                    .hasMessage("6 bells need ringing and only 3 are here");
        }

        @Test
        void refusesWhenNobodyCanTakeABell() {
            List<Ringer> light =
                    band().stream().map(each -> member(each.id(), 300)).toList();
            assertThatThrownBy(() -> assignBells(Request.of(tower(), light, 6)))
                    .hasMessage("nobody here can take bell 2");
        }

        @Test
        void refusesWhenTheBandCannotBeMatchedToTheBells() {
            List<Ringer> heavyOnly = new ArrayList<>(List.of(member("a", 200), member("b", 200)));
            heavyOnly.addAll(band().subList(2, 6));
            assertThatThrownBy(() -> assignBells(Request.of(tower(), heavyOnly, 6)))
                    .isInstanceOf(ValidationException.class);
        }
    }
}
