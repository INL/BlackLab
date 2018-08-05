package nl.inl.blacklab.interfaces.index;

import java.io.Closeable;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.CompositeReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.interfaces.ContextSize;
import nl.inl.blacklab.interfaces.SearchSettings;
import nl.inl.blacklab.interfaces.results.Hit;
import nl.inl.blacklab.interfaces.search.SearchForHitGroups;
import nl.inl.blacklab.interfaces.search.SearchForHits;
import nl.inl.blacklab.interfaces.struct.AnnotatedField;
import nl.inl.blacklab.interfaces.struct.Annotation;
import nl.inl.blacklab.interfaces.struct.Field;
import nl.inl.blacklab.interfaces.struct.IndexMetadata;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.ParseException;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * Represents a BlackLab index (or atomic 'leaf' of it)
 * 
 * (Formerly called Searcher)
 * 	
 * This wraps an {@link IndexReader} and can be used both on a 
 * {@link CompositeReader} (which represents the whole index) or a {@link LeafReader}
 * (which represents a subset of documents from the index, and can often
 * be processed separately, e.g. in parallel).
 * 
 * Calling the methods directly on a LeafReader instance of the BlackLabIndex object 
 * is often more efficient, because you don't have to go through CompositeReader 
 * to figure out where a document is stored.
 * 
 * You can call {@link BlackLabIndex#leafIndexes()} to get all the wrapped LeafReaders.
 */
public interface BlackLabIndex extends Closeable {
	
	// Settings
	// ===========================================================
	// Programs should be able to change several (default) settings,
	// including logging, search and indexing settings.
	
	/**
	 * Set some default settings related to logging, search, etc.
	 * 
	 * For example: what types of activity to log, the maximum number of hits to process, etc.
	 * 
	 * @param sett search settings
	 */
	void settings(SearchSettings sett);
	
	// TODO: how to customize search settings for a specific search? extra parameter to find()?
	
	// TODO: indexing settings?
	
	/**
	 * Get the analyzer for indexing and searching.
	 * @return the analyzer
	 */
	Analyzer analyzer();
	
	/**
	 * Is this a newly created, empty index?
	 * @return true if it is, false if not
	 */
	boolean isEmpty();
	
	
	// Search-related
	// ===========================================================
	// We should provide convenient ways to run searches.
	
	/**
	 * Create a {@link BLSpanQuery} from a TextPattern.
	 * 
	 * (Eventually, we can probably get rid of TextPattern and just move that functionality 
	 *  to BLSpanQuery classes directly instead) 
	 * 
	 * @param pattern pattern to search for
	 * @param field field to search
	 * @param filter document filter to apply (e.g. metadata search)
	 * @return query
	 */
	BLSpanQuery createSpanQuery(TextPattern pattern, AnnotatedField field, Query filter);

    /**
     * Find {@link TextPattern} in field and filter. 
     * 
     * @param cqlQuery pattern to search for
     * @param field field to search, e.g. "contents"
     * @param filter document filter to apply (e.g. metadata search) or null for none
     * @return hits
     * @throws ParseException if the query failed to parse 
     */
    default SearchForHits find(String cqlQuery, AnnotatedField field, Query filter) throws ParseException {
        return find(CorpusQueryLanguageParser.parse(cqlQuery), field, filter);
    }
    
    /**
     * Find {@link TextPattern} in field and filter. 
     * 
     * @param cqlQuery pattern to search for
     * @param field field to search, e.g. "contents"
     * @return hits
     * @throws ParseException 
     */
    default SearchForHits find(String cqlQuery, AnnotatedField field) throws ParseException {
        return find(cqlQuery, field, null);
    }
    
    /**
     * Find {@link TextPattern} in field and filter. 
     * 
     * @param pattern pattern to search for
     * @param field field to search, e.g. "contents"
     * @param filter document filter to apply (e.g. metadata search) or null for none
     * @return hits
     */
    SearchForHits find(TextPattern pattern, AnnotatedField field, Query filter);
    
    /**
     * Find {@link TextPattern} in field and filter. 
     * 
     * @param pattern pattern to search for
     * @param field field to search, e.g. "contents"
     * @return hits
     */
    default SearchForHits find(TextPattern pattern, AnnotatedField field) {
        return find(pattern, field, null);
    }
    
    /**
     * Execute query, returning hits. 
     * @param query
     * @param fieldNameConc
     * @return hits
     */
    SearchForHits find(BLSpanQuery query);
    
    /**
     * Perform a document query only (no hits)
     * 
     * @param query the document-level query
     * @return the matching documents
     */
    SearchForHitGroups findDocs(Query query);

	/**
	 * Explain how a {@link TextPattern} is rewritten into a {@link BLSpanQuery} and optimized.
	 * 
	 * @param pattern pattern to search for
	 * @param field field to search, e.g. "contents"
	 * @param filter document filter to apply (e.g. metadata search)
	 * @return explanation
	 */
	QueryExplanation explain(TextPattern pattern, AnnotatedField field, Query filter);
	
	/**
	 * Return the list of terms that occur in a field.
	 *
	 * @param field the field
	 * @param maxResults maximum number to return (or HitsSettings.UNLIMITED (== -1) for no limit)
	 * @return the matching terms
	 */
	List<String> fieldTerms(AnnotatedField field, int maxResults);
	

	// Lucene index / forward index / content store
	// ===========================================================
	// (Advanced)
	// Programs should have access to the Lucene index objects, the
	// forward index and content store for implementing additional features.
	
	/**
	 * Get information about the structure of the BlackLab index.
	 *
	 * @return the structure object
	 */
	IndexMetadata structure();
	
	/**
	 * Return the Lucene index searcher.
	 * @return Lucene index searcher
	 */
	IndexSearcher searcher();

	/**
	 * Return the Lucene index reader, from which documents can be retrieved, etc.
	 * @return Lucene index reader
	 */
	IndexReader reader();
	
	/**
	 * Get the leaves of this index.
	 * 
	 * If our reader is a {@link CompositeReader}, return BlackLabIndex instances for the 
	 * readers associated with its leaf contexts. If not, it will return itself.
	 * 
	 * This allows us to use the same methods on both the CompositeReader and
	 * on the {@link LeafReader} if we have it available, improving efficiency and concurrency.
	 *  
	 * @return leaf BLIndex instances
	 */
	List<BlackLabIndex> leafIndexes();
	
	/**
	 * Get a BlackLab document.
	 * 
	 * A BlackLab document represents the combination of a document in the
	 * reverse index, forward index and content store.
	 * 
	 * @param docId Lucene id of the document
	 * @return BlackLab document
	 */
	Doc doc(int docId);
	
	/**
	 * Get an iterator over all Lucene documents.
	 * 
	 * @return iterator over Lucene documents
	 */
	Iterator<Doc> docIterator();
	
	/**
	 * Stream all Lucene documents sequentially.
	 * 
	 * @return stream of Lucene documents
	 */
	Stream<Doc> docStream();
	
    /**
     * Gets the forward index for a field.
     * 
     * NOTE: it is usually better to get a {@link Doc} and call {@link Doc#forwardIndexDoc(AnnotatedField) forwardIndexDoc(AnnotatedField)} on that.
     * 
     * @param field the field
     * @return the ForwardIndex object
     */
    ForwardIndex forwardIndex(AnnotatedField field);
    
    /**
     * Get the content store for a field.
     *
     * NOTE: it is usually better to get a {@link Doc} and call {@link Doc#contentStoreDoc(Field) contentStoreDoc(Field)} on that.
     * 
     * @param field the field
     * @return the content store, or null if there is no content store for this field
     */
    ContentStore contentStore(Field field);
    
    
	// Output-related (kwic, concordance, highlighting)
	// ===========================================================
	// Programs should be able to show results the way they want,
	// be it as a KWIC view, concordances from the original content,
	// highlighted documents, etc.
	
	/**
	 * Get keyword in context for a hit.
	 * 
	 * KWIC is made by getting a snippet with the different annotations
	 * from the forward index.
	 * 
	 * @param hit hit to get Kwic for
	 * @param field what field the hit comes from
	 * @param size context size around the hit
	 * @return keyword in context for the hit
	 */
	Kwic kwic(Hit hit, AnnotatedField field, ContextSize size);
	
	/**
	 * Get concordance for a hit.
	 * 
	 * A concordance is a snippet cut from the original document based
	 * on the word positions of the hit (or other annotation).
	 * 
	 * @param hit hit to get Concordance for
	 * @param annotation what annotation to use
	 * @param size context size around the hit
	 * @return keyword in context for the hit
	 */
	Concordance concordance(Hit hit, Annotation annotation, ContextSize size);
	
}
