package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.GarterholeException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Stage;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Method titles.
 *
 * <p>A method title is its name, then its class, then the name of its stage:
 * {@code Cambridge Surprise Major}. The class is left out for a principle,
 * which is why {@code Stedman Triples} has only two words of its own.
 *
 * <p>Reading a title back apart is worth doing carefully, because the name can
 * contain any word at all. {@code Double Norwich Court Bob Major} has a class
 * word in the middle of its name and a different one at the end.
 */
public final class Title {

    /** The class words, longest first so that Treble Bob is matched before Bob. */
    private static final List<String> CLASS_WORDS = List.of(
            "Treble Place", "Treble Bob", "Slow Course", "Surprise", "Delight", "Alliance", "Place", "Hybrid", "Bob");

    /** Words that can sit in front of the class word. */
    private static final List<String> MODIFIERS = List.of("Differential", "Little");

    private static final String PRINCIPLE = "Principle";

    private static final String SPLICED = "Spliced";

    /** The method count a report writes after a spliced title, {@code (8m)} or {@code (8 methods)}. */
    private static final Pattern METHOD_COUNT = Pattern.compile("\\s*\\((\\d+)\\s*(?:m|methods?)\\)$");

    private Title() {}

    /**
     * A title read apart.
     *
     * @param name the name, which may be empty for a title such as {@code Little Bob Major}
     * @param className the class with its modifiers, or {@code Principle} when there is none
     * @param stage the stage the title ends with
     */
    public record Parts(String name, String className, int stage) {}

    /**
     * Build a title from its parts.
     *
     * @param name the name, or {@code null} for none
     * @param className the class, {@code Principle} or {@code null} to leave it out
     * @param stage the stage
     * @return the title
     * @throws ValidationException when there is neither a name nor a class
     * @throws IllegalArgumentException when the stage has no name
     */
    public static String build(String name, String className, int stage) {
        String squashedName = Text.squash(name == null ? "" : name);
        String squashedClass = Text.squash(className == null ? "" : className);
        boolean hasClass = !squashedClass.isEmpty() && !PRINCIPLE.equals(squashedClass);
        if (squashedName.isEmpty() && !hasClass) {
            throw new ValidationException("a title needs a name or a class");
        }
        List<String> words = new ArrayList<>();
        if (!squashedName.isEmpty()) {
            words.add(squashedName);
        }
        if (hasClass) {
            words.add(squashedClass);
        }
        words.add(Stage.name(stage));
        return String.join(" ", words);
    }

    /**
     * Build the title of a principle, or of anything else with no class.
     *
     * @param name the name
     * @param stage the stage
     * @return the title
     */
    public static String build(String name, int stage) {
        return build(name, null, stage);
    }

    /**
     * The title a method should have, worked out from its own changes.
     *
     * @param method a method
     * @return its title
     */
    public static String of(Method method) {
        return build(method.name(), Classifier.classify(method).className(), method.stage());
    }

    /**
     * Read a title apart. The title is read a word at a time from the end: a
     * spliced title's method count first, written {@code (8m)} or
     * {@code (8 methods)}, then the stage, then any class word and the
     * modifiers in front of it, and whatever is left is the name. Some titles
     * have no name of their own at all, {@code Little Bob Major} being one,
     * and those give back an empty name rather than an error. A spliced title
     * reads like any other: {@code Spliced Surprise Major (8m)} has the name
     * {@code Spliced}.
     *
     * @param title the title
     * @return its parts
     * @throws ValidationException when the title is blank, does not end with a stage,
     *     or is nothing but a stage name
     */
    public static Parts parse(String title) {
        String text =
                METHOD_COUNT.matcher(Text.squash(title == null ? "" : title)).replaceFirst("");
        if (text.isEmpty()) {
            throw new ValidationException("a title cannot be blank");
        }
        List<String> words = new ArrayList<>(List.of(text.split(" ")));
        String last = words.remove(words.size() - 1);
        OptionalInt stage = stageNamed(last);
        if (stage.isEmpty()) {
            if (words.isEmpty() && isBareStage(last)) {
                throw new ValidationException(Json.quote(title) + " is nothing but a stage name");
            }
            throw new ValidationException(Json.quote(title) + " does not end with the name of a stage");
        }
        List<String> className = classWordsAtTheEnd(words);
        if (words.isEmpty() && className.isEmpty()) {
            throw new ValidationException(Json.quote(title) + " is nothing but a stage name");
        }
        return new Parts(
                String.join(" ", words),
                className.isEmpty() ? PRINCIPLE : String.join(" ", className),
                stage.getAsInt());
    }

    /**
     * @param title a title
     * @return its parts, or nothing when it does not have the shape a title should have
     */
    public static Optional<Parts> tryParse(String title) {
        try {
            return Optional.of(parse(title));
        } catch (GarterholeException notATitle) {
            return Optional.empty();
        }
    }

    /**
     * @param title a title
     * @return true when the title has the shape a title should have
     */
    public static boolean isWellFormed(String title) {
        return tryParse(title).isPresent();
    }

    /**
     * How many methods a spliced title says were rung, from the count a
     * report writes after it.
     *
     * @param title a title, for example {@code Spliced Surprise Major (8m)}
     * @return the count, or nothing when the title gives none
     */
    public static OptionalInt methodCount(String title) {
        Matcher found = METHOD_COUNT.matcher(Text.squash(title == null ? "" : title));
        return found.find() ? OptionalInt.of(Integer.parseInt(found.group(1))) : OptionalInt.empty();
    }

    /**
     * @param title a title
     * @return true when it is a title of spliced: it says so, or it counts more than one method
     */
    public static boolean isSpliced(String title) {
        boolean named = tryParse(title)
                .map(parts -> parts.name().equals(SPLICED) || parts.name().startsWith(SPLICED + " "))
                .orElse(false);
        return named || methodCount(title).orElse(1) > 1;
    }

    private static OptionalInt stageNamed(String word) {
        for (int stage : Stage.supported()) {
            if (Stage.name(stage).equals(word)) {
                return OptionalInt.of(stage);
            }
        }
        return OptionalInt.empty();
    }

    private static boolean isBareStage(String word) {
        try {
            Stage.fromName(word);
            return true;
        } catch (IllegalArgumentException notAStage) {
            return false;
        }
    }

    /**
     * Take the class words and their modifiers off the end of the words, and
     * give them back in order. A two word class is tried before a one word
     * one, so that Treble Bob is not read as Bob.
     */
    private static List<String> classWordsAtTheEnd(List<String> words) {
        LinkedList<String> found = new LinkedList<>();
        for (String word : CLASS_WORDS) {
            List<String> wanted = List.of(word.split(" "));
            int from = words.size() - wanted.size();
            if (from >= 0 && words.subList(from, words.size()).equals(wanted)) {
                found.addAll(wanted);
                words.subList(from, words.size()).clear();
                break;
            }
        }
        while (!found.isEmpty() && !words.isEmpty() && MODIFIERS.contains(words.get(words.size() - 1))) {
            found.addFirst(words.remove(words.size() - 1));
        }
        return found;
    }
}
