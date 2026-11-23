package io.github.rkaushikdev.garterhole.tower;

import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Stage;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A tower: a ring of bells in a building, with a name and a place.
 *
 * <p>The bells are numbered from the treble and there are no gaps in the
 * numbering, which is checked rather than assumed. A tower record with bells
 * one, two and four in it is a data entry mistake, and finding it here saves
 * the assignment solver from producing a plan nobody can ring.
 */
public final class Tower {

    private static final int SMALLEST_STAGE = 3;

    private final String name;
    private final String place;
    private final List<Bell> bells;
    private final String ringingRoom;

    /**
     * What a tower is built from.
     *
     * @param name what the tower is called, for example the church's dedication
     * @param place where it is, or {@code null} when that is not given
     * @param bells the ring, in any order
     * @param ringingRoom where the ringing room is, or {@code null}
     */
    public record Definition(String name, String place, List<Bell> bells, String ringingRoom) {}

    /**
     * A tower with no place given.
     *
     * @param name what the tower is called
     * @param bells the ring, in any order
     */
    public Tower(String name, List<Bell> bells) {
        this(new Definition(name, null, bells, null));
    }

    /**
     * @param name what the tower is called
     * @param place where it is, or {@code null} when that is not given
     * @param bells the ring, in any order
     */
    public Tower(String name, String place, List<Bell> bells) {
        this(new Definition(name, place, bells, null));
    }

    /**
     * @param definition the name, place, bells and ringing room
     * @throws ValidationException when there are no bells or the ring has a gap
     * @throws IllegalArgumentException when the name is missing or empty
     */
    public Tower(Definition definition) {
        if (definition == null) {
            throw new ValidationException("a tower needs a definition object");
        }
        Guards.nonEmptyString(definition.name(), "a tower name");
        if (definition.bells() == null || definition.bells().isEmpty()) {
            throw new ValidationException("a tower needs at least one bell");
        }
        for (int index = 0; index < definition.bells().size(); index += 1) {
            if (definition.bells().get(index) == null) {
                throw new ValidationException("entry " + index + " of the ring is not a bell");
            }
        }
        List<Bell> sorted = new ArrayList<>(definition.bells());
        sorted.sort(Comparator.comparingInt(Bell::number));
        for (int index = 0; index < sorted.size(); index += 1) {
            if (sorted.get(index).number() != index + 1) {
                throw new ValidationException("the ring jumps from bell " + index + " to bell "
                        + sorted.get(index).number() + ", so it has a gap");
            }
        }
        this.name = Text.trim(definition.name());
        this.place = definition.place() == null ? null : Text.trim(definition.place());
        this.bells = List.copyOf(sorted);
        this.ringingRoom = definition.ringingRoom();
    }

    /**
     * @return what the tower is called
     */
    public String name() {
        return name;
    }

    /**
     * @return where it is, when that was given
     */
    public Optional<String> place() {
        return Optional.ofNullable(place);
    }

    /**
     * @return the bells, treble first, as a list that cannot be changed
     */
    public List<Bell> bells() {
        return bells;
    }

    /**
     * @return where the ringing room is, when that was given
     */
    public Optional<String> ringingRoom() {
        return Optional.ofNullable(ringingRoom);
    }

    /**
     * @return how many bells hang in the tower
     */
    public int size() {
        return bells.size();
    }

    /**
     * @return the tenor, which is the heaviest and lowest bell
     */
    public Bell tenor() {
        return bells.get(bells.size() - 1);
    }

    /**
     * @return the treble
     */
    public Bell treble() {
        return bells.get(0);
    }

    /**
     * @return what the whole ring weighs, in pounds
     */
    public double totalWeightPounds() {
        return bells.stream().mapToDouble(Bell::weightPounds).sum();
    }

    /**
     * @param number a bell number
     * @return the bell with that number
     * @throws NotFoundException when the tower has no such bell
     */
    public Bell bell(int number) {
        if (number < 1 || number > bells.size()) {
            throw new NotFoundException("bell", String.valueOf(number));
        }
        return bells.get(number - 1);
    }

    /**
     * @param stage a number of bells
     * @return true when a method at this stage can be rung here at all
     */
    public boolean canRing(int stage) {
        return Stage.isSupported(stage) && size() >= stage;
    }

    /**
     * True when the tower has a spare bell to cover behind an odd stage. Bands
     * prefer to cover, but a tower of exactly the stage can still ring it, so
     * this is a separate question from whether the stage is possible.
     *
     * @param stage a number of bells
     * @return true when there is a bell spare to cover with
     */
    public boolean canCover(int stage) {
        return canRing(stage) && Stage.hasCover(stage) && size() >= Stage.bellsInTower(stage);
    }

    /**
     * @return the stages this tower can ring, largest first, which is what a
     *     practice planner works down
     */
    public List<Integer> stages() {
        List<Integer> stages = new ArrayList<>();
        for (int stage = size(); stage >= SMALLEST_STAGE; stage -= 1) {
            if (canRing(stage)) {
                stages.add(stage);
            }
        }
        return List.copyOf(stages);
    }

    /**
     * @return the largest stage this tower can ring, or nought when it can ring none
     */
    public int largestStage() {
        return stages().stream().findFirst().orElse(0);
    }

    /**
     * @return an identifier safe in a URL, built from the place and the name
     */
    public String id() {
        return Text.slugify(place == null ? name : place + " " + name);
    }

    @Override
    public String toString() {
        String where = place == null ? "" : ", " + place;
        return name + where + ": " + size() + " bells, tenor " + tenor().weightText();
    }

    /**
     * How the tower would describe itself in a directory: the key of the ring
     * when the tenor's note is known, and a warning when some bells are off the
     * scale, because that is the first thing a visiting band asks about. A
     * tower of one or two bells says so rather than naming a stage it cannot ring.
     *
     * @return the description
     */
    public String describe() {
        StringBuilder text = new StringBuilder(toString());
        Tuning.keyOf(this)
                .ifPresent(key -> text.append(" in ").append(tenor().note().orElse(key.sharpName())));
        if (largestStage() == 0) {
            text.append(", too few bells to ring changes on");
        } else {
            text.append(", ringing up to ").append(Stage.name(largestStage()));
        }
        List<Tuning.Discord> discords = Tuning.discords(this);
        if (!discords.isEmpty()) {
            text.append(", ").append(Text.plural(discords.size(), "bell")).append(" off the scale");
        }
        return text.toString();
    }
}
