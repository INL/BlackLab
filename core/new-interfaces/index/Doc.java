package nl.inl.blacklab.interfaces.index;

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.CompositeReader;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.LeafReader;

import nl.inl.blacklab.highlight.XmlHighlighter.UnbalancedTagsStrategy;
import nl.inl.blacklab.interfaces.results.Hit;
import nl.inl.blacklab.interfaces.results.Hits;
import nl.inl.blacklab.interfaces.struct.AnnotatedField;
import nl.inl.blacklab.interfaces.struct.Field;

/**
 * Combination of a document in the everse index, forward index and 
 * content store.
 * 
 * Note that Doc, like Hit, can be ephemeral, so call save() if you
 * need an immutable copy of it.
 */
public interface Doc {
    
    /**
     * Like {@link Hit#save()}, this will return an immutable version of this instance.
     * 
     * An ephemeral implementation will return an immutable copy; an immutable implementation
     * will just return itself.
     * 
     * @return immutable version of this instance
     */
    Doc save();
    
    /** @return index this document came from (may be a wrapped {@link LeafReader} or {@link CompositeReader}) */
    BlackLabIndex index();
    
    /** @return Lucene id of the document. */
    int id();
    
    /**
     * @return Lucene document 
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     */
    default Document luceneDoc() throws IOException {
        return index().reader().document(id());
    }
    
    /** Get our forward index document for the specified field.
     * 
     * @param field the field
     * @return Forward index document */
    ForwardIndexDoc forwardIndexDoc(AnnotatedField field);
    
    /** Get our content store document for the specified field.
     * 
     * @param field the field
     * @return Content store document */
    ContentStoreDoc contentStoreDoc(Field field);
    
    /**
     * Get part of the contents of a field from a Lucene Document.
     *
     * This takes into account that some fields are stored externally in content stores
     * instead of in the Lucene index.
     *
     * @param field the field
     * @param startAtChar where to start getting the content (-1 for start of document, 0 for first char)
     * @param endAtChar where to end getting the content (-1 for end of document)
     * @return the field content
     */
    String getContentByCharPos(Field field, int startAtChar, int endAtChar);

    /**
     * Get part of the contents of a field from a Lucene Document.
     *
     * This takes into account that some fields are stored externally in content stores
     * instead of in the Lucene index.
     *
     * @param field the field
     * @param startAtWord where to start getting the content (-1 for start of document, 0 for first word)
     * @param endAtWord where to end getting the content (-1 for end of document)
     * @return the field content
     */
    String getContent(Field field, int startAtWord, int endAtWord);
    
    /**
     * Highlight part of field content with the specified hits,
     * and make sure it's well-formed.
     *
     * Uses &lt;hl&gt;&lt;/hl&gt; tags to highlight the content.
     *
     * @param field field to highlight
     * @param hits the hits
     * @param startAtWord where to start highlighting (first word returned)
     * @param endAtWord where to end highlighting (first word not returned)
     * @param unbalancedStrat what to do with unbalanced tags
     * @return the highlighted content
     */
    String highlightContent(Field field, Hits hits, int startAtWord, int endAtWord, UnbalancedTagsStrategy unbalancedStrat);
    
}
