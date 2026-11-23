package io.github.rkaushikdev.garterhole.records;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.validation.Coerce;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A performance as a document, read and written in one place.
 *
 * <p>The store, a request body and the JSON report all carry a performance as
 * the same document, and when each of them wrote its own the store quietly
 * dropped what it did not know about: a performance filed with footnotes and
 * a time came back without either. Reading and writing here, side by side,
 * means a field added to one is added to the other.
 *
 * <p>The fields are written in a fixed order, {@code tower}, {@code date},
 * {@code title}, {@code changes}, {@code band}, {@code conductor},
 * {@code composer}, {@code kind}, {@code duration} and {@code footnotes}, with
 * anything not known written as {@code null}. A handbell performance has
 * {@code handbells} set to {@code true} at the end; a tower performance, which
 * is nearly all of them, leaves it out.
 */
public final class PerformanceDocument {

    private PerformanceDocument() {}

    /**
     * Write a performance as a document.
     *
     * @param rung the performance
     * @return its document, including the kind its length counts as
     * @throws ValidationException when there is no performance
     */
    public static ObjectNode write(Performance rung) {
        if (rung == null) {
            throw new ValidationException("a Performance is needed to write it as a document");
        }
        ObjectNode document = Json.object();
        document.put("tower", rung.tower());
        document.put("date", rung.dateText());
        document.put("title", rung.title());
        document.put("changes", rung.changes());
        rung.band().forEach(document.putArray("band")::add);
        document.put("conductor", rung.conductor().orElse(null));
        document.put("composer", rung.composer().orElse(null));
        document.put("kind", rung.kind().label());
        if (rung.duration().isPresent()) {
            document.put("duration", rung.duration().getAsInt());
        } else {
            document.putNull("duration");
        }
        rung.footnotes().forEach(document.putArray("footnotes")::add);
        if (rung.handbells()) {
            document.put("handbells", true);
        }
        return document;
    }

    /**
     * Build a performance from a document: a request body, or a record read back
     * out of the store. The date is written the way a form sends it,
     * {@code 2025-05-18}. A field that is missing or {@code null} is not given,
     * because a record read back out of the store carries {@code null} where a
     * field was missing, and treating that as a value rather than as an absence
     * is how a performance with no conductor comes back refusing to be rebuilt.
     * The {@code kind} is never read, since it is worked out from the length.
     *
     * @param document the fields of the performance
     * @return the performance
     * @throws ValidationException when the document does not describe a performance
     */
    public static Performance read(JsonNode document) {
        if (document == null || !document.isObject()) {
            throw new ValidationException("a performance needs a definition object");
        }
        Performance.Builder builder = Performance.builder()
                .tower(textOf(document.path("tower")))
                .title(textOf(document.path("title")))
                .date(dateOf(document.path("date")))
                .changes(wholeNumber(document.path("changes"), " is not a number of changes"))
                .band(bandOf(document.path("band")))
                .footnotes(footnotesOf(document.path("footnotes")))
                .handbells(document.path("handbells").asBoolean(false));
        JsonNode conductor = document.path("conductor");
        if (isGiven(conductor)) {
            builder.conductor(givenText(conductor, "the conductor"));
        }
        JsonNode composer = document.path("composer");
        if (isGiven(composer)) {
            builder.composer(givenText(composer, "the composer"));
        }
        JsonNode duration = document.path("duration");
        if (isGiven(duration)) {
            builder.duration(wholeNumber(duration, " is not a length of time in minutes"));
        }
        return builder.build();
    }

    private static boolean isGiven(JsonNode value) {
        return !Json.isUndefined(value) && !value.isNull();
    }

    private static String textOf(JsonNode value) {
        return value.isTextual() ? value.textValue() : null;
    }

    private static String givenText(JsonNode value, String label) {
        if (!value.isTextual()) {
            throw new IllegalArgumentException(label + " must be a string");
        }
        return value.textValue();
    }

    private static LocalDate dateOf(JsonNode value) {
        return value.isTextual() ? Coerce.parseDate(value.textValue()).orElse(null) : null;
    }

    private static int wholeNumber(JsonNode value, String complaint) {
        if (!value.isNumber() || value.doubleValue() != Math.rint(value.doubleValue()) || !value.canConvertToInt()) {
            throw new ValidationException(Json.templateText(value) + complaint);
        }
        return value.intValue();
    }

    private static List<String> bandOf(JsonNode value) {
        if (!value.isArray()) {
            return null;
        }
        List<String> names = new ArrayList<>(value.size());
        value.forEach(name -> names.add(textOf(name)));
        return names;
    }

    private static List<String> footnotesOf(JsonNode value) {
        List<String> lines = new ArrayList<>();
        if (value.isArray()) {
            value.forEach(line -> lines.add(Json.templateText(line)));
        }
        return lines;
    }
}
