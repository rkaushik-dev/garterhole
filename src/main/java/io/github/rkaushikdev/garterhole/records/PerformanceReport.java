package io.github.rkaushikdev.garterhole.records;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Bells;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.ArrayList;
import java.util.List;

/**
 * Writing performances up.
 *
 * <p>A single performance is written as a peal board: the tower, the date, the
 * length and the method, then the band one bell to a line, or one pair to a
 * line for a handbell peal. That is what lives
 * here. Writing a whole year of them out belongs to {@link Writers}, which cover
 * text, lists, spreadsheets and JSON without this class knowing about any of
 * them.
 */
public final class PerformanceReport {

    private PerformanceReport() {}

    /**
     * One performance, laid out the way it goes on a board.
     *
     * @param rung the performance
     * @return the lines of the board
     * @throws ValidationException when there is no performance
     */
    public static List<String> boardLines(Performance rung) {
        requirePerformance(rung);
        List<String> lines = new ArrayList<>();
        lines.add(rung.tower());
        lines.add(rung.dateText());
        lines.add(rung.changes() + " " + rung.title());
        if (rung.handbells()) {
            lines.add("on handbells");
        }
        rung.composer().ifPresent(composer -> lines.add("composed by " + composer));
        rung.duration().ifPresent(minutes -> lines.add(Text.plural(minutes, "minute")));
        lines.add("");
        List<String> band = rung.band();
        for (int index = 0; index < band.size(); index += 1) {
            String name = band.get(index);
            String conductor = rung.conductor().filter(name::equals).isPresent() ? " (c)" : "";
            lines.add(bellLabel(rung, index + 1) + "  " + name + conductor);
        }
        if (!rung.footnotes().isEmpty()) {
            lines.add("");
            lines.addAll(rung.footnotes());
        }
        return List.copyOf(lines);
    }

    /**
     * The one line form, which is what an association report prints.
     *
     * @param rung the performance
     * @return the line
     * @throws ValidationException when there is no performance
     */
    public static String summaryLine(Performance rung) {
        requirePerformance(rung);
        String conductor = rung.conductor().map(name -> " (c: " + name + ")").orElse("");
        return rung.dateText() + "  " + rung.tower() + "  " + rung.changes() + " " + rung.title() + conductor;
    }

    /**
     * The bell a board puts against a name: its symbol in a tower, so the
     * tenor of Royal is {@code 0}, and the pair on handbells, {@code 9-0}.
     */
    private static String bellLabel(Performance rung, int position) {
        if (!rung.handbells()) {
            return String.valueOf(Bells.symbol(position));
        }
        return Bells.symbol(2 * position - 1) + "-" + Bells.symbol(2 * position);
    }

    private static void requirePerformance(Performance rung) {
        if (rung == null) {
            throw new ValidationException("a Performance is needed to write it up");
        }
    }
}
