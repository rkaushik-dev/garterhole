package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.Stage;
import io.github.rkaushikdev.garterhole.core.Transposition;
import java.util.List;
import java.util.Optional;

/**
 * The Central Council's lead head codes, worked out from the notation.
 *
 * <p>A collection prints a letter beside every method whose lead head is one of
 * the Plain Bob or Grandsire lead heads, so that a band choosing methods to
 * splice can see at a glance which ones go together. Cambridge and Yorkshire
 * are both {@code b}, Bristol is {@code m}, and Plain Bob itself is {@code a}.
 *
 * <p>The letter says two things. Which lead head it is, counted along the plain
 * course of Plain Bob (or of Grandsire, for a method with two hunt bells). And
 * which change the lead end uses, because a seconds place method and an eighths
 * place method can share a lead head and still not splice. On the higher stages
 * there are more lead heads than letters, and the extra ones take a number
 * after the letter, so Royal has {@code c1} and {@code d1} between {@code c} and
 * {@code d}.
 *
 * <p>A method with any other lead head has no code, which is not a fault in it.
 */
public final class LeadHeadCode {

    /** The first group of letters, counted forwards along the course. */
    private static final String FORWARD = "abc";

    /** The same group counted backwards from the last lead head of the course. */
    private static final String BACKWARD = "fed";

    /** The second group, for the other lead end, matching {@code abcdef} letter for letter. */
    private static final String OTHER_END = "ghjklm";

    private LeadHeadCode() {}

    /** The two families of lead head a code can be given for. */
    public enum Family {
        /** One hunt bell, the treble, and Plain Bob's lead heads. */
        PLAIN_BOB,
        /** Two hunt bells, the treble and the two, and Grandsire's lead heads. */
        GRANDSIRE
    }

    /**
     * The code of a method, when it has one.
     *
     * @param method a method
     * @return the code, for example {@code b} or {@code j1}
     * @throws IllegalArgumentException when there is no method
     */
    public static Optional<String> codeOf(Method method) {
        HuntBells.requireMethod(method);
        int stage = method.stage();
        if (stage < 5) {
            return Optional.empty();
        }
        List<Integer> hunts = HuntBells.huntBells(method);
        if (hunts.equals(List.of(1))) {
            return codeFor(Family.PLAIN_BOB, method.leadHead(), method.leadEndChange());
        }
        if (hunts.equals(List.of(1, 2))) {
            return codeFor(Family.GRANDSIRE, method.leadHead(), method.changes().get(0));
        }
        return Optional.empty();
    }

    /**
     * The lead head every member of a family starts its plain course from: the
     * row a plain lead of Plain Bob, or of Grandsire, finishes on.
     *
     * @param family Plain Bob or Grandsire
     * @param stage a stage of five or more
     * @return the row
     * @throws IllegalArgumentException when the stage is below five or not supported
     */
    public static Row firstLeadHead(Family family, int stage) {
        if (stage < 5 || !Stage.isSupported(stage)) {
            throw new IllegalArgumentException("lead head codes start at five bells, not " + stage);
        }
        Row row = Row.rounds(stage);
        for (int index = 0; index < 2 * stage - 1; index += 1) {
            row = huntingChange(stage, index).apply(row);
        }
        Row plainBob = Stage.isOdd(stage)
                ? Change.of(stage, 1, 2, stage).apply(row)
                : Change.of(stage, 1, 2).apply(row);
        return family == Family.PLAIN_BOB ? plainBob : swapTwoAndThree(plainBob);
    }

    /**
     * The code for a lead head and the change that decides the group, which is
     * what a composer looking at a lead head in a table wants to know. For the
     * Plain Bob family the deciding change is the lead end change; for
     * Grandsire it is the change straight after the lead head.
     *
     * @param family Plain Bob or Grandsire
     * @param leadHead the lead head
     * @param decider the deciding change, at the same stage
     * @return the code, or nothing when the lead head or the change has none
     * @throws IllegalArgumentException when the stage is below five or the stages differ
     */
    public static Optional<String> codeFor(Family family, Row leadHead, Change decider) {
        int stage = leadHead.stage();
        if (decider.stage() != stage) {
            throw new IllegalArgumentException(
                    "a change on " + decider.stage() + " cannot decide a lead head on " + stage);
        }
        Row first = firstLeadHead(family, stage);
        int leads = Transposition.order(first) - 1;
        int power = 0;
        for (int step = 1; step <= leads && power == 0; step += 1) {
            if (Transposition.power(first, step).equals(leadHead)) {
                power = step;
            }
        }
        if (power == 0) {
            return Optional.empty();
        }
        boolean sixLetters = (family == Family.PLAIN_BOB) != Stage.isOdd(stage);
        Optional<Boolean> firstGroup = group(family, stage, decider);
        if (firstGroup.isEmpty()) {
            return Optional.empty();
        }
        if (sixLetters) {
            String letter = sixLetterCode(power, leads);
            return Optional.of(firstGroup.get() ? letter : otherEnd(letter));
        }
        int forwards = family == Family.PLAIN_BOB ? (leads + 1) / 2 : leads / 2;
        boolean early = power <= forwards;
        int counted = early ? power - 1 : leads - power;
        String letter = firstGroup.get() ? (early ? "p" : "q") : (early ? "r" : "s");
        return Optional.of(letter + (counted == 0 ? "" : String.valueOf(counted)));
    }

    /**
     * Which group the deciding change puts a method in: true for the first
     * group of letters, false for the second, and nothing when the change is
     * neither of the two a code is given for.
     */
    private static Optional<Boolean> group(Family family, int stage, Change decider) {
        List<Integer> places = decider.places();
        boolean odd = Stage.isOdd(stage);
        List<Integer> first;
        List<Integer> second;
        if (family == Family.PLAIN_BOB) {
            first = odd ? List.of(1, 2, stage) : List.of(1, 2);
            second = odd ? List.of(1) : List.of(1, stage);
        } else {
            first = odd ? List.of(3) : List.of(3, stage);
            second = odd ? List.of(stage) : List.of();
        }
        if (places.equals(first)) {
            return Optional.of(true);
        }
        return places.equals(second) ? Optional.of(false) : Optional.empty();
    }

    /** {@code a} to {@code f}, with a number added once the letters run out in the middle. */
    private static String sixLetterCode(int power, int leads) {
        int half = leads / 2;
        if (power <= half) {
            int index = power - 1;
            return index < FORWARD.length() ? String.valueOf(FORWARD.charAt(index)) : "c" + (index - 2);
        }
        int fromEnd = leads - power;
        return fromEnd < BACKWARD.length() ? String.valueOf(BACKWARD.charAt(fromEnd)) : "d" + (fromEnd - 2);
    }

    private static String otherEnd(String code) {
        return OTHER_END.charAt(code.charAt(0) - 'a') + code.substring(1);
    }

    /** Plain hunting: cross and back on an even stage, the back and the front on an odd one. */
    private static Change huntingChange(int stage, int index) {
        if (Stage.isOdd(stage)) {
            return index % 2 == 0 ? Change.of(stage, stage) : Change.of(stage, 1);
        }
        return index % 2 == 0 ? Change.cross(stage) : Change.of(stage, 1, stage);
    }

    /** Grandsire's lead head is Plain Bob's with the two and the three written the other way round. */
    private static Row swapTwoAndThree(Row row) {
        int[] bells = row.toArray();
        for (int index = 0; index < bells.length; index += 1) {
            if (bells[index] == 2) {
                bells[index] = 3;
            } else if (bells[index] == 3) {
                bells[index] = 2;
            }
        }
        return new Row(bells);
    }
}
