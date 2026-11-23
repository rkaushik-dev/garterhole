package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.core.CoursingOrder;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowBlock;
import io.github.rkaushikdev.garterhole.method.HuntBells;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.method.Title;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Looking for a touch.
 *
 * <p>This is a depth first search over the calls, one lead at a time, keeping
 * the rows in a {@link TruthTracker} as it goes. A branch that repeats a row is
 * abandoned at once rather than being rung out and proved afterwards, which is
 * the whole reason the tracker exists.
 *
 * <p>It is not a composing program. It will find the touches of a hundred and
 * twenty changes that a tower rings on a practice night, and it will not find a
 * peal composition, because the search space of a peal is far past what a
 * depth first walk can cover. Saying so here is better than letting somebody
 * find out by leaving it running overnight.
 */
public final class TouchSearch {

    /** The most leads a search will ring down one branch. */
    public static final int MAX_DEPTH = 60;

    private static final int DEFAULT_MAX_LEADS = 12;
    private static final int DEFAULT_LIMIT = 20;

    private TouchSearch() {}

    /**
     * A touch the search found.
     *
     * @param calling the call symbol of every lead, for example {@code -..-..}
     * @param changes how long it is
     * @param leads how many leads it has
     * @param notation the method's notation written out in full
     */
    public record FoundTouch(String calling, int changes, int leads, String notation) {}

    /**
     * What to search for. Anything not given takes its default: the standard
     * calls for the method, any length, twelve leads deep, twenty touches, and
     * the tenors allowed to part.
     *
     * @param method the method to search for touches of
     * @param calls the calls to try at every lead, by role, or {@code null} for the standard ones
     * @param lengths the lengths worth reporting, or {@code null} for any
     * @param maxLeads how deep to search
     * @param limit how many touches to find before stopping
     * @param tenorsTogether true to keep only touches whose every lead head has the tenors coursing
     */
    public record Request(
            Method method,
            Map<String, Call> calls,
            Set<Integer> lengths,
            int maxLeads,
            int limit,
            boolean tenorsTogether) {

        /**
         * @param method the method to search for touches of
         * @return a request with everything else at its default
         */
        public static Request of(Method method) {
            return new Request(method, null, null, DEFAULT_MAX_LEADS, DEFAULT_LIMIT, false);
        }

        /**
         * @param chosen the calls to try at every lead, by role, tried in their order
         * @return this request with its own calls
         */
        public Request withCalls(Map<String, Call> chosen) {
            return new Request(method, chosen, lengths, maxLeads, limit, tenorsTogether);
        }

        /**
         * @param wanted the lengths worth reporting, in changes
         * @return this request keeping only touches of those lengths
         */
        public Request withLengths(Collection<Integer> wanted) {
            return new Request(method, calls, Set.copyOf(wanted), maxLeads, limit, tenorsTogether);
        }

        /**
         * @param leads how deep to search, from one to {@link #MAX_DEPTH}
         * @return this request searching that deep
         */
        public Request withMaxLeads(int leads) {
            return new Request(method, calls, lengths, leads, limit, tenorsTogether);
        }

        /**
         * @param touches how many touches to find before stopping
         * @return this request stopping there
         */
        public Request withLimit(int touches) {
            return new Request(method, calls, lengths, maxLeads, touches, tenorsTogether);
        }

        /**
         * A practice night touch that parts the tenors throws away the music
         * the band came for, so a conductor will often ask only for the ones
         * that keep them coursing. A branch is given up at the first lead head
         * that parts them, which also makes the search a great deal quicker.
         *
         * @return this request keeping only touches with the tenors together throughout
         */
        public Request keepingTenorsTogether() {
            return new Request(method, calls, lengths, maxLeads, limit, true);
        }
    }

