package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Defined terms and the quotation marks around them.
 *
 * <p>A definitions section reads {@code "Board" means the state board of control}, and the term is
 * then quoted, unquoted or capitalised at every later use depending on the drafter. The engine
 * strips the quotes from any phrase the document itself defines, so {@code "board"} and {@code
 * board} fingerprint alike. Only phrases the document defines are touched: stripping every quoted
 * phrase would destroy the difference between a quotation and a definition.
 */
public final class DefinedTerms {

    private static final String OPENERS = "\"'“‘";
    private static final String CLOSERS = "\"'”’";
    private static final String QUOTED = "[" + OPENERS + "](?<term>[^" + CLOSERS + "]{1,80})[" + CLOSERS + "]";
    private static final Pattern DEFINITION = Regex.ignoringCase(QUOTED + "\\s+means\\b");
    private static final Pattern QUOTED_PHRASE = Regex.ignoringCase(QUOTED);
    private static final int MAX_TERM_WORDS = 6;

    private DefinedTerms() {}

    /**
     * A term the document defines, and where it does so.
     *
     * @param term the term as printed, trimmed
     * @param span where the term is printed inside its definition
     */
    public record Definition(String term, TextSpan span) {

        /** The term lowercased with its whitespace collapsed, as later uses are compared. */
        public String key() {
            return Text.squash(Text.lower(term));
        }
    }

    /** Every {@code "term" means} definition in {@code text}, in document order. */
    public static List<Definition> findDefinitions(String text) {
        List<Definition> found = new ArrayList<>();
        Matcher match = DEFINITION.matcher(text);
        while (match.find()) {
            String term = Text.strip(match.group("term"));
            if (term.isEmpty() || Text.words(term).size() > MAX_TERM_WORDS) {
                continue;
            }
            found.add(new Definition(term, new TextSpan(match.start("term"), match.end("term"))));
        }
        return found;
    }

    private static List<Edit> quotedOccurrences(String text, Set<String> terms) {
        List<Edit> edits = new ArrayList<>();
        Matcher match = QUOTED_PHRASE.matcher(text);
        while (match.find()) {
            String inner = match.group("term");
            if (!terms.contains(Text.squash(Text.lower(inner)))) {
                continue;
            }
            edits.add(new Edit(new TextSpan(match.start(), match.end()), inner));
        }
        return edits;
    }

    /** Strips quotation marks from terms the document defines. */
    public static final class DefinedTermRule implements Rule {

        @Override
        public String name() {
            return "defined-terms";
        }

        @Override
        public List<Edit> edits(String text, CanonConfig config) {
            List<Definition> definitions = findDefinitions(text);
            if (definitions.isEmpty()) {
                return List.of();
            }
            return quotedOccurrences(
                    text, definitions.stream().map(Definition::key).collect(Collectors.toSet()));
        }
    }
}
