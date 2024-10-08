package nl.inl.blacklab.search.textpattern;

/**
 * Either a regex or an integer range that we want to match an annotation or attribute value to.
 */
public interface MatchValue {

    static MatchValue regex(String regex) {
        return new MatchValueRegex(regex);
    }

    static MatchValue intRange(int min, int max) {
        return new MatchValueIntRange(min, max);
    }

    /** Special prefix for range queries so we can recognize them... */
    String JSON_ENCODING_RANGE_TAG = "@@RANGE@@";

    static MatchValue decodeFromJson(String encoded) {
        if (!encoded.startsWith(JSON_ENCODING_RANGE_TAG))
            return new MatchValueRegex(encoded);
        String[] parts = encoded.substring(JSON_ENCODING_RANGE_TAG.length()).split(",");
        if (parts.length != 2)
            throw new IllegalArgumentException("Not a range: " + encoded);
        return new MatchValueIntRange(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    /**
     * Return the Lucene regex.
     */
    String getRegex();

    /**
     * Return the BCQL syntax, for serialization.
     */
    String getBcql();

    String encodeForJson();

    TextPatternTerm textPattern();
}
