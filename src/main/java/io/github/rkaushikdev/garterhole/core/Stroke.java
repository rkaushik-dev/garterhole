package io.github.rkaushikdev.garterhole.core;

import java.util.Locale;

/**
 * Handstroke and backstroke.
 *
 * <p>A bell is rung in pairs of blows: the handstroke, pulled from the sally,
 * and the backstroke, pulled from the tail end. Rows alternate between the two
 * all the way through a touch, and which stroke a row falls at matters more
 * than it looks. Music at backstroke carries further than the same row at
 * handstroke, the tenors reversed at backstroke is the one fault of a
 * composition everybody hears, and a ringer who strikes well at one stroke and
 * badly at the other is a different problem from one who is uneven at both.
 */
public enum Stroke {
    /** Pulled from the sally, with the handstroke gap in front of the row. */
    HANDSTROKE,
    /** Pulled from the tail end, straight after the handstroke row. */
    BACKSTROKE;

    /**
     * @return the stroke that follows this one
     */
    public Stroke other() {
        return this == HANDSTROKE ? BACKSTROKE : HANDSTROKE;
    }

    /**
     * The stroke of a row, counting from a row whose stroke is known.
     *
     * @param index the row, counted from nought
     * @param first the stroke of row nought
     * @return the stroke of the row
     * @throws IllegalArgumentException when the index is negative
     */
    public static Stroke at(int index, Stroke first) {
        if (index < 0) {
            throw new IllegalArgumentException("row " + index + " is before the first");
        }
        return index % 2 == 0 ? first : first.other();
    }

    /**
     * @return the stroke as a ringer writes it, for example {@code backstroke}
     */
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
