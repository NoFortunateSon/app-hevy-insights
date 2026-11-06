package io.github.nofortunateson.hevyinsights;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Three layers (ADR 0007): dependencies only go downwards, presentation to business to data, without
 * skipping a layer. Inside the data layer, the Hevy source and the database do not know each other.
 * <p>
 * Checked on the source text (imports and fully qualified names) so no extra test library is needed.
 */
class LayerDependencyTests {

    private static final String ROOT_PACKAGE = "io.github.nofortunateson.hevyinsights";
    private static final Path MAIN_SOURCES = Path.of("src/main/java", ROOT_PACKAGE.split("\\."));
    private static final Pattern APP_REFERENCE =
            Pattern.compile(Pattern.quote(ROOT_PACKAGE) + "\\.([a-z]+(?:\\.[a-z]+)?)");

    /** Which package may not reference which: {from, forbidden}. Prefix match on the sub-package. */
    private static final List<Rule> FORBIDDEN = List.of(
            new Rule("presentation", "data"),
            new Rule("business", "presentation"),
            new Rule("data", "presentation"),
            new Rule("data", "business"),
            new Rule("data.hevy", "data.db"),
            new Rule("data.db", "data.hevy"),
            new Rule("business.metrics", "data.hevy"));

    @Test
    void dependenciesOnlyGoDownwards() {
        List<String> violations = sourceFiles()
                .flatMap(file -> violationsIn(file).stream())
                .toList();

        assertThat(violations).isEmpty();
    }

    @Test
    void ruleCatchesAForbiddenReference() {
        assertThat(referencedPackages("import " + ROOT_PACKAGE + ".data.db.Something;"))
                .containsExactly("data.db");
        assertThat(new Rule("presentation", "data").forbids("presentation", "data.db")).isTrue();
        assertThat(new Rule("presentation", "data").forbids("presentation", "database")).isFalse();
    }

    private static Stream<Path> sourceFiles() {
        try {
            return Files.walk(MAIN_SOURCES).filter(path -> path.toString().endsWith(".java")).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> violationsIn(Path file) {
        String from = packageOf(file);
        return referencedPackages(read(file)).stream()
                .filter(to -> FORBIDDEN.stream().anyMatch(rule -> rule.forbids(from, to)))
                .map(to -> MAIN_SOURCES.relativize(file) + " references " + to)
                .distinct()
                .toList();
    }

    /** Sub-package of the root, e.g. {@code data.hevy}; empty for the root package itself. */
    private static String packageOf(Path file) {
        Path parent = MAIN_SOURCES.relativize(file).getParent();
        return parent == null ? "" : parent.toString().replace('\\', '.').replace('/', '.');
    }

    private static List<String> referencedPackages(String source) {
        String withoutPackageLine = source.replaceFirst("(?m)^package .*;$", "");
        Matcher matcher = APP_REFERENCE.matcher(withoutPackageLine);
        return matcher.results().map(result -> result.group(1)).distinct().toList();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Rule(String from, String forbidden) {

        boolean forbids(String sourcePackage, String referencedPackage) {
            return isWithin(sourcePackage, from) && isWithin(referencedPackage, forbidden);
        }

        private static boolean isWithin(String pkg, String prefix) {
            return pkg.equals(prefix) || pkg.startsWith(prefix + ".");
        }
    }
}
