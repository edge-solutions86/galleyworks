package io.github.rkaushikdev.galleyworks;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * House rules, checked rather than remembered: the conventions a reviewer would otherwise have to
 * hold in their head across a hundred source files.
 */
class RepoInvariantsTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Path TEST = Path.of("src", "test", "java");
    private static final Path FIXTURES = Path.of("src", "testFixtures", "java");

    // Assembled rather than written out, because this file is itself scanned.
    private static final List<String> MARKERS =
            List.of("TO" + "DO", "FIX" + "ME", "X" + "XX", "HA" + "CK", "@Dis" + "abled");
    private static final List<String> DEBUGGING =
            List.of("System.out.print" + "ln(", "System.err.print" + "ln(", ".printStack" + "Trace(");

    private static final Pattern TOP_LEVEL = Pattern.compile(
            "^(?:public |final |abstract |sealed |non-sealed )*(?:class|interface|record|enum|@interface) (\\w+)",
            Pattern.MULTILINE);
    private static final Pattern PACKAGE = Pattern.compile("^package ([\\w.]+);$", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile("^import (static )?([\\w.*]+);$", Pattern.MULTILINE);

    static List<Path> sources() {
        return javaFiles(MAIN);
    }

    static List<Path> testFiles() {
        List<Path> files = new ArrayList<>(javaFiles(TEST));
        files.addAll(javaFiles(FIXTURES));
        return files;
    }

    static List<Path> allFiles() {
        List<Path> all = new ArrayList<>(sources());
        all.addAll(testFiles());
        return all;
    }

    private static List<Path> javaFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
    }

    private static String stem(Path path) {
        String name = path.getFileName().toString();
        return name.substring(0, name.length() - ".java".length());
    }

    /** The package a file under {@code root} has to declare. */
    private static String expectedPackage(Path root, Path path) {
        List<String> parts = new ArrayList<>();
        root.relativize(path.getParent()).forEach(part -> parts.add(part.toString()));
        return String.join(".", parts);
    }

    /** The names of the types declared at the top level of {@code text}. */
    static List<String> topLevelTypes(String text) {
        List<String> names = new ArrayList<>();
        Matcher match = TOP_LEVEL.matcher(text);
        while (match.find()) {
            names.add(match.group(1));
        }
        return names;
    }

    /** True when a doc comment opens between the imports and the first top-level type. */
    static boolean documented(String text) {
        Matcher declaration = TOP_LEVEL.matcher(text);
        if (!declaration.find()) {
            return false;
        }
        String head = text.substring(0, declaration.start());
        int afterImports = Math.max(head.lastIndexOf("\nimport "), head.lastIndexOf("\npackage "));
        return head.indexOf("/**", Math.max(afterImports, 0)) >= 0;
    }

    /** True when each import group, static first, is in order and the static group comes first. */
    static boolean importsSorted(String text) {
        List<String> statics = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        boolean seenPlain = false;
        Matcher match = IMPORT.matcher(text);
        while (match.find()) {
            if (match.group(1) != null) {
                if (seenPlain) {
                    return false;
                }
                statics.add(match.group(2));
            } else {
                seenPlain = true;
                plain.add(match.group(2));
            }
        }
        return statics.equals(statics.stream().sorted().toList())
                && plain.equals(plain.stream().sorted().toList());
    }

    /** The first unfinished-work marker in {@code text}. */
    static Optional<String> marker(String text) {
        return MARKERS.stream().filter(text::contains).findFirst();
    }

    /** The first debugging call in {@code text} outside a comment line. */
    static Optional<String> debugging(String text) {
        return text.lines()
                .map(String::strip)
                .filter(line -> !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*"))
                .flatMap(line -> DEBUGGING.stream().filter(line::contains))
                .findFirst();
    }

    /** The package {@code text} declares. */
    static Optional<String> declaredPackage(String text) {
        Matcher match = PACKAGE.matcher(text);
        return match.find() ? Optional.of(match.group(1)) : Optional.empty();
    }

    @Test
    void thereAreSourcesToCheck() {
        assertThat(sources()).hasSizeGreaterThan(40);
        assertThat(testFiles()).hasSizeGreaterThan(40);
    }

    @ParameterizedTest
    @MethodSource("allFiles")
    void everyFileUsesUnixLineEndings(Path path) {
        assertThat(read(path)).as(path.toString()).doesNotContain("\r\n");
    }

    @ParameterizedTest
    @MethodSource("allFiles")
    void everyFileEndsWithOneNewline(Path path) {
        assertThat(read(path)).as(path.toString()).endsWith("\n").doesNotEndWith("\n\n");
    }

    @ParameterizedTest
    @MethodSource("sources")
    void everySourceHasADocComment(Path path) {
        assertThat(documented(read(path))).as(path.toString()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("sources")
    void everySourceDeclaresOneTypeNamedAfterItsFile(Path path) {
        assertThat(topLevelTypes(read(path))).as(path.toString()).containsExactly(stem(path));
    }

    @ParameterizedTest
    @MethodSource("allFiles")
    void everyImportListIsSorted(Path path) {
        assertThat(importsSorted(read(path))).as(path.toString()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("sources")
    void everyDeclaredTypeIsLoadable(Path path) throws ClassNotFoundException {
        String name = expectedPackage(MAIN, path) + "." + stem(path);
        Class<?> type = Class.forName(name, false, getClass().getClassLoader());
        assertThat(type.getEnclosingClass()).as(name).isNull();
    }

    @ParameterizedTest
    @MethodSource("allFiles")
    void noUnfinishedWorkMarkers(Path path) {
        assertThat(marker(read(path))).as(path.toString()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("allFiles")
    void noStrayDebugging(Path path) {
        assertThat(debugging(read(path))).as(path.toString()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("allFiles")
    void everyFileDeclaresThePackageOfItsDirectory(Path path) {
        Path root = path.startsWith(MAIN) ? MAIN : path.startsWith(FIXTURES) ? FIXTURES : TEST;
        assertThat(declaredPackage(read(path))).as(path.toString()).contains(expectedPackage(root, path));
    }

    @Test
    void everyClassInitializes() throws ClassNotFoundException {
        for (Path path : sources()) {
            Class.forName(
                    expectedPackage(MAIN, path) + "." + stem(path),
                    true,
                    getClass().getClassLoader());
        }
    }

    @Test
    void theTestSuiteCoversEverySourcePackage() {
        TreeSet<String> packages = new TreeSet<>();
        sources().forEach(path -> packages.add(expectedPackage(MAIN, path)));
        TreeSet<String> tested = new TreeSet<>();
        javaFiles(TEST).stream()
                .filter(path -> stem(path).endsWith("Test"))
                .forEach(path -> tested.add(expectedPackage(TEST, path)));
        assertThat(tested).containsAll(packages);
    }

    @Test
    void theCheckersSeePlantedProblems() {
        String good = "package a.b;\n\nimport static x.Y.z;\n\nimport java.util.List;\n\n/** Doc. */\n"
                + "public final class Good {}\n";
        assertThat(documented(good)).isTrue();
        assertThat(topLevelTypes(good)).containsExactly("Good");
        assertThat(importsSorted(good)).isTrue();
        assertThat(marker(good)).isEmpty();
        assertThat(debugging(good)).isEmpty();
        assertThat(declaredPackage(good)).contains("a.b");

        assertThat(documented("package a;\n\n// not a doc comment\nclass Bare {}\n"))
                .isFalse();
        assertThat(topLevelTypes("class One {}\nrecord Two() {}\n")).containsExactly("One", "Two");
        assertThat(importsSorted("import java.util.Map;\nimport java.util.List;\n"))
                .isFalse();
        assertThat(importsSorted("import java.util.List;\nimport static x.Y.z;\n"))
                .isFalse();
        assertThat(marker("// " + MARKERS.get(1) + " later\n")).contains(MARKERS.get(1));
        assertThat(debugging("    " + DEBUGGING.getFirst() + "x);\n")).contains(DEBUGGING.getFirst());
        assertThat(debugging(" * " + DEBUGGING.getFirst() + "x);\n")).isEmpty();
        assertThat(declaredPackage("// no package\nclass X {}\n")).isEmpty();
    }
}
