package io.github.rkaushikdev.garterhole.method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TitleTest {

    @Nested
    class BuildTitle {

        @Test
        void putsTheNameTheClassAndTheStageTogether() {
            assertThat(Title.build("Cambridge", "Surprise", 8)).isEqualTo("Cambridge Surprise Major");
        }

        @Test
        void leavesTheClassOutForAPrinciple() {
            assertThat(Title.build("Stedman", "Principle", 7)).isEqualTo("Stedman Triples");
            assertThat(Title.build("Stedman", 7)).isEqualTo("Stedman Triples");
        }

        @Test
        void allowsATitleWithAClassButNoName() {
            assertThat(Title.build("", "Little Bob", 8)).isEqualTo("Little Bob Major");
        }

        @Test
        void squashesStrayWhitespace() {
            assertThat(Title.build("  Plain  ", " Bob ", 6)).isEqualTo("Plain Bob Minor");
        }

        @Test
        void refusesATitleWithNeitherANameNorAClass() {
            assertThatThrownBy(() -> Title.build("", 8)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> Title.build("  ", "Principle", 8)).hasMessageContaining("needs a name or a class");
        }

        @Test
        void refusesAStageThatHasNoName() {
            assertThatThrownBy(() -> Title.build("Nonsense", 2)).hasMessageContaining("no stage name for 2");
        }
    }

    @Nested
    class ParseTitle {

        @Test
        void readsAnOrdinaryTitle() {
            assertThat(Title.parse("Cambridge Surprise Major")).isEqualTo(new Title.Parts("Cambridge", "Surprise", 8));
        }

        @Test
        void readsAClassWordThatAppearsInTheMiddleOfTheName() {
            assertThat(Title.parse("Double Norwich Court Bob Major"))
                    .isEqualTo(new Title.Parts("Double Norwich Court", "Bob", 8));
        }

        @Test
        void readsATwoWordClass() {
            assertThat(Title.parse("Kent Treble Bob Major")).isEqualTo(new Title.Parts("Kent", "Treble Bob", 8));
        }

        @Test
        void readsTheModifiersInFrontOfTheClass() {
            assertThat(Title.parse("Little Bob Major")).isEqualTo(new Title.Parts("", "Little Bob", 8));
            assertThat(Title.parse("Fenchurch Differential Little Place Royal").className())
                    .isEqualTo("Differential Little Place");
        }

        @Test
        void callsATitleWithNoClassAPrinciple() {
            assertThat(Title.parse("Stedman Triples")).isEqualTo(new Title.Parts("Stedman", "Principle", 7));
        }

        @Test
        void squashesWhitespaceBeforeReading() {
            assertThat(Title.parse("  Cambridge   Surprise  Major ").name()).isEqualTo("Cambridge");
        }

        @Test
        void refusesABlankTitle() {
            assertThatThrownBy(() -> Title.parse("")).hasMessage("a title cannot be blank");
            assertThatThrownBy(() -> Title.parse("   ")).isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesATitleThatDoesNotEndWithAStage() {
            assertThatThrownBy(() -> Title.parse("Cambridge Surprise"))
                    .hasMessageContaining("does not end with the name of a stage");
        }

        @Test
        void refusesATitleThatIsOnlyAStageName() {
            assertThatThrownBy(() -> Title.parse("Major")).hasMessageContaining("nothing but a stage name");
        }
    }

    @Nested
    class SplicedTitles {

        @Test
        void readsTheMethodCountOffTheEnd() {
            assertThat(Title.parse("Spliced Surprise Major (8m)")).isEqualTo(new Title.Parts("Spliced", "Surprise", 8));
            assertThat(Title.methodCount("Spliced Surprise Major (8m)")).hasValue(8);
            assertThat(Title.methodCount("Spliced Surprise Royal (23 methods)")).hasValue(23);
            assertThat(Title.methodCount("Surprise Minor (1 method)")).hasValue(1);
            assertThat(Title.methodCount("Cambridge Surprise Major")).isEmpty();
        }

        @Test
        void knowsASplicedTitleByItsNameOrItsCount() {
            assertThat(Title.isSpliced("Spliced Surprise Major")).isTrue();
            assertThat(Title.isSpliced("Surprise Minor (7m)")).isTrue();
            assertThat(Title.isSpliced("Spliced Plain Bob and Grandsire Triples"))
                    .isTrue();
            assertThat(Title.isSpliced("Splicedwell Surprise Major")).isFalse();
            assertThat(Title.isSpliced("Surprise Minor (1m)")).isFalse();
            assertThat(Title.isSpliced("nonsense")).isFalse();
        }

        @Test
        void stillRefusesACountWithNoTitleInFrontOfIt() {
            assertThatThrownBy(() -> Title.parse("(8m)")).hasMessage("a title cannot be blank");
            assertThatThrownBy(() -> Title.parse("Surprise (8m)"))
                    .hasMessageContaining("does not end with the name of a stage");
        }
    }

    @Nested
    class TryParse {

        @Test
        void givesThePartsOrNothing() {
            assertThat(Title.tryParse("Stedman Triples")).contains(new Title.Parts("Stedman", "Principle", 7));
            assertThat(Title.tryParse("Triples")).isEmpty();
            assertThat(Title.tryParse("triples")).isEmpty();
            assertThat(Title.tryParse("Cambridge Surprise major")).isEmpty();
            assertThat(Title.tryParse(null)).isEmpty();
        }

        @Test
        void tellsABareStageInAnyCaseFromATitleWithNoStage() {
            assertThatThrownBy(() -> Title.parse("MAJOR")).hasMessageContaining("nothing but a stage name");
            assertThatThrownBy(() -> Title.parse("Grandsire")).hasMessageContaining("does not end with");
            assertThatThrownBy(() -> Title.parse("Little Bob")).hasMessageContaining("does not end with");
        }
    }

    @Nested
    class IsWellFormedTitle {

        @Test
        void acceptsTheTitlesItCanRead() {
            assertThat(Title.isWellFormed("Cambridge Surprise Major")).isTrue();
            assertThat(Title.isWellFormed("Stedman Triples")).isTrue();
        }

        @Test
        void rejectsTheOnesItCannot() {
            assertThat(Title.isWellFormed("Cambridge Surprise")).isFalse();
            assertThat(Title.isWellFormed("")).isFalse();
        }
    }

    @Nested
    class TitleOf {

        @Test
        void worksTheClassOutFromTheChangesRatherThanTheName() {
            assertThat(Title.of(Method.of("Cambridge", 8, "x38x14x1258x36x14x58x16x78,12")))
                    .isEqualTo("Cambridge Surprise Major");
            assertThat(Title.of(Method.of("Plain", 6, "x1x1x1,2"))).isEqualTo("Plain Bob Minor");
            assertThat(Title.of(Method.of("Bristol", 8, "x58x14.58x58.36.14x14.58x14x18,18")))
                    .isEqualTo("Bristol Surprise Major");
        }

        @Test
        void leavesTheClassOutOfAPrincipleTitle() {
            assertThat(Title.of(Method.of("Test", 5, "3.1.5.3.1.3.5.1.3.1"))).isEqualTo("Test Doubles");
        }

        @Test
        void roundTripsThroughTheReader() {
            String title = Title.of(Method.of("Yorkshire", 8, "x38x14x58x16x12x38x14x78,12"));
            assertThat(Title.parse(title)).isEqualTo(new Title.Parts("Yorkshire", "Surprise", 8));
        }
    }
}
