package io.github.rkaushikdev.garterhole.method;

import static io.github.rkaushikdev.garterhole.method.LeadHeads.describeLeadHead;
import static io.github.rkaushikdev.garterhole.method.LeadHeads.groupByCode;
import static io.github.rkaushikdev.garterhole.method.LeadHeads.groupByLeadHead;
import static io.github.rkaushikdev.garterhole.method.LeadHeads.leadEndPlaces;
import static io.github.rkaushikdev.garterhole.method.LeadHeads.leadHeadOrder;
import static io.github.rkaushikdev.garterhole.method.LeadHeads.leadHeadSignature;
import static io.github.rkaushikdev.garterhole.method.LeadHeads.sharesLeadHead;
import static io.github.rkaushikdev.garterhole.method.LeadHeads.spliceableEveryLead;
import static io.github.rkaushikdev.garterhole.method.LeadHeads.spliceableWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LeadHeadsTest {

    private static final Method CAMBRIDGE = Method.of("Cambridge Surprise", 8, "x38x14x1258x36x14x58x16x78,12");
    private static final Method YORKSHIRE = Method.of("Yorkshire Surprise", 8, "x38x14x58x16x12x38x14x78,12");
    private static final Method BRISTOL = Method.of("Bristol Surprise", 8, "x58x14.58x58.36.14x14.58x14x18,18");
    private static final Method PLAIN_BOB_MAJOR = Method.of("Plain Bob", 8, "x18x18x18x18,12");
    private static final Method PLAIN_BOB_MINOR = Method.of("Plain Bob", 6, "x1x1x1,2");

    @Nested
    class LeadHeadSignature {

        @Test
        void includesTheStageSoTwoStagesNeverCollide() {
            assertThat(leadHeadSignature(PLAIN_BOB_MAJOR)).isEqualTo("8:13527486");
            assertThat(leadHeadSignature(PLAIN_BOB_MINOR)).isEqualTo("6:135264");
        }

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> leadHeadSignature(null)).hasMessageContaining("a Method is needed");
        }
    }

    @Nested
    class LeadEndPlaces {

        @Test
        void readsThePlacesMadeAtTheLeadEnd() {
            assertThat(leadEndPlaces(CAMBRIDGE)).containsExactly(1, 2);
            assertThat(leadEndPlaces(BRISTOL)).containsExactly(1, 8);
        }

        @Test
        void handsBackACopy() {
            List<Integer> places = leadEndPlaces(CAMBRIDGE);
            places.add(9);
            assertThat(leadEndPlaces(CAMBRIDGE)).containsExactly(1, 2);
        }
    }

    @Nested
    class LeadHeadOrder {

        @Test
        void countsTheLeadsInAPlainCourse() {
            assertThat(leadHeadOrder(CAMBRIDGE)).isEqualTo(7);
            assertThat(leadHeadOrder(PLAIN_BOB_MINOR)).isEqualTo(5);
        }
    }

    @Nested
    class SharesLeadHead {

        @Test
        void findsTwoSurpriseMajorMethodsShareALeadHead() {
            assertThat(sharesLeadHead(CAMBRIDGE, YORKSHIRE)).isTrue();
        }

        @Test
        void separatesMethodsWhoseLeadsAreDifferentLengths() {
            assertThat(sharesLeadHead(CAMBRIDGE, PLAIN_BOB_MAJOR)).isFalse();
        }

        @Test
        void separatesASecondsPlaceMethodFromAnEighthsPlaceOne() {
            assertThat(sharesLeadHead(CAMBRIDGE, BRISTOL)).isFalse();
        }

        @Test
        void separatesTwoStages() {
            assertThat(sharesLeadHead(PLAIN_BOB_MAJOR, PLAIN_BOB_MINOR)).isFalse();
        }
    }

    @Nested
    class GroupByLeadHead {

        @Test
        void collectsTheMethodsThatFinishOnTheSameRow() {
            Map<String, List<Method>> groups =
                    groupByLeadHead(List.of(CAMBRIDGE, YORKSHIRE, BRISTOL, PLAIN_BOB_MAJOR, PLAIN_BOB_MINOR));
            assertThat(groups).hasSize(4);
            assertThat(groups.get("8:15738264")).hasSize(2);
            assertThat(groups.get("8:14263857")).hasSize(1);
            assertThat(groups.get("8:13527486")).hasSize(1);
        }

        @Test
        void givesAnEmptyMapForNoMethods() {
            assertThat(groupByLeadHead(List.of())).isEmpty();
        }
    }

    @Nested
    class SpliceableWith {

        private final List<Method> all = List.of(CAMBRIDGE, YORKSHIRE, BRISTOL, PLAIN_BOB_MAJOR);

        @Test
        void listsTheMethodsThatShareTheLeadHead() {
            assertThat(spliceableWith(CAMBRIDGE, all)).extracting(Method::name).containsExactly("Yorkshire Surprise");
        }

        @Test
        void leavesTheMethodItselfOut() {
            assertThat(spliceableWith(CAMBRIDGE, all)).doesNotContain(CAMBRIDGE);
        }

        @Test
        void givesNothingWhenNothingMatches() {
            assertThat(spliceableWith(BRISTOL, all)).isEmpty();
        }
    }

    @Nested
    class SpliceableEveryLead {

        @Test
        void needsTheLeadLengthsToMatchAsWell() {
            assertThat(spliceableEveryLead(CAMBRIDGE, YORKSHIRE)).isTrue();
            assertThat(spliceableEveryLead(CAMBRIDGE, PLAIN_BOB_MAJOR)).isFalse();
        }

        @Test
        void isFalseWheneverTheLeadHeadsDiffer() {
            assertThat(spliceableEveryLead(CAMBRIDGE, BRISTOL)).isFalse();
        }
    }

    @Nested
    class DescribeLeadHead {

        @Test
        void reportsEverythingASplicingTableNeeds() {
            assertThat(describeLeadHead(CAMBRIDGE))
                    .isEqualTo(new LeadHeads.Description("15738264", 7, "12", 32, Optional.of("b")));
        }

        @Test
        void reportsAnEighthsPlaceMethod() {
            assertThat(describeLeadHead(BRISTOL).leadEnd()).isEqualTo("18");
            assertThat(describeLeadHead(BRISTOL).code()).contains("m");
        }
    }

    @Nested
    class GroupByCode {

        @Test
        void putsCambridgeAndYorkshireTogetherAndBristolApart() {
            Map<String, List<Method>> groups =
                    groupByCode(List.of(CAMBRIDGE, BRISTOL, YORKSHIRE, PLAIN_BOB_MAJOR, PLAIN_BOB_MINOR));
            assertThat(groups.keySet()).containsExactly("8:b", "8:m", "8:a", "6:a");
            assertThat(groups.get("8:b")).containsExactly(CAMBRIDGE, YORKSHIRE);
        }

        @Test
        void leavesOutAMethodWithNoCode() {
            Method stedman = Method.of("Stedman", 7, "3.1.7.3.1.3,1");
            assertThat(groupByCode(List.of(stedman))).isEmpty();
        }
    }
}
