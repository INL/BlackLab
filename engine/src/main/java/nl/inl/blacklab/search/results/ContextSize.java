package nl.inl.blacklab.search.results;

import java.util.List;
import java.util.Objects;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;

/**
 * Represents the size of the context around a hit.
 * 
 * WARNING: right now, only before() is used (for both before and after context size)!
 * The other functionality will be added in the future.
 */
public class ContextSize {

    public static final int SAFE_MAX_CONTEXT_SIZE = (Constants.JAVA_MAX_ARRAY_SIZE - 100) / 2;

    /**
     * Context based on am inline tag containing the hit instead of the hit.
     *
     * Note that the tag MUST contain the hit, or Bad Things will happen.
     * So this is useful for finding the whole sentence or paragraph a hit occurs in,
     * for example.
     *
     * @param matchInfoName match info to use for context
     * @return context size object
     */
    public static ContextSize matchInfo(String matchInfoName, int maxSnippetLength) {
        return new ContextSize(0, 0, true, matchInfoName, maxSnippetLength);
    }

    /**
     * Get ContexSize.
     *
     * WARNING: right now, only before() is used (for both before and after context size)!
     * The other functionality will be added in the future.
     *
     * @param before context size before hit
     * @param after context size after hit
     * @param includeHit should the hit itself be included in the context or not?
     * @param matchInfoName match info to use for context
     * @return context size object
     */
    public static ContextSize get(int before, int after, boolean includeHit, String matchInfoName, int maxSnippetLength) {
        return new ContextSize(before, after, includeHit, matchInfoName, maxSnippetLength);
    }

    /**
     * Get ContexSize.
     *
     * WARNING: right now, only before() is used (for both before and after context size)!
     * The other functionality will be added in the future.
     *
     * @param before context size before hit
     * @param after context size after hit
     * @param includeHit should the hit itself be included in the context or not?
     * @return context size object
     */
    public static ContextSize get(int before, int after, boolean includeHit, int maxSnippetLength) {
        return new ContextSize(before, after, includeHit, null, maxSnippetLength);
    }

    /**
     * Get ContexSize.
     *
     * WARNING: right now, only before() is used (for both before and after context size)!
     * The other functionality will be added in the future.
     *
     * Hit is always included in the context with this method.
     *
     * @param before context size before hit
     * @param after context size after hit
     * @return context size object
     */
    public static ContextSize get(int before, int after, int maxSnippetLength) {
        return new ContextSize(before, after, true, null, maxSnippetLength);
    }

    /**
     * Get ContexSize.
     *
     * The number is used for the context size both before and after the hit.
     *
     * Hit is always included in the context with this method.
     *
     * @param size context size before and after hit
     * @return context size object
     */
    public static ContextSize get(int size, int maxSnippetLength) {
        return new ContextSize(size, size, true, null, maxSnippetLength);
    }

    /**
     * Return the minimal context size that encompasses both parameters.
     *
     * Note: if matchInfoIndex differs, results are undefined.
     *
     * @param a first context size
     * @param b second context size
     * @return union of both context sizes
     */
    public static ContextSize union(ContextSize a, ContextSize b) {
        int before = Math.max(a.before, b.before);
        int after = Math.max(a.after, b.after);
        boolean includeHit = a.includeHit || b.includeHit;
        int maxSnippetLength = Math.min(a.maxSnippetLength, b.maxSnippetLength);
        return new ContextSize(before, after, includeHit, a.matchInfoName, maxSnippetLength);
    }

    private final int before;

    private final int after;

    private final boolean includeHit;

    /**
     * If set, base the context around this match info instead of the main hit text.
     */
    private final String matchInfoName;

    /** What is the largest length allowed for our snippets?
     *  (limit the amount of content you can retrieve at once to keep rightsholders happy...)
     */
    private final int maxSnippetLength;

    private ContextSize(int before, int after, boolean includeHit, String matchInfoName) {
        this(before, after, includeHit, matchInfoName, Integer.MAX_VALUE);
    }

    private ContextSize(int before, int after, boolean includeHit, String matchInfoName, int maxSnippetLength) {
        super();
        assert before >= 0;
        assert after >= 0;
        assert matchInfoName == null || !matchInfoName.isEmpty();
        assert maxSnippetLength >= 0;
        this.before = before;
        this.after = after;
        this.includeHit = includeHit;
        this.matchInfoName = matchInfoName;
        this.maxSnippetLength = maxSnippetLength;
    }

    public static int maxSnippetLengthFromMaxContextSize(int maxContextSize) {
        if (maxContextSize > SAFE_MAX_CONTEXT_SIZE)
            maxContextSize = SAFE_MAX_CONTEXT_SIZE;
        return maxContextSize * 2 + 10; // 10 seems a reasonable maximum hit length
    }

