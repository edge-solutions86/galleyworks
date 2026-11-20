package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.CanonError;
import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.util.OffsetMap;
import io.github.rkaushikdev.galleyworks.util.OffsetMapBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * An ordered set of rules applied as one rewrite.
 *
 * <p>Rules run in a fixed order and each one sees the output of the last, so an earlier rule can
 * change what a later one matches. That is deliberate: citation normalization has to run before
 * number normalization, or {@code 41-3-101} is read as three numbers.
 */
public final class CanonPipeline implements Canonicalizer {

    private static final Comparator<Edit> LEFT_TO_RIGHT = Comparator.comparingInt(
                    (Edit edit) -> edit.span().start())
            .thenComparingInt(edit -> edit.span().end());

    private final List<Rule> rules;

    /** A pipeline over {@code rules}, whose names must be unique. */
    public CanonPipeline(List<? extends Rule> rules) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new TreeSet<>();
        for (Rule rule : rules) {
            if (!seen.add(rule.name())) {
                duplicates.add(rule.name());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new CanonError(
                    "pipeline has duplicate rule names", Context.of("names", String.join(",", duplicates)));
        }
        this.rules = List.copyOf(rules);
    }

    @Override
    public List<String> ruleNames() {
        return rules.stream().map(Rule::name).toList();
    }

    @Override
    public CanonResult run(String text, CanonConfig config) {
        String current = text;
        OffsetMap mapping = OffsetMap.identity(text.length());
        List<String> applied = new ArrayList<>();
        for (Rule rule : rules) {
            List<Edit> edits = rule.edits(current, config);
            if (edits.isEmpty()) {
                continue;
            }
            Rewrite step = applyEdits(current, edits, rule.name());
            current = step.text();
            mapping = mapping.compose(step.mapping());
            applied.add(rule.name());
        }
        return new CanonResult(text, current, mapping, applied);
    }

    /** Apply non-overlapping edits left to right, tracking offsets. */
    public static Rewrite applyEdits(String text, List<Edit> edits) {
        return applyEdits(text, edits, "");
    }

    /** Apply non-overlapping edits left to right, tracking offsets, blaming {@code rule}. */
    public static Rewrite applyEdits(String text, List<Edit> edits, String rule) {
        List<Edit> ordered = new ArrayList<>(edits);
        ordered.sort(LEFT_TO_RIGHT);
        OffsetMapBuilder builder = new OffsetMapBuilder();
        StringBuilder out = new StringBuilder(text.length());
        int cursor = 0;
        for (Edit edit : ordered) {
            if (edit.span().start() < cursor) {
                throw new CanonError(
                        "canonicalization rule produced overlapping edits",
                        Context.of("rule", rule, "at", edit.span().start()));
            }
            if (edit.span().end() > text.length()) {
                throw new CanonError(
                        "canonicalization edit runs past the end of the text",
                        Context.of("rule", rule, "end", edit.span().end(), "length", text.length()));
            }
            int gap = edit.span().start() - cursor;
            if (gap > 0) {
                out.append(text, cursor, edit.span().start());
                builder.keep(gap);
            }
            out.append(edit.replacement());
            builder.replace(edit.span().length(), edit.replacement().length());
            cursor = edit.span().end();
        }
        if (cursor < text.length()) {
            out.append(text, cursor, text.length());
            builder.keep(text.length() - cursor);
        }
        return new Rewrite(out.toString(), builder.build(text.length()));
    }
}
