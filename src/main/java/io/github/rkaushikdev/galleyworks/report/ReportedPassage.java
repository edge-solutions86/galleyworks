package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.util.TextSpan;

/**
 * One passage, quoted on both sides.
 *
 * @param querySpan where it is printed in the reported instrument
 * @param otherSpan where it is printed in the other instrument
 * @param queryText the reported instrument's words
 * @param otherText the other instrument's words, empty when it was not available
 * @param resolved whether the other instrument's text could be found
 */
public record ReportedPassage(
        TextSpan querySpan, TextSpan otherSpan, String queryText, String otherText, boolean resolved) {

    /** The shorter of the two sides. */
    public int characters() {
        return Math.min(querySpan.length(), otherSpan.length());
    }
}
