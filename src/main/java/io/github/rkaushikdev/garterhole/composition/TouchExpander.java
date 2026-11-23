package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.TouchParser.Instruction;
import io.github.rkaushikdev.garterhole.composition.TouchParser.Touch;
import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowBlock;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ringing a touch. The instructions say what to call; this works out what the
 * bells actually do.
 *
 * <p>A touch written as one symbol per lead is straightforward: ring the lead
 * the symbol names and move on. A touch written as calling positions is not,
 * because the position says where the observation bell has to be when the call
 * comes, and getting there takes as many plain leads as it takes.
 */
public final class TouchExpander {

    /** A touch that has not come round by here has gone wrong. */
    public static final int MAX_LEADS = 2000;

    private TouchExpander() {}

    /**
     * One lead as it was rung.
     *
     * @param symbol the call made at its end, {@code .} for a plain lead
     * @param position the calling position the call was made at, when the touch named one
     * @param leadHead the row the lead finished on
     * @param changes the changes of the lead, the call included
     */
    public record RungLead(String symbol, Optional<String> position, Row leadHead, List<Change> changes) {}

    /**
     * A touch, rung.
     *
     * @param block every row, the start included
     * @param leads every lead, in order
     * @param comesRound true when the touch finished on the row it started on
     */
    public record Rung(RowBlock block, List<RungLead> leads, boolean comesRound) {}

    /**
     * What to ring and how. Anything not given takes its default: the standard
     * calls for the method, the tenor as the observation bell, rounds as the
     * start, and ringing on to rounds when the touch is written as calling
     * positions.
     *
     * @param method the method
     * @param touch the touch, read
     * @param calls the calls to ring, by role, or {@code null} for the standard ones
     * @param observation the bell whose place names the calling positions, or {@code null} for the tenor
     * @param start the row the touch starts from, or {@code null} for rounds
     * @param ringToRounds whether to ring plain leads until the touch comes round, or
     *     {@code null} to do so only for a touch written as calling positions
     */
    public record Request(
            Method method, Touch touch, Map<String, Call> calls, Integer observation, Row start, Boolean ringToRounds) {

        /**
         * @param method the method
         * @param touch the touch, read
         * @return a request with everything else at its default
         */
        public static Request of(Method method, Touch touch) {
            return new Request(method, touch, null, null, null, null);
        }

        /**
         * @param chosen the calls to ring, by role
         * @return this request with its own calls
         */
        public Request withCalls(Map<String, Call> chosen) {
            return new Request(method, touch, chosen, observation, start, ringToRounds);
        }

        /**
         * @param bell the bell whose place names the calling positions
         * @return this request with another observation bell
         */
        public Request withObservation(int bell) {
            return new Request(method, touch, calls, bell, start, ringToRounds);
        }

        /**
         * @param row the row the touch starts from
         * @return this request starting somewhere else
         */
        public Request withStart(Row row) {
            return new Request(method, touch, calls, observation, row, ringToRounds);
        }

        /**
         * @param ringOn whether to ring plain leads until the touch comes round
         * @return this request with that said
         */
        public Request withRingToRounds(boolean ringOn) {
            return new Request(method, touch, calls, observation, start, ringOn);
        }
    }

    /**
     * Ring a touch with everything at its default.
     *
     * @param method the method
     * @param touch the touch, read
     * @return the touch, rung
     */
    public static Rung expandTouch(Method method, Touch touch) {
        return expandTouch(Request.of(method, touch));
    }

