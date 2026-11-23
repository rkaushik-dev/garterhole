package io.github.rkaushikdev.garterhole.core;

import io.github.rkaushikdev.garterhole.util.Guards;
import java.util.ArrayList;
import java.util.List;

/**
 * The coursing order of a lead head: the order the working bells follow each
 * other round the plain hunt.
 *
 * <p>A composer thinks in coursing orders rather than rows. Every lead head of
 * a plain course has the same one, so it names the course; a call changes it,
 * so it shows what the call did; and it says at a glance whether the tenors
 * are still coursing each other, which is most of what makes a composition
 * musical. On eight, rounds courses {@code 8 7 5 3 2 4 6}, and a composer
 * writes that as {@code 53246} because the tenors are where they belong.
 *
 * <p>The order is read off a row with the treble leading, from the even places
 * going up the row and then the odd places coming back down. It is cyclic, so
 * it is always written from the tenor round.
 *
 * @param bells the working bells in coursing order, the tenor first
 */
public record CoursingOrder(List<Integer> bells) {

    /**
     * @param bells the working bells in coursing order, the tenor first
     */
    public CoursingOrder {
        bells = List.copyOf(bells);
    }

    /**
     * Read the coursing order of a row.
     *
     * @param row a row with the treble leading, such as a lead head or a course head
     * @return its coursing order
     * @throws IllegalArgumentException when there is no row, or the treble is not leading
     */
    public static CoursingOrder of(Row row) {
        Guards.present(row, "a Row is needed to read a coursing order from");
        if (row.bellAt(1) != Bells.TREBLE) {
            throw new IllegalArgumentException(
                    "a coursing order is read with the treble leading, and " + row + " does not have it there");
        }
        int stage = row.stage();
        List<Integer> places = new ArrayList<>(Bells.evenBells(stage));
        List<Integer> odds = new ArrayList<>(Bells.oddBells(stage));
        odds.remove(Integer.valueOf(1));
        for (int index = odds.size() - 1; index >= 0; index -= 1) {
            places.add(odds.get(index));
        }
        List<Integer> read = new ArrayList<>(places.size());
        for (int place : places) {
            read.add(row.bellAt(place));
        }
        return new CoursingOrder(fromTenor(read, stage));
    }

    /**
     * @param stage a supported stage
     * @return the coursing order of rounds, which is every plain course of Plain Bob
     */
    public static CoursingOrder natural(int stage) {
        return of(Row.rounds(stage));
    }

    /**
     * True when the heaviest bells course each other the way they do in
     * rounds. That is what a composer means by the tenors being together, and
     * it holds for a course whose lead heads move the tenors about as long as
     * they keep following each other.
     *
     * @param tenors how many of the back bells to look at, two or more
     * @return true when they sit in the same block as in the natural coursing order
     * @throws IllegalArgumentException when fewer than two tenors or more bells than there are are asked about
     */
    public boolean tenorsTogether(int tenors) {
        int stage = bells.size() + 1;
        if (tenors < 2 || tenors > bells.size()) {
            throw new IllegalArgumentException(tenors + " tenors cannot be looked at on " + stage + " bells");
        }
        List<Integer> wanted = tenorBlock(natural(stage).bells, stage, tenors);
        List<Integer> found = tenorBlock(bells, stage, tenors);
        return wanted.equals(found);
    }

    /**
     * The coursing order the way a composer writes it, with the tenors left
     * off: the bells that follow the tenors, in order, round to the ones that
     * lead into them. On eight with the tenors together, rounds is {@code 53246}.
     *
     * @param tenors how many of the back bells to leave off
     * @return the rest of the order, as bell symbols
     * @throws IllegalArgumentException when the tenors are not together, since
     *     leaving them off would hide where they are
     */
    public String withoutTenors(int tenors) {
        if (!tenorsTogether(tenors)) {
            throw new IllegalArgumentException("the tenors are parted in " + this + ", so they cannot be left off");
        }
        int size = bells.size();
        int lightest = size + 2 - tenors;
        int start = bells.indexOf(size + 1);
        int steps = 0;
        while (steps < size && bells.get((start + steps) % size) >= lightest) {
            steps += 1;
        }
        List<Integer> rest = new ArrayList<>();
        for (int step = steps; step < size; step += 1) {
            int bell = bells.get((start + step) % size);
            if (bell < lightest) {
                rest.add(bell);
            }
        }
        return symbols(rest);
    }

    /**
     * @return the order written from the tenor round, for example {@code 8753246}
     */
    @Override
    public String toString() {
        return symbols(bells);
    }

    /**
     * The first unbroken run of tenors in the order, starting where one comes
     * straight after a lighter bell. When the tenors are together that is all
     * of them; when they are parted it is fewer.
     */
    private static List<Integer> tenorBlock(List<Integer> order, int stage, int tenors) {
        int lightest = stage - tenors + 1;
        int size = order.size();
        int start = 0;
        for (int index = 0; index < size; index += 1) {
            if (order.get(index) >= lightest && order.get(Math.floorMod(index - 1, size)) < lightest) {
                start = index;
                break;
            }
        }
        List<Integer> block = new ArrayList<>();
        for (int step = 0; step < size && order.get((start + step) % size) >= lightest; step += 1) {
            block.add(order.get((start + step) % size));
        }
        return block;
    }

    private static List<Integer> fromTenor(List<Integer> read, int stage) {
        int at = read.indexOf(stage);
        List<Integer> rotated = new ArrayList<>(read.size());
        for (int step = 0; step < read.size(); step += 1) {
            rotated.add(read.get((at + step) % read.size()));
        }
        return rotated;
    }

    private static String symbols(List<Integer> order) {
        StringBuilder text = new StringBuilder(order.size());
        order.forEach(bell -> text.append(Bells.symbol(bell)));
        return text.toString();
    }
}
