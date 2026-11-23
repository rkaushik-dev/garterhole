package io.github.rkaushikdev.garterhole.core;

import io.github.rkaushikdev.garterhole.util.Guards;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;

/**
 * A handful of rows have names, and a band that rings one on the way past will
 * mention it in the pub afterwards. The music scorer counts them, so they have
 * to be built rather than typed in for every stage.
 *
 * <p>Every named row here is defined over the bells of the stage. The cover
 * bell on an odd stage is not part of the stage, so Queens on Doubles is
 * {@code 13524} and the tenor stands behind it.
 */
public final class NamedRows {

    /** Each stage's named rows, keyed by the row, filled in as stages are asked about. */
    private static final Map<Integer, Map<Row, String>> BY_ROW = new ConcurrentHashMap<>();

    private NamedRows() {}

    /**
     * @param stage a supported stage
     * @return odd bells ascending, then even bells ascending
     */
    public static Row queens(int stage) {
        return build(stage, working -> {
            List<Integer> bells = new ArrayList<>(Bells.oddBells(working));
            bells.addAll(Bells.evenBells(working));
            return bells;
        });
    }

    /**
     * @param stage a supported stage
     * @return odd bells descending, then even bells ascending
     */
    public static Row kings(int stage) {
        return build(stage, working -> {
            List<Integer> bells = new ArrayList<>(Bells.oddBells(working));
            Collections.reverse(bells);
            bells.addAll(Bells.evenBells(working));
            return bells;
        });
    }

    /**
     * The two halves of the row interleaved, which gives the rocking sound the
     * name comes from.
     *
     * @param stage a supported stage
     * @return tittums
     */
    public static Row tittums(int stage) {
        return build(stage, working -> {
            int half = (working + 1) / 2;
            List<Integer> bells = new ArrayList<>();
            for (int index = 0; index < half; index += 1) {
                bells.add(index + 1);
                if (half + index + 1 <= working) {
                    bells.add(half + index + 1);
                }
            }
            return bells;
        });
    }

    /**
     * @param stage a supported stage
     * @return every bell in reverse
     */
    public static Row backrounds(int stage) {
        return build(stage, working -> {
            List<Integer> bells = new ArrayList<>();
            for (int bell = working; bell >= 1; bell -= 1) {
                bells.add(bell);
            }
            return bells;
        });
    }

    /**
     * Whittingtons, which only exists on eight. There is no rule behind it that
     * generalises, so asking for it at another stage is an error rather than a
     * silent invention.
     *
     * @param stage the stage, which must be eight
     * @return whittingtons
     * @throws IllegalArgumentException at any stage but eight
     */
    public static Row whittingtons(int stage) {
        if (stage != 8) {
            throw new IllegalArgumentException("Whittingtons is only rung on eight bells");
        }
        return new Row(1, 2, 7, 5, 3, 4, 6, 8);
    }

    /**
     * @param stage a supported stage
     * @return every named row at the stage, keyed by name, rounds first
     */
    public static Map<String, Row> namedRows(int stage) {
        Map<String, Row> named = new LinkedHashMap<>();
        named.put("Rounds", Row.rounds(stage));
        named.put("Backrounds", backrounds(stage));
        named.put("Queens", queens(stage));
        named.put("Kings", kings(stage));
        named.put("Tittums", tittums(stage));
        if (stage == 8) {
            named.put("Whittingtons", whittingtons(stage));
        }
        return Collections.unmodifiableMap(named);
    }

    /**
     * The name of a row. The names at each stage are worked out once and kept,
     * because the music scorer and the call change caller both ask about
     * every row they see. Where two names give the same row the one listed
     * first in {@link #namedRows} wins.
     *
     * @param row a row
     * @return its name, when it has one
     */
    public static Optional<String> nameOfRow(Row row) {
        Guards.present(row, "a row is needed to look up its name");
        Map<Row, String> names = BY_ROW.computeIfAbsent(row.stage(), stage -> {
            Map<Row, String> inverted = new HashMap<>();
            namedRows(stage).forEach((name, named) -> inverted.putIfAbsent(named, name));
            return Map.copyOf(inverted);
        });
        return Optional.ofNullable(names.get(row));
    }

    private static Row build(int stage, IntFunction<List<Integer>> bells) {
        if (!Stage.isSupported(stage)) {
            throw new IllegalArgumentException("stage " + stage + " is not supported");
        }
        return Row.of(bells.apply(stage));
    }
}
