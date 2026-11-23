package io.github.rkaushikdev.garterhole;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.composition.Analysis;
import io.github.rkaushikdev.garterhole.composition.TouchAnalysis;
import io.github.rkaushikdev.garterhole.method.Classifier;
import io.github.rkaushikdev.garterhole.method.LeadHeadCode;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.method.MethodSerialiser;
import io.github.rkaushikdev.garterhole.method.Title;
import io.github.rkaushikdev.garterhole.records.Performance;
import io.github.rkaushikdev.garterhole.records.PerformanceValidator;
import io.github.rkaushikdev.garterhole.store.Repository;
import io.github.rkaushikdev.garterhole.store.UnitOfWork;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Logger;
import java.util.List;
import java.util.Map;

/**
 * What the service can be asked to do.
 *
 * <p>Both the HTTP surface and the command line talk to this, and neither of
 * them talks to the repository directly. That is what stops the two drifting
 * apart: a rule added here, such as refusing a method whose title is already
 * taken, applies to both without anybody remembering to add it twice.
 *
 * <p>Documents go in and out as JSON trees. The domain classes are built when
 * something needs to be worked out and thrown away afterwards, which keeps the
 * stored shape independent of the classes.
 */
public final class Service {

    /** The collection the methods are filed in, by title. */
    public static final String METHODS = "methods";

    /** The collection the towers are filed in, by identifier. */
    public static final String TOWERS = "towers";

    /** The collection the performances are filed in, by identifier. */
    public static final String PERFORMANCES = "performances";

    private final Repository repository;
    private final Logger logger;

    /**
     * @param repository where the documents are kept
     * @throws ValidationException when there is no repository
     */
    public Service(Repository repository) {
        this(repository, Logger.silent());
    }

    /**
     * @param repository where the documents are kept
     * @param logger where the service says what it did
     * @throws ValidationException when there is no repository
     */
    public Service(Repository repository, Logger logger) {
        if (repository == null) {
            throw new ValidationException("a service needs a Repository");
        }
        this.repository = repository;
        this.logger = logger == null ? Logger.silent() : logger;
    }

    /**
     * What was filed, and what was worth mentioning about it.
     *
     * @param id the identifier the performance was filed under
     * @param warnings things that did not stop it being filed
     */
    public record Filed(String id, List<String> warnings) {}

    /**
     * How much the service holds, for the health route.
     *
     * @param methods how many methods
     * @param towers how many towers
     * @param performances how many performances
     */
    public record Counts(int methods, int towers, int performances) {}

    /**
     * @return the repository the service keeps its documents in
     */
    public Repository repository() {
        return repository;
    }

    /**
     * @return every method in the library, in title order
     */
    public List<JsonNode> listMethods() {
        return documentsIn(METHODS);
    }

    /**
     * @param title the method's title
     * @return its document
     * @throws NotFoundException when the library does not hold it
     */
    public JsonNode getMethod(String title) {
        return repository.find(METHODS, title).orElseThrow(() -> new NotFoundException("method", title));
    }

    /**
     * Add a method, working its title and class out from the notation.
     *
     * @param definition the name, stage and notation
     * @return the document filed
     * @throws ConflictException when the library already holds a method with that title
     */
    public ObjectNode addMethod(Method.Definition definition) {
        Method method = new Method(definition);
        String title = Title.of(method);
        if (repository.has(METHODS, title)) {
            throw new ConflictException("the library already holds " + Json.quote(title));
        }
        ObjectNode document = MethodSerialiser.methodToJson(method);
        repository.put(METHODS, title, document);
        logger.info("method added", Map.of("title", title));
        return document;
    }

    /**
     * @param title the method's title
     * @return true when there was such a method to remove
     */
    public boolean removeMethod(String title) {
        return repository.delete(METHODS, title);
    }

