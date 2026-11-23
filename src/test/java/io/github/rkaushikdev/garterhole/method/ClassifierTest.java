package io.github.rkaushikdev.garterhole.method;

import static io.github.rkaushikdev.garterhole.method.Classifier.baseClass;
import static io.github.rkaushikdev.garterhole.method.Classifier.classify;
import static io.github.rkaushikdev.garterhole.method.Classifier.crossSections;
import static io.github.rkaushikdev.garterhole.method.Classifier.isDifferential;
import static io.github.rkaushikdev.garterhole.method.Classifier.isLittle;
import static io.github.rkaushikdev.garterhole.method.Classifier.makesInternalPlace;
import static io.github.rkaushikdev.garterhole.method.Classifier.ringsEvenly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClassifierTest {

    private static final Method PLAIN_BOB_MINOR = Method.of("Plain Bob", 6, "x1x1x1,2");
    private static final Method PLAIN_BOB_MAJOR = Method.of("Plain Bob", 8, "x18x18x18x18,12");
    private static final Method GRANDSIRE_DOUBLES = Method.of("Grandsire", 5, "3,1.5.1.5.1");
    private static final Method DOUBLE_NORWICH = Method.of("Double Norwich Court Bob", 8, "x14x36x58x18,18");
    private static final Method CAMBRIDGE_MINOR = Method.of("Cambridge Surprise", 6, "x36x14x12x36x14x56,12");
    private static final Method CAMBRIDGE_MAJOR = Method.of("Cambridge Surprise", 8, "x38x14x1258x36x14x58x16x78,12");
    private static final Method YORKSHIRE_MAJOR = Method.of("Yorkshire Surprise", 8, "x38x14x58x16x12x38x14x78,12");
    private static final Method BRISTOL_MAJOR = Method.of("Bristol Surprise", 8, "x58x14.58x58.36.14x14.58x14x18,18");
    private static final Method KENT_MAJOR = Method.of("Kent Treble Bob", 8, "34x34.18x12x18x12x18x12x18,18");
    private static final Method LITTLE_BOB_MAJOR = Method.of("Little Bob", 8, "x18x14x18x12");

    /** Written for the tests: an internal place at some cross sections but not all. */
    private static Method delight() {
        return Method.of("Test Delight", 6, "34x34.16x12x36x14x56,12");
    }

    /** Written for the tests: no bell is left in its place by the lead head. */
    private static Method principle() {
        return Method.of("Test Principle", 5, "3.1.5.3.1.3.5.1.3.1");
    }

    /** Written for the tests: two hunt bells and working bells in cycles of different length. */
    private static Method differential() {
        return Method.of("Test Differential", 8, "x38x18x12x18,18");
    }

    /** Written for the tests: the treble plain hunts but leads for four blows, twice as long as anywhere else. */
    private static Method hybrid() {
        return Method.of("Test", 6, "1236.56.16.12.36.14.16,12");
    }

    /** Written for the tests: every bell hunts and nothing dodges. */
    private static Method everyBellHunts() {
        return Method.of("Test Place", 8, "x18x18x18x18,18");
    }

    @Nested
    class SectionOf {

        @Test
        void pairsThePlacesUpFromTheFront() {
            assertThat(IntStream.rangeClosed(1, 6).map(Classifier::sectionOf).boxed())
                    .containsExactly(1, 1, 2, 2, 3, 3);
        }
    }

    @Nested
    class CrossSections {

        @Test
        void marksWhereThePathMovesBetweenPairs() {
            assertThat(crossSections(List.of(1, 2, 1, 2, 3, 4, 3, 4))).containsExactly(3);
        }

        @Test
        void marksNothingWhileThePathStaysInOnePair() {
            assertThat(crossSections(List.of(1, 2, 1, 2))).isEmpty();
        }

        @Test
        void marksEveryStepOfPlainHunt() {
            assertThat(crossSections(List.of(1, 2, 3, 4, 5, 6))).containsExactly(1, 3);
        }
    }

    @Nested
    class MakesInternalPlace {

        @Test
        void ignoresPlacesAtTheFrontAndTheBack() {
            assertThat(makesInternalPlace(Change.of(8, 1, 8))).isFalse();
            assertThat(makesInternalPlace(Change.cross(8))).isFalse();
        }

        @Test
        void findsAPlaceInTheMiddle() {
            assertThat(makesInternalPlace(Change.of(8, 1, 4))).isTrue();
            assertThat(makesInternalPlace(Change.of(8, 3, 4))).isTrue();
        }
    }

    @Nested
    class IsLittle {

        @Test
        void findsATrebleThatNeverReachesTheBack() {
            assertThat(isLittle(LITTLE_BOB_MAJOR, 1)).isTrue();
        }

        @Test
        void doesNotCallAnOrdinaryMethodLittle() {
            assertThat(isLittle(PLAIN_BOB_MAJOR, 1)).isFalse();
            assertThat(isLittle(CAMBRIDGE_MAJOR, 1)).isFalse();
        }
    }

    @Nested
    class IsDifferential {

        @Test
        void isFalseWhenEveryWorkingBellRingsTheSameLine() {
            assertThat(isDifferential(PLAIN_BOB_MINOR)).isFalse();
            assertThat(isDifferential(CAMBRIDGE_MAJOR)).isFalse();
            assertThat(isDifferential(GRANDSIRE_DOUBLES)).isFalse();
        }

        @Test
        void isTrueWhenTheWorkingBellsFallIntoCyclesOfDifferentLengths() {
            assertThat(isDifferential(differential())).isTrue();
        }

        @Test
        void isFalseWhenThereAreNoWorkingBellsAtAll() {
            assertThat(isDifferential(everyBellHunts())).isFalse();
        }
    }

    @Nested
    class RingsEvenly {

        @Test
        void holdsForEveryTidyFamilyOfHuntBellPath() {
            assertThat(ringsEvenly(PLAIN_BOB_MAJOR, 1)).isTrue();
            assertThat(ringsEvenly(CAMBRIDGE_MAJOR, 1)).isTrue();
            assertThat(ringsEvenly(KENT_MAJOR, 1)).isTrue();
            assertThat(ringsEvenly(DOUBLE_NORWICH, 1)).isTrue();
        }

        @Test
        void countsOnlyThePlacesALittleTrebleReaches() {
            assertThat(HuntBells.blowsInEachPlace(LITTLE_BOB_MAJOR, 1)).containsExactly(2, 2, 2, 2, 0, 0, 0, 0);
            assertThat(ringsEvenly(LITTLE_BOB_MAJOR, 1)).isTrue();
        }

        @Test
        void failsATrebleThatLingersAtTheFront() {
            assertThat(HuntBells.blowsInEachPlace(hybrid(), 1)).containsExactly(4, 2, 2, 2, 2, 2);
            assertThat(ringsEvenly(hybrid(), 1)).isFalse();
        }

        @Test
        void failsAHuntBellThatHurriesThroughTheFront() {
            assertThat(HuntBells.blowsInEachPlace(differential(), 4)).containsExactly(0, 0, 2, 2, 3, 3, 3, 3);
            assertThat(ringsEvenly(differential(), 4)).isFalse();
        }
    }

    @Nested
    class BaseClass {

        @Test
        void callsAMethodHybridWhenItsTrebleDoesNotRingEvenly() {
            assertThat(baseClass(hybrid())).isEqualTo("Hybrid");
            assertThat(classify(hybrid()).huntPath()).contains(HuntPath.PLAIN);
            assertThat(Title.of(hybrid())).isEqualTo("Test Hybrid Minor");
            assertThat(Title.parse(Title.of(hybrid())).className()).isEqualTo("Hybrid");
        }

        @Test
        void callsAPlainMethodWithDodgesABobMethod() {
            assertThat(baseClass(PLAIN_BOB_MINOR)).isEqualTo("Bob");
            assertThat(baseClass(PLAIN_BOB_MAJOR)).isEqualTo("Bob");
            assertThat(baseClass(GRANDSIRE_DOUBLES)).isEqualTo("Bob");
            assertThat(baseClass(DOUBLE_NORWICH)).isEqualTo("Bob");
        }

        @Test
        void callsAPlainMethodWithNoDodgesAPlaceMethod() {
            assertThat(baseClass(everyBellHunts())).isEqualTo("Place");
        }

        @Test
        void callsATrebleBobMethodWithAPlaceAtEveryCrossSectionSurprise() {
            assertThat(baseClass(CAMBRIDGE_MINOR)).isEqualTo("Surprise");
            assertThat(baseClass(CAMBRIDGE_MAJOR)).isEqualTo("Surprise");
            assertThat(baseClass(YORKSHIRE_MAJOR)).isEqualTo("Surprise");
            assertThat(baseClass(BRISTOL_MAJOR)).isEqualTo("Surprise");
        }

        @Test
        void callsATrebleBobMethodWithNoInternalPlacesTrebleBob() {
            assertThat(baseClass(KENT_MAJOR)).isEqualTo("Treble Bob");
        }

        @Test
        void callsATrebleBobMethodWithSomeInternalPlacesDelight() {
            assertThat(baseClass(delight())).isEqualTo("Delight");
        }

        @Test
        void callsAMethodWithNoHuntBellAPrinciple() {
            assertThat(baseClass(principle())).isEqualTo("Principle");
        }

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> baseClass(null)).hasMessageContaining("a Method is needed");
        }
    }

    @Nested
    class Classify {

        @Test
        void reportsTheClassAndTheEvidenceForIt() {
            assertThat(classify(CAMBRIDGE_MAJOR))
                    .isEqualTo(new Classification(
                            "Surprise", "Surprise", false, false, List.of(1), Optional.of(HuntPath.TREBLE_BOB)));
            assertThat(classify(CAMBRIDGE_MAJOR).toJson())
                    .isEqualTo(Json.parse("{\"className\": \"Surprise\", \"base\": \"Surprise\", \"little\": false,"
                            + " \"differential\": false, \"huntBells\": [1], \"huntPath\": \"treble bob\"}"));
        }

        @Test
        void putsLittleInFrontOfTheClassName() {
            assertThat(classify(LITTLE_BOB_MAJOR).className()).isEqualTo("Little Bob");
        }

        @Test
        void putsDifferentialInFrontOfTheClassName() {
            assertThat(classify(differential()).className()).isEqualTo("Differential Hybrid");
            assertThat(classify(differential()).differential()).isTrue();
        }

        @Test
        void reportsNoHuntPathForAPrinciple() {
            Classification answer = classify(principle());
            assertThat(answer.className()).isEqualTo("Principle");
            assertThat(answer.huntPath()).isEmpty();
            assertThat(answer.toJson().get("huntPath").isNull()).isTrue();
            assertThat(answer.huntBells()).isEmpty();
        }

        @Test
        void listsBothHuntBellsOfGrandsire() {
            assertThat(classify(GRANDSIRE_DOUBLES).huntBells()).containsExactly(1, 2);
        }
    }
}
