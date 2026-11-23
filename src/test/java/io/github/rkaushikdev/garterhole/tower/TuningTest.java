package io.github.rkaushikdev.garterhole.tower;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.garterhole.tower.Bell.Details;
import io.github.rkaushikdev.garterhole.tower.Tuning.Discord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TuningTest {

    /** An eight in D, the way the founder tuned it, treble first. */
    private static final List<String> EIGHT_IN_D = List.of("D", "C#", "B", "A", "G", "F#", "E", "D");

    private static Tower ring(List<String> notes) {
        List<Bell> bells = new ArrayList<>();
        for (int index = 0; index < notes.size(); index += 1) {
            String note = notes.get(index);
            Details details = note == null ? Details.NONE : Details.NONE.withNote(note);
            bells.add(new Bell(index + 1, 400 + 100 * index, details));
        }
        return new Tower("Test", bells);
    }

    @Nested
    class Scale {

        @Test
        void runsUpAMajorScaleFromTheKeyNote() {
            assertThat(Tuning.scale(Note.D, 8))
                    .containsExactly(Note.D, Note.E, Note.F_SHARP, Note.G, Note.A, Note.B, Note.C_SHARP, Note.D);
        }

        @Test
        void carriesOnIntoTheNextOctaveForTwelve() {
            List<Note> twelve = Tuning.scale(Note.C, 12);
            assertThat(twelve.subList(7, 12)).containsExactly(Note.C, Note.D, Note.E, Note.F, Note.G);
        }
    }

    @Nested
    class Key {

        @Test
        void isTheTenorsNote() {
            assertThat(Tuning.keyOf(ring(EIGHT_IN_D))).contains(Note.D);
            assertThat(Tuning.ringText(ring(EIGHT_IN_D))).isEqualTo("8 in D");
        }

        @Test
        void keepsTheFlatTheBoardWrites() {
            assertThat(Tuning.ringText(ring(List.of("D", "C", "Bb")))).isEqualTo("3 in Bb");
        }

        @Test
        void isUnknownWhenTheTenorHasNoNote() {
            List<String> notes = new ArrayList<>(EIGHT_IN_D);
            notes.set(7, null);
            assertThat(Tuning.keyOf(ring(notes))).isEmpty();
            assertThat(Tuning.ringText(ring(notes))).isEqualTo("8");
            assertThat(Tuning.discords(ring(notes))).isEmpty();
        }
    }

    @Nested
    class Discords {

        @Test
        void findsNothingWrongWithADiatonicRing() {
            assertThat(Tuning.discords(ring(EIGHT_IN_D))).isEmpty();
            assertThat(Tuning.isDiatonic(ring(EIGHT_IN_D))).isTrue();
        }

        @Test
        void namesABellOffTheScaleAndTheNoteItShouldHave() {
            List<String> notes = new ArrayList<>(EIGHT_IN_D);
            notes.set(6, "F");
            assertThat(Tuning.discords(ring(notes))).containsExactly(new Discord(7, Note.F, Note.E));
            assertThat(Tuning.isDiatonic(ring(notes))).isFalse();
        }

        @Test
        void doesNotHoldAMissingNoteAgainstTheRing() {
            List<String> notes = new ArrayList<>(EIGHT_IN_D);
            notes.set(2, null);
            assertThat(Tuning.discords(ring(notes))).isEmpty();
            assertThat(Tuning.isDiatonic(ring(notes))).isFalse();
        }

        @Test
        void hearsTheBackSixOfAnEightInTheSameKeyButNotTheFrontSix() {
            List<Bell> bells = ring(EIGHT_IN_D).bells();
            assertThat(Tuning.discords(bells.subList(2, 8))).isEmpty();
            assertThat(Tuning.discords(bells.subList(0, 6)))
                    .extracting(Discord::bell)
                    .containsExactly(1, 4, 5);
        }

        @Test
        void hasNothingToSayAboutNoBells() {
            assertThat(Tuning.discords(List.<Bell>of())).isEmpty();
        }
    }
}
