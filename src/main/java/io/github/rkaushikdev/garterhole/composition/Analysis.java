package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.PartStructure.Parts;
import io.github.rkaushikdev.garterhole.composition.Prover.Proof;
import io.github.rkaushikdev.garterhole.composition.TouchAnalysis.CallCount;
import io.github.rkaushikdev.garterhole.composition.TouchAnalysis.PartsFound;
import io.github.rkaushikdev.garterhole.composition.TouchAnalysis.TenorsFound;
import io.github.rkaushikdev.garterhole.composition.TouchAnalysis.WorkFound;
import io.github.rkaushikdev.garterhole.composition.TouchExpander.Rung;
import io.github.rkaushikdev.garterhole.composition.TouchExpander.RungLead;
import io.github.rkaushikdev.garterhole.composition.music.MusicRule;
import io.github.rkaushikdev.garterhole.composition.music.MusicScorer;
import io.github.rkaushikdev.garterhole.composition.music.MusicScorer.MusicSummary;
import io.github.rkaushikdev.garterhole.composition.music.MusicScorer.RunLine;
import io.github.rkaushikdev.garterhole.core.CoursingOrder;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.method.HuntBells;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.method.Title;
import io.github.rkaushikdev.garterhole.util.CollectionHelpers;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.LocaleOrder;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The report a composer wants back after handing over a calling: how long it
 * is, whether it is true, what it is worth musically, and how it is built.
 *
 * <p>Everything in the report is derived by ringing the touch. Nothing is taken
 * on trust from the composition file, because the whole point of the exercise
 * is to check what the file claims.
 */
public final class Analysis {

    private Analysis() {}

    /**
     * What to analyse. The calls, the observation bell and the start default as
     * they do when a touch is rung, and the music is scored on the default
     * scheme unless another is given.
     *
     * @param method the method
     * @param touch the touch, as written
     * @param calls the calls to ring, by role, or {@code null} for the standard ones
     * @param observation the bell whose place names the calling positions, or {@code null} for the tenor
     * @param start the row to ring from, or {@code null} for rounds
     * @param scheme the music scheme to score against, or {@code null} for the default
     */
    public record Request(
            Method method,
            String touch,
            Map<String, Call> calls,
            Integer observation,
            Row start,
            List<MusicRule> scheme) {

        /**
         * @param method the method
         * @param touch the touch, as written
         * @return a request with the calls and observation bell at their defaults
         */
        public static Request of(Method method, String touch) {
            return new Request(method, touch, null, null, null, null);
        }

        /**
         * @param chosen the calls to ring, by role
         * @return this request with its own calls
         */
        public Request withCalls(Map<String, Call> chosen) {
            return new Request(method, touch, chosen, observation, start, scheme);
        }

        /**
         * @param bell the bell whose place names the calling positions
         * @return this request with another observation bell
         */
        public Request withObservation(int bell) {
            return new Request(method, touch, calls, bell, start, scheme);
        }

        /**
         * A touch started from somewhere other than rounds, such as the row a
         * set of call changes left the band in, comes round when it gets back
         * to that row, and its music is scored on the rows it actually rang.
         *
         * @param row the row to ring from
         * @return this request ringing from that row
         */
        public Request from(Row row) {
            return new Request(method, touch, calls, observation, row, scheme);
        }

        /**
         * @param rules the music scheme to score against, such as the composition scheme
         * @return this request scoring the music that way
         */
        public Request scoredBy(List<MusicRule> rules) {
            return new Request(method, touch, calls, observation, start, List.copyOf(rules));
        }
    }

    /**
     * What a composition claims to be. Each part of the claim is checked only
     * when it is made.
     *
     * @param length the length claimed, when one is
     * @param kind what the touch is claimed to count as, when that is claimed
     * @param stage the stage it is rung at
     */
    public record Claim(OptionalInt length, Optional<Lengths.Kind> kind, int stage) {

        /**
         * @param stage the stage
         * @return a claim of nothing but the stage, which still checks the touch is round and true
         */
        public static Claim atStage(int stage) {
            return new Claim(OptionalInt.empty(), Optional.empty(), stage);
        }

