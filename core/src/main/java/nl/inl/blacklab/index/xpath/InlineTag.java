package nl.inl.blacklab.index.xpath;

/** Information about an inline tag
 *  (tags of elements such as p, s, b, ne, etc. that occur within the text of annotated fields)
 */
class InlineTag implements Comparable<InlineTag> {

    private String tagName;

    private int offset;

    private int length;

    private boolean isStartTag;

    /** The close tag to this open tag, or vice versa */
    private InlineTag matchingTag;

    public InlineTag(String tagName, int offset, int length, boolean isStartTag) {
        super();
        this.tagName = tagName;
        this.offset = offset;
        this.length = length;
        this.isStartTag = isStartTag;
    }

    public String getTagName() {
        return tagName;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public boolean isStartTag() {
        return isStartTag;
    }

    public void setMatchingTag(InlineTag matchingTag) {
        this.matchingTag = matchingTag;
    }

    public InlineTag getMatchingTag() {
        return matchingTag;
    }

    public long fragment() {
        return offset | ((long)length << 32);
    }

    @Override
    public int compareTo(InlineTag o) {
        if (o.offset != offset)
            return Long.compare(offset, o.offset);
        if (o.isStartTag != isStartTag)
            return Boolean.compare(isStartTag, o.isStartTag);
        return tagName.compareTo(o.tagName);
    }

}