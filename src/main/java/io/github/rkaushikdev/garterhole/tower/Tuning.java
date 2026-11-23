package io.github.rkaushikdev.garterhole.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The key of a ring and whether its bells are in it.
 *
 * <p>A ring of bells is tuned to a major scale, and the tenor is the key note:
 * an eight in D has D on the tenor and the scale runs up from it, E on the
 * seventh, F sharp on the sixth, and so on to D again on the treble. A
 * directory writes this as "8 in D", and a band visiting for a peal wants to
 * know it.
 *
 * <p>Not every ring is diatonic. Some were augmented with a bell that does not
 * fit, some were cast at different times and never retuned, and some have a
 * note on the board that is simply wrong. So this reports the bells that are
 * off the scale rather than refusing the tower, and a bell with no note given
 * is never counted against it.
 */
public final class Tuning {

    /** The steps of a major scale in semitones, going up from the key note. */
    private static final int[] MAJOR_SCALE_STEPS = {2, 2, 1, 2, 2, 2, 1};

    private Tuning() {}

    /**
     * A bell whose note is not the one the scale calls for.
     *
     * @param bell the bell's number
     * @param found the note it is tuned to
     * @param expected the note the scale up from the tenor would give it
     */
    public record Discord(int bell, Note found, Note expected) {}

    /**
     * @param tower a tower
     * @return the key it is in, which is the tenor's note, when the tenor's note is known
     */
    public static Optional<Note> keyOf(Tower tower) {
        return tower.tenor().pitch();
    }

    /**
     * The notes a diatonic ring would have, tenor first and going up the scale.
     *
     * @param key the tenor's note
     * @param bells how many bells
     * @return one note per bell, the tenor's first
     */
    public static List<Note> scale(Note key, int bells) {
        List<Note> notes = new ArrayList<>(bells);
        Note current = key;
        for (int index = 0; index < bells; index += 1) {
            notes.add(current);
            current = current.up(MAJOR_SCALE_STEPS[index % MAJOR_SCALE_STEPS.length]);
        }
        return List.copyOf(notes);
    }

    /**
     * The bells that are off the scale running up from the tenor. A tower with
     * no note on its tenor has no key to measure against, so nothing is
     * reported for it.
     *
     * @param tower a tower
     * @return the bells off the scale, treble first
     */
    public static List<Discord> discords(Tower tower) {
        return discords(tower.bells());
    }

    /**
     * The bells that are off the scale running up from the heaviest of them,
     * for a set of bells that is not a whole ring, such as the back six of an
     * eight.
     *
     * @param bells some bells, treble first, the last being the one taken as the tenor
     * @return the bells off the scale, treble first
     */
    public static List<Discord> discords(List<Bell> bells) {
        if (bells.isEmpty()) {
            return List.of();
        }
        Optional<Note> key = bells.get(bells.size() - 1).pitch();
        if (key.isEmpty()) {
            return List.of();
        }
        List<Note> wanted = scale(key.get(), bells.size());
        List<Discord> found = new ArrayList<>();
        for (int index = 0; index < bells.size(); index += 1) {
            Bell bell = bells.get(index);
            Note expected = wanted.get(bells.size() - 1 - index);
            bell.pitch()
                    .filter(note -> note != expected)
                    .ifPresent(note -> found.add(new Discord(bell.number(), note, expected)));
        }
        return List.copyOf(found);
    }

    /**
     * @param tower a tower
     * @return true when every bell has a note and every note is on the scale up from the tenor
     */
    public static boolean isDiatonic(Tower tower) {
        return tower.bells().stream().allMatch(bell -> bell.pitch().isPresent())
                && discords(tower).isEmpty();
    }

    /**
     * How a directory writes the ring: the number of bells, and the key when
     * the tenor's note is known.
     *
     * @param tower a tower
     * @return for example {@code 8 in D} or {@code 6}
     */
    public static String ringText(Tower tower) {
        String key = keyOf(tower)
                .map(note -> " in " + tower.tenor().note().orElse(note.sharpName()))
                .orElse("");
        return tower.size() + key;
    }
}
