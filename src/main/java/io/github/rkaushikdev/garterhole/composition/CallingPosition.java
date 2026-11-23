package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.Stage;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.validation.Coerce;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

/**
 * Calling positions. A conductor says "wrong" rather than "a bob at the end of
 * the fourth lead", because the position tells the band where the observation
 * bell is and the lead number does not.
 *
 * <p>The convention here is the usual one: the position is named by the place
 * the observation bell occupies in the lead head that follows the call. Home is
 * at the back, Wrong two places under it, and Middle two under that. The front
 * three places are Before, In and Out.
 *
 * <p>Fourths and Middle land on the same place at Major, and Fifths and Wrong do
 * the same at Minor. Where two names collide the one nearer the back wins,
 * which is what a band would say out loud.
 */
public final class CallingPosition {

    /**
     * The named positions, front to back, each with the place it sits at on a
     * stage. Where two land on the same place the later one in this list wins.
     */
    public enum Position {
        /** The observation bell leading: Before. */
        BEFORE("B", "Before", stage -> 1),
        /** In seconds: In. */
        IN("I", "In", stage -> 2),
        /** In thirds: Out. */
        OUT("O", "Out", stage -> 3),
        /** In fourths: Fourths. */
        FOURTHS("F", "Fourths", stage -> 4),
        /** In fifths: Fifths. */
        FIFTHS("V", "Fifths", stage -> 5),
        /** Four places from the back: Middle. */
        MIDDLE("M", "Middle", stage -> stage - 4),
        /** Two places from the back: Wrong. */
        WRONG("W", "Wrong", stage -> stage - 2),
        /** At the back: Home. */
        HOME("H", "Home", stage -> stage);

        private final String code;
        private final String longName;
        private final IntUnaryOperator place;

        Position(String code, String longName, IntUnaryOperator place) {
            this.code = code;
            this.longName = longName;
            this.place = place;
        }

        /**
         * @return the one letter code, for example {@code W}
         */
        public String code() {
            return code;
        }

        /**
         * @return the long name, for example {@code Wrong}
         */
        public String longName() {
            return longName;
        }

        /**
         * @param stage a stage
         * @return the place the position sits at on that stage, which may be outside it
         */
        public int placeAt(int stage) {
            return place.applyAsInt(stage);
        }

        /**
         * @param code a one letter code
         * @return the position with that code, when there is one
         */
        public static Optional<Position> withCode(String code) {
            return Arrays.stream(values())
                    .filter(each -> each.code.equals(code))
                    .findFirst();
        }
    }

    private CallingPosition() {}

    /**
     * A named position, as a list of them shows it.
     *
     * @param code the one letter code, for example {@code W}
     * @param place the place it sits at
     * @param name the long name, for example {@code Wrong}
     */
    public record NamedPosition(String code, int place, String name) {}

    /**
     * The place each named position sits at, for a stage. Positions that fall
     * outside the stage are left out.
     *
     * @param stage a supported stage
     * @return each code against its place, from Before to Home
     * @throws ValidationException when the stage is not supported
     */
    public static Map<String, Integer> positionPlaces(int stage) {
        requireStage(stage);
        Map<String, Integer> places = new LinkedHashMap<>();
        for (Position position : Position.values()) {
            int place = position.placeAt(stage);
            if (place >= 1 && place <= stage) {
                places.put(position.code(), place);
            }
        }
        return Collections.unmodifiableMap(places);
    }

    /**
     * The code for each place, with the collisions resolved in favour of the
     * name nearer the back.
     *
     * @param stage a supported stage
     * @return each place against its code
     */
    public static Map<Integer, String> placeCodes(int stage) {
        Map<Integer, String> codes = new LinkedHashMap<>();
        positionPlaces(stage).forEach((code, place) -> codes.put(place, code));
        return Collections.unmodifiableMap(codes);
    }

    /**
     * @param stage a supported stage
     * @param place a place in the row
     * @return the position code for the place, or the place written as a number when it has no name
     * @throws ValidationException when the place is outside the stage
     */
    public static String positionAt(int stage, int place) {
        requireStage(stage);
        if (place < 1 || place > stage) {
            throw new ValidationException("place " + place + " is outside 1.." + stage);
        }
        String code = placeCodes(stage).get(place);
        return code == null ? Integer.toString(place) : code;
    }

    /**
     * The place a named position sits at.
     *
     * @param stage a supported stage
     * @param code a position code, or a place written as a number
     * @return the place
     * @throws ValidationException when the code is neither
     */
    public static int placeForPosition(int stage, String code) {
        Integer place = positionPlaces(stage).get(code);
        if (place != null) {
            return place;
        }
        if (code != null) {
            double number = Coerce.readNumber(code);
            if (number == Math.rint(number) && number >= 1 && number <= stage) {
                return (int) number;
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("stage", stage);
        details.put("code", code);
        throw new ValidationException(Json.quote(code) + " is not a calling position on " + stage, details);
    }

    /**
     * @param code a position code
     * @return the long name of the code, or the code itself when it has none
     */
    public static String positionName(String code) {
        return Position.withCode(code).map(Position::longName).orElse(code);
    }

    /**
     * @param stage a supported stage
     * @return every position that has a name at the stage, from the front to the back
     */
    public static List<NamedPosition> namedPositions(int stage) {
        return placeCodes(stage).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new NamedPosition(entry.getValue(), entry.getKey(), positionName(entry.getValue())))
                .toList();
    }

    /**
     * The position a call was made at, worked out from where the observation
     * bell ended up.
     *
     * @param leadHead the row after the call
     * @param observation the bell being watched, usually the tenor
     * @return the position code
     */
    public static String positionOfCall(Row leadHead, int observation) {
        return positionAt(leadHead.stage(), leadHead.placeOf(observation));
    }

    private static void requireStage(int stage) {
        if (!Stage.isSupported(stage)) {
            throw new ValidationException("stage " + stage + " is not supported");
        }
    }
}
