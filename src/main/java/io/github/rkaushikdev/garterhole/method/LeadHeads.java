package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.core.Cycles;
import io.github.rkaushikdev.garterhole.util.CollectionHelpers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lead heads and splicing.
 *
 * <p>Two methods can be spliced lead by lead when a lead of one finishes where
 * a lead of the other would have. That is exactly the condition that they are
 * on the same stage and have the same lead head, and it is the first thing a
 * band ringing a spliced quarter needs to know.
 *
 * <p>Nothing here reads a lead head code somebody recorded. The lead head is
 * rung out of the notation, and so is its code, by {@link LeadHeadCode}, since
 * the notation is the only version that cannot be wrong.
 */
public final class LeadHeads {

    private LeadHeads() {}

    /**
     * A short description of a lead head, for a report or a splicing table.
     *
     * @param leadHead the lead head row
     * @param leads how many leads a plain course runs to
     * @param leadEnd the change at the lead end, as notation
     * @param leadLength the number of changes in a lead
     * @param code the Central Council lead head code, when the lead head has one
     */
    public record Description(String leadHead, int leads, String leadEnd, int leadLength, Optional<String> code) {}

    /**
     * @param method a method
     * @return the stage and the lead head as one string, which is what grouping is keyed on
     * @throws IllegalArgumentException when there is no method
     */
    public static String leadHeadSignature(Method method) {
        HuntBells.requireMethod(method);
        return method.stage() + ":" + method.leadHead();
    }

    /**
     * @param method a method
     * @return the places made by the change at the lead end, which is where a
     *     call goes, as a copy the caller may change
     */
    public static List<Integer> leadEndPlaces(Method method) {
        HuntBells.requireMethod(method);
        return new ArrayList<>(method.leadEndChange().places());
    }

    /**
     * @param method a method
     * @return how many leads the plain course runs to
     */
    public static int leadHeadOrder(Method method) {
        HuntBells.requireMethod(method);
        return Cycles.orderOf(method.leadHead());
    }

    /**
     * @param left one method
     * @param right another
     * @return true when a lead of one finishes on the row a lead of the other would have finished on
     */
    public static boolean sharesLeadHead(Method left, Method right) {
        HuntBells.requireMethod(left);
        HuntBells.requireMethod(right);
        return left.stage() == right.stage() && left.leadHead().equals(right.leadHead());
    }

    /**
     * @param methods some methods
     * @return the methods grouped by the lead head they finish on, keyed by signature
     */
    public static Map<String, List<Method>> groupByLeadHead(List<Method> methods) {
        return CollectionHelpers.groupBy(methods, LeadHeads::leadHeadSignature);
    }

    /**
     * The methods in a list that can be spliced with a given method at a lead
     * head. The method itself is left out, since splicing a method with itself is
     * just ringing it.
     *
     * @param method a method
     * @param candidates the methods to look through
     * @return the ones that share its lead head
     */
    public static List<Method> spliceableWith(Method method, List<Method> candidates) {
        HuntBells.requireMethod(method);
        return candidates.stream()
                .filter(candidate -> candidate != method && sharesLeadHead(method, candidate))
                .toList();
    }

    /**
     * Splicing at every lead needs the lead lengths to match as well, otherwise
     * the treble is in the wrong place when the change of method comes. This is
     * the stricter test, and it is the one a band ringing spliced actually wants.
     *
     * @param left one method
     * @param right another
     * @return true when they share a lead head and a lead length
     */
    public static boolean spliceableEveryLead(Method left, Method right) {
        return sharesLeadHead(left, right) && left.leadLength() == right.leadLength();
    }

    /**
     * @param method a method
     * @return the lead head, the leads in a course, the lead end change, the
     *     lead length and the lead head code
     */
    public static Description describeLeadHead(Method method) {
        HuntBells.requireMethod(method);
        return new Description(
                method.leadHead().toString(),
                leadHeadOrder(method),
                method.leadEndChange().toString(),
                method.leadLength(),
                LeadHeadCode.codeOf(method));
    }

    /**
     * Methods that share a lead head code splice at a lead end without a
     * second thought, so a splicing table is grouped by code. Methods with no
     * code are left out rather than lumped together.
     *
     * @param methods some methods
     * @return the methods with a code, grouped by stage and code, for example {@code 8:b}
     */
    public static Map<String, List<Method>> groupByCode(List<Method> methods) {
        List<Method> coded = methods.stream()
                .filter(method -> LeadHeadCode.codeOf(method).isPresent())
                .toList();
        return Collections.unmodifiableMap(CollectionHelpers.groupBy(
                coded,
                method -> method.stage() + ":" + LeadHeadCode.codeOf(method).orElseThrow()));
    }
}
