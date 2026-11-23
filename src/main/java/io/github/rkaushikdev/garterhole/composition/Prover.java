package io.github.rkaushikdev.garterhole.composition;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.TouchExpander.Rung;
import io.github.rkaushikdev.garterhole.composition.TruthTracker.Repeat;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowBlock;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Proving a touch. A touch is true when no row is rung twice, and the only way
 * to be sure of that is to look at every row.
 *
 * <p>The row a touch finishes on is the row it started on, and that repeat does
 * not count. Everything else does, including a repeat that a composer meant to
 * be there, because a touch with a deliberate repeat is still false.
 */
public final class Prover {

    private Prover() {}

    /**
     * What proving a block found.
     *
     * @param isTrue true when no row is rung twice
     * @param rows how many rows were looked at, the closing rounds left out
     * @param distinct how many different rows there are
     * @param repeats every row rung more than once, in the order they started
     * @param comesRound true when the block finishes where it started
     */
    public record Proof(boolean isTrue, int rows, int distinct, List<Repeat> repeats, boolean comesRound) {

        /**
         * @return the proof as a document, with the field names a report expects
         */
        public ObjectNode toJson() {
            ObjectNode json = Json.object();
            json.put("isTrue", isTrue);
            json.put("rows", rows);
            json.put("distinct", distinct);
            ArrayNode repeated = json.putArray("repeats");
            for (Repeat repeat : repeats) {
                ObjectNode entry = repeated.addObject().put("row", repeat.row());
                ArrayNode at = entry.putArray("at");
                repeat.at().forEach(at::add);
            }
            json.put("comesRound", comesRound);
            return json;
        }
    }

    /**
     * Prove a block of rows.
     *
     * @param block the rows
     * @return the proof
     * @throws ValidationException when there is no block
     */
    public static Proof prove(RowBlock block) {
        if (block == null) {
            throw new ValidationException("a RowBlock is needed to prove it");
        }
        TruthTracker tracker = track(rowsRung(block));
        return new Proof(tracker.isTrue(), tracker.count(), tracker.distinct(), tracker.repeats(), block.comesRound());
    }

    /**
     * What proving a block extent by extent found.
     *
     * @param extentLength how many rows an extent has over the bells that change
     * @param extents how many whole extents the block holds
     * @param whole true when the block is a whole number of extents, with nothing left over
     * @param falseExtents the extents, counted from one, that repeat a row within themselves
     * @param comesRound true when the block finishes where it started
     */
    public record ExtentProof(
            long extentLength, int extents, boolean whole, List<Integer> falseExtents, boolean comesRound) {

        /**
         * @param extentLength how many rows an extent has
         * @param extents how many whole extents the block holds
         * @param whole true when nothing is left over
         * @param falseExtents the false extents, counted from one
         * @param comesRound true when the block comes round
         */
        public ExtentProof {
            falseExtents = List.copyOf(falseExtents);
        }

        /**
         * @return true when the block is whole extents, every one of them true, and comes round
         */
        public boolean ok() {
            return whole && extents > 0 && falseExtents.isEmpty() && comesRound;
        }
    }

    /**
     * Prove a block that is meant to be a number of extents rung one after
     * another, which is how a peal of Minor or Doubles is built. Proving it as
     * one block calls it false, because the seven extents of a 5040 of Minor
     * ring every row seven times; the rule is that each extent is true on its
     * own, so that is what is checked.
     *
     * @param block the rows, with the closing rounds
     * @return what was found
     * @throws ValidationException when there is no block
     */
    public static ExtentProof proveExtents(RowBlock block) {
        if (block == null) {
            throw new ValidationException("a RowBlock is needed to prove it");
        }
        long extent = extentSize(changingBells(block));
        List<Row> rows = rowsRung(block);
        int extents = (int) (rows.size() / extent);
        List<Integer> falseOnes = new ArrayList<>();
        for (int index = 0; index < extents; index += 1) {
            int from = (int) (index * extent);
            if (!track(rows.subList(from, (int) (from + extent))).isTrue()) {
                falseOnes.add(index + 1);
            }
        }
        return new ExtentProof(extent, extents, rows.size() % extent == 0, falseOnes, block.comesRound());
    }

