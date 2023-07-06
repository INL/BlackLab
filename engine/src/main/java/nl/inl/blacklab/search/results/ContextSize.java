package nl.inl.blacklab.search.results;

import java.util.Objects;

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

    /**
     * Context based on am inline tag containing the hit instead of the hit.
     *
     * Note that the tag MUST contain the hit, or Bad Things will happen.
     * So this is useful for finding the whole sentence or paragraph a hit occurs in,
     * for example.
     *
     * @param inlineTagName inline tag to use for context
     * @return context size object
     */
    public static ContextSize matchInfo(String inlineTagName) {
        return new ContextSize(0, 0, true, inlineTagName);
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
     * @param inlineTagName inline tag to use for context
     * @return context size object
     */
    public static ContextSize get(int before, int after, boolean includeHit, String inlineTagName) {
        return new ContextSize(before, after, includeHit, inlineTagName);
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
    public static ContextSize get(int before, int after, boolean includeHit) {
        return new ContextSize(before, after, includeHit, null);
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
    public static ContextSize get(int before, int after) {
        return new ContextSize(before, after, true, null);
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
    public static ContextSize get(int size) {
        return new ContextSize(size, size, true, null);
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
        return new ContextSize(before, after, includeHit, a.inlineTagName);
    }

    private final int before;

    private final int after;

    private final boolean includeHit;

    /**
     * If set, base the context around this inline tag instead of the main hit text.
     */
    private final String inlineTagName;

    private ContextSize(int before, int after, boolean includeHit, String inlineTagName) {
        super();
        this.before = before;
        this.after = after;
        this.includeHit = includeHit;
        this.inlineTagName = inlineTagName;
    }

    public int snippetStart(Hit hit) {
        int start;
        if (inlineTagName == null) {
            // Use the hit to determine snippet
            start = hit.start();
        } else {
            // Use a match info group to determine snippet
            MatchInfo tag = findTag(hit, inlineTagName);
            start = tag == null ? hit.start() : tag.getSpanStart();
        }
        return Math.max(0, start - before());
    }

    public int snippetEnd(Hit hit) {
        int end;
        if (inlineTagName == null) {
            // Use the hit to determine snippet
            end = hit.end();
        } else {
            // Use a match info group to determine snippet
            MatchInfo tag = findTag(hit, inlineTagName);
            end = tag == null ? hit.start() : tag.getSpanEnd();
        }
        return end + after();
    }

    private MatchInfo findTag(Hit hit, String inlineTagName) {
        MatchInfo[] matchInfos = hit.matchInfo();
        if (matchInfos != null) {
            for (int i = matchInfos.length - 1; i >= 0; i--) { // reverse because we expect it to be the last
                MatchInfo mi = matchInfos[i];
                if (mi.getType() == MatchInfo.Type.INLINE_TAG) {
                    String relType = ((RelationInfo) mi).getFullRelationType();
                    String tagName = RelationUtil.classAndType(relType)[1];
                    if (tagName.equals(inlineTagName)) {
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
        return inlineTagName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ContextSize))
            return false;
        ContextSize that = (ContextSize) o;
        return before == that.before && after == that.after && includeHit == that.includeHit
                && Objects.equals(inlineTagName, that.inlineTagName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(before, after, includeHit, inlineTagName);
    }

    @Override
    public String toString() {
        return "ContextSize{" +
                "before=" + before +
                ", after=" + after +
                ", includeHit=" + includeHit +
                ", inlineTagName='" + inlineTagName + '\'' +
                '}';
    }

    public boolean isNone() {
        return before == 0 && after == 0;
    }

    /**
     * Return a version of this context size clamped to a maximum before/after size.
     * @param max maximum value before and after may have
     * @return a version of this context size clamped to a maximum before/after size
     */
    public ContextSize clampedTo(int max) {
        if (before <= max && after <= max)
            return this;
        return new ContextSize(Math.min(before, max), Math.min(after, max), includeHit, inlineTagName);
    }
}
