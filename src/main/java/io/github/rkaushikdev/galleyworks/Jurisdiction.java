package io.github.rkaushikdev.galleyworks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A body that can enact text: a state, a county, a city.
 *
 * @param slug the lowercase identifier the corpus uses for it
 * @param name the name it is printed under
 * @param level where it sits in the hierarchy of government
 * @param parent the slug of the jurisdiction it sits under, if any
 */
public record Jurisdiction(String slug, String name, GovernmentLevel level, Optional<String> parent)
        implements Comparable<Jurisdiction> {

    static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{0,31}$");

    private static final Comparator<Jurisdiction> ORDER = Comparator.comparing(Jurisdiction::slug)
            .thenComparing(Jurisdiction::name)
            .thenComparing(jurisdiction -> jurisdiction.level().value())
            .thenComparing(jurisdiction -> jurisdiction.parent().orElse(""));

    /** Validates the slug, the name and the parent link. */
    public Jurisdiction {
        if (!SLUG.matcher(slug).matches()) {
            throw new ConfigError("jurisdiction slug is not a valid identifier", Context.of("slug", slug));
        }
        if (name.isBlank()) {
            throw new ConfigError("jurisdiction name is empty", Context.of("slug", slug));
        }
        if (parent.isPresent() && !SLUG.matcher(parent.get()).matches()) {
            throw new ConfigError("jurisdiction parent is not a valid identifier", Context.of("parent", parent.get()));
        }
        if (parent.isPresent() && parent.get().equals(slug)) {
            throw new ConfigError("jurisdiction is its own parent", Context.of("slug", slug));
        }
    }

    /** A jurisdiction with no parent. */
    public Jurisdiction(String slug, String name, GovernmentLevel level) {
        this(slug, name, level, Optional.empty());
    }

    /** A jurisdiction under {@code parent}. */
    public Jurisdiction(String slug, String name, GovernmentLevel level, String parent) {
        this(slug, name, level, Optional.of(parent));
    }

    /** True when the jurisdiction sits under another. */
    public boolean isSubordinate() {
        return parent.isPresent();
    }

    /** Walk {@code parent} links from this jurisdiction upward, stopping at an unknown parent. */
    public List<String> ancestry(Map<String, Jurisdiction> table) {
        List<String> chain = new ArrayList<>(List.of(slug));
        Set<String> seen = new HashSet<>(chain);
        Optional<String> cursor = parent;
        while (cursor.isPresent()) {
            String current = cursor.get();
            if (!seen.add(current)) {
                throw new ConfigError("jurisdiction ancestry contains a cycle", Context.of("slug", current));
            }
            chain.add(current);
            Jurisdiction found = table.get(current);
            if (found == null) {
                break;
            }
            cursor = found.parent();
        }
        return chain;
    }

    @Override
    public int compareTo(Jurisdiction other) {
        return ORDER.compare(this, other);
    }
}
