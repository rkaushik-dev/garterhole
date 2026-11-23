package io.github.rkaushikdev.garterhole.core;

import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Call changes: rounds moved about one swap at a time by the conductor
 * calling a bell to follow another.
 *
 * <p>It is where every band starts and where a good many stay, and the
 * calling is the part a learner conductor gets wrong. A call names the bell
 * that moves and the bell it is to follow, {@code 2 to 3}, and it only works
 * when the bell it is to follow is the one straight behind it; then the two
 * swap and the first is following the second. A bell in seconds can also be
 * called to lead, {@code 3 lead}, which swaps it with the bell in front.
 *
 * <p>Each call gives one new row, rung over and over until the next call, so
 * the rows here are the rows the band settles into, not every blow rung.
 */
public final class CallChanges {

    private static final Pattern FOLLOW =
            Pattern.compile("^(\\S)\\s+(?:to|follow|after)\\s+(\\S)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEAD = Pattern.compile("^(\\S)\\s+(?:to\\s+)?lead$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARATOR = Pattern.compile("[,;\\n]+");

    private CallChanges() {}

    /**
     * One call, and the row it gives.
     *
     * @param call the call as a conductor says it, for example {@code 2 to 3}
     * @param row the row the band rings after it
     * @param name the row's name, when it has one
     */
    public record Called(String call, Row row, Optional<String> name) {}

    /**
     * Ring a sequence of calls from a row.
     *
     * @param start the row the band is ringing when the calling starts, usually rounds
     * @param calling the calls, separated by commas, semicolons or lines
     * @return each call with the row it gives
     * @throws NotationException naming the call that cannot be read or cannot be made from where the band is
     */
    public static List<Called> ring(Row start, String calling) {
        Guards.present(start, "a Row is needed to call changes from");
        List<Called> rung = new ArrayList<>();
        Row row = start;
        for (String piece : SEPARATOR.split(calling == null ? "" : calling)) {
            String call = piece.trim();
            if (call.isEmpty()) {
                continue;
            }
            row = apply(row, call);
            rung.add(new Called(call, row, NamedRows.nameOfRow(row)));
        }
        return List.copyOf(rung);
    }

    /**
     * Make one call.
     *
     * @param row the row the band is ringing
     * @param call the call, {@code 2 to 3} or {@code 3 lead}
     * @return the row after it
     * @throws NotationException when the call cannot be read or cannot be made from this row
     */
    public static Row apply(Row row, String call) {
        Matcher follow = FOLLOW.matcher(call.trim());
        if (follow.matches()) {
            int mover = bellIn(call, follow.group(1), row.stage());
            int leader = bellIn(call, follow.group(2), row.stage());
            int place = row.placeOf(mover);
            if (place == row.stage() || row.bellAt(place + 1) != leader) {
                throw new NotationException(
                        Json.quote(call) + " cannot be called from " + row + ": " + Bells.symbol(leader)
                                + " is not straight behind " + Bells.symbol(mover),
                        call,
                        1);
            }
            return swapAt(row, place);
        }
        Matcher lead = LEAD.matcher(call.trim());
        if (lead.matches()) {
            int mover = bellIn(call, lead.group(1), row.stage());
            if (row.placeOf(mover) != 2) {
                throw new NotationException(
                        Json.quote(call) + " cannot be called from " + row + ": " + Bells.symbol(mover)
                                + " is not in seconds",
                        call,
                        1);
            }
            return swapAt(row, 1);
        }
        throw new NotationException(Json.quote(call) + " is not a call change", call, 1);
    }

    /**
     * The calls that take one row to another, worked from the front: the bell
     * wanted at lead is called down to it, then the bell wanted in seconds,
     * and so on. Every call moves one bell back past one other, so the number
     * of calls is the number of pairs the two rows have in opposite orders.
     *
     * @param from the row the band is ringing
     * @param to the row wanted, for example Queens
     * @return the calls, in order, which ring back to {@code to}
     * @throws ValidationException when the rows are at different stages
     */
    public static List<String> callsBetween(Row from, Row to) {
        Guards.present(from, "a Row is needed to call changes from");
        Guards.present(to, "a Row is needed to call changes to");
        if (from.stage() != to.stage()) {
            throw new ValidationException("a row on " + from.stage() + " cannot be called to one on " + to.stage());
        }
        List<String> calls = new ArrayList<>();
        Row row = from;
        for (int place = 1; place <= to.stage(); place += 1) {
            int wanted = to.bellAt(place);
            while (row.placeOf(wanted) > place) {
                int at = row.placeOf(wanted);
                int inFront = row.bellAt(at - 1);
                String call = at == 2
                        ? Bells.symbol(wanted) + " lead"
                        : Bells.symbol(inFront) + " to " + Bells.symbol(wanted);
                calls.add(call);
                row = apply(row, call);
            }
        }
        return List.copyOf(calls);
    }

    private static int bellIn(String call, String symbol, int stage) {
        char written = symbol.charAt(0);
        if (symbol.length() != 1 || !Bells.isSymbol(written) || Bells.fromSymbol(written) > stage) {
            throw new NotationException(
                    Json.quote(symbol) + " in " + Json.quote(call) + " is not a bell on " + stage, call, 1);
        }
        return Bells.fromSymbol(written);
    }

    private static Row swapAt(Row row, int place) {
        int[] bells = row.toArray();
        int held = bells[place - 1];
        bells[place - 1] = bells[place];
        bells[place] = held;
        return new Row(bells);
    }
}
