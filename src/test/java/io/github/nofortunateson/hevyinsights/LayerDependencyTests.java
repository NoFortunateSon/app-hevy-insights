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
 * Package structure (ADR 0007): controller calls service, service calls repository, never the other way
 * and without skipping a layer. {@code model}, {@code exception} and {@code utils} are used by everyone and
 * depend on none of the layers. The rules cover sub-packages too (e.g. {@code controller/hevy}).
 * <p>
 * Checked on the source text (imports and fully qualified names) so no extra test library is needed.
 */
class LayerDependencyTests {

    private static final String ROOT_PACKAGE = "io.github.nofortunateson.hevyinsights";
    private static final Path MAIN_SOURCES = Path.of("src/main/java", ROOT_PACKAGE.split("\\."));
    private static final Pattern APP_REFERENCE =
            Pattern.compile(Pattern.quote(ROOT_PACKAGE) + "\\.([a-z]+(?:\\.[a-z]+)?)");

    private static final String HEVY_HELPERS_PACKAGE = "controller.hevy";
    private static final Path HEVY_ENTRY_POINT = Path.of("controller", "HevyApiController.java");

    /** Which package may not reference which: {from, forbidden}. Prefix match on the sub-package. */
    private static final List<Rule> FORBIDDEN = List.of(
            new Rule("controller", "repository"),
            new Rule("service", "controller"),
            new Rule("repository", "controller"),
            new Rule("repository", "service"),
            new Rule("model", "controller"), new Rule("model", "service"), new Rule("model", "repository"),
            new Rule("model", "config"), new Rule("model", "security"),
            new Rule("exception", "controller"), new Rule("exception", "service"),
            new Rule("exception", "repository"), new Rule("exception", "config"),
            new Rule("exception", "security"),
            new Rule("utils", "controller"), new Rule("utils", "service"), new Rule("utils", "repository"),
            new Rule("utils", "config"), new Rule("utils", "security"));

    @Test
    void dependenciesOnlyGoDownwards() {
        List<String> violations = sourceFiles()
                .flatMap(file -> violationsIn(file).stream())
                .toList();

        assertThat(violations).isEmpty();
    }

    /**
     * The helpers in {@code controller/hevy} are public only because Java has no sub-package visibility
     * (ADR 0007, addendum): outside their package, only {@code HevyApiController} may use them.
     */
    @Test
    void hevyHelpersAreUsedOnlyByHevyApiController() {
        List<String> outsiders = sourceFiles()
                .filter(file -> !isWithin(packageOf(file), HEVY_HELPERS_PACKAGE))
                .filter(file -> !MAIN_SOURCES.relativize(file).equals(HEVY_ENTRY_POINT))
                .filter(file -> referencedPackages(read(file)).stream()
                        .anyMatch(pkg -> isWithin(pkg, HEVY_HELPERS_PACKAGE)))
                .map(file -> MAIN_SOURCES.relativize(file).toString())
                .toList();

        assertThat(outsiders).isEmpty();
    }

    @Test
    void ruleCatchesAForbiddenReference() {
        assertThat(referencedPackages("import " + ROOT_PACKAGE + ".model.dto.Something;"))
                .containsExactly("model.dto");
        assertThat(new Rule("utils", "model").forbids("utils", "model.dto")).isTrue();
        assertThat(new Rule("controller", "repository").forbids("controller.hevy", "repository")).isTrue();
        assertThat(new Rule("controller", "repository").forbids("controller", "repositoryx")).isFalse();
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

    /** {@code pkg} is {@code prefix} or one of its sub-packages. */
    private static boolean isWithin(String pkg, String prefix) {
        return pkg.equals(prefix) || pkg.startsWith(prefix + ".");
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
    }
}
