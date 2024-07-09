package nl.inl.blacklab.search.textpattern;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * Apply some settings to part of the query.
 *
 * Example: @relationclass=al to change default relation class for (part of) the query.
 */
public class TextPatternSettings extends TextPattern {

    final TextPattern clause;

    final Map<String, String> settings = new LinkedHashMap<>();

    public TextPatternSettings(Map<String, String> settings, TextPattern clause) {
        this.clause = clause;
        this.settings.putAll(settings);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        for (Map.Entry<String, String> e : settings.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            switch (key.toLowerCase()) {
            case "relationclass":
            case "rc":
                // Set default relation class to use if not overridden
                context = context.withDefaultRelationClass(value);
                break;
            default:
                throw new InvalidQuery("Unknown setting: " + key + "= " + value);
            }
        }
        return clause.translate(context);
    }

    @Override
    public String toString() {
        return "SETTINGS(" + settings + ", " + clause + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternSettings that = (TextPatternSettings) o;
        return Objects.equals(clause, that.clause) && Objects.equals(settings, that.settings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clause, settings);
    }

    public TextPattern getClause() {
        return clause;
    }

    public Map<String, String> getSettings() {
        return Collections.unmodifiableMap(settings);
    }

    @Override
    public boolean isRelationsQuery() {
        return clause.isRelationsQuery();
    }
}
