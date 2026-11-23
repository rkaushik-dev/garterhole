package io.github.rkaushikdev.garterhole.core;

import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.List;

/**
 * Bells are written with a single character each so that a row is a string a
 * ringer can read at a glance. The sequence is the one used by every printed
 * method collection: digits up to nine, then zero for the tenth bell, then
 * letters for the rest. Lower case letters are accepted on input because they
 * are what people type.
 */
public final class Bells {

    /**
     * The treble is always bell one, whatever the stage. The constant exists so
     * that code reads as ringing rather than as arithmetic.
     */
    public static final int TREBLE = 1;

    private static final String SYMBOLS = "1234567890ETABCD";

    private Bells() {}

    /**
     * @param bell one based bell number, the treble being 1
     * @return the character used to write the bell
     * @throws IllegalArgumentException when the bell is outside 1..16
     */
    public static char symbol(int bell) {
        Guards.integerInRange(bell, 1, Stage.MAX, "bell");
        return SYMBOLS.charAt(bell - 1);
    }

    /**
     * @param symbol a single character, either case
     * @return the one based bell it stands for
     * @throws IllegalArgumentException when the character is not a bell
     */
    public static int fromSymbol(char symbol) {
        int index = indexOf(symbol);
        if (index == -1) {
            throw new IllegalArgumentException(Json.quote(String.valueOf(symbol)) + " is not a bell symbol");
        }
        return index + 1;
    }

    /**
     * @param symbol a single character
     * @return true when the character names a bell
     */
    public static boolean isSymbol(char symbol) {
        return indexOf(symbol) != -1;
    }

    /**
     * @param stage a number of bells
     * @return the symbols for the whole stage, in order, as one string
     */
    public static String symbolsForStage(int stage) {
        Guards.integerInRange(stage, 1, Stage.MAX, "stage");
        return SYMBOLS.substring(0, stage);
    }

    /**
     * @param text a run of bell symbols
     * @return the bells they name, in order
     */
    public static int[] fromSymbols(String text) {
        int[] bells = new int[text.length()];
        for (int index = 0; index < text.length(); index += 1) {
            bells[index] = fromSymbol(text.charAt(index));
        }
        return bells;
    }

    /**
     * @param bells a sequence of bells
     * @return the bells written back out as symbols
     */
    public static String toSymbols(int... bells) {
        StringBuilder text = new StringBuilder(bells.length);
        for (int bell : bells) {
            text.append(symbol(bell));
        }
        return text.toString();
    }

    /**
     * The tenor of a stage. Ringers say "the tenor" far more often than they say
     * the number, and the phrase turns up in call definitions and in the
     * striking analysis, so it earns a name.
     *
     * @param stage a number of bells
     * @return the highest numbered bell of the stage
     */
    public static int tenorOf(int stage) {
        return stage;
    }

    /**
     * The odd bells of a stage, the treble first. Queens, Kings and the
     * coursing order are all built from the odd bells and the even bells taken
     * separately, so the split is worth naming once.
     *
     * @param stage a number of bells
     * @return one, three, five and so on up to the stage, as a list that cannot be changed
     */
    public static List<Integer> oddBells(int stage) {
        return everyOther(1, stage);
    }

    /**
     * @param stage a number of bells
     * @return two, four, six and so on up to the stage, as a list that cannot be changed
     */
    public static List<Integer> evenBells(int stage) {
        return everyOther(2, stage);
    }

    private static List<Integer> everyOther(int first, int stage) {
        List<Integer> bells = new ArrayList<>();
        for (int bell = first; bell <= stage; bell += 2) {
            bells.add(bell);
        }
        return List.copyOf(bells);
    }

    private static int indexOf(char symbol) {
        int index = SYMBOLS.indexOf(symbol);
        if (index == -1 && Character.isLowerCase(symbol)) {
            index = SYMBOLS.indexOf(Character.toUpperCase(symbol));
        }
        return index;
    }
}
