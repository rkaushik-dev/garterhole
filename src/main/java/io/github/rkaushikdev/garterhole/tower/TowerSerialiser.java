package io.github.rkaushikdev.garterhole.tower;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.tower.CompetencyRegister.Level;
import io.github.rkaushikdev.garterhole.tower.Ringer.Role;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.List;

/**
 * Towers and bands on disk.
 *
 * <p>A tower record is the thing most likely to be typed once and then carried
 * from machine to machine for twenty years, so the format is written down
 * plainly and every reader checks the version before it believes anything.
 *
 * <p>Weights are written the way a tower board writes them rather than as
 * pounds, because somebody will read the file with their own eyes and
 * {@code 23-2-11} is what they expect to see.
 */
public final class TowerSerialiser {

    /** The version stamped into every document written here. */
    public static final int FORMAT_VERSION = 1;

    /** The format a tower document says it is. */
    public static final String TOWER_FORMAT = "garterhole.tower";

    /** The format a band document says it is. */
    public static final String BAND_FORMAT = "garterhole.band";

    private TowerSerialiser() {}

    /**
     * A band and what it can ring.
     *
     * @param band the ringers
     * @param competency what they can ring
     */
    public record Band(List<Ringer> band, CompetencyRegister competency) {}

    /**
     * @param tower a tower
     * @return its document: format, version, id, name, place and bells, a
     *     missing place or bell detail written as {@code null}
     * @throws ValidationException when there is no tower
     */
    public static ObjectNode towerToJson(Tower tower) {
        if (tower == null) {
            throw new ValidationException("only a Tower can be serialised");
        }
        ObjectNode document = Json.object();
        document.put("format", TOWER_FORMAT);
        document.put("version", FORMAT_VERSION);
        document.put("id", tower.id());
        document.put("name", tower.name());
        document.put("place", tower.place().orElse(null));
        ArrayNode bells = document.putArray("bells");
        for (Bell bell : tower.bells()) {
            ObjectNode entry = bells.addObject();
            entry.put("number", bell.number());
            entry.put("weight", bell.weightText());
            entry.put("note", bell.note().orElse(null));
            entry.put("founder", bell.founder().orElse(null));
            if (bell.cast().isPresent()) {
                entry.put("cast", bell.cast().getAsInt());
            } else {
                entry.putNull("cast");
            }
        }
        return document;
    }

    /**
     * Read a tower document. A bell may give its weight as a board writes it or
     * in pounds.
     *
     * @param document the document
     * @return the tower
     * @throws ValidationException when the document is not a tower document this reads
     */
    public static Tower towerFromJson(JsonNode document) {
        requireDocument(document, TOWER_FORMAT);
        if (!document.path("bells").isArray()) {
            throw new ValidationException("a tower document needs its bells");
        }
        return towerFromFields(document);
    }

    /**
     * Read a tower from its fields alone, the way a request body or a stored
     * tower carries them, with no format or version in front. The bells may
     * give their weight as a board writes it, in pounds, or in kilograms as a
     * founder's certificate does. This is the one reader of a tower's bells,
     * which the HTTP surface and the command line both go through.
     *
     * @param fields the name, place and bells
     * @return the tower
     * @throws ValidationException when a bell cannot be read or the ring has a gap
     * @throws IllegalArgumentException when the name is missing or empty
     */
    public static Tower towerFromFields(JsonNode fields) {
        JsonNode bells = fields.path("bells");
        List<Bell> ring = new ArrayList<>();
        for (int index = 0; index < bells.size(); index += 1) {
            ring.add(bellFromJson(bells.get(index), index));
        }
        JsonNode name = fields.path("name");
        String place = DocumentFields.text(fields.path("place"));
        return new Tower(DocumentFields.isAbsent(name) ? "" : Json.templateText(name), place, ring);
    }

    /**
     * @param band the ringers
     * @param competency what they can ring
     * @return the band document: format, version, ringers and their repertoires
     * @throws ValidationException when there is no band or no register
     */
    public static ObjectNode bandToJson(List<Ringer> band, CompetencyRegister competency) {
        if (band == null) {
            throw new ValidationException("a band document needs a band");
        }
        if (competency == null) {
            throw new ValidationException("a band document needs a competency register");
        }
        ObjectNode document = Json.object();
        document.put("format", BAND_FORMAT);
        document.put("version", FORMAT_VERSION);
        ArrayNode ringers = document.putArray("ringers");
        for (Ringer member : band) {
            ObjectNode entry = ringers.addObject();
            entry.put("id", member.id());
            entry.put("name", member.name());
            if (member.heaviestBellPounds().isPresent()) {
                Json.putNumber(
                        entry, "heaviestBellPounds", member.heaviestBellPounds().getAsDouble());
            } else {
                entry.putNull("heaviestBellPounds");
            }
            ArrayNode roles = entry.putArray("roles");
            member.roleLabels().forEach(roles::add);
            entry.put("notes", member.notes());
        }
        ArrayNode repertoires = document.putArray("competency");
        for (Ringer member : band) {
            ObjectNode entry = repertoires.addObject();
            entry.put("ringer", member.id());
            ArrayNode methods = entry.putArray("methods");
            for (CompetencyRegister.Entry method : competency.repertoireOf(member.id())) {
                ObjectNode written = methods.addObject();
                written.put("title", method.title());
                written.put("level", method.level().label());
            }
        }
        return document;
    }