    /**
     * Ring a touch of a method the library holds and report on it.
     *
     * @param title the method's title
     * @param touch the touch, written either way
     * @return the report: title, length, kind, whether it comes round and is
     *     true, the proof summary, the calls, the parts, the music and whether
     *     the tenors stayed together; the analysis document with the proof
     *     cut down to whether it is true
     * @throws NotFoundException when the library does not hold the method
     */
    public ObjectNode analyse(String title, String touch) {
        Method method = MethodSerialiser.methodFromJson(getMethod(title));
        TouchAnalysis analysis = Analysis.analyseTouch(method, touch);
        ObjectNode report = Json.object();
        analysis.toJson().properties().forEach(field -> {
            if (field.getKey().equals("proof")) {
                report.put("true", analysis.proof().isTrue());
            } else {
                report.set(field.getKey(), field.getValue());
            }
        });
        return report;
    }

    /**
     * @param title the method's title
     * @return its document with the facts derived from its changes laid over
     *     it, the lead head code among them ({@code null} when it has none)
     * @throws NotFoundException when the library does not hold the method
     */
    public ObjectNode describeMethod(String title) {
        Method method = MethodSerialiser.methodFromJson(getMethod(title));
        ObjectNode described = MethodSerialiser.methodToJson(method);
        described.setAll(Classifier.classify(method).toJson());
        described.put("leadHeadCode", LeadHeadCode.codeOf(method).orElse(null));
        return described;
    }

    /**
     * @return every tower, in identifier order
     */
    public List<JsonNode> listTowers() {
        return documentsIn(TOWERS);
    }

    /**
     * @param id the tower's identifier
     * @return its document
     * @throws NotFoundException when there is no such tower
     */
    public JsonNode getTower(String id) {
        return repository.find(TOWERS, id).orElseThrow(() -> new NotFoundException("tower", id));
    }

    /**
     * File a tower, replacing one already filed under the identifier.
     *
     * @param id the tower's identifier
     * @param document the tower
     * @return the document filed
     * @throws ValidationException when the document is not an object
     */
    public JsonNode putTower(String id, JsonNode document) {
        if (document == null || !document.isObject()) {
            throw new ValidationException("a tower must be an object");
        }
        repository.put(TOWERS, id, document);
        return document;
    }

    /**
     * @return every performance, in identifier order
     */
    public List<JsonNode> listPerformances() {
        return documentsIn(PERFORMANCES);
    }

    /**
     * File a performance, checking it first and refusing a repeat.
     *
     * @param fields the performance, with its date written {@code 2025-05-18}
     * @return the identifier it was filed under and any warnings
     * @throws ValidationException when the performance does not add up, carrying every problem
     * @throws ConflictException when it is already filed
     */
    public Filed filePerformance(JsonNode fields) {
        Performance rung = Performance.fromDocument(fields);
        PerformanceValidator.Verdict verdict = PerformanceValidator.validatePerformance(rung);
        if (!verdict.ok()) {
            throw ValidationException.withIssues(String.join("; ", verdict.problems()), verdict.problems());
        }
        if (repository.has(PERFORMANCES, rung.id())) {
            throw new ConflictException("this performance is already filed as " + Json.quote(rung.id()));
        }
        return UnitOfWork.inUnitOfWork(repository, unit -> {
            ObjectNode document = Json.object();
            document.put("tower", rung.tower());
            document.put("date", rung.dateText());
            document.put("title", rung.title());
            document.put("changes", rung.changes());
            rung.band().forEach(document.putArray("band")::add);
            document.put("conductor", rung.conductor().orElse(null));
            document.put("composer", rung.composer().orElse(null));
            document.put("kind", rung.kind().label());
            unit.put(PERFORMANCES, rung.id(), document);
            logger.info("performance filed", Map.of("id", rung.id()));
            return new Filed(rung.id(), verdict.warnings());
        });
    }

    /**
     * @return how many methods, towers and performances the service holds
     */
    public Counts counts() {
        return new Counts(repository.count(METHODS), repository.count(TOWERS), repository.count(PERFORMANCES));
    }

    private List<JsonNode> documentsIn(String collection) {
        return repository.all(collection).stream()
                .map(Repository.Entry::document)
                .toList();
    }
}
