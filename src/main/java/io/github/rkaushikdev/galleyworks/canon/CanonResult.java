package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.util.OffsetMap;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.List;

/**
 * The canonical text plus the trail back to the source.
 *
 * @param source the text as printed
 * @param text the canonical form
 * @param mapping translates canonical offsets back to the source
 * @param applied the rules that changed something, in order
 */
public record CanonResult(String source, String text, OffsetMap mapping, List<String> applied) {

    /** Copies the trail. */
    public CanonResult {
        applied = List.copyOf(applied);
    }

    /** The source span behind a canonical span. */
    public TextSpan toSource(TextSpan span) {
        return mapping.toSourceSpan(span);
    }

    /** Quote a canonical span using the words of the source text. */
    public String quote(TextSpan span) {
        return toSource(span).sliceOf(source);
    }
}
