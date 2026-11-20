package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.match.Confidence;
import java.util.List;

/**
 * One finding against one other instrument, resolved into quotations and an explanation.
 *
 * @param queryPath the reported instrument's segment
 * @param otherKey the instrument it matched
 * @param otherPath the segment it matched
 * @param containment how much of the reported segment appears in the other
 * @param jaccard the symmetric similarity of the pair
 * @param passages the quoted passages
 * @param confidence how strong the evidence is
 * @param headline the one-line verdict
 * @param reasons the observations behind it
 */
public record ReportedMatch(
        String queryPath,
        InstrumentKey otherKey,
        String otherPath,
        double containment,
        double jaccard,
        List<ReportedPassage> passages,
        Confidence confidence,
        String headline,
        List<String> reasons) {

    /** Copies the lists. */
    public ReportedMatch {
        passages = List.copyOf(passages);
        reasons = List.copyOf(reasons);
    }

    /** Characters across every passage. */
    public int characters() {
        return passages.stream().mapToInt(ReportedPassage::characters).sum();
    }

    /** True when every passage could be quoted from the other document. */
    public boolean resolved() {
        return passages.stream().allMatch(ReportedPassage::resolved);
    }

    /** This finding with {@code kept} as its passages. */
    public ReportedMatch withPassages(List<ReportedPassage> kept) {
        return new ReportedMatch(
                queryPath, otherKey, otherPath, containment, jaccard, kept, confidence, headline, reasons);
    }
}