    /**
     * @param document a band document
     * @return the band and what it can ring
     * @throws ValidationException when the document is not a band document this reads
     */
    public static Band bandFromJson(JsonNode document) {
        requireDocument(document, BAND_FORMAT);
        JsonNode ringers = document.path("ringers");
        if (!ringers.isArray()) {
            throw new ValidationException("a band document needs its ringers");
        }
        List<Ringer> band = new ArrayList<>();
        for (JsonNode entry : ringers) {
            JsonNode name = entry.path("name");
            band.add(new Ringer(
                    DocumentFields.isAbsent(name) ? "" : Json.templateText(name),
                    new Ringer.Details(
                            DocumentFields.text(entry.path("id")),
                            heaviestOf(entry.path("heaviestBellPounds")),
                            rolesOf(entry.path("roles")),
                            DocumentFields.text(entry.path("notes")))));
        }
        CompetencyRegister competency = new CompetencyRegister();
        for (JsonNode entry : DocumentFields.elements(document.path("competency"))) {
            for (JsonNode method : DocumentFields.elements(entry.path("methods"))) {
                competency.record(
                        DocumentFields.text(entry.path("ringer")),
                        DocumentFields.text(method.path("title")),
                        Level.of(DocumentFields.text(method.path("level"))));
            }
        }
        return new Band(List.copyOf(band), competency);
    }

    private static Bell bellFromJson(JsonNode entry, int index) {
        if (entry == null || !entry.isContainerNode()) {
            throw new ValidationException("bell " + index + " is not an object");
        }
        Bell.Details details = new Bell.Details(
                DocumentFields.text(entry.path("note")),
                DocumentFields.text(entry.path("founder")),
                castOf(entry.path("cast")));
        JsonNode weight = entry.path("weight");
        if (weight.isTextual()) {
            return Bell.weighing(numberOf(entry.path("number")), weight.textValue(), details);
        }
        JsonNode weightPounds = entry.path("weightPounds");
        if (weightPounds.isNumber()) {
            return new Bell(numberOf(entry.path("number")), weightPounds.doubleValue(), details);
        }
        JsonNode weightKg = entry.path("weightKg");
        if (weightKg.isNumber()) {
            return Bell.weighingKilograms(numberOf(entry.path("number")), weightKg.doubleValue(), details);
        }
        String named =
                entry.path("number").isNumber() ? Json.templateText(entry.path("number")) : String.valueOf(index);
        throw new ValidationException("bell " + named + " does not say what it weighs");
    }

    private static int numberOf(JsonNode number) {
        if (number.isNumber() && number.doubleValue() == Math.rint(number.doubleValue())) {
            return number.intValue();
        }
        throw new ValidationException(Json.templateText(number) + " is not a bell number");
    }

    private static Integer castOf(JsonNode cast) {
        if (DocumentFields.isAbsent(cast)) {
            return null;
        }
        if (cast.isNumber() && cast.doubleValue() == Math.rint(cast.doubleValue())) {
            return cast.intValue();
        }
        throw new ValidationException(Json.templateText(cast) + " is not a year a bell was cast in");
    }

    private static Double heaviestOf(JsonNode heaviest) {
        if (DocumentFields.isAbsent(heaviest)) {
            return null;
        }
        if (heaviest.isNumber()) {
            return heaviest.doubleValue();
        }
        throw new ValidationException(Json.templateText(heaviest) + " is not a bell weight a ringer can handle");
    }

    private static List<Role> rolesOf(JsonNode roles) {
        if (DocumentFields.isAbsent(roles)) {
            return List.of();
        }
        if (!roles.isArray()) {
            throw new ValidationException("a ringer needs a list of roles");
        }
        List<Role> read = new ArrayList<>();
        for (JsonNode role : roles) {
            if (!role.isTextual()) {
                throw new ValidationException(Json.write(role).orElse("undefined") + " is not a role in a tower");
            }
            read.add(Role.of(role.textValue()));
        }
        return read;
    }

    private static void requireDocument(JsonNode document, String format) {
        if (document == null || !document.isContainerNode()) {
            throw new ValidationException("a " + format + " document must be an object");
        }
        JsonNode found = document.path("format");
        if (!found.isTextual() || !found.textValue().equals(format)) {
            throw new ValidationException("this reads " + format + " documents and that one says "
                    + Json.write(found).orElse("undefined"));
        }
        JsonNode version = document.path("version");
        if (!version.isNumber() || version.doubleValue() != FORMAT_VERSION) {
            throw new ValidationException("this reads version " + FORMAT_VERSION + " and that document is version "
                    + Json.templateText(version));
        }
    }
}
