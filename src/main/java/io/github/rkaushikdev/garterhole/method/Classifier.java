package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.core.Cycles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The class name of a method. Cambridge is Surprise, Kent is Treble Bob, Plain
 * Bob is Bob, and the difference between the three is not the name anybody gave
 * them but where the internal places fall against the treble's path.
 *
 * <p>Everything here is worked out from the changes. A collection that records
 * a class name is not consulted, because collections disagree with themselves.
 */
public final class Classifier {

    private Classifier() {}

    /**
     * @param place a place
     * @return the pair of places a bell is dodging in, counting pairs from the front
     */
    public static int sectionOf(int place) {
        return (place + 1) / 2;
    }

    /**
     * The indices of the changes where the hunt bell moves from one dodging
     * position to the next. These are the cross sections, and what happens at
     * them decides between Treble Bob, Delight and Surprise.
     *
     * @param path the places the hunt bell rings in
     * @return the indices of the cross sections
     */
    public static List<Integer> crossSections(List<Integer> path) {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index + 1 < path.size(); index += 1) {
            if (sectionOf(path.get(index)) != sectionOf(path.get(index + 1))) {
                indices.add(index);
            }
        }
        return List.copyOf(indices);
    }

    /**
     * @param change a change
     * @return true when the change makes a place anywhere except the front and the back
     */
    public static boolean makesInternalPlace(Change change) {
        return change.places().stream().anyMatch(place -> place != 1 && place != change.stage());
    }

    /**
     * @param method a method
     * @param bell its hunt bell
     * @return true when the hunt bell never reaches the back, which makes the method little
     */
    public static boolean isLittle(Method method, int bell) {
        return Collections.max(HuntBells.pathOf(method, bell)) < method.stage();
    }

    /**
     * @param method a method
     * @return true when the working bells do not all belong to cycles of one length
     *     in the lead head, so they do not all ring the same line
     */
    public static boolean isDifferential(Method method) {
        Set<Integer> working = new HashSet<>(HuntBells.workingBells(method));
        if (working.isEmpty()) {
            return false;
        }
        Set<Integer> lengths = new HashSet<>();
        for (List<Integer> cycle : Cycles.cycles(method.leadHead())) {
            if (cycle.stream().anyMatch(working::contains)) {
                lengths.add(cycle.size());
            }
        }
        return lengths.size() > 1;
    }

    /**
     * True when the bell rings the same number of blows in every place it
     * reaches over a lead. A treble that hunts, dodges or makes places in
     * the same measure everywhere does; one that lingers at the front and
     * hurries through the back does not, and that is what makes a method
     * Hybrid rather than anything tidier. Only the places the bell reaches
     * are counted, so a little method is not marked down for never reaching
     * the back.
     *
     * @param method a method
     * @param bell the bell, usually the hunt bell
     * @return true when every place it reaches gets the same share of the lead
     */
    public static boolean ringsEvenly(Method method, int bell) {
        List<Integer> path = HuntBells.pathOf(method, bell);
        List<Integer> blows = HuntBells.blowsInEachPlace(method, bell);
        Set<Integer> shares = new HashSet<>(blows.subList(Collections.min(path) - 1, Collections.max(path)));
        return shares.size() == 1;
    }

    /**
     * The class name a method belongs to, without the Little and Differential
     * prefixes.
     *
     * @param method a method
     * @return for example {@code Surprise}, {@code Bob}, {@code Hybrid} or {@code Principle}
     * @throws IllegalArgumentException when there is no method
     */
    public static String baseClass(Method method) {
        return classify(method).base();
    }

    /**
     * The full classification, including the prefixes and the parts that went
     * into the decision.
     *
     * <p>The hunt bell is looked at in two steps. First whether it rings evenly,
     * the same number of blows in each place it reaches, because a method whose
     * treble does not is Hybrid whatever else its path does. Then, for one that
     * does, the shape of the path decides between the plain, the treble dodging
     * and the treble place families.
     *
     * @param method a method
     * @return the classification
     * @throws IllegalArgumentException when there is no method
     */
    public static Classification classify(Method method) {
        HuntBells.requireMethod(method);
        List<Integer> hunts = HuntBells.huntBells(method);
        boolean differential = isDifferential(method);
        if (hunts.isEmpty()) {
            String name = differential ? "Differential Principle" : "Principle";
            return new Classification(name, "Principle", false, differential, hunts, Optional.empty());
        }
        int principal = hunts.get(0);
        HuntPath shape = HuntBells.huntPathKind(method, principal);
        String base = ringsEvenly(method, principal) ? familyClass(method, principal, shape) : "Hybrid";
        boolean little = isLittle(method, principal);
        List<String> parts = new ArrayList<>();
        if (differential) {
            parts.add("Differential");
        }
        if (little) {
            parts.add("Little");
        }
        parts.add(base);
        return new Classification(String.join(" ", parts), base, little, differential, hunts, Optional.of(shape));
    }

    private static String familyClass(Method method, int principal, HuntPath shape) {
        List<Integer> path = HuntBells.pathOf(method, principal);
        return switch (shape) {
            case PLAIN -> hasWorkingDodges(method) ? "Bob" : "Place";
            case TREBLE_BOB -> trebleBobFamily(method, path);
            case TREBLE_PLACE -> HuntBells.dodgePlaces(path, method.stage()).isEmpty() ? "Treble Place" : "Alliance";
        };
    }

    /**
     * Treble Bob, Delight and Surprise differ only in how many cross sections
     * have an internal place.
     */
    private static String trebleBobFamily(Method method, List<Integer> path) {
        List<Integer> sections = crossSections(path);
        if (sections.isEmpty()) {
            return "Treble Bob";
        }
        long withPlace = sections.stream()
                .filter(index -> makesInternalPlace(method.changes().get(index)))
                .count();
        if (withPlace == sections.size()) {
            return "Surprise";
        }
        if (withPlace == 0) {
            return "Treble Bob";
        }
        return "Delight";
    }

    /**
     * True when any working bell turns round away from the front and the back,
     * which is what separates a Bob method from a Place method.
     */
    private static boolean hasWorkingDodges(Method method) {
        return HuntBells.workingBells(method).stream()
                .anyMatch(bell -> !HuntBells.dodgePlaces(HuntBells.pathOf(method, bell), method.stage())
                        .isEmpty());
    }
}
