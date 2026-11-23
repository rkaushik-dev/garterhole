package io.github.rkaushikdev.garterhole.tower;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.validation.Coerce;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One bell in a tower.
 *
 * <p>Weight is in hundredweight, quarters and pounds, because that is what is
 * cast on the bell and what every ringer quotes. Storing it as pounds and
 * converting on the way out loses nothing and stops the arithmetic going wrong:
 * a tenor of twenty three hundredweight two quarters and eleven pounds is 2647
 * pounds, and that is a number that can be added up.
 *
 * <p>Bells are immutable values.
 */
public final class Bell {

    /** Pounds in a hundredweight. */
    public static final int POUNDS_PER_HUNDREDWEIGHT = 112;

    /** Pounds in a quarter. */
    public static final int POUNDS_PER_QUARTER = 28;

    private static final int EARLIEST_CASTING = 1200;
    private static final int LATEST_CASTING = 2200;
    private static final int MOST_QUARTERS = 3;
    private static final int MOST_POUNDS = 27;

    private final int number;
    private final double weightPounds;
    private final String note;
    private final Note pitch;
    private final String founder;
    private final Integer cast;

    /**
     * What is known about a bell besides its number and its weight. Each part
     * is {@code null} when it is not known.
     *
     * @param note the note it is tuned to, with a sharp or a flat as the board writes it
     * @param founder who cast it
     * @param cast the year it was cast
     */
    public record Details(String note, String founder, Integer cast) {

        /** Nothing known. */
        public static final Details NONE = new Details(null, null, null);

        /**
         * @param newNote the note
         * @return these details with the note
         */
        public Details withNote(String newNote) {
            return new Details(newNote, founder, cast);
        }

        /**
         * @param newFounder who cast the bell
         * @return these details with the founder
         */
        public Details withFounder(String newFounder) {
            return new Details(note, newFounder, cast);
        }

        /**
         * @param year the year the bell was cast
         * @return these details with the casting year
         */
        public Details withCast(Integer year) {
            return new Details(note, founder, year);
        }
    }

    /**
     * The weight broken into hundredweight, quarters and pounds.
     *
     * @param hundredweight whole hundredweight
     * @param quarters the quarters left over, nought to three
     * @param pounds the pounds left over, nought to twenty seven
     */
    public record Weight(int hundredweight, int quarters, int pounds) {}

    /**
     * A bell with nothing known about it but its number and its weight.
     *
     * @param number its number, counting from the treble
     * @param weightPounds what it weighs, in pounds
     * @throws ValidationException when the number or the weight is not one
     */
    public Bell(int number, double weightPounds) {
        this(number, weightPounds, Details.NONE);
    }

    /**
     * @param number its number, counting from the treble
     * @param weightPounds what it weighs, in pounds
     * @param details its note, founder and casting year, as far as they are known
     * @throws ValidationException when any part of the definition is not one
     */
    public Bell(int number, double weightPounds, Details details) {
        if (details == null) {
            throw new ValidationException("a bell needs a definition object");
        }
        if (number < 1) {
            throw new ValidationException(number + " is not a bell number");
        }
        if (!(weightPounds > 0)) {
            throw new ValidationException("a bell cannot weigh " + Json.number(weightPounds) + " pounds");
        }
        Note tuned = details.note() == null ? null : Note.parse(details.note());
        if (details.cast() != null && (details.cast() < EARLIEST_CASTING || details.cast() > LATEST_CASTING)) {
            throw new ValidationException(details.cast() + " is not a year a bell was cast in");
        }
        this.number = number;
        this.weightPounds = weightPounds;
        this.pitch = tuned;
        this.note = details.note() == null ? null : Note.tidy(details.note());
        this.founder = details.founder();
        this.cast = details.cast();
    }

    /**
     * Build a bell from a weight written the way a tower board writes it.
     *
     * @param number its number, counting from the treble
     * @param weight for example {@code 23-2-11}
     * @return the bell
     * @throws ValidationException when the weight cannot be read
     */
    public static Bell weighing(int number, String weight) {
        return weighing(number, weight, Details.NONE);
    }

