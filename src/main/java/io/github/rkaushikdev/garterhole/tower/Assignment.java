package io.github.rkaushikdev.garterhole.tower;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.tower.CompetencyRegister.Level;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Putting the band on the bells.
 *
 * <p>Every ringer can take some of the bells and not others: the tenor is too
 * heavy for some, the method is beyond others. That makes this a matching
 * problem, and a greedy pass at it fails on exactly the cases that matter, when
 * one ringer is the only person who can take one bell.
 *
 * <p>So it backtracks, and it fills the hardest bell first. Ordering the bells
 * by how few people can take them is what turns a search that would run for
 * minutes into one that finishes at once.
 */
public final class Assignment {

    private Assignment() {}

    /**
     * What is to be rung and by whom.
     *
     * @param tower the tower
     * @param band the ringers present
     * @param stage the stage to be rung
     * @param title the method, or {@code null} when it does not matter who knows it
     * @param competency what the band can ring, or {@code null} when it does not matter
     * @param level the level a ringer must be at in the method, or {@code null} for inside
     * @param conductor true when somebody on the bells has to be able to conduct
     * @param ring which end of the tower's ring to ring on when it has more bells than are needed
     */
    public record Request(
            Tower tower,
            List<Ringer> band,
            int stage,
            String title,
            CompetencyRegister competency,
            Level level,
            boolean conductor,
            Ring ring) {

        /**
         * @param tower the tower
         * @param band the ringers present
         * @param stage the stage to be rung
         * @return a request that does not care what method is rung
         */
        public static Request of(Tower tower, List<Ringer> band, int stage) {
            return new Request(tower, band, stage, null, null, null, false, Ring.FRONT);
        }

        /**
         * @param method the method title
         * @param register what the band can ring
         * @return this request, putting only those who know the method on the bells
         */
        public Request withMethod(String method, CompetencyRegister register) {
            return new Request(tower, band, stage, method, register, level, conductor, ring);
        }

        /**
         * @param atLeast the level a ringer must be at in the method
         * @return this request at that level
         */
        public Request withLevel(Level atLeast) {
            return new Request(tower, band, stage, title, competency, atLeast, conductor, ring);
        }

        /**
         * @return this request, refusing a band with nobody on the bells to conduct
         */
        public Request requiringConductor() {
            return new Request(tower, band, stage, title, competency, level, true, ring);
        }

        /**
         * Six on an eight are usually rung on the back six, which are in the
         * key of the ring and heavy enough to sound like a peal; the front six
         * are lighter and are not in any key.
         *
         * @return this request ringing on the heaviest bells the stage needs
         */
        public Request onTheBackBells() {
            return new Request(tower, band, stage, title, competency, level, conductor, Ring.BACK);
        }
    }

    /** Which end of a tower's ring a band rings on when it has more bells than are needed. */
    public enum Ring {
        /** The lightest bells, from the treble. */
        FRONT,
        /** The heaviest bells, down to the tenor. */
        BACK
    }

    /**
     * The band put on the bells.
     *
     * @param assignment the ringer on each bell, by bell number in order
     * @param conductor the first ringer on the bells who conducts, when there is one
     */
    public record Result(Map<Integer, String> assignment, Optional<String> conductor) {}

    /**
     * Which bells a ringer could take, given the weight and the method.
     *
     * @param request what is to be rung
     * @param member one of the band
     * @return the numbers of the bells they could take
     */
    public static List<Integer> bellsOpenTo(Request request, Ringer member) {
        List<Integer> bells = new ArrayList<>();
        for (int number : ringingBells(request)) {
            if (!member.canHandle(request.tower().bell(number))) {
                continue;
            }
            if (!knowsTheMethod(request, member)) {
                continue;
            }
            bells.add(number);
        }
        return List.copyOf(bells);
    }

