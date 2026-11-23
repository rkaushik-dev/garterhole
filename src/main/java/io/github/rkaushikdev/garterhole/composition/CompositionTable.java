package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.TouchExpander.Rung;
import io.github.rkaushikdev.garterhole.composition.TouchExpander.RungLead;
import io.github.rkaushikdev.garterhole.core.Bells;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A touch written out the way a composition is printed: one line per course,
 * the course head at the left and the calls under the calling positions.
 *
 * <p>A course ends wherever the observation bell comes home, which for the
 * tenor in a seconds place method is the end of a plain course or a bob at
 * Home. The columns are the positions the calling uses, from the front of the
 * row to the back, so a table of Plain Bob Major reads {@code M W H} from left
 * to right, as a conductor learns it.
 *
 * <p>A course head is written without the treble, and without the two tenors
 * when they are home, so rounds on eight is {@code 23456}. A bob is a dash, two
 * bobs at one position in a course are {@code 2}, and any other call is its own
 * symbol.
 */
public final class CompositionTable {

    private CompositionTable() {}

    /**
     * One course of the table.
     *
     * @param courseHead the lead head the course finishes on, as a table writes it
     * @param calls the mark under each column that has a call in this course
     */
    public record Course(String courseHead, Map<String, String> calls) {

        /**
         * @param courseHead the course head
         * @param calls the marks, by column
         */
        public Course {
            calls = Collections.unmodifiableMap(new LinkedHashMap<>(calls));
        }
    }

    /**
     * A composition table.
     *
     * @param columns the calling positions used, front to back
     * @param courses the courses, in the order they are rung
     */
    public record Table(List<String> columns, List<Course> courses) {

        /**
         * @param columns the columns
         * @param courses the courses
         */
        public Table {
            columns = List.copyOf(columns);
            courses = List.copyOf(courses);
        }
    }

    /**
     * Write out a touch rung with the tenor observing.
     *
     * @param method the method
     * @param rung the touch, rung
     * @return the table
     */
    public static Table of(Method method, Rung rung) {
        return of(method, rung, method == null ? Bells.TREBLE : method.stage());
    }

    /**
     * Write out a touch.
     *
     * @param method the method
     * @param rung the touch, rung
     * @param observation the bell whose place names the positions
     * @return the table
     * @throws ValidationException when there is no method or no touch, or the observation bell is not on the stage
     */
    public static Table of(Method method, Rung rung, int observation) {
        if (method == null || rung == null) {
            throw new ValidationException("a method and a rung touch are needed to write a composition table");
        }
        int stage = method.stage();
        if (observation < 1 || observation > stage) {
            throw new ValidationException("bell " + observation + " is not on " + stage + " bells");
        }
        List<Course> courses = new ArrayList<>();
        Map<String, List<String>> current = new LinkedHashMap<>();
        Set<String> used = new LinkedHashSet<>();
        RungLead last = null;
        for (RungLead lead : rung.leads()) {
            if (!lead.symbol().equals(TouchParser.PLAIN_SYMBOL)) {
                String position =
                        lead.position().orElseGet(() -> CallingPosition.positionOfCall(lead.leadHead(), observation));
                current.computeIfAbsent(position, key -> new ArrayList<>()).add(lead.symbol());
                used.add(position);
            }
            if (lead.leadHead().placeOf(observation) == stage) {
                courses.add(course(lead.leadHead(), current));
                current.clear();
            }
            last = lead;
        }
        if (!current.isEmpty() && last != null) {
            courses.add(course(last.leadHead(), current));
        }
        return new Table(columnOrder(stage, used), courses);
    }

    /**
     * @param table a composition table
     * @return the table as lines of text, a heading of positions and a line per course
     */
    public static List<String> lines(Table table) {
        List<List<String>> rows = new ArrayList<>();
        List<String> heading = new ArrayList<>();
        heading.add("");
        heading.addAll(table.columns());
        rows.add(heading);
        for (Course course : table.courses()) {
            List<String> row = new ArrayList<>();
            row.add(course.courseHead());
            table.columns().forEach(column -> row.add(course.calls().getOrDefault(column, "")));
            rows.add(row);
        }
        List<String> lines = new ArrayList<>(Text.tabulate(rows));
        lines.add(Text.plural(table.courses().size(), "course"));
        return lines;
    }

    /**
     * The course head as a table writes it: the row without the treble, and
     * without the tenors when they are both home.
     *
     * @param head a lead head
     * @return the written course head, for example {@code 34256}
     */
    public static String courseHead(Row head) {
        int stage = head.stage();
        boolean tenorsHome = stage >= 6 && head.bellAt(stage) == stage && head.bellAt(stage - 1) == stage - 1;
        String row = head.toString();
        return row.substring(1, tenorsHome ? stage - 2 : stage);
    }

    private static Course course(Row head, Map<String, List<String>> calls) {
        Map<String, String> marks = new LinkedHashMap<>();
        calls.forEach((position, symbols) -> marks.put(position, mark(symbols)));
        return new Course(courseHead(head), marks);
    }

    private static String mark(List<String> symbols) {
        boolean allTheSame = symbols.stream().distinct().count() == 1;
        if (!allTheSame) {
            return String.join("", symbols);
        }
        String symbol = symbols.get(0);
        if (symbols.size() == 1) {
            return symbol;
        }
        return symbols.size() + (symbol.equals(TouchParser.DEFAULT_CALL_SYMBOL) ? "" : symbol);
    }

    /** The positions used, from the front of the row to the back. */
    private static List<String> columnOrder(int stage, Set<String> used) {
        return used.stream()
                .sorted(Comparator.comparingInt(position -> CallingPosition.placeForPosition(stage, position)))
                .toList();
    }
}
