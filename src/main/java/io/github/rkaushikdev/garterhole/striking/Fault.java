package io.github.rkaushikdev.garterhole.striking;

import java.util.Locale;

/**
 * A named fault in a piece of ringing.
 *
 * @param kind what sort of fault it is
 * @param bell the bell responsible
 * @param rowIndex the row it happened in, or started in for a drift, counted from zero
 * @param place the place it happened in, counted from one
 * @param size the error in milliseconds for a clip or a gap, the number of
 *     blows for a drift, or the milliseconds between the two blows of a clash
 */
public record Fault(Kind kind, int bell, int rowIndex, int place, double size) {

    /**
     * The names ringers use, which describe the shape of the error rather than
     * its size.
     */
    public enum Kind {
        /** Struck too soon after the bell in front. */
        CLIP,
        /** Left too long a gap after the bell in front. */
        GAP,
        /** Consistently early or late over a run of blows. */
        DRIFT,
        /** Struck on top of the bell in front, so the two sound as one. */
        CLASH;

        /**
         * @return the name as it is written in a document, for example {@code clip}
         */
        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