        /**
         * @param length the length claimed
         * @param stage the stage
         * @return a claim of a length
         */
        public static Claim ofLength(int length, int stage) {
            return new Claim(OptionalInt.of(length), Optional.empty(), stage);
        }

        /**
         * @param kind what the touch is claimed to count as
         * @param stage the stage
         * @return a claim of a kind of performance
         */
        public static Claim ofKind(Lengths.Kind kind, int stage) {
            return new Claim(OptionalInt.empty(), Optional.of(kind), stage);
        }
    }

    /**
     * Ring a touch and report on it, with the standard calls and the tenor observing.
     *
     * @param method the method
     * @param touch the touch, as written
     * @return the report
     */
    public static TouchAnalysis analyseTouch(Method method, String touch) {
        return analyseTouch(Request.of(method, touch));
    }

    /**
     * Ring a touch and report on it.
     *
     * @param request what to analyse
     * @return the report
     * @throws ValidationException when there is no method or no touch, or the touch cannot be rung
     */
    public static TouchAnalysis analyseTouch(Request request) {
        if (request == null || request.method == null) {
            throw new ValidationException("a Method is needed to analyse a touch of it");
        }
        if (request.touch == null) {
            throw new ValidationException("the touch must be written out as a string");
        }
        Method method = request.method;
        Rung rung = TouchExpander.expandTouch(new TouchExpander.Request(
                method,
                TouchParser.parseTouch(request.touch),
                request.calls,
                request.observation,
                request.start,
                null));
        Proof proof = Prover.prove(rung.block());
        Optional<Parts> detected = PartStructure.detectParts(
                rung.leads().stream().map(RungLead::leadHead).toList());
        int length = rung.block().changeCount();
        List<Row> rows = rung.block().rows();
        List<MusicRule> scheme = request.scheme != null ? request.scheme : MusicScorer.defaultScheme(method.stage());
        MusicSummary music = MusicScorer.musicSummary(rows.subList(0, rows.size() - 1), method.stage(), scheme);
        return new TouchAnalysis(
                Title.of(method),
                length,
                Lengths.classifyLength(length, method.stage()),
                rung.comesRound(),
                proof,
                Prover.describeProof(proof),
                callCounts(rung.leads().stream().map(RungLead::symbol).toList()),
                detected.map(found -> new PartsFound(
                        found.parts(), found.leadsPerPart(), found.partEnd().toString())),
                music,
                tenorsFound(rung.leads().stream().map(RungLead::leadHead).toList(), method.stage()),
                workFound(method, rung));
    }

    /**
     * Whether every working bell started a lead from every working place,
     * which is what a composer means by all the work. A bell that starts a
     * lead in a place rings that place's piece of work, so a touch that misses
     * a place for some bell leaves that bell short of the method.
     *
     * @param method the method, which needs a hunt bell for the leads to have pieces of work
     * @param rung the touch, rung
     * @return what was found, or nothing for a principle
     */
    public static Optional<WorkFound> workFound(Method method, Rung rung) {
        if (HuntBells.isPrinciple(method)) {
            return Optional.empty();
        }
        List<Integer> working = HuntBells.workingBells(method);
        List<Row> starts = new ArrayList<>();
        starts.add(rung.block().first());
        rung.leads().subList(0, Math.max(0, rung.leads().size() - 1)).forEach(lead -> starts.add(lead.leadHead()));
        Map<Integer, List<Integer>> missing = new LinkedHashMap<>();
        for (int bell : working) {
            Set<Integer> started = new HashSet<>();
            starts.forEach(start -> started.add(start.placeOf(bell)));
            List<Integer> shortOf =
                    working.stream().filter(place -> !started.contains(place)).toList();
            if (!shortOf.isEmpty()) {
                missing.put(bell, shortOf);
            }
        }
        return Optional.of(new WorkFound(missing.isEmpty(), missing));
    }

    /**
     * How many of the back bells count as the tenors: the back two up to
     * Major, and everything above the sixth on ten and twelve, which is what a
     * band ringing Royal means by keeping the tenors together.
     *
     * @param stage a stage of six or more
     * @return how many tenors to watch
     */
    public static int tenorCount(int stage) {
        return stage <= 8 ? 2 : stage - 6;
    }