    /**
     * Build a bell from a weight written the way a tower board writes it.
     *
     * @param number its number, counting from the treble
     * @param weight for example {@code 23-2-11}
     * @param details its note, founder and casting year
     * @return the bell
     * @throws ValidationException when the weight cannot be read
     * @throws IllegalArgumentException when there is no weight at all
     */
    public static Bell weighing(int number, String weight, Details details) {
        Guards.nonEmptyString(weight, "a bell weight");
        String[] parts = weight.split("-", -1);
        if (parts.length != 3) {
            throw new ValidationException(
                    Json.quote(weight) + " is not a weight in hundredweight, quarters and pounds");
        }
        double[] values = new double[3];
        for (int index = 0; index < parts.length; index += 1) {
            values[index] = wholePart(parts[index], weight);
        }
        double quarters = values[1];
        double pounds = values[2];
        if (quarters > MOST_QUARTERS) {
            throw new ValidationException(Json.number(quarters) + " quarters is more than a hundredweight");
        }
        if (pounds > MOST_POUNDS) {
            throw new ValidationException(Json.number(pounds) + " pounds is more than a quarter");
        }
        return new Bell(number, values[0] * POUNDS_PER_HUNDREDWEIGHT + quarters * POUNDS_PER_QUARTER + pounds, details);
    }

    /**
     * @return every note a bell may be tuned to, in their sharp spellings, as a
     *     list the caller may change; the flat spellings are read as well
     */
    public static List<String> notes() {
        List<String> names = new ArrayList<>();
        for (Note each : Note.values()) {
            names.add(each.sharpName());
        }
        return names;
    }

    /**
     * @return the bell's number, counting from the treble
     */
    public int number() {
        return number;
    }

    /**
     * @return what the bell weighs, in pounds
     */
    public double weightPounds() {
        return weightPounds;
    }

    /**
     * @return the note it is tuned to, spelt the way it was given, when that is known
     */
    public Optional<String> note() {
        return Optional.ofNullable(note);
    }

    /**
     * @return the note it is tuned to as a pitch, so that {@code Eb} and {@code D#}
     *     compare equal, when that is known
     */
    public Optional<Note> pitch() {
        return Optional.ofNullable(pitch);
    }

    /**
     * @return who cast it, when that is known
     */
    public Optional<String> founder() {
        return Optional.ofNullable(founder);
    }

    /**
     * @return the year it was cast, when that is known
     */
    public OptionalInt cast() {
        return cast == null ? OptionalInt.empty() : OptionalInt.of(cast);
    }

    /**
     * @return the weight broken into hundredweight, quarters and pounds
     */
    public Weight weight() {
        long left = Math.round(weightPounds);
        int hundredweight = (int) Math.floorDiv(left, POUNDS_PER_HUNDREDWEIGHT);
        left -= (long) hundredweight * POUNDS_PER_HUNDREDWEIGHT;
        int quarters = (int) Math.floorDiv(left, POUNDS_PER_QUARTER);
        return new Weight(hundredweight, quarters, (int) (left - (long) quarters * POUNDS_PER_QUARTER));
    }

    /**
     * @return the weight written the way a tower board writes it, for example {@code 23-2-11}
     */
    public String weightText() {
        Weight weight = weight();
        return weight.hundredweight() + "-" + weight.quarters() + "-" + weight.pounds();
    }

    @Override
    public String toString() {
        return "bell " + number + ", " + weightText() + (note == null ? "" : " in " + note);
    }

    /**
     * One part of a written weight, read the way the original read a number
     * from text: blank is nought, and anything else has to be a whole number
     * that is not negative.
     */
    private static double wholePart(String part, String weight) {
        double value = Coerce.readNumber(part);
        if (value != Math.rint(value) || value < 0) {
            throw new ValidationException(Json.quote(weight) + " has a part that is not a whole number");
        }
        return value;
    }
}
