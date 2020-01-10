package nl.inl.blacklab.search;

import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.CompositeReader;
import org.apache.lucene.index.LeafReader;

import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.util.XmlHighlighter;

/**
 * Combination of a document in the everse index, forward index and 
 * content store.
 * 
 * Note that Doc, like Hit, can be ephemeral, so call save() if you
 * need an immutable copy of it.
 */
public interface Doc {
    
    static Doc get(BlackLabIndex index, int id) {
        return new DocImpl(index, id);
    }
    
    /** @return index this document came from (may be a wrapped {@link LeafReader} or {@link CompositeReader}) */
    BlackLabIndex index();
    
    /** @return Lucene id of the document. */
    int id();
    
    /**
     * Retrieve a Lucene Document object from the index.
     *
     * NOTE: you must check if the document isn't deleted using Search.isDeleted()
     * first! Lucene 4.0+ allows you to retrieve deleted documents, making you
     * responsible for checking whether documents are deleted or not. (This doesn't
     * apply to search results; searches should never produce deleted documents. It
     * does apply when you're e.g. iterating over all documents in the index)
     *
     * @param docId the document id
     * @return the Lucene Document
     * @throws RuntimeException if the document doesn't exist (use maxDoc() and
     *             isDeleted() to check first!)
     */
    Document luceneDoc();
    
    /**
     * Have we loaded and cached the Lucene document?
     * @return true iff we have loaded the Lucene document already
     */
    boolean isLuceneDocCached();
    
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
    String contentsByCharPos(Field field, int startAtChar, int endAtChar);
    
    /**
     * Get the contents of a field from a Lucene Document.
     *
     * This takes into account that some fields are stored externally in content
     * stores instead of in the Lucene index.
     *
     * @param docId the Document id
     * @param field the field
     * @return the field content
     */
    default String contents(Field field) {
        return contents(field, -1, -1);
    }

    /**
     * Get the document contents (original XML).
     *
     * @param docId the Document id
     * @return the field content
     */
    default String contents() {
        return contents(index().mainAnnotatedField(), -1, -1);
    }

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
    String contents(Field field, int startAtWord, int endAtWord);
    
    /**
     * Highlight part of field content with the specified hits,
     * and make sure it's well-formed.
     *
     * Uses &lt;hl&gt;&lt;/hl&gt; tags to highlight the content.
     *
     * @param hits the hits
     * @param startAtWord where to start highlighting (first word returned)
     * @param endAtWord where to end highlighting (first word not returned)
     * @return the highlighted content
     */
    String highlightContent(Hits hits, int startAtWord, int endAtWord);

    /**
     * Highlight field content with the specified hits.
     *
     * Uses &lt;hl&gt;&lt;/hl&gt; tags to highlight the content.
     *
     * @param docId document to highlight a field from
     * @param hits the hits
     * @return the highlighted content
     */
    default String highlightContent(Hits hits) {
        return highlightContent(hits, -1, -1);
    }
    
    void characterOffsets(Field field, int[] startsOfWords, int[] endsOfWords,
            boolean fillInDefaultsIfNotFound);

    /**
     * Determine the concordance strings for a number of concordances, given the
     * relevant character positions.
     *
     * Every concordance requires four character positions: concordance start and
     * end, and hit start and end. Visualising it ('fox' is the hit word):
     *
     * [A] the quick brown [B] fox [C] jumps over the [D]
     *
     * The startsOfWords array contains the [A] and [B] positions for each
     * concordance. The endsOfWords array contains the [C] and [D] positions for
     * each concordance.
     *
     * @param doc the Lucene document number
     * @param field the field
     * @param startsOfWords the array of starts of words ([A] and [B] positions)
     * @param endsOfWords the array of ends of words ([C] and [D] positions)
     * @param hl
     * @return the list of concordances
     */
    List<Concordance> makeConcordancesFromContentStore(Field field, int[] startsOfWords,
            int[] endsOfWords, XmlHighlighter hl);

    @Override
    boolean equals(Object obj);
    
    @Override
    int hashCode();
    
}