    /**
     * Whether the tenors stayed coursing each other at every lead head. It can
     * only be read when every lead head has the treble leading, which is true
     * of a treble dominated method and not of a principle.
     *
     * @param leadHeads the lead head of every lead rung, in order
     * @param stage the stage
     * @return what was found, or nothing below six bells or when a lead head has the treble elsewhere
     */
    public static Optional<TenorsFound> tenorsFound(List<Row> leadHeads, int stage) {
        if (stage < 6 || leadHeads.stream().anyMatch(head -> head.bellAt(1) != 1)) {
            return Optional.empty();
        }
        int tenors = tenorCount(stage);
        List<Integer> parted = new ArrayList<>();
        for (int index = 0; index < leadHeads.size(); index += 1) {
            if (!CoursingOrder.of(leadHeads.get(index)).tenorsTogether(tenors)) {
                parted.add(index + 1);
            }
        }
        return Optional.of(new TenorsFound(tenors, parted.isEmpty(), parted));
    }

    /**
     * Check a touch against what it claims to be, and list everything that does
     * not add up.
     *
     * @param analysis the report on the touch
     * @param claim what it claims to be
     * @return the problems, empty when the claim holds
     */
    public static List<String> checkClaim(TouchAnalysis analysis, Claim claim) {
        List<String> problems = new ArrayList<>();
        if (!analysis.comesRound()) {
            problems.add("the touch does not come round");
        }
        if (!analysis.proof().isTrue()) {
            problems.add(analysis.summary());
        }
        if (claim.length().isPresent() && claim.length().getAsInt() != analysis.length()) {
            problems.add("the calling gives " + analysis.length() + " changes, not the "
                    + claim.length().getAsInt() + " claimed");
        }
        claim.kind().ifPresent(kind -> {
            Lengths.Check checked = Lengths.checkLength(analysis.length(), claim.stage(), kind);
            if (!checked.ok()) {
                checked.reason().ifPresent(problems::add);
            }
        });
        return List.copyOf(problems);
    }

    /**
     * How many of each call there are, plain leads left out.
     *
     * @param symbols the call symbol at the end of every lead
     * @return each symbol and its count, in collation order
     */
    public static List<CallCount> callCounts(Collection<String> symbols) {
        Map<String, Integer> counts = CollectionHelpers.countBy(
                symbols.stream()
                        .filter(symbol -> !symbol.equals(TouchParser.PLAIN_SYMBOL))
                        .toList(),
                symbol -> symbol);
        return counts.entrySet().stream()
                .map(entry -> new CallCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CallCount::symbol, LocaleOrder.ORDER))
                .toList();
    }

    /**
     * The report as lines of text, which is what the command line prints.
     *
     * @param analysis the report on a touch
     * @return the lines
     */
    public static List<String> reportLines(TouchAnalysis analysis) {
        List<String> lines = new ArrayList<>();
        lines.add(analysis.title());
        lines.add(analysis.length() + " changes, " + analysis.kind());
        lines.add(analysis.summary());
        analysis.parts()
                .ifPresent(parts -> lines.add(
                        parts.parts() + " parts of " + parts.leadsPerPart() + " leads, part end " + parts.partEnd()));
        String calls = analysis.calls().stream()
                .map(entry -> entry.count() + entry.symbol())
                .collect(Collectors.joining(" "));
        lines.add(calls.isEmpty() ? "no calls" : "calls: " + calls);
        analysis.work()
                .ifPresent(found -> lines.add(
                        found.allTheWork()
                                ? "all the work"
                                : "not all the work: "
                                        + Text.plural(found.missing().size(), "bell") + " short of it"));
        analysis.tenors()
                .ifPresent(found -> lines.add(
                        found.together()
                                ? "tenors together throughout"
                                : "tenors parted at "
                                        + Text.plural(found.partedAt().size(), "lead head")));
        lines.add("music score " + Json.number(analysis.music().score()));
        for (RunLine run : analysis.music().runs()) {
            if (run.total() > 0) {
                lines.add("  " + run.length() + " bell runs: " + run.total() + " (" + run.front() + " front, "
                        + run.back() + " back)");
            }
        }
        return lines;
    }
}
