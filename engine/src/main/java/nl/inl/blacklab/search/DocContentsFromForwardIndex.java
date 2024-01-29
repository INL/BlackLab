package nl.inl.blacklab.search;

import java.util.Collections;
import java.util.List;

import org.apache.commons.text.StringEscapeUtils;

import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * (Part of) the contents of a document, in separate annotations read from the
 * forward indices.
 *
 * The tokens list in this class stores all the annotations for each word, in
 * this order:
 * 
 * <ul>
 * <li>punctuation before this word ("punct")
 * <li>all other annotations except punctuation and word (e.g. "lemma", "pos")
 * <li>the word itself ("word")
 * </ul>
 *
 * So if you had "lemma" and "pos" as extra annotations in addition to "punct"
 * and "word", and you had 10 words of context, the List size would be 40.
 *
 * (The reason for the specific ordering is ease of converting it to XML, with
 * the extra annotations being attributes and the word itself being the element
 * content of the word tags)
 */
public class DocContentsFromForwardIndex extends DocContents {

    /**
     * What annotations are stored in what order for this Kwic (e.g. word, lemma,
     * pos)
     */
    final List<Annotation> annotations;

    /**
     * Word annotations for context left of match (annotations.size() values per word;
     * e.g. punct 1, lemma 1, pos 1, word 1, punct 2, lemma 2, pos 2, word 2, etc.)
     */
    final List<String> tokens;

    /**
     * Construct DocContentsFromForwardIndex object.
     *
     * @param annotations the order of annotations in the tokens list
     * @param tokens the tokens
     */
    public DocContentsFromForwardIndex(List<Annotation> annotations, List<String> tokens) {
        this.annotations = annotations;
        this.tokens = tokens;
    }

    public List<Annotation> annotations() {
        return Collections.unmodifiableList(annotations);
    }

    public List<String> tokens() {
        return Collections.unmodifiableList(tokens);
    }

    @Override
    public String xml() {
        int valuesPerWord = annotations.size();
        int numberOfWords = tokens.size() / valuesPerWord;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < numberOfWords; i++) {
            int vIndex = i * valuesPerWord;
            int j = 0;
            if (i > 0)
                b.append(StringEscapeUtils.escapeXml10(tokens.get(vIndex)));
            b.append("<w");
            for (int k = 1; k < annotations.size() - 1; k++) {
                String name = annotations.get(k).name();
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

    public int length() {
        return tokens.size() / annotations.size();
    }
}
