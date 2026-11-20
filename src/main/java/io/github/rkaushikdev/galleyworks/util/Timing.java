package io.github.rkaushikdev.galleyworks.util;

/**
 * How long one pipeline stage took, and how often it ran.
 *
 * @param name the stage
 * @param seconds wall-clock seconds accumulated over every call
 * @param calls how many times the stage ran
 */
public record Timing(String name, double seconds, int calls) {

    /** Seconds per call, or zero for a stage that never ran. */
    public double average() {
        return calls == 0 ? 0.0 : seconds / calls;
    }

    @Override
    public String toString() {
        return name + ": " + Floats.fixed(seconds, 3) + "s over " + calls + " calls";
    }
}
