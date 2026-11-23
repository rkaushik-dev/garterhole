package io.github.rkaushikdev.garterhole.tower;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.Locale;
import java.util.Optional;

/**
 * The note a bell is tuned to, as a pitch class.
 *
 * <p>Tower boards and founders' certificates are not consistent about sharps
 * and flats. The same tenor is written {@code Eb} on one board and {@code D#}
 * in a directory, and a ring of eight in B flat is never written in A sharp by
 * anybody who rings on it. So both spellings are read, and they are the same
 * note: comparing two bells, or working out the key of a ring, goes by the
 * pitch and not by the letters.
 *
 * <p>Only the twelve notes of the octave are here. A tower records which note
 * a bell is, not which octave it sounds in, and the octave never decides
 * anything a ringer asks about.
 */
public enum Note {
    /** C. */
    C("C", "C"),
    /** C sharp, or D flat. */
    C_SHARP("C#", "Db"),
    /** D. */
    D("D", "D"),
    /** D sharp, or E flat. */
    D_SHARP("D#", "Eb"),
    /** E. */
    E("E", "E"),
    /** F. */
    F("F", "F"),
    /** F sharp, or G flat. */
    F_SHARP("F#", "Gb"),
    /** G. */
    G("G", "G"),
    /** G sharp, or A flat. */
    G_SHARP("G#", "Ab"),
    /** A. */
    A("A", "A"),
    /** A sharp, or B flat. */
    A_SHARP("A#", "Bb"),
    /** B. */
    B("B", "B");

    /** The semitones above C of each natural note, in letter order from A. */
    private static final int[] NATURALS = {9, 11, 0, 2, 4, 5, 7};

    private static final Note[] BY_SEMITONE = values();

    private final String sharp;
    private final String flat;

    Note(String sharp, String flat) {
        this.sharp = sharp;
        this.flat = flat;
    }

    /**
     * Read a note written either way: a letter, then nothing, {@code #} for a
     * sharp or {@code b} for a flat. The letter may be in either case.
     *
     * @param written for example {@code F#}, {@code Bb} or {@code e}
     * @return the note, or nothing when the text is not a note
     */
    public static Optional<Note> find(String written) {
        if (written == null || written.isEmpty() || written.length() > 2) {
            return Optional.empty();
        }
        char letter = Character.toUpperCase(written.charAt(0));
        if (letter < 'A' || letter > 'G') {
            return Optional.empty();
        }
        int semitone = NATURALS[letter - 'A'];
        if (written.length() == 2) {
            switch (written.charAt(1)) {
                case '#' -> semitone += 1;
                case 'b' -> semitone -= 1;
                default -> {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(BY_SEMITONE[Math.floorMod(semitone, BY_SEMITONE.length)]);
    }

    /**
     * @param written a note written either way
     * @return the note
     * @throws ValidationException when the text is not a note
     */
    public static Note parse(String written) {
        return find(written)
                .orElseThrow(() -> new ValidationException(Json.quote(String.valueOf(written)) + " is not a note"));
    }

    /**
     * @return the note written with a sharp where it needs one, for example {@code D#}
     */
    public String sharpName() {
        return sharp;
    }

    /**
     * @return the note written with a flat where it needs one, for example {@code Eb}
     */
    public String flatName() {
        return flat;
    }

    /**
     * @return how many semitones the note is above C, nought to eleven
     */
    public int semitone() {
        return ordinal();
    }

    /**
     * @param semitones how far to go up, or down when negative
     * @return the note that far above this one, wrapping round the octave
     */
    public Note up(int semitones) {
        return BY_SEMITONE[Math.floorMod(ordinal() + semitones, BY_SEMITONE.length)];
    }

    /**
     * @param higher another note
     * @return how many semitones up from this note the other one is, nought to eleven
     */
    public int semitonesUpTo(Note higher) {
        return Math.floorMod(higher.ordinal() - ordinal(), BY_SEMITONE.length);
    }

    /**
     * @return the sharp spelling, which is how a directory writes it
     */
    @Override
    public String toString() {
        return sharp;
    }

    /**
     * @param text a word that may be a note
     * @return the same text with its letter in upper case, which is how a board writes it
     */
    static String tidy(String text) {
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
