package io.github.rkaushikdev.garterhole.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Writing a performance as a document and reading it back, which is what the
 * store does with every performance filed.
 */
class PerformanceDocumentWritingTest {

    private static Performance.Builder fields() {
        return Performance.builder()
                .tower("St Mary")
                .date(LocalDate.of(2025, 5, 18))
                .title("Plain Bob Minor")
                .changes(1260)
                .band("Alice", "Bob", "Carol", "Dan", "Erin", "Frank")
                .conductor("Alice");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void writesEveryFieldInItsOrderWithTheUnknownOnesAsNull() {
        ObjectNode written = PerformanceDocument.write(fields().build());
        assertThat(fieldNames(written))
                .containsExactly(
                        "tower",
                        "date",
                        "title",
                        "changes",
                        "band",
                        "conductor",
                        "composer",
                        "kind",
                        "duration",
                        "footnotes");
        assertThat(written.get("composer").isNull()).isTrue();
        assertThat(written.get("duration").isNull()).isTrue();
        assertThat(written.get("footnotes")).isEqualTo(Json.array());
        assertThat(written.get("kind").asText()).isEqualTo("quarter peal");
    }

    @Test
    void keepsTheFootnotesTheTimeAndTheHandbellsThroughARoundTrip() {
        Performance rung = fields().duration(48)
                .footnotes(List.of("First quarter for Bob."))
                .handbells(true)
                .band("Alice", "Bob", "Carol")
                .build();
        ObjectNode written = PerformanceDocument.write(rung);
        assertThat(written.get("handbells").asBoolean()).isTrue();
        Performance back = PerformanceDocument.read(written);
        assertThat(back.duration()).hasValue(48);
        assertThat(back.footnotes()).containsExactly("First quarter for Bob.");
        assertThat(back.handbells()).isTrue();
        assertThat(back.id()).isEqualTo(rung.id());
        assertThat(PerformanceDocument.write(back)).isEqualTo(written);
    }

    @Test
    void leavesTheHandbellFlagOffATowerPerformance() {
        assertThat(PerformanceDocument.write(fields().build()).has("handbells")).isFalse();
    }

    @Test
    void refusesToWriteNothing() {
        assertThatThrownBy(() -> PerformanceDocument.write(null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> PerformanceDocument.read(null)).hasMessageContaining("definition object");
    }
}
