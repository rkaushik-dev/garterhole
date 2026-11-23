package io.github.rkaushikdev.garterhole.tower;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.ValidationException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TowerTest {

    private static List<Bell> ring(int count) {
        List<Bell> bells = new ArrayList<>();
        for (int number = 1; number <= count; number += 1) {
            bells.add(new Bell(number, 300 + number * 200));
        }
        return bells;
    }

    private static Tower stMary() {
        return new Tower("St Mary the Virgin", "Bridgend", ring(8));
    }

    @Nested
    class TowerItself {

        @Test
        void keepsTheNameAndThePlace() {
            assertThat(stMary().name()).isEqualTo("St Mary the Virgin");
            assertThat(stMary().place()).hasValue("Bridgend");
        }

        @Test
        void isFrozen() {
            assertThat(Modifier.isFinal(Tower.class.getModifiers())).isTrue();
            assertThatThrownBy(() -> stMary().bells().add(new Bell(9, 3000)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void sortsTheBellsByNumber() {
            List<Bell> reversed = ring(4);
            Collections.reverse(reversed);
            Tower tower = new Tower("Test", reversed);
            assertThat(tower.bells()).extracting(Bell::number).containsExactly(1, 2, 3, 4);
        }

        @Test
        void countsTheBells() {
            assertThat(stMary().size()).isEqualTo(8);
        }

        @Test
        void namesTheTrebleAndTheTenor() {
            assertThat(stMary().treble().number()).isEqualTo(1);
            assertThat(stMary().tenor().number()).isEqualTo(8);
        }

        @Test
        void addsTheRingUp() {
            assertThat(new Tower("Test", ring(2)).totalWeightPounds()).isEqualTo(500 + 700);
        }

        @Test
        void hasNoPlaceWhenNoneWasGiven() {
            assertThat(new Tower("Test", ring(6)).place()).isEmpty();
        }

        @Test
        void refusesADefinitionItCannotUse() {
            assertThatThrownBy(() -> new Tower((Tower.Definition) null))
                    .hasMessageContaining("needs a definition object");
            assertThatThrownBy(() -> new Tower("", ring(6))).hasMessageContaining("a tower name");
            assertThatThrownBy(() -> new Tower("Test", List.of())).hasMessageContaining("at least one bell");
        }

        @Test
        void refusesAnEntryThatIsNotABell() {
            assertThatThrownBy(() -> new Tower("Test", Arrays.asList((Bell) null)))
                    .hasMessageContaining("entry 0 of the ring is not a bell");
        }

        @Test
        void refusesARingWithAGapInTheNumbering() {
            List<Bell> bells = List.of(new Bell(1, 400), new Bell(3, 900));
            assertThatThrownBy(() -> new Tower("Test", bells))
                    .hasMessage("the ring jumps from bell 1 to bell 3, so it has a gap");
        }
    }

    @Nested
    class LookingBellsUp {

        @Test
        void findsABellByNumber() {
            assertThat(stMary().bell(3).number()).isEqualTo(3);
        }

        @Test
        void throwsForABellThatIsNotThere() {
            assertThatThrownBy(() -> stMary().bell(9)).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> stMary().bell(0)).hasMessageContaining("no bell with id \"0\"");
        }
    }

    @Nested
    class WhatATowerCanRing {

        @Test
        void ringsAnyEvenStageUpToItsSize() {
            Tower tower = stMary();
            assertThat(tower.canRing(8)).isTrue();
            assertThat(tower.canRing(6)).isTrue();
            assertThat(tower.canRing(10)).isFalse();
        }

        @Test
        void ringsAnOddStageWithOrWithoutACover() {
            Tower six = new Tower("Test", ring(6));
            assertThat(six.canRing(5)).isTrue();
            assertThat(six.canRing(7)).isFalse();
        }

        @Test
        void saysWhenThereIsASpareBellToCoverWith() {
            Tower six = new Tower("Test", ring(6));
            assertThat(six.canCover(5)).isTrue();
            assertThat(six.canCover(6)).isFalse();
            Tower five = new Tower("Test", ring(5));
            assertThat(five.canRing(5)).isTrue();
            assertThat(five.canCover(5)).isFalse();
        }

        @Test
        void refusesAStageThatIsNotAStage() throws NoSuchMethodException {
            assertThat(stMary().canRing(2)).isFalse();
            // A stage of six and a half cannot be asked for: the stage is declared a whole number.
            assertThat(Tower.class.getMethod("canRing", int.class).getParameterTypes()[0])
                    .isEqualTo(int.class);
        }

        @Test
        void listsTheStagesLargestFirst() {
            assertThat(new Tower("Test", ring(6)).stages()).containsExactly(6, 5, 4, 3);
        }

        @Test
        void namesTheLargestStageItCanRing() {
            assertThat(stMary().largestStage()).isEqualTo(8);
            assertThat(new Tower("Test", ring(3)).largestStage()).isEqualTo(3);
        }

        @Test
        void givesNoStagesToATowerWithTooFewBells() {
            Tower two = new Tower("Test", ring(2));
            assertThat(two.stages()).isEmpty();
            assertThat(two.largestStage()).isZero();
        }
    }

    @Nested
    class IdentifyingATower {

        @Test
        void makesAnIdFromThePlaceAndTheName() {
            assertThat(stMary().id()).isEqualTo("bridgend-st-mary-the-virgin");
        }

        @Test
        void usesTheNameAloneWhenThereIsNoPlace() {
            assertThat(new Tower("Test Tower", ring(6)).id()).isEqualTo("test-tower");
        }

        @Test
        void printsItself() {
            Tower tower =
                    new Tower("St Mary", "Bridgend", List.of(Bell.weighing(1, "4-0-0"), Bell.weighing(2, "23-2-11")));
            assertThat(tower).hasToString("St Mary, Bridgend: 2 bells, tenor 23-2-11");
        }

        @Test
        void describesWhatItRings() {
            assertThat(stMary().describe()).contains("ringing up to Major");
        }

        @Test
        void putsTheKeyInTheDescriptionWhenTheTenorHasANote() {
            Tower tower = new Tower(
                    "St Mary",
                    List.of(
                            Bell.weighing(1, "4-0-0", Bell.Details.NONE.withNote("F")),
                            Bell.weighing(2, "5-0-0", Bell.Details.NONE.withNote("Eb")),
                            Bell.weighing(3, "6-0-0", Bell.Details.NONE.withNote("D")),
                            Bell.weighing(4, "8-0-0", Bell.Details.NONE.withNote("C")),
                            Bell.weighing(5, "10-0-0", Bell.Details.NONE.withNote("Bb"))));
            assertThat(tower.describe()).isEqualTo("St Mary: 5 bells, tenor 10-0-0 in Bb, ringing up to Doubles");
        }

        @Test
        void warnsWhenSomeBellsAreOffTheScale() {
            Tower tower = new Tower(
                    "St Mary",
                    List.of(
                            Bell.weighing(1, "4-0-0", Bell.Details.NONE.withNote("F")),
                            Bell.weighing(2, "5-0-0", Bell.Details.NONE.withNote("Eb")),
                            Bell.weighing(3, "6-0-0", Bell.Details.NONE.withNote("C"))));
            assertThat(tower.describe())
                    .isEqualTo("St Mary: 3 bells, tenor 6-0-0 in C, ringing up to Singles," + " 2 bells off the scale");
        }

        @Test
        void picksTheFrontOrTheBackOfTheRing() {
            assertThat(stMary().frontBells(6)).extracting(Bell::number).containsExactly(1, 2, 3, 4, 5, 6);
            assertThat(stMary().backBells(6)).extracting(Bell::number).containsExactly(3, 4, 5, 6, 7, 8);
            assertThat(stMary().backBells(8)).extracting(Bell::number).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        }

        @Test
        void refusesMoreBellsThanItHas() {
            assertThatThrownBy(() -> stMary().backBells(9)).hasMessageContaining("has 8 bells, not 9");
            assertThatThrownBy(() -> stMary().frontBells(0)).isInstanceOf(ValidationException.class);
        }

        @Test
        void coversAnOddStageOnlyWithABellToSpare() {
            assertThat(stMary().canCover(7)).isTrue();
            assertThat(stMary().canCover(6)).isFalse();
            assertThat(new Tower("Five", ring(5)).canCover(5)).isFalse();
            assertThat(new Tower("None", ring(2)).largestStage()).isZero();
            assertThat(new Tower("None", ring(2)).describe()).endsWith(", too few bells to ring changes on");
        }
    }
}
