package io.github.rkaushikdev.garterhole.records;

import static io.github.rkaushikdev.garterhole.util.Text.slugify;
import static io.github.rkaushikdev.garterhole.util.Text.squash;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.Lengths;
import io.github.rkaushikdev.garterhole.method.Title;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Json;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A performance: a peal, a quarter peal or a touch that somebody wrote down.
 *
 * <p>The record is what goes on the peal board and into the association report,
 * so it holds the band in bell order, the date, the length and the method. What
 * it does not hold is any judgement about whether the performance counts,
 * because that depends on rules that change and is worked out separately by
 * {@link PerformanceValidator}.
 *
 * <p>A performance on handbells is written the same way, but each ringer holds
 * two bells, so the band lists one name for each pair: the first ringer has
 * the treble and the two, the second the three and the four, and so on.
 *
 * <p>Performances are immutable. Names are squashed to single spaces as they come
 * in, so that a ringer typed with a stray space is still the same ringer.
 */
public final class Performance {

    private final String tower;
    private final LocalDate date;
    private final String title;
    private final int changes;
    private final List<String> band;
    private final String conductor;
    private final String composer;
    private final Integer duration;
    private final List<String> footnotes;
    private final boolean handbells;

    private Performance(Builder definition) {
        Guards.nonEmptyString(definition.tower, "a tower name");
        Guards.nonEmptyString(definition.title, "a method title");
        if (definition.date == null) {
            throw new ValidationException("a performance needs the date it was rung");
        }
        if (definition.changes < 1) {
            throw new ValidationException(definition.changes + " is not a number of changes");
        }
        if (definition.band == null || definition.band.isEmpty()) {
            throw new ValidationException("a performance needs a band");
        }
        List<String> squashed = new ArrayList<>(definition.band.size());
        for (int index = 0; index < definition.band.size(); index += 1) {
            squashed.add(
                    squash(Guards.nonEmptyString(definition.band.get(index), "the ringer of bell " + (index + 1))));
        }
        if (definition.conductor != null && !squashed.contains(squash(definition.conductor))) {
            throw new ValidationException("the conductor " + Json.quote(definition.conductor) + " is not in the band");
        }
        if (definition.duration != null && definition.duration < 1) {
            throw new ValidationException(definition.duration + " is not a length of time in minutes");
        }
        this.tower = squash(definition.tower);
        this.date = definition.date;
        this.title = squash(definition.title);
        this.changes = definition.changes;
        this.band = List.copyOf(squashed);
        this.conductor = definition.conductor == null ? null : squash(definition.conductor);
        this.composer = definition.composer == null ? null : squash(definition.composer);
        this.duration = definition.duration;
        this.footnotes = List.copyOf(definition.footnotes);
        this.handbells = definition.handbells;
    }

    /**
     * @return a builder for a performance, taking the fields a form collects
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Build a performance from a document: a request body, or a record read back
     * out of the store. The date is written the way a form sends it,
     * {@code 2025-05-18}. A field that is missing or {@code null} is not given,
     * because a record read back out of the store carries {@code null} where a
     * field was missing, and treating that as a value rather than as an absence
     * is how a performance with no conductor comes back refusing to be rebuilt.
     *
     * @param document the fields of the performance
     * @return the performance
     * @throws ValidationException when the document does not describe a performance
     */
    public static Performance fromDocument(JsonNode document) {
        return PerformanceDocument.read(document);
    }

    /**
     * @return the tower it was rung at
     */
    public String tower() {
        return tower;
    }

    /**
     * @return the day it was rung
     */
    public LocalDate date() {
        return date;
    }

    /**
     * @return the method title, for example {@code Plain Bob Minor}
     */
    public String title() {
        return title;
    }

    /**
     * @return how many changes were rung
     */
    public int changes() {
        return changes;
    }

    /**
     * @return the band in bell order, treble first
     */
    public List<String> band() {
        return band;
    }

    /**
     * @return who conducted, when that is recorded
     */
    public Optional<String> conductor() {
        return Optional.ofNullable(conductor);
    }

    /**
     * @return who composed it, when that is recorded
     */
    public Optional<String> composer() {
        return Optional.ofNullable(composer);
    }

    /**
     * @return how many minutes it took, when that is recorded
     */
    public OptionalInt duration() {
        return duration == null ? OptionalInt.empty() : OptionalInt.of(duration);
    }

    /**
     * @return the lines written under the band, possibly none
     */
    public List<String> footnotes() {
        return footnotes;
    }

    /**
     * @return true when it was rung on handbells, two bells to each ringer
     */
    public boolean handbells() {
        return handbells;
    }