    /**
     * Get the start and end position of the snippet for the specified hit.
     *
     * Because this may involve finding a named match info group (e.g. if we want a whole sentence
     * instead of X words before/after), and we want to return two values without extra allocations,
     * we've combined getting the start and end into this slightly crazy single method.
     *
     * It will also make sure that the resulting snippet doesn't exceed the maximum snippet length,
     * decreasing the end position if necessary.
     *
     * @param hit hit to get snippet boundaries for
     * @param matchInfoNames names of match info groups
     * @param lastWordInclusive should snippet end point to the last word of the snippet, or to the first word after it?
     * @param startArr array to write start position to
     * @param startIndex index in startArr to write start position to
     * @param endArr array to write end position to
     * @param endIndex index in endArr to write end position to
     */
    public void getSnippetStartEnd(Hit hit, List<String> matchInfoNames, boolean lastWordInclusive, int[] startArr, int startIndex, int[] endArr, int endIndex) {
        assert hit.start() <= hit.end();
        int start, end;
        if (!isInlineTag()) {
            // Use the hit to determine snippet
            start = hit.start();
            end = hit.end();
        } else {
            // Use a match info group to determine snippet
            MatchInfo tag = findTag(hit, inlineTagName(), matchInfoNames);
            start = tag == null ? hit.start() : tag.getSpanStart();
            end = tag == null ? hit.start() : tag.getSpanEnd();
        }
        start = Math.max(0, start - before());
        end = (int)Math.min(end + after, (long)start + maxSnippetLength); // make sure snippet doesn't get longer than allowed
        if (lastWordInclusive) {
            // End should point to the last word of the snippet, not to the first word after the snippet
            end--;
        }

        assert start <= end;

        // Write results into output arrays
        startArr[startIndex] = start;
        endArr[endIndex] = end;
    }

    private static MatchInfo findTag(Hit hit, String matchInfoName, List<String> matchInfoNames) {
        MatchInfo[] matchInfos = hit.matchInfo();
        if (matchInfos != null) {
            // Return the match info group with the specified name
            int index = matchInfoNames.indexOf(matchInfoName);
            if (index >= 0)
                return matchInfos[index];

            // Maybe it's a tag name, not a match info capture name? (REMOVE THIS?)
            for (int i = matchInfos.length - 1; i >= 0; i--) { // reverse because we expect it to be the last
                MatchInfo mi = matchInfos[i];
                if (mi.getType() == MatchInfo.Type.INLINE_TAG) {
                    String relType = ((RelationInfo) mi).getFullRelationType();
                    String tagName = RelationUtil.classAndType(relType)[1];
                    if (tagName.equals(matchInfoName)) {
                        return mi;
                    }
                }
            }
        }
        return null;
    }

    public int before() {
        return before;
    }

    public int after() {
        return after;
    }

    @Deprecated
    public int left() {
        return before();
    }

    @Deprecated
	public int right() {
	    return after();
	}
	
	/**
	 * Should the hit itself be included in the context or not?
	 * 
	 * By default it always is, but for some operations you only need
	 * the before context. And to determine collocations you might only want
	 * before and after context and not the hit context itself (because you're
	 * counting words that occur around the hit)
	 * 
	 * @return whether or not the hit context should be included
	 */
	public boolean includeHit() {
	    return includeHit;
	}

    /** Base the context on inline tag instead of the normal hit?
     *
     *  @return inline tag name or null if none
     */
    public String inlineTagName() {
        return matchInfoName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ContextSize))
            return false;
        ContextSize that = (ContextSize) o;
        return before == that.before && after == that.after && includeHit == that.includeHit
                && Objects.equals(matchInfoName, that.matchInfoName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(before, after, includeHit, matchInfoName);
    }

    @Override
    public String toString() {
        return "ContextSize{" +
                "before=" + before +
                ", after=" + after +
                ", includeHit=" + includeHit +
                ", inlineTagName='" + matchInfoName + '\'' +
                '}';
    }

    public boolean isNone() {
        return before == 0 && after == 0 && matchInfoName == null;
    }

    /**
     * Return a version of this context size clamped to a maximum before/after size.
     * @param max maximum value before and after may have
     * @return a version of this context size clamped to a maximum before/after size
     */
    public ContextSize clampedTo(int max) {
        if (before <= max && after <= max)
            return this;
        return new ContextSize(Math.min(before, max), Math.min(after, max), includeHit, matchInfoName, maxSnippetLength);
    }

    public boolean isInlineTag() {
        return matchInfoName != null;
    }
}
