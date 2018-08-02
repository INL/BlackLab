package nl.inl.blacklab.index.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    private InlineObjectType type;

    /** The close tag to this open tag, or vice versa */
    private InlineObject matchingTag;

    private Map<String, String> attributes;

    public InlineObject(String text, int offset, InlineObjectType type, Map<String, String> attributes) {
        super();
        this.text = text;
        this.offset = offset;
        if (offset < 0)
            throw new RuntimeException();
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

    public InlineObjectType type() {
        return type;
    }

    public void setMatchingTag(InlineObject matchingTag) {
        this.matchingTag = matchingTag;
    }

    public InlineObject getMatchingTag() {
        return matchingTag;
    }

    public Map<String, String> getAttributes() {
    	return attributes;
    }

    @Override
    public int compareTo(InlineObject o) {
        if (offset == o.offset) {
            // Self-closing tag. Make sure open tag sorts before close tag
            return type == InlineObjectType.OPEN_TAG ? -1 : 1;
        }
        return offset - o.offset;
    }
    
    @Override
    public boolean equals(Object obj) {
        return obj instanceof InlineObject ? compareTo((InlineObject) obj) == 0 : false;
    }

    @Override
    public String toString() {
    	return type.toString() + " " + text;
    }

    public static void main(String[] args) {
        List<InlineObject> l = new ArrayList<>();
        l.add(new InlineObject("bla", 0, InlineObjectType.OPEN_TAG, null));
        l.add(new InlineObject("zwets", 1, InlineObjectType.OPEN_TAG, null));
        l.add(new InlineObject("zwets", 2, InlineObjectType.CLOSE_TAG, null));
        l.add(new InlineObject("bla", 3, InlineObjectType.CLOSE_TAG, null));
        Collections.sort(l);
        System.out.println(l);
    }

}