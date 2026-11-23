package io.github.rkaushikdev.garterhole.tower;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NoteTest {

    @Nested
    class Reading {

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
            "C, C",
            "c, C",
            "C#, C_SHARP",
            "Db, C_SHARP",
            "Eb, D_SHARP",
            "Gb, F_SHARP",
            "Ab, G_SHARP",
            "Bb, A_SHARP",
            "B, B",
            "Cb, B",
            "B#, C",
            "E#, F",
            "Fb, E"
        })
        void readsEitherSpelling(String written, Note expected) {
            assertThat(Note.find(written)).contains(expected);
        }

        @ParameterizedTest(name = "{0} is not a note")
        @ValueSource(strings = {"", "H", "C##", "Cx", "1", "#"})
        void findsNothingInTextThatIsNotANote(String written) {
            assertThat(Note.find(written)).isEmpty();
        }

        @Test
        void findsNothingInNothing() {
            assertThat(Note.find(null)).isEmpty();
        }

        @Test
        void refusesByNameWhenParsing() {
            assertThatThrownBy(() -> Note.parse("H"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("\"H\" is not a note");
            assertThat(Note.parse("F#")).isEqualTo(Note.F_SHARP);
        }
    }

    @Nested
    class Spelling {

        @Test
        void writesBothSpellings() {
            assertThat(Note.A_SHARP.sharpName()).isEqualTo("A#");
            assertThat(Note.A_SHARP.flatName()).isEqualTo("Bb");
            assertThat(Note.D.flatName()).isEqualTo("D");
            assertThat(Note.G_SHARP).hasToString("G#");
        }
    }

    @Nested
    class Intervals {

        @Test
        void countsSemitonesFromC() {
            assertThat(Note.C.semitone()).isZero();
            assertThat(Note.B.semitone()).isEqualTo(11);
        }

        @Test
        void goesUpAndDownRoundTheOctave() {
            assertThat(Note.A.up(3)).isEqualTo(Note.C);
            assertThat(Note.C.up(-1)).isEqualTo(Note.B);
            assertThat(Note.D.up(12)).isEqualTo(Note.D);
        }

        @Test
        void measuresTheIntervalUpToAnotherNote() {
            assertThat(Note.D.semitonesUpTo(Note.E)).isEqualTo(2);
            assertThat(Note.B.semitonesUpTo(Note.C)).isEqualTo(1);
            assertThat(Note.E.semitonesUpTo(Note.D)).isEqualTo(10);
            assertThat(Note.F.semitonesUpTo(Note.F)).isZero();
        }
    }
}
