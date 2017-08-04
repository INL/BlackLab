package nl.inl.blacklab.index.config;

import java.util.Collections;
import java.util.Map;

/** Information about an inline object while parsing.
 *
 *  Inline objects are either elements like p, s, b, ne, etc. that
 *  occur within the text of annotated fields, or punctuation occurring
 *  between the words.
 */
class InlineObject implements Comparable<InlineObject> {

	public static enum InlineObjectType {
		OPEN_TAG,
		CLOSE_TAG,
		PUNCTUATION
	}

    private String text;

    private int offset;

    private int length;

    private InlineObjectType type;

    /** The close tag to this open tag, or vice versa */
    private InlineObject matchingTag;

    private Map<String, String> attributes;

    public InlineObject(String text, int offset, int length, InlineObjectType type, Map<String, String> attributes) {
        super();
        this.text = text;
        this.offset = offset;
        this.length = length;
        this.type = type;
        this.attributes = Collections.emptyMap();
        if (attributes != null)
        	this.attributes = attributes;
    }

    public String getText() {
        return text;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public InlineObjectType type() {
        return type;
    }

    public void setMatchingTag(InlineObject matchingTag) {
        this.matchingTag = matchingTag;
    }

    public InlineObject getMatchingTag() {
        return matchingTag;
    }

    public long fragment() {
        return offset | ((long)length << 32);
    }

    public Map<String, String> getAttributes() {
    	return attributes;
    }

    @Override
    public int compareTo(InlineObject o) {
        if (o.offset != offset)
            return Long.compare(offset, o.offset);
        if (o.type != type)
            return type.compareTo(o.type);
        return text.compareTo(o.text);
    }

    @Override
    public String toString() {
    	return type.toString() + " " + text;
    }

}