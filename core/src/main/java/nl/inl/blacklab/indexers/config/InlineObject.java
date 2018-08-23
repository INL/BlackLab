package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * Information about an inline object while parsing.
 *
 * Inline objects are either elements like p, s, b, ne, etc. that occur within
 * the text of annotated fields, or punctuation occurring between the words.
 */
class InlineObject implements Comparable<InlineObject> {

    public enum InlineObjectType {
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
            throw new BlackLabRuntimeException("Inline object with offset < 0");
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
    public String toString() {
        return type.toString() + " " + text;
    }

    public static void main(String[] args) {
        List<InlineObject> l = new ArrayList<>();
        l.add(new InlineObject("bla", 0, InlineObjectType.OPEN_TAG, null));
        l.add(new InlineObject("zwets", 1, InlineObjectType.OPEN_TAG, null));
        l.add(new InlineObject("zwets", 2, InlineObjectType.CLOSE_TAG, null));
        l.add(new InlineObject("bla", 3, InlineObjectType.CLOSE_TAG, null));
        l.sort(Comparator.naturalOrder());
        System.out.println(l);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((attributes == null) ? 0 : attributes.hashCode());
        result = prime * result + ((matchingTag == null) ? 0 : matchingTag.hashCode());
        result = prime * result + offset;
        result = prime * result + ((text == null) ? 0 : text.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        InlineObject other = (InlineObject) obj;
        if (attributes == null) {
            if (other.attributes != null)
                return false;
        } else if (!attributes.equals(other.attributes))
            return false;
        if (matchingTag == null) {
            if (other.matchingTag != null)
                return false;
        } else if (!matchingTag.equals(other.matchingTag))
            return false;
        if (offset != other.offset)
            return false;
        if (text == null) {
            if (other.text != null)
                return false;
        } else if (!text.equals(other.text))
            return false;
        return type == other.type;
    }

}
