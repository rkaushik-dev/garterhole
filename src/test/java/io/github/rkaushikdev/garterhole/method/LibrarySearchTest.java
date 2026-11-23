package io.github.rkaushikdev.garterhole.method;

import static io.github.rkaushikdev.garterhole.method.LibrarySearch.countByClass;
import static io.github.rkaushikdev.garterhole.method.LibrarySearch.countByStage;
import static io.github.rkaushikdev.garterhole.method.LibrarySearch.search;
import static io.github.rkaushikdev.garterhole.method.LibrarySearch.spliceGroups;
import static io.github.rkaushikdev.garterhole.method.LibrarySearch.summarise;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.method.LibrarySearch.Query;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LibrarySearchTest {

    private MethodLibrary library;

    private static Method cambridge() {
        return Method.of("Cambridge", 8, "x38x14x1258x36x14x58x16x78,12");
    }

    private static Method yorkshire() {
        return Method.of("Yorkshire", 8, "x38x14x58x16x12x38x14x78,12");
    }

    private static Method bristol() {
        return Method.of("Bristol", 8, "x58x14.58x58.36.14x14.58x14x18,18");
    }

    private static Method plainBobMajor() {
        return Method.of("Plain", 8, "x18x18x18x18,12");
    }

    private static Method plainBobMinor() {
        return Method.of("Plain", 6, "x1x1x1,2");
    }

    private static Method littleBob() {
        return Method.of("Little", 8, "x18x14x18x12");
    }

    private static List<String> names(List<Method> methods) {
        return methods.stream().map(Method::name).toList();
    }

    @BeforeEach
    void fillALibrary() {
        library = new MethodLibrary();
        library.addAll(List.of(cambridge(), yorkshire(), bristol(), plainBobMajor(), plainBobMinor(), littleBob()));
    }

    @Nested
    class Search {

        @Test
        void givesEverythingWhenNothingIsAskedFor() {
            assertThat(search(library)).hasSize(6);
        }

        @Test
        void filtersByStage() {
            assertThat(names(search(library, Query.all().stage(6)))).containsExactly("Plain");
            assertThat(search(library, Query.all().stage(8))).hasSize(5);
        }

        @Test
        void filtersByClass() {
            assertThat(names(search(library, Query.all().className("Surprise"))))
                    .containsExactly("Bristol", "Cambridge", "Yorkshire");
        }

        @Test
        void filtersByAPieceOfTheTitle() {
            assertThat(names(search(library, Query.all().name("bristol")))).containsExactly("Bristol");
            assertThat(search(library, Query.all().name("major"))).hasSize(5);
        }

        @Test
        void filtersByTheLittleFlag() {
            assertThat(names(search(library, Query.all().little(true)))).containsExactly("Little");
            assertThat(search(library, Query.all().little(false))).hasSize(5);
        }

        @Test
        void filtersByDifferential() {
            assertThat(search(library, Query.all().differential(true))).isEmpty();
        }

        @Test
        void findsWhatSharesALeadHeadWithAMethod() {
            assertThat(names(search(library, Query.all().sharesLeadHeadWith(cambridge()))))
                    .containsExactly("Cambridge", "Yorkshire");
        }

        @Test
        void findsTheMethodsWithALeadHeadCode() {
            assertThat(names(search(library, Query.all().leadHeadCode("b")))).containsExactly("Cambridge", "Yorkshire");
            assertThat(names(search(library, Query.all().leadHeadCode("a")))).containsExactly("Plain", "Plain");
            assertThat(search(library, Query.all().leadHeadCode("q"))).isEmpty();
        }

        @Test
        void leavesTheQueryItNarrowedAsItWas() {
            Query major = Query.all().stage(8);
            Query surprise = major.className("Surprise");
            assertThat(search(library, major)).hasSize(5);
            assertThat(search(library, surprise)).hasSize(3);
            assertThat(search(library, Query.all())).hasSize(6);
        }

        @Test
        void combinesFilters() {
            assertThat(search(
                            library, Query.all().stage(8).className("Surprise").name("york")))
                    .hasSize(1);
        }

        @Test
        void returnsMatchesInTitleOrder() {
            assertThat(names(search(library, Query.all().stage(8))).get(0)).isEqualTo("Bristol");
        }

        @Test
        void refusesSomethingThatIsNotALibrary() {
            assertThatThrownBy(() -> search(null, Query.all())).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class CountByStage {

        @Test
        void countsEachStageAscending() {
            assertThat(countByStage(library)).containsExactly(entry(6, 1), entry(8, 5));
        }

        @Test
        void givesAnEmptyMapForAnEmptyLibrary() {
            assertThat(countByStage(new MethodLibrary())).isEmpty();
        }
    }

    @Nested
    class CountByClass {

        @Test
        void countsEachClassInNameOrder() {
            assertThat(countByClass(library))
                    .containsExactly(entry("Bob", 2), entry("Little Bob", 1), entry("Surprise", 3));
        }
    }

    @Nested
    class SpliceGroups {

        @Test
        void keepsOnlyTheGroupsWithSomethingToSplice() {
            Map<String, List<Method>> groups = spliceGroups(library);
            assertThat(groups).hasSize(1);
            assertThat(names(groups.values().iterator().next())).containsExactly("Cambridge", "Yorkshire");
        }

        @Test
        void givesNothingForALibraryWhereEveryLeadHeadIsDifferent() {
            MethodLibrary small = new MethodLibrary();
            small.addAll(List.of(cambridge(), bristol()));
            assertThat(spliceGroups(small)).isEmpty();
        }
    }

    @Nested
    class CountByCode {

        @Test
        void countsTheMethodsInEachCodeAtEachStage() {
            assertThat(LibrarySearch.countByCode(library))
                    .containsEntry("8:b", 2)
                    .containsEntry("8:m", 1)
                    .containsEntry("8:a", 1)
                    .containsEntry("6:a", 1);
        }

        @Test
        void countsNothingInAnEmptyLibrary() {
            assertThat(LibrarySearch.countByCode(new MethodLibrary())).isEmpty();
        }
    }

    @Nested
    class Summarise {

        @Test
        void countsTheMethodsByStage() {
            assertThat(summarise(library)).isEqualTo("6 methods: 1 on 6, 5 on 8");
        }

        @Test
        void saysSoWhenThereIsNothing() {
            assertThat(summarise(new MethodLibrary())).isEqualTo("no methods");
        }
    }
}
