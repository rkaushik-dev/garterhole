package io.github.rkaushikdev.garterhole.striking;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.striking.Fault.Kind;
import io.github.rkaushikdev.garterhole.util.CollectionHelpers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Naming what went wrong.
 *
 * <p>A number tells a band how badly they rang. A name tells them what to do
 * about it, and the names ringers use describe the shape of the error rather
 * than its size: clipping is striking too soon after the bell in front, gapping
 * is leaving too long, drifting is being consistently on one side of where
 * you should be for a stretch of ringing, and clashing is striking so close
 * behind the bell in front that the two sound as one.
 */
public final class Faults {

    private Faults() {}

    /**
     * Find the faults in a piece of ringing with the default thresholds.
     *
     * @param errors the blow errors
     * @return the faults, in the order they were rung
     */
    public static List<Fault> findFaults(List<BlowError> errors) {
        return findFaults(errors, Thresholds.DEFAULT);
    }

    /**
     * Find the faults in a piece of ringing.
     *
     * @param errors the blow errors
     * @param limits how far out a blow has to be to be named
     * @return the faults, in the order they were rung
     * @throws ValidationException when there are no errors to look at
     */
    public static List<Fault> findFaults(List<BlowError> errors, Thresholds limits) {
        if (errors == null) {
            throw new ValidationException("a list of blow errors is needed to find faults in it");
        }
        List<Fault> faults = new ArrayList<>();
        BlowError previous = null;
        for (BlowError entry : errors) {
            blowFault(entry, previous, limits).ifPresent(faults::add);
            previous = entry;
        }
        faults.addAll(driftFaults(errors, limits));
        faults.sort(Comparator.comparingInt(Fault::rowIndex).thenComparingInt(Fault::place));
        return List.copyOf(faults);
    }

    /**
     * A run of blows by one bell that were all on the same side of where they
     * should have been. This is what a band hears as a bell being "in the wrong
     * place" even though no single blow is badly out.
     *
     * @param errors the blow errors
     * @param limits the drift threshold and the run it takes
     * @return one fault per run long enough to count, bell by bell
     */
    public static List<Fault> driftFaults(List<BlowError> errors, Thresholds limits) {
        Map<Integer, List<BlowError>> byBell = CollectionHelpers.groupBy(errors, BlowError::bell);
        List<Fault> faults = new ArrayList<>();
        byBell.forEach((bell, entries) -> {
            List<BlowError> run = new ArrayList<>();
            for (BlowError entry : entries) {
                boolean same = run.isEmpty()
                        || Math.signum(entry.error()) == Math.signum(run.get(0).error());
                boolean outOfPlace = Math.abs(entry.error()) >= limits.drift() && entry.error() != 0;
                if (outOfPlace && same) {
                    run.add(entry);
                    continue;
                }
                faults.addAll(emitDrift(bell, run, limits));
                run = new ArrayList<>();
                if (outOfPlace) {
                    run.add(entry);
                }
            }
            faults.addAll(emitDrift(bell, run, limits));
        });
        return faults;
    }

    /**
     * Count the faults of each kind.
     *
     * @param faults the faults
     * @return how many of each kind, every kind present even at zero
     */
    public static Map<Kind, Integer> countFaults(List<Fault> faults) {
        Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            counts.put(kind, 0);
        }
        for (Fault fault : faults) {
            counts.merge(fault.kind(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    /**
     * The faults each bell was responsible for.
     *
     * @param faults the faults
     * @return the faults of each bell, the treble first
     */
    public static Map<Integer, List<Fault>> faultsByBell(List<Fault> faults) {
        Map<Integer, List<Fault>> sorted = new TreeMap<>(CollectionHelpers.groupBy(faults, Fault::bell));
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    /**
     * A sentence describing a fault, for a report a ringer will actually read.
     *
     * @param fault the fault
     * @return the sentence
     */
    public static String describeFault(Fault fault) {
        long size = Math.round(Math.abs(fault.size()));
        int row = fault.rowIndex() + 1;
        return switch (fault.kind()) {
            case CLIP -> "bell " + fault.bell() + " clipped by " + size + "ms in row " + row;
            case GAP -> "bell " + fault.bell() + " left a " + size + "ms gap in row " + row;
            case DRIFT -> "bell " + fault.bell() + " drifted for " + size + " blows from row " + row;
            case CLASH ->
                "bell " + fault.bell() + " clashed with the bell in front, " + size + "ms apart in row " + row;
        };
    }

    private static List<Fault> emitDrift(int bell, List<BlowError> run, Thresholds limits) {
        if (run.size() < limits.driftRun()) {
            return List.of();
        }
        BlowError first = run.get(0);
        return List.of(new Fault(Kind.DRIFT, bell, first.rowIndex(), first.place(), run.size()));
    }

    /**
     * What one blow did wrong, judged against the blow struck before it and
     * against where it should have been. A clash is named before a clip,
     * because two bells sounding as one is what the band heard, whichever of
     * them was out.
     */
    private static Optional<Fault> blowFault(BlowError entry, BlowError previous, Thresholds limits) {
        double apart = previous == null ? Double.POSITIVE_INFINITY : entry.at() - previous.at();
        if (apart >= 0 && apart < limits.clash()) {
            return Optional.of(new Fault(Kind.CLASH, entry.bell(), entry.rowIndex(), entry.place(), apart));
        }
        if (entry.error() <= -limits.clip()) {
            return Optional.of(new Fault(Kind.CLIP, entry.bell(), entry.rowIndex(), entry.place(), entry.error()));
        }
        if (entry.error() >= limits.gap()) {
            return Optional.of(new Fault(Kind.GAP, entry.bell(), entry.rowIndex(), entry.place(), entry.error()));
        }
        return Optional.empty();
    }
}