    /**
     * Search for true touches that come round.
     *
     * @param request what to search for
     * @return the touches found, in the order the search reached them
     * @throws ValidationException when there is no method, or the depth or limit is out of range
     */
    public static List<FoundTouch> searchTouches(Request request) {
        if (request == null || request.method == null) {
            throw new ValidationException("a Method is needed to search for touches of it");
        }
        Method method = request.method;
        Map<String, Call> calls = request.calls != null ? request.calls : CallSet.standardCalls(method);
        if (request.maxLeads < 1 || request.maxLeads > MAX_DEPTH) {
            throw new ValidationException("a search of " + request.maxLeads + " leads is outside 1.." + MAX_DEPTH);
        }
        if (request.limit < 1) {
            throw new ValidationException("a search that returns " + request.limit + " touches is not a search");
        }
        if (request.tenorsTogether
                && (method.stage() < 6 || !HuntBells.huntBells(method).contains(1))) {
            throw new ValidationException("the tenors can only be kept together in a treble dominated method"
                    + " on six or more, which " + Title.of(method) + " is not");
        }
        Row start = Row.rounds(method.stage());
        Walk walk = new Walk(method, List.copyOf(calls.values()), request);
        walk.tracker.add(start);
        walk.from(start, new ArrayList<>(), 0);
        return List.copyOf(walk.found);
    }

    /**
     * @param found touches a search found
     * @return the shortest of them, the first on a tie, when there are any
     */
    public static Optional<FoundTouch> shortest(List<FoundTouch> found) {
        FoundTouch best = null;
        for (FoundTouch touch : found) {
            if (best == null || touch.changes() < best.changes()) {
                best = touch;
            }
        }
        return Optional.ofNullable(best);
    }

    /** One search in progress. */
    private static final class Walk {
        private final Method method;
        private final List<Call> order;
        private final List<List<Change>> calledLeads;
        private final Request request;
        private final TruthTracker tracker = new TruthTracker();
        private final List<FoundTouch> found = new ArrayList<>();
        private final String notation;

        Walk(Method method, List<Call> order, Request request) {
            this.method = method;
            this.order = order;
            this.request = request;
            this.calledLeads =
                    order.stream().map(call -> call.applyToLead(method)).toList();
            this.notation = method.fullNotation();
        }

        /** Ring one lead with each call in turn, and either report a touch or carry on. */
        void from(Row row, List<String> calling, int leads) {
            if (found.size() >= request.limit || leads >= request.maxLeads) {
                return;
            }
            for (int choice = 0; choice < order.size(); choice += 1) {
                RowBlock lead = RowBlock.fromChanges(row, calledLeads.get(choice));
                Optional<List<Row>> added = ringTrue(lead);
                if (added.isEmpty()) {
                    continue;
                }
                calling.add(order.get(choice).symbol());
                Row head = lead.last();
                if (head.isRounds() && leads + 1 >= 2) {
                    report(calling, (leads + 1) * method.leadLength());
                } else if (keepsTheTenors(head)) {
                    from(head, calling, leads + 1);
                }
                calling.remove(calling.size() - 1);
                undo(added.get());
            }
        }

        /**
         * Offer the rows of a lead to the tracker, leaving out the rounds a
         * touch finishes on. When a row repeats, the rows already offered are
         * taken back and nothing is returned.
         */
        private Optional<List<Row>> ringTrue(RowBlock lead) {
            List<Row> added = new ArrayList<>(lead.length());
            for (int index = 1; index < lead.length(); index += 1) {
                Row next = lead.at(index);
                if (index == lead.length() - 1 && next.isRounds()) {
                    break;
                }
                if (!tracker.add(next)) {
                    tracker.undo(next);
                    undo(added);
                    return Optional.empty();
                }
                added.add(next);
            }
            return Optional.of(added);
        }

        private void undo(List<Row> added) {
            for (int index = added.size() - 1; index >= 0; index -= 1) {
                tracker.undo(added.get(index));
            }
        }

        private boolean keepsTheTenors(Row head) {
            return !request.tenorsTogether
                    || CoursingOrder.of(head).tenorsTogether(Analysis.tenorCount(method.stage()));
        }

        private void report(List<String> calling, int changes) {
            if (request.lengths != null && !request.lengths.contains(changes)) {
                return;
            }
            found.add(new FoundTouch(String.join("", calling), changes, calling.size(), notation));
        }
    }
}
