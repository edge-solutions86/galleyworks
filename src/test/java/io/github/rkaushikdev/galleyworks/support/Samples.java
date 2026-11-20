package io.github.rkaushikdev.galleyworks.support;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;

/**
 * Seeded example generators for the property tests.
 *
 * <p>The original suite drew 50 examples per property ({@code max_examples=50}). These draw the
 * same number from a fixed seed, always including the edge values a shrinker would reach, so a
 * failure reproduces on every run.
 */
public final class Samples {

    /** Examples per property, as the original profile ran. */
    public static final int EXAMPLES = 50;

    private Samples() {}

    /** A random source fixed for one property. */
    public static SplittableRandom random(String property) {
        return new SplittableRandom(property.hashCode());
    }

    /** Unsigned 64-bit values, edges first. */
    public static Stream<Long> u64(String property) {
        SplittableRandom random = random(property);
        List<Long> values = new ArrayList<>(List.of(0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE));
        while (values.size() < EXAMPLES) {
            values.add(random.nextLong());
        }
        return values.stream();
    }

    /** Integers in {@code [low, high]}, both ends included. */
    public static Stream<Integer> ints(String property, int low, int high) {
        SplittableRandom random = random(property);
        List<Integer> values = new ArrayList<>(List.of(low, high));
        while (values.size() < EXAMPLES) {
            values.add(random.nextInt(low, high + 1));
        }
        return values.stream();
    }

    /** Byte strings up to {@code maxSize} long, the empty one first. */
    public static Stream<byte[]> binary(String property, int maxSize) {
        SplittableRandom random = random(property);
        List<byte[]> values = new ArrayList<>();
        values.add(new byte[0]);
        while (values.size() < EXAMPLES) {
            byte[] data = new byte[random.nextInt(maxSize + 1)];
            random.nextBytes(data);
            values.add(data);
        }
        return values.stream();
    }

    /** Text drawn from {@code alphabet}, up to {@code maxSize} characters, the empty one first. */
    public static Stream<String> text(String property, String alphabet, int maxSize) {
        return text(property, alphabet, maxSize, EXAMPLES);
    }

    /** {@code examples} texts drawn from {@code alphabet}, up to {@code maxSize} characters. */
    public static Stream<String> text(String property, String alphabet, int maxSize, int examples) {
        SplittableRandom random = random(property);
        List<String> values = new ArrayList<>();
        values.add("");
        int[] points = alphabet.codePoints().toArray();
        while (values.size() < examples) {
            int length = random.nextInt(maxSize + 1);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < length; i++) {
                out.appendCodePoint(points[random.nextInt(points.length)]);
            }
            values.add(out.toString());
        }
        return values.stream();
    }
}
