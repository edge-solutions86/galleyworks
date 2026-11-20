package io.github.rkaushikdev.galleyworks.report;

/**
 * Totals for one report. It describes the document, not whatever subset of findings survived a
 * display filter.
 *
 * @param matchedSegments segments with at least one finding
 * @param totalSegments body segments in the document
 * @param reusedCharacters distinct characters covered by any passage
 * @param documentCharacters characters in the document
 * @param otherInstruments distinct instruments matched
 */
public record ReportSummary(
        int matchedSegments, int totalSegments, int reusedCharacters, int documentCharacters, int otherInstruments) {

    /** The share of the document that was reused, at most one. */
    public double reuseRatio() {
        if (documentCharacters == 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) reusedCharacters / documentCharacters);
    }
}