    /**
     * @return the stage, read out of the method title
     * @throws ValidationException when the title cannot be read
     */
    public int stage() {
        return Title.parse(title).stage();
    }

    /**
     * @return what the length counts as
     * @throws ValidationException when the title cannot be read
     */
    public Lengths.Kind kind() {
        return Lengths.classifyLength(changes, stage());
    }

    /**
     * @return how many rang, which is not always the stage: an odd stage has a
     *     cover, and on handbells each ringer has two bells
     */
    public int bandSize() {
        return band.size();
    }

    /**
     * @return the date as it is written on a peal board, for example {@code 2025-05-18}
     */
    public String dateText() {
        return date.toString();
    }

    /**
     * A stable identifier, built from what was rung, so that the same
     * performance filed twice is caught.
     *
     * @return the identifier
     */
    public String id() {
        return slugify(dateText() + " " + tower + " " + title + " " + changes);
    }

    /**
     * @param bell a bell
     * @return the ringer of the bell, who on handbells holds it with its pair
     * @throws ValidationException when nobody rang that bell
     */
    public String ringerOf(int bell) {
        int bells = handbells ? band.size() * 2 : band.size();
        if (bell < 1 || bell > bells) {
            throw new ValidationException("bell " + bell + " was not rung in this performance");
        }
        return band.get(handbells ? (bell - 1) / 2 : bell - 1);
    }

    /**
     * @param name a ringer
     * @return the bell they rang, or the lower of the pair on handbells, when they were in the band
     */
    public OptionalInt bellOf(String name) {
        List<Integer> bells = bellsOf(name);
        return bells.isEmpty() ? OptionalInt.empty() : OptionalInt.of(bells.get(0));
    }

    /**
     * @param name a ringer
     * @return the bells they rang, one in a tower and a pair on handbells, none
     *     when they were not in the band
     */
    public List<Integer> bellsOf(String name) {
        int index = band.indexOf(squash(name));
        if (index == -1) {
            return List.of();
        }
        return handbells ? List.of(2 * index + 1, 2 * index + 2) : List.of(index + 1);
    }

    @Override
    public String toString() {
        return tower + ", " + dateText() + ": " + changes + " " + title;
    }

    /**
     * The fields of a performance, as a form collects them. The tower, date,
     * title, length and band are needed; the rest may be left out.
     */
    public static final class Builder {
        private String tower;
        private LocalDate date;
        private String title;
        private int changes;
        private List<String> band;
        private String conductor;
        private String composer;
        private Integer duration;
        private List<String> footnotes = List.of();
        private boolean handbells;

        private Builder() {}

        /**
         * @param name the tower it was rung at
         * @return this builder
         */
        public Builder tower(String name) {
            this.tower = name;
            return this;
        }

        /**
         * @param day the day it was rung
         * @return this builder
         */
        public Builder date(LocalDate day) {
            this.date = day;
            return this;
        }

        /**
         * @param method the method title
         * @return this builder
         */
        public Builder title(String method) {
            this.title = method;
            return this;
        }

        /**
         * @param count how many changes were rung
         * @return this builder
         */
        public Builder changes(int count) {
            this.changes = count;
            return this;
        }

        /**
         * @param ringers the band in bell order
         * @return this builder
         */
        public Builder band(List<String> ringers) {
            this.band = ringers == null ? null : new ArrayList<>(ringers);
            return this;
        }

        /**
         * @param ringers the band in bell order
         * @return this builder
         */
        public Builder band(String... ringers) {
            return band(Arrays.asList(ringers));
        }

        /**
         * @param name who conducted, or {@code null} when nobody is recorded
         * @return this builder
         */
        public Builder conductor(String name) {
            this.conductor = name;
            return this;
        }

        /**
         * @param name who composed it, or {@code null} when nobody is recorded
         * @return this builder
         */
        public Builder composer(String name) {
            this.composer = name;
            return this;
        }

        /**
         * @param minutes how long it took, or {@code null} when that is not recorded
         * @return this builder
         */
        public Builder duration(Integer minutes) {
            this.duration = minutes;
            return this;
        }

        /**
         * @param lines the lines written under the band
         * @return this builder
         */
        public Builder footnotes(List<String> lines) {
            this.footnotes = lines == null ? List.of() : List.copyOf(lines);
            return this;
        }

        /**
         * @param inHand true when it was rung on handbells, two bells to each ringer
         * @return this builder
         */
        public Builder handbells(boolean inHand) {
            this.handbells = inHand;
            return this;
        }

        /**
         * @return the performance
         * @throws ValidationException when a field is missing or does not make sense
         * @throws IllegalArgumentException when a name that must be given is blank
         */
        public Performance build() {
            return new Performance(this);
        }
    }
}
