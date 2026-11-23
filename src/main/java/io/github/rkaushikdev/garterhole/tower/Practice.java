package io.github.rkaushikdev.garterhole.tower;

import io.github.rkaushikdev.garterhole.GarterholeException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.method.Title;
import io.github.rkaushikdev.garterhole.tower.CompetencyRegister.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Planning a practice night.
 *
 * <p>A practice is not a list of the hardest methods the band can ring. It is a
 * sequence that gets everybody ringing, teaches somebody something, and does
 * not leave the same three people standing out all evening.
 *
 * <p>So the planner works from two lists: what the band present can already
 * ring, which fills most of the evening, and what one or two of them are
 * learning, which fills the rest. A method nobody present can ring is left out
 * however much the captain wants to ring it.
 */
public final class Practice {

    private static final int DEFAULT_SLOTS = 6;
    private static final int DEFAULT_STRETCHES = 1;

    private Practice() {}

    /** Why a touch is in the plan. */
    public enum Purpose {
        /** Enough of the band can ring it to fill the bells. */
        REPERTOIRE,
        /** Some of the band are learning it. */
        STRETCH;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * What a practice is planned from.
     *
     * @param tower the tower
     * @param present the ringers who turned up
     * @param competency what the band can ring
     * @param slots how many touches the evening has room for
     * @param stretches how many of them may be methods the band is still learning
     */
    public record Setting(Tower tower, List<Ringer> present, CompetencyRegister competency, int slots, int stretches) {

        /**
         * @param tower the tower
         * @param present the ringers who turned up
         * @param competency what the band can ring
         * @return a setting with room for six touches, one of them a stretch
         */
        public static Setting of(Tower tower, List<Ringer> present, CompetencyRegister competency) {
            return new Setting(tower, present, competency, DEFAULT_SLOTS, DEFAULT_STRETCHES);
        }

        /**
         * @param count how many touches the evening has room for
         * @return this setting with that many slots
         */
        public Setting withSlots(int count) {
            return new Setting(tower, present, competency, count, stretches);
        }

        /**
         * @param count how many stretches are allowed
         * @return this setting with that many stretches
         */
        public Setting withStretches(int count) {
            return new Setting(tower, present, competency, slots, count);
        }
    }

    /**
     * A method worth considering tonight.
     *
     * @param title the method title
     * @param stage its stage
     * @param ready how many present can ring an inside bell to it
     * @param learning how many present are at least learning it
     */
    public record Candidate(String title, int stage, int ready, int learning) {}

    /**
     * One touch in the plan.
     *
     * @param title the method title
     * @param stage its stage
     * @param purpose whether it is repertoire or a stretch
     * @param band the ringers on the bells, in bell order
     * @param assignment the ringer on each bell
     * @param conductor who conducts, when somebody on the bells can
     */
    public record Item(
            String title,
            int stage,
            Purpose purpose,
            List<String> band,
            Map<Integer, String> assignment,
            Optional<String> conductor) {}

    /**
     * @param competency what the band can ring
     * @param present the ringers who turned up
     * @param title the method title
     * @param level the level asked for
     * @return how many of the band present can ring the method at that level
     */
    public static int readyFor(CompetencyRegister competency, List<Ringer> present, String title, Level level) {
        return (int) present.stream()
                .filter(member -> competency.canRing(member.id(), title, level))
                .count();
    }

    /**
     * The methods worth ringing tonight, best first.
     *
     * <p>A method is worth ringing when enough of the band present can ring it
     * to fill the bells. Everything else is a stretch, and a stretch is worth one
     * slot at most. A title that cannot be read, or names a stage the tower
     * cannot ring, is left out.
     *
     * @param setting the tower, the band present and what they can ring
     * @return the candidates, most ready first, then the larger stage, then by title
     * @throws ValidationException when the setting cannot be used
     */
    public static List<Candidate> candidates(Setting setting) {
        requireSetting(setting);
        List<Candidate> found = new ArrayList<>();
        for (String title : setting.competency().titles()) {
            int stage = Title.tryParse(title).map(Title.Parts::stage).orElse(0);
            if (!setting.tower().canRing(stage)) {
                continue;
            }
            found.add(new Candidate(
                    title,
                    stage,
                    readyFor(setting.competency(), setting.present(), title, Level.INSIDE),
                    readyFor(setting.competency(), setting.present(), title, Level.LEARNING)));
        }
        found.sort(Comparator.comparingInt(Candidate::ready)
                .reversed()
                .thenComparing(Comparator.comparingInt(Candidate::stage).reversed())
                .thenComparing(Candidate::title));
        return List.copyOf(found);
    }

    /**
     * Plan an evening.
     *
     * @param setting the tower, the band present, what they can ring and the size of the evening
     * @return the touches, in the order they should be rung
     * @throws ValidationException when the setting cannot be used or has no slots
     */
    public static List<Item> planPractice(Setting setting) {
        requireSetting(setting);
        if (setting.slots() < 1) {
            throw new ValidationException("a practice of " + setting.slots() + " touches is not a practice");
        }
        List<Item> plan = new ArrayList<>();
        int stretchesUsed = 0;
        for (Candidate option : candidates(setting)) {
            if (plan.size() >= setting.slots()) {
                break;
            }
            Purpose purpose = option.ready() >= option.stage() ? Purpose.REPERTOIRE : Purpose.STRETCH;
            if (purpose == Purpose.STRETCH && stretchesUsed >= setting.stretches()) {
                continue;
            }
            Level level = purpose == Purpose.REPERTOIRE ? Level.INSIDE : Level.LEARNING;
            Optional<Item> item = tryItem(setting, option, level, purpose);
            if (item.isEmpty()) {
                continue;
            }
            if (purpose == Purpose.STRETCH) {
                stretchesUsed += 1;
            }
            plan.add(item.get());
        }
        return List.copyOf(plan);
    }

    /**
     * @param item one touch in the plan
     * @return the line as a captain would read it out
     */
    public static String describeItem(Item item) {
        String bells = item.assignment().entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(" "));
        String conductor = item.conductor().map(id -> "conducted by " + id).orElse("nobody conducting");
        return item.title() + " (" + item.purpose() + ") " + bells + ", " + conductor;
    }

    private static Optional<Item> tryItem(Setting setting, Candidate option, Level level, Purpose purpose) {
        try {
            Assignment.Result result =
                    Assignment.assignBells(Assignment.Request.of(setting.tower(), setting.present(), option.stage())
                            .withMethod(option.title(), setting.competency())
                            .withLevel(level));
            return Optional.of(new Item(
                    option.title(),
                    option.stage(),
                    purpose,
                    List.copyOf(result.assignment().values()),
                    result.assignment(),
                    result.conductor()));
        } catch (GarterholeException | IllegalArgumentException cannotBeManned) {
            return Optional.empty();
        }
    }

    private static void requireSetting(Setting setting) {
        if (setting == null) {
            throw new ValidationException("a practice needs a setting");
        }
        if (setting.tower() == null) {
            throw new ValidationException("a practice needs a Tower");
        }
        if (setting.present() == null || setting.present().isEmpty()) {
            throw new ValidationException("a practice needs somebody to turn up");
        }
        if (setting.competency() == null) {
            throw new ValidationException("a practice needs to know what the band can ring");
        }
    }
}