    /**
     * Ring a touch.
     *
     * @param request what to ring and how
     * @return the touch, rung
     * @throws ValidationException when there is no method or touch, a symbol has
     *     no call, a position is never reached, or the touch never comes round
     */
    public static Rung expandTouch(Request request) {
        Method method = request.method;
        if (method == null) {
            throw new ValidationException("a Method is needed to ring a touch");
        }
        Touch touch = request.touch;
        if (touch == null) {
            throw new ValidationException("a parsed touch is needed to ring it");
        }
        Map<String, Call> calls = request.calls != null ? request.calls : CallSet.standardCalls(method);
        int observation = request.observation != null ? request.observation : method.stage();
        Row start = request.start != null ? request.start : Row.rounds(method.stage());
        boolean ringToRounds =
                request.ringToRounds != null ? request.ringToRounds : touch.style() == TouchParser.Style.POSITIONS;

        Ringing ringing = new Ringing(method, calls, start);
        for (Instruction instruction : touch.instructions()) {
            Call called = callFor(calls, instruction.symbol());
            if (instruction.position().isEmpty()) {
                ringing.ringLead(called, Optional.empty());
            } else {
                ringing.ringToPosition(called, instruction.position().get(), observation);
            }
        }
        if (ringToRounds) {
            ringing.ringHome(start);
        }
        RowBlock block = new RowBlock(ringing.rows);
        return new Rung(block, List.copyOf(ringing.leads), block.last().equals(start));
    }

    /**
     * @param rung a touch, rung
     * @return the number of changes it runs to
     */
    public static int touchLength(Rung rung) {
        return rung.block().changeCount();
    }

    private static Call callFor(Map<String, Call> calls, String symbol) {
        if (symbol.equals(TouchParser.PLAIN_SYMBOL)) {
            return plainOf(calls);
        }
        return CallSet.callBySymbol(calls, symbol)
                .orElseThrow(() ->
                        new ValidationException("no call is written " + Json.quote(symbol), Map.of("symbol", symbol)));
    }

    private static Call plainOf(Map<String, Call> calls) {
        Call plain = calls.get("plain");
        if (plain == null) {
            throw new ValidationException("the calls have no plain lead");
        }
        return plain;
    }

    /**
     * The rows and leads rung so far. The rows are gathered in one list rather
     * than joined block by block, so ringing a long touch does not copy every
     * row once per lead.
     */
    private static final class Ringing {
        private final Method method;
        private final Map<String, Call> calls;
        private final List<Row> rows = new ArrayList<>();
        private final List<RungLead> leads = new ArrayList<>();

        Ringing(Method method, Map<String, Call> calls, Row start) {
            this.method = method;
            this.calls = calls;
            rows.add(start);
        }

        Row last() {
            return rows.get(rows.size() - 1);
        }

        int changeCount() {
            return rows.size() - 1;
        }

        void ringLead(Call called, Optional<String> position) {
            List<Change> changes = called.applyToLead(method);
            RowBlock lead = RowBlock.fromChanges(last(), changes);
            leads.add(new RungLead(called.symbol(), position, lead.last(), changes));
            rows.addAll(lead.rows().subList(1, lead.length()));
        }

        /**
         * Ring plain leads until the call would put the observation bell where
         * the calling position says, then ring the called lead.
         */
        void ringToPosition(Call called, String position, int observation) {
            for (int attempt = 0; attempt <= method.stage() * 2; attempt += 1) {
                List<Change> changes = called.applyToLead(method);
                RowBlock trial = RowBlock.fromChanges(last(), changes);
                if (CallingPosition.positionOfCall(trial.last(), observation).equals(position)) {
                    leads.add(new RungLead(called.symbol(), Optional.of(position), trial.last(), changes));
                    rows.addAll(trial.rows().subList(1, trial.length()));
                    return;
                }
                ringLead(plainOf(calls), Optional.empty());
                if (changeCount() > MAX_LEADS * method.leadLength()) {
                    break;
                }
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("position", position);
            details.put("observation", observation);
            throw new ValidationException(
                    "the observation bell never reaches " + Json.quote(position) + " in this method", details);
        }

        /** Ring plain leads until the touch comes back to where it started. */
        void ringHome(Row start) {
            int rung = 0;
            while (!last().equals(start) && rung < MAX_LEADS) {
                ringLead(plainOf(calls), Optional.empty());
                rung += 1;
            }
            if (!last().equals(start)) {
                throw new ValidationException("the touch never comes round", Map.of("leads", leads.size()));
            }
        }
    }
}
