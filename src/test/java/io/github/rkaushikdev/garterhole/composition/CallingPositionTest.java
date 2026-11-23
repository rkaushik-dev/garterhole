package io.github.rkaushikdev.garterhole.composition;

import static io.github.rkaushikdev.garterhole.composition.CallingPosition.namedPositions;
import static io.github.rkaushikdev.garterhole.composition.CallingPosition.placeCodes;
import static io.github.rkaushikdev.garterhole.composition.CallingPosition.placeForPosition;
import static io.github.rkaushikdev.garterhole.composition.CallingPosition.positionAt;
import static io.github.rkaushikdev.garterhole.composition.CallingPosition.positionName;
import static io.github.rkaushikdev.garterhole.composition.CallingPosition.positionOfCall;
import static io.github.rkaushikdev.garterhole.composition.CallingPosition.positionPlaces;
import static io.github.rkaushikdev.garterhole.core.RowParse.parseRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.CallingPosition.NamedPosition;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CallingPositionTest {

    @Nested
    class PositionPlaces {

        @Test
        void placesHomeAtTheBackAndWrongTwoUnderIt() {
            Map<String, Integer> places = positionPlaces(8);
            assertThat(places.get("H")).isEqualTo(8);
            assertThat(places.get("W")).isEqualTo(6);
            assertThat(places.get("M")).isEqualTo(4);
        }

        @Test
        void putsTheFrontThreePositionsAtTheFront() {
            Map<String, Integer> places = positionPlaces(8);
            assertThat(places.get("B")).isEqualTo(1);
            assertThat(places.get("I")).isEqualTo(2);
            assertThat(places.get("O")).isEqualTo(3);
        }

        @Test
        void spreadsOutAtRoyal() {
            Map<String, Integer> places = positionPlaces(10);
            assertThat(places.get("H")).isEqualTo(10);
            assertThat(places.get("W")).isEqualTo(8);
            assertThat(places.get("M")).isEqualTo(6);
            assertThat(places.get("V")).isEqualTo(5);
            assertThat(places.get("F")).isEqualTo(4);
        }

        @Test
        void leavesOutPositionsThatFallOffTheFrontAtMinor() {
            Map<String, Integer> places = positionPlaces(6);
            assertThat(places.get("M")).isEqualTo(2);
            assertThat(places).containsKey("V");
        }

        @Test
        void refusesAnUnsupportedStage() {
            assertThatThrownBy(() -> positionPlaces(2)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class PlaceCodes {

        @Test
        void resolvesTheCollisionAtMajorInFavourOfTheMiddle() {
            assertThat(placeCodes(8).get(4)).isEqualTo("M");
        }

        @Test
        void keepsFourthsAndMiddleApartAtRoyal() {
            assertThat(placeCodes(10).get(4)).isEqualTo("F");
            assertThat(placeCodes(10).get(6)).isEqualTo("M");
        }
    }

    @Nested
    class PositionAt {

        @Test
        void namesThePlacesThatHaveNames() {
            assertThat(positionAt(8, 8)).isEqualTo("H");
            assertThat(positionAt(8, 6)).isEqualTo("W");
            assertThat(positionAt(8, 1)).isEqualTo("B");
        }

        @Test
        void givesThePlaceAsANumberWhenItHasNoName() {
            assertThat(positionAt(8, 7)).isEqualTo("7");
            assertThat(positionAt(10, 9)).isEqualTo("9");
        }

        @Test
        void refusesAPlaceOutsideTheStage() {
            assertThatThrownBy(() -> positionAt(8, 9)).hasMessageContaining("place 9 is outside 1..8");
            assertThatThrownBy(() -> positionAt(8, 0)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class PlaceForPosition {

        @Test
        void readsANamedPositionBack() {
            assertThat(placeForPosition(8, "H")).isEqualTo(8);
            assertThat(placeForPosition(8, "M")).isEqualTo(4);
        }

        @Test
        void readsAPlaceWrittenAsANumber() {
            assertThat(placeForPosition(8, "7")).isEqualTo(7);
        }

        @Test
        void readsAPlaceTheWayJavaScriptReadsANumber() {
            assertThat(placeForPosition(8, "0x3")).isEqualTo(3);
            assertThat(placeForPosition(8, " 6\u00A0")).isEqualTo(6);
            assertThatThrownBy(() -> placeForPosition(8, "6.5")).hasMessageContaining("is not a calling position on 8");
        }

        @Test
        void refusesACodeThatIsNotAPosition() {
            assertThatThrownBy(() -> placeForPosition(8, "Z")).hasMessageContaining("is not a calling position on 8");
            assertThatThrownBy(() -> placeForPosition(8, "9")).isInstanceOf(ValidationException.class);
        }

        @Test
        void roundTripsEveryNamedPlace() {
            for (Map.Entry<String, Integer> entry : positionPlaces(10).entrySet()) {
                assertThat(placeForPosition(10, entry.getKey())).isEqualTo(entry.getValue());
            }
        }
    }

    @Nested
    class PositionName {

        @Test
        void spellsTheCodesOut() {
            assertThat(positionName("H")).isEqualTo("Home");
            assertThat(positionName("W")).isEqualTo("Wrong");
            assertThat(positionName("B")).isEqualTo("Before");
        }

        @Test
        void leavesAnUnknownCodeAlone() {
            assertThat(positionName("7")).isEqualTo("7");
        }
    }

    @Nested
    class NamedPositions {

        @Test
        void listsThemFromTheFrontToTheBack() {
            assertThat(namedPositions(8))
                    .extracting(NamedPosition::code)
                    .containsExactly("B", "I", "O", "M", "V", "W", "H");
        }

        @Test
        void givesTheLongNameWithEach() {
            assertThat(namedPositions(8).get(0)).isEqualTo(new NamedPosition("B", 1, "Before"));
        }
    }

    @Nested
    class PositionOfCall {

        @Test
        void namesThePositionFromWhereTheObservationBellEndedUp() {
            assertThat(positionOfCall(parseRow("13527468"), 8)).isEqualTo("H");
            assertThat(positionOfCall(parseRow("12345678"), 8)).isEqualTo("H");
        }

        @Test
        void findsTheTenorInTheMiddle() {
            assertThat(positionOfCall(parseRow("12384567"), 8)).isEqualTo("M");
            assertThat(positionOfCall(parseRow("13527486"), 8)).isEqualTo("7");
        }

        @Test
        void refusesABellThatIsNotRinging() {
            assertThatThrownBy(() -> positionOfCall(parseRow("123456"), 8)).hasMessageContaining("not in a row of 6");
        }
    }

    @Nested
    class Positions {

        @Test
        void carryTheirCodeTheirNameAndWhereTheySit() {
            assertThat(CallingPosition.Position.WRONG.code()).isEqualTo("W");
            assertThat(CallingPosition.Position.WRONG.longName()).isEqualTo("Wrong");
            assertThat(CallingPosition.Position.WRONG.placeAt(8)).isEqualTo(6);
            assertThat(CallingPosition.Position.MIDDLE.placeAt(10)).isEqualTo(6);
            assertThat(CallingPosition.Position.HOME.placeAt(12)).isEqualTo(12);
        }

        @Test
        void areFoundByTheirCode() {
            assertThat(CallingPosition.Position.withCode("V")).contains(CallingPosition.Position.FIFTHS);
            assertThat(CallingPosition.Position.withCode("Z")).isEmpty();
            assertThat(CallingPosition.positionName("7")).isEqualTo("7");
        }
    }
}