    /**
     * The bells that change in a block: the stage, less any bells at the back
     * that stay in their own place in every row. Doubles rung with the tenor
     * covering is five changing bells on six, and its extent is a hundred and
     * twenty, not seven hundred and twenty.
     *
     * @param block the rows
     * @return how many bells change, counted from the treble
     */
    public static int changingBells(RowBlock block) {
        int changing = block.stage();
        while (changing > 1 && covers(block, changing)) {
            changing -= 1;
        }
        return changing;
    }

    /**
     * Prove a rung touch.
     *
     * @param rung the touch, rung
     * @return the proof
     * @throws ValidationException when there is no rung touch
     */
    public static Proof proveTouch(Rung rung) {
        if (rung == null || rung.block() == null) {
            throw new ValidationException("a rung touch is needed to prove it");
        }
        return prove(rung.block());
    }

    /**
     * How many rows there are at a stage, which is what an extent has to contain.
     *
     * @param stage a number of bells
     * @return the factorial of the stage
     */
    public static long extentSize(int stage) {
        return Lengths.extentLength(stage);
    }

    /**
     * True when the block contains every row of its changing bells exactly
     * once. A cover bell that never moves is not one of them, so Doubles rung
     * on six with the tenor behind is an extent at a hundred and twenty rows.
     *
     * @param block the rows
     * @return true when the block is an extent
     */
    public static boolean isExtent(RowBlock block) {
        Proof proof = prove(block);
        return proof.isTrue() && proof.distinct() == extentSize(changingBells(block));
    }

    /**
     * @param proof a proof of a block extent by extent
     * @return a one line summary, for example {@code 7 true extents of 720}
     */
    public static String describeExtents(ExtentProof proof) {
        String noun = proof.extents() == 1 ? "extent" : "extents";
        String size = " of " + proof.extentLength();
        if (!proof.whole()) {
            return "not whole extents" + size;
        }
        if (!proof.falseExtents().isEmpty()) {
            String which = proof.falseExtents().stream().map(String::valueOf).collect(Collectors.joining(", "));
            return proof.extents() + " " + noun + size + ", false in " + which;
        }
        if (!proof.comesRound()) {
            return proof.extents() + " " + noun + size + ", but it does not come round";
        }
        return proof.extents() + " true " + noun + size;
    }

    /**
     * @param proof a proof
     * @return the first repeat in a false touch, which is what a composer wants
     *     to see before the whole list
     */
    public static Optional<Repeat> firstRepeat(Proof proof) {
        return proof.repeats().isEmpty()
                ? Optional.empty()
                : Optional.of(proof.repeats().get(0));
    }

    /**
     * @param proof a proof
     * @return a one line summary, for example {@code true, 336 rows}
     */
    public static String describeProof(Proof proof) {
        if (proof.isTrue()) {
            String ending = proof.comesRound() ? "" : ", but it does not come round";
            return "true, " + proof.rows() + " rows" + ending;
        }
        Repeat first = proof.repeats().get(0);
        return "false: " + first.row() + " is rung " + first.at().size() + " times, first at row "
                + (first.at().get(0) + 1);
    }

    /** The rows that count, which leaves out the rounds a block closes on. */
    private static List<Row> rowsRung(RowBlock block) {
        List<Row> rows = block.rows();
        boolean closes = block.comesRound() && block.length() > 1;
        return closes ? rows.subList(0, rows.size() - 1) : rows;
    }

    private static TruthTracker track(List<Row> rows) {
        TruthTracker tracker = new TruthTracker();
        rows.forEach(tracker::add);
        return tracker;
    }

    private static boolean covers(RowBlock block, int bell) {
        for (Row row : block.rows()) {
            if (row.bellAt(bell) != bell) {
                return false;
            }
        }
        return true;
    }
}
