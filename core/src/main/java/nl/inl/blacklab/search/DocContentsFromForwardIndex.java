package nl.inl.blacklab.search;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;

import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * (Part of) the contents of a document, in separate properties read from the
 * forward indices.
 *
 * The tokens list in this class stores all the properties for each word, in
 * this order:
 * 
 * <ul>
 * <li>punctuation before this word ("punct")
 * <li>all other properties except punctuation and word (e.g. "lemma", "pos")
 * <li>the word itself ("word")
 * </ul>
 *
 * So if you had "lemma" and "pos" as extra properties in addition to "punct"
 * and "word", and you had 10 words of context, the List size would be 40.
 *
 * (The reason for the specific ordering is ease of converting it to XML, with
 * the extra properties being attributes and the word itself being the element
 * content of the word tags)
 */
public class DocContentsFromForwardIndex extends DocContents {

    /**
     * What properties are stored in what order for this Kwic (e.g. word, lemma,
     * pos)
     */
    List<Annotation> properties;

    /**
     * Word properties for context left of match (properties.size() values per word;
     * e.g. punct 1, lemma 1, pos 1, word 1, punct 2, lemma 2, pos 2, word 2, etc.)
     */
    List<String> tokens;

    /**
     * Construct DocContentsFromForwardIndex object.
     *
     * @param properties the order of properties in the tokens list
     * @param tokens the tokens
     */
    public DocContentsFromForwardIndex(List<Annotation> properties, List<String> tokens) {
        this.properties = properties;
        this.tokens = tokens;
    }

    public List<Annotation> getProperties() {
        return Collections.unmodifiableList(properties);
    }

    public List<String> getTokens() {
        return Collections.unmodifiableList(tokens);
    }

    /**
     * Get the tokens of a specific property
     * 
     * @param property the property to get the tokens for
     * @return the tokens
     */
    public List<String> getTokens(Annotation property) {
        return getSinglePropertyContext(property);
    }

    @Override
    public String getXml() {
        int valuesPerWord = properties.size();
        int numberOfWords = tokens.size() / valuesPerWord;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < numberOfWords; i++) {
            int vIndex = i * valuesPerWord;
            int j = 0;
            if (i > 0)
                b.append(StringEscapeUtils.escapeXml10(tokens.get(vIndex)));
            b.append("<w");
            for (int k = 1; k < properties.size() - 1; k++) {
                String name = properties.get(k).name();
                String value = tokens.get(vIndex + 1 + j);
                b.append(' ').append(name).append("=\"").append(StringEscapeUtils.escapeXml10(value)).append('"');
                j++;
            }
            b.append('>');
            b.append(StringEscapeUtils.escapeXml10(tokens.get(vIndex + 1 + j)));
            b.append("</w>");
        }
        return b.toString();
    }

    /**
     * Get the context of a specific property from the complete context list.
     *
     * @param property the property to get the context for
     * @return the context for this property
     */
    private List<String> getSinglePropertyContext(Annotation property) {
        final int nProp = properties.size();
        final int size = tokens.size() / nProp;
        final int propIndex = properties.indexOf(property);
        if (propIndex == -1)
            return null;
        return new AbstractList<String>() {
            @Override
            public String get(int index) {
                return tokens.get(propIndex + nProp * index);
            }

            @Override
            public int size() {
                return size;
            }
        };
    }

}
