package io.github.rkaushikdev.garterhole.composition;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The analysis as a document. The service, the HTTP surface and the command
 * line all send it on, so its field names and their order are part of what
 * they promise their callers.
 */
class TouchAnalysisTest {

    private static Method plainBobMajor() {
        return Method.of("Plain", 8, "x18x18x18x18,12");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void writesEveryFieldInTheOrderTheyAreListed() {
        ObjectNode json = Analysis.analyseTouch(plainBobMajor(), "3(H)").toJson();
        assertThat(fieldNames(json))
                .containsExactly(
                        "title",
                        "length",
                        "kind",
                        "comesRound",
                        "proof",
                        "summary",
                        "calls",
                        "parts",
                        "music",
                        "tenors");
        assertThat(json.get("kind").asText()).isEqualTo("touch");
        assertThat(json.get("calls")).isEqualTo(Json.parse("[{\"symbol\":\"-\",\"count\":3}]"));
        assertThat(fieldNames(json.get("music"))).containsExactly("score", "runs", "named");
    }

    @Test
    void writesTheProofWithItsRepeats() {
        Method plainBobMinor = Method.of("Plain", 6, "x1x1x1,2");
        JsonNode proof =
                Analysis.analyseTouch(plainBobMinor, "3(-..-..)").toJson().get("proof");
        assertThat(fieldNames(proof)).containsExactly("isTrue", "rows", "distinct", "repeats", "comesRound");
        assertThat(proof.get("isTrue").asBoolean()).isFalse();
        assertThat(proof.get("repeats").get(0).get("at").size()).isGreaterThan(1);
    }

    @Test
    void writesNoPartStructureAsNull() {
        TouchAnalysis analysis = Analysis.analyseTouch(plainBobMajor(), "-...");
        assertThat(analysis.parts()).isEmpty();
        assertThat(analysis.toJson().get("parts").isNull()).isTrue();
    }

    @Test
    void writesTheTenorsWithTheLeadsThatPartedThem() {
        JsonNode together =
                Analysis.analyseTouch(plainBobMajor(), "3(H)").toJson().get("tenors");
        assertThat(together).isEqualTo(Json.parse("{\"tenors\":2,\"together\":true,\"partedAt\":[]}"));
        JsonNode parted = Analysis.analyseTouch(plainBobMajor(), "..-").toJson().get("tenors");
        assertThat(parted.get("partedAt")).isEqualTo(Json.parse("[3]"));
    }

    @Test
    void writesAWholeMusicScoreAsAWholeNumber() {
        String written =
                Json.stringify(Analysis.analyseTouch(plainBobMajor(), "3(H)").toJson());
        assertThat(written).containsPattern("\"music\":\\{\"score\":\\d+,");
    }

    @Test
    void ringsWithTheCallsAndObservationBellARequestNames() {
        Analysis.Request request = Analysis.Request.of(plainBobMajor(), "3(H)")
                .withCalls(CallSet.standardCalls(plainBobMajor()))
                .withObservation(7);
        TouchAnalysis analysis = Analysis.analyseTouch(request);
        assertThat(analysis.comesRound()).isTrue();
        assertThat(analysis.proof().isTrue()).isTrue();
    }
}