    /**
     * The bells that have to be filled: the stage, plus a cover when the stage
     * is odd and the tower has a bell spare, taken from the front of the ring
     * or the back as the request says.
     *
     * @param request what is to be rung
     * @return the bell numbers, the lightest first
     * @throws ValidationException when the request cannot be used or the tower cannot ring the stage
     */
    public static List<Integer> ringingBells(Request request) {
        requireRequest(request);
        Tower tower = request.tower();
        int stage = request.stage();
        if (!tower.canRing(stage)) {
            throw new ValidationException(tower.name() + " cannot ring " + stage + " bells");
        }
        int count = tower.canCover(stage) ? stage + 1 : stage;
        List<Bell> rung = request.ring() == Ring.BACK ? tower.backBells(count) : tower.frontBells(count);
        return rung.stream().map(Bell::number).toList();
    }

    /**
     * Put the band on the bells, or say why it cannot be done.
     *
     * @param request what is to be rung
     * @return who is on which bell, and who conducts
     * @throws ValidationException when the band cannot be put on the bells
     */
    public static Result assignBells(Request request) {
        List<Integer> bells = ringingBells(request);
        if (request.band().size() < bells.size()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("needed", bells.size());
            details.put("present", request.band().size());
            throw new ValidationException(
                    bells.size() + " bells need ringing and only "
                            + request.band().size() + " are here",
                    details);
        }
        Map<Integer, List<Ringer>> options = new LinkedHashMap<>();
        for (int number : bells) {
            List<Ringer> able = request.band().stream()
                    .filter(member -> bellsOpenTo(request, member).contains(number))
                    .toList();
            if (able.isEmpty()) {
                throw new ValidationException("nobody here can take bell " + number, Map.of("bell", number));
            }
            options.put(number, able);
        }
        List<Integer> order = new ArrayList<>(bells);
        order.sort(Comparator.comparingInt(number -> options.get(number).size()));
        Map<Integer, String> assignment = new LinkedHashMap<>();
        if (!fill(order, 0, options, assignment, new HashSet<>())) {
            throw new ValidationException(
                    "there is no way to put this band on these bells", Map.of("bells", bells.size()));
        }
        Optional<String> conductor = findConductor(request, assignment);
        if (request.conductor() && conductor.isEmpty()) {
            throw new ValidationException("nobody on the bells can conduct");
        }
        return new Result(Collections.unmodifiableMap(new LinkedHashMap<>(new TreeMap<>(assignment))), conductor);
    }

    /**
     * Try to fill the bells one at a time, going back when a choice leaves a
     * later bell with nobody.
     */
    private static boolean fill(
            List<Integer> order,
            int index,
            Map<Integer, List<Ringer>> options,
            Map<Integer, String> assignment,
            Set<String> taken) {
        if (index == order.size()) {
            return true;
        }
        int number = order.get(index);
        for (Ringer member : options.get(number)) {
            if (taken.contains(member.id())) {
                continue;
            }
            assignment.put(number, member.id());
            taken.add(member.id());
            if (fill(order, index + 1, options, assignment, taken)) {
                return true;
            }
            assignment.remove(number);
            taken.remove(member.id());
        }
        return false;
    }

    private static Optional<String> findConductor(Request request, Map<Integer, String> assignment) {
        Set<String> onTheBells = new HashSet<>(assignment.values());
        return request.band().stream()
                .filter(member -> onTheBells.contains(member.id()) && member.conducts())
                .map(Ringer::id)
                .findFirst();
    }

    private static boolean knowsTheMethod(Request request, Ringer member) {
        if (request.competency() == null || request.title() == null) {
            return true;
        }
        Level level = request.level() == null ? CompetencyRegister.DEFAULT_LEVEL : request.level();
        return request.competency().canRing(member.id(), request.title(), level);
    }

    private static void requireRequest(Request request) {
        if (request == null) {
            throw new ValidationException("an assignment needs a request object");
        }
        if (request.tower() == null) {
            throw new ValidationException("an assignment needs a Tower");
        }
        if (request.band() == null || request.band().isEmpty()) {
            throw new ValidationException("an assignment needs a band");
        }
        for (Ringer member : request.band()) {
            if (member == null) {
                throw new ValidationException("everybody in the band must be a Ringer");
            }
        }
    }
}
