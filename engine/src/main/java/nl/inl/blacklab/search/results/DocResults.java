/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.Weight;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.DocPropertyAnnotatedFieldLength;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDoc;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueDoc;
import nl.inl.blacklab.resultproperty.PropertyValueInt;
import nl.inl.blacklab.search.results.Hits.EphemeralHit;
import nl.inl.blacklab.search.results.Hits.HitsArrays;

/**
 * A list of DocResult objects (document-level query results).
 */
public class DocResults extends ResultsList<DocResult, DocProperty> implements ResultGroups<Hit> {

    static final Logger logger = LogManager.getLogger(DocResults.class);

    private static final class SimpleDocCollector extends SimpleCollector {
        private final List<DocResult> results;
        private final QueryInfo queryInfo;
        private int docBase;

        SimpleDocCollector(List<DocResult> results, QueryInfo queryInfo) {
            this.results = results;
            this.queryInfo = queryInfo;
        }

        @Override
        protected void doSetNextReader(LeafReaderContext context)
                throws IOException {
            docBase = context.docBase;
            super.doSetNextReader(context);
        }

        @Override
        public void collect(int docId) throws IOException {
            int globalDocId = docId + docBase;
            results.add(DocResult.fromDoc(queryInfo, new PropertyValueDoc(queryInfo.index().doc(globalDocId)), 0.0f, 0));
        }

        @Override
        public void setScorer(Scorer scorer) {
            // (ignore)
        }

        @Override
        public boolean needsScores() {
            return false;
        }
    }

    /**
     * Construct an empty DocResults.
     * @param queryInfo query info
     * @return document results
     */
    public static DocResults empty(QueryInfo queryInfo) {
        return new DocResults(queryInfo);
    }

    /**
     * Construct a DocResults from a list of results.
     *
     * @param queryInfo query info
     * @param results results
     * @param sampleParameters sample parameters (if this is a sample)
     * @param windowStats window stats (if this is a window)
     * @return document results
     */
    public static DocResults fromList(QueryInfo queryInfo, List<DocResult> results, SampleParameters sampleParameters, WindowStats windowStats) {
        return new DocResults(queryInfo, results, sampleParameters, windowStats);
    }

    /**
     * Construct a DocResults from a Hits instance.
     *
     * @param queryInfo query info
     * @param hits hits to get document results from
     * @param maxHitsToStorePerDoc how many hits to store per document, for displaying snippets (-1 for all)
     * @return document results
     */
    public static DocResults fromHits(QueryInfo queryInfo, Hits hits, int maxHitsToStorePerDoc) {
        return new DocResults(queryInfo, hits, maxHitsToStorePerDoc);
    }

    /**
     * Don't use this, use BlackLabIndex.queryDocuments().
     *
     * @param queryInfo query info
     * @param query query to execute
     * @return per-document results
     */
    public static DocResults fromQuery(QueryInfo queryInfo, Query query) {
        return new DocResults(queryInfo, query);
    }

    /**
     * Iterator in our source hits object
     */
    private Iterator<EphemeralHit> sourceHitsIterator;

    /**
     * A partial list of hits in a doc, because we stopped iterating through the
     * Hits. (or null if we don't have partial doc hits) Pick this up when we
     * continue iterating through it.
     */
    private HitsArrays partialDocHits;

    /**
     * id of the partial doc we've done (because we stopped iterating through the
     * Hits), or -1 for no partial doc.
     */
    private int partialDocId = -1;

    private HitPropertyDoc groupByDoc;

    Lock ensureResultsReadLock;

    /** Largest number of hits in a single document */
    private int mostHitsInDocument = 0;

    private int totalHits = 0;

    private int resultObjects = 0;

    private WindowStats windowStats;

    private SampleParameters sampleParameters;

    private int maxHitsToStorePerDoc = 0;

    /** The Query this was created from, or null if it wasn't created from a Query.
     *
     * If created from a query, we can re-execute that query to e.g. count tokens in matching documents.
     */
    private Query query;

    /**
     * Total number of documents/tokens in the matched documents, or null if not yet determined.
     */
    private CorpusSize corpusSize = null;

    /**
     * Construct an empty DocResults.
     * @param queryInfo
     */
    protected DocResults(QueryInfo queryInfo) {
        super(queryInfo);
    }

    /**
     * Construct per-document results objects from a Hits object
     *
     * @param queryInfo query info
     * @param hits the hits to view per-document
     * @param maxHitsToStorePerDoc hits to store per document
     */
    protected DocResults(QueryInfo queryInfo, Hits hits, int maxHitsToStorePerDoc) {
        this(queryInfo);
        this.groupByDoc = (HitPropertyDoc) new HitPropertyDoc(queryInfo.index()).copyWith(hits, null, false);
        this.sourceHitsIterator = hits.ephemeralIterator();
        this.maxHitsToStorePerDoc = maxHitsToStorePerDoc;
        partialDocHits = null;
        ensureResultsReadLock = new ReentrantLock();
    }

    /**
     * Wraps a list of DocResult objects with the DocResults interface.
     *
     * NOTE: the list is not copied but referenced!
     *
     * Used by DocGroups constructor.
     *
     * @param queryInfo query info
     * @param results the list of results
     * @param windowStats window stats
     */
    protected DocResults(QueryInfo queryInfo, List<DocResult> results, SampleParameters sampleParameters, WindowStats windowStats) {
        this(queryInfo);
        this.results = results;
        this.sampleParameters = sampleParameters;
        this.windowStats = windowStats;
    }

    private DocResults(QueryInfo queryInfo, Query query) {
        this(queryInfo);
        this.query = query;
        // TODO: a better approach is to only read documents we're actually interested in instead of all of them; compare with Hits.
        //    even better: make DocResults abstract and provide two implementations, DocResultsFromHits and DocResultsFromQuery.
        results = new ArrayList<>();
        try {
            queryInfo.index().searcher().search(query, new SimpleDocCollector(results, queryInfo));
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public WindowStats windowStats() {
        return windowStats;
    }

    @Override
    public SampleParameters sampleParameters() {
        return sampleParameters;
    }

    @Override
    public DocResults sort(DocProperty sortProp) {
        ensureAllResultsRead();
        List<DocResult> sorted = new ArrayList<DocResult>(this.results);
        sorted.sort(sortProp);
        return DocResults.fromList(queryInfo(), sorted, (SampleParameters)null, (WindowStats)null);
    }

    /**
     * Determines if there are at least a certain number of results
     *
     * This may be used if we don't want to process all results (which may be a lot)
     * but we do need to know something about the size of the result set (such as
     * for paging).
     *
     * @param lowerBound the number we're testing against
     *
     * @return true if the size of this set is at least lowerBound, false otherwise.
     */
    public boolean docsProcessedAtLeast(int lowerBound) {
        return resultsProcessedAtLeast(lowerBound);
    }

    public int docsProcessedSoFar() {
        return resultsProcessedSoFar();
    }

    /**
     * Get the number of documents in this results set.
     *
     * Note that this returns the number of document results available; if there
     * were so many hits that not all were retrieved (call maxHitsRetrieved()), you
     * can find the grand total of documents by calling totalSize().
     *
     * @return the number of documents.
     */
    protected int docsProcessedTotal() {
        return size();
    }

    /**
     * If we still have only partially read our Hits object, read some more of it
     * and add the hits.
     *
     * @param index the number of results we want to ensure have been read, or
     *            negative for all results
     */
    @Override
    protected void ensureResultsRead(int index) {
        try {
            if (doneProcessingAndCounting() || (index >= 0 && results.size() > index))
                return;

            while (!ensureResultsReadLock.tryLock()) {
                /*
                * Another thread is already counting, we don't want to straight up block until it's done
                * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction
                * So instead poll our own state, then if we're still missing results after that just count them ourselves
                */
                Thread.sleep(50);
                if (doneProcessingAndCounting() || (index >= 0 && results.size() >= index))
                    return;
            }

            try {
                // Fill list of document results
                HitsArrays docHits = partialDocHits;
                int lastDocId = partialDocId;

                while (sourceHitsIterator.hasNext() && (index < 0 || index > results.size())) {
                    EphemeralHit h = sourceHitsIterator.next();
                    int curDoc = h.doc;
                    if (curDoc != lastDocId) {
                        if (docHits != null) {
                            PropertyValueDoc doc = new PropertyValueDoc(index().doc(lastDocId));
                            Hits hits = Hits.fromList(queryInfo(), docHits, null);
                            int size = docHits.size();
                            addDocResultToList(doc, hits, size);
                        }

                        docHits = new HitsArrays();
                    }

                    docHits.add(h);
                    lastDocId = curDoc;
                }

                // add the final dr instance to the results collection
                if (docHits != null && docHits.size() > 0) {
                    if (sourceHitsIterator.hasNext()) {
                        partialDocId = lastDocId;
                        partialDocHits = docHits; // not done, continue from here later
                    } else {
                        PropertyValueDoc doc = new PropertyValueDoc(index().doc(lastDocId));
                        Hits hits = Hits.fromList(queryInfo(), docHits, null);
                        int size = docHits.size();
                        addDocResultToList(doc, hits, size);
                        sourceHitsIterator = null; // allow this to be GC'ed
                        partialDocHits = null;
                    }
                }
            } finally {
                ensureResultsReadLock.unlock();
            }
        } catch (InterruptedException e) {
            throw new InterruptedSearch(e);
        }
    }

    private void addDocResultToList(PropertyValueDoc doc, Hits docHits, int totalNumberOfHits) {
        DocResult docResult;
        if (maxHitsToStorePerDoc == 0)
            docResult = DocResult.fromHits(doc, Hits.immutableEmptyList(queryInfo()), totalNumberOfHits);
        else if (maxHitsToStorePerDoc > 0 && docHits.size() > maxHitsToStorePerDoc)
            docResult = DocResult.fromHits(doc, docHits.window(0, maxHitsToStorePerDoc), totalNumberOfHits);
        else
            docResult = DocResult.fromHits(doc, docHits, totalNumberOfHits);
        results.add(docResult);
        if (docHits.size() > mostHitsInDocument)
            mostHitsInDocument = docHits.size();
        totalHits += docHits.size();
        resultObjects += docHits.numberOfResultObjects() + 1;
    }

    @Override
    public DocGroups group(DocProperty groupBy, int maxResultsToStorePerGroup) {
        ensureAllResultsRead();

        Map<PropertyValue, List<DocResult>> groupLists = new HashMap<>();
        Map<PropertyValue, Integer> groupSizes = new HashMap<>();
        Map<PropertyValue, Long> groupTokenSizes = new HashMap<>();

        String tokenLengthFieldName = queryInfo().index().mainAnnotatedField().tokenLengthField();
        DocPropertyAnnotatedFieldLength fieldLengthProp = new DocPropertyAnnotatedFieldLength(queryInfo().index(), tokenLengthFieldName);

        for (DocResult r : this) {
            PropertyValue groupId = groupBy.get(r);
            List<DocResult> group = groupLists.get(groupId);
            if (group == null) {
                group = new ArrayList<>();
                groupLists.put(groupId, group);
            }
            if (maxResultsToStorePerGroup < 0 || group.size() < maxResultsToStorePerGroup)
                group.add(r);
            Integer groupSize = groupSizes.get(groupId);
            Long groupTokenSize = groupTokenSizes.get(groupId);
            long docLengthTokens = fieldLengthProp.get(r.identity().id());
            if (groupSize == null) {
                groupSize = 1;
                groupTokenSize = docLengthTokens;
            } else {
                groupSize++;
                groupTokenSize += docLengthTokens;
            }
            groupSizes.put(groupId, groupSize);
            groupTokenSizes.put(groupId, groupTokenSize);
        }
        List<DocGroup> results = new ArrayList<>();
        for (Map.Entry<PropertyValue, List<DocResult>> e : groupLists.entrySet()) {
            DocGroup docGroup = DocGroup.fromList(queryInfo(), e.getKey(), e.getValue(), groupSizes.get(e.getKey()), groupTokenSizes.get(e.getKey()));
            results.add(docGroup);
        }
        return DocGroups.fromList(queryInfo(), results, groupBy, (SampleParameters)null, (WindowStats)null);
    }

    /**
     * Get a window into the doc results
     *
     * @param first first document result to include
     * @param number maximum number of document results to include
     * @return the window
     */
    @Override
    public DocResults window(int first, int number) {
        List<DocResult> resultsWindow = Results.doWindow(this, first, number);
        boolean hasNext = resultsProcessedAtLeast(first + resultsWindow.size() + 1);
        WindowStats windowStats = new WindowStats(hasNext, first, number, resultsWindow.size());
        return DocResults.fromList(queryInfo(), resultsWindow, (SampleParameters)null, windowStats);
    }

    /**
     * Sum a property for all the documents.
     *
     * Can be used to calculate the total number of tokens in a subcorpus, for
     * example. Note that this does retrieve all results, so it may be slow for
     * large sets. In particular, you should try to call this method only for
     * DocResults created with BlackLabIndex.queryDocuments() (and not ones created with
     * Hits.perDocResults()) to avoid the overhead of fetching hits.
     *
     * @param numProp a numeric property to sum
     * @return the sum
     */
    public int intSum(DocProperty numProp) {
        ensureAllResultsRead();
        int sum = 0;
        for (DocResult result : results) {
            sum += ((PropertyValueInt) numProp.get(result)).value();
        }
        return sum;
    }

    @Override
    public int sumOfGroupSizes() {
        return totalHits;
    }

    @Override
    public int largestGroupSize() {
        ensureAllResultsRead();
        return mostHitsInDocument;
    }

    @Override
    public Group<Hit> get(PropertyValue prop) {
        ensureAllResultsRead();
        return results.stream().filter(d -> d.identity().equals(prop)).findFirst().orElse(null);
    }

    @Override
    public HitProperty groupCriteria() {
        return groupByDoc;
    }

    @Override
    public DocResults withFewerStoredResults(int maximumNumberOfResultsPerGroup) {
        if (maximumNumberOfResultsPerGroup < 0)
            maximumNumberOfResultsPerGroup = Integer.MAX_VALUE;
        List<DocResult> truncatedGroups = new ArrayList<>();
        for (DocResult group: results) {
            DocResult newGroup = DocResult.fromHits(group.identity(), group.storedResults().window(0, maximumNumberOfResultsPerGroup), group.size());
            truncatedGroups.add(newGroup);
        }
        return DocResults.fromList(queryInfo(), truncatedGroups, (SampleParameters)null, windowStats);
    }

    @Override
    public DocResults filter(DocProperty property, PropertyValue value) {
        List<DocResult> list = stream().filter(g -> property.get(g).equals(value)).collect(Collectors.toList());
        return DocResults.fromList(queryInfo(), list, (SampleParameters)null, (WindowStats)null);
    }

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters
     * @return the sample
     */
    @Override
    public DocResults sample(SampleParameters sampleParameters) {
        return DocResults.fromList(queryInfo(), Results.doSample(this, sampleParameters), sampleParameters, (WindowStats)null);
    }

    @Override
    public boolean doneProcessingAndCounting() {
        return sourceHitsIterator == null || !sourceHitsIterator.hasNext();
    }

    @Override
    public Map<PropertyValue, ? extends Group<Hit>> getGroupMap() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int numberOfResultObjects() {
        return resultObjects;
    }

    /**
     * Count total number of tokens in matching documents.
     *
     * This is fast if the query was created from a Query object (and the index contains DocValues),
     * but slower if it was created from Hits or a list of DocResult objects.
     *
     * @return total number of tokens in matching documents.
     * @deprecated use subcorpusSize().getTokens()
     */
    @Deprecated
    public long tokensInMatchingDocs() {
        return subcorpusSize().getTokens();
    }

    /**
     * Determine the size of the subcorpus defined by this set of documents.
     *
     * Counts number of documents and tokens.
     *
     * This is fast if the query was created from a Query object (and the index contains DocValues),
     * but slower if it was created from Hits or a list of DocResult objects.
     *
     * @return subcorpus size
     */
    public CorpusSize subcorpusSize() {
        return subcorpusSize(true);
    }

    /**
     * Determine the size of the subcorpus defined by this set of documents.
     *
     * Counts number of documents and tokens (if countTokens is true).
     *
     * This is fast if the query was created from a Query object (and the index contains DocValues),
     * but slower if it was created from Hits or a list of DocResult objects.
     *
     * @param countTokens whether or not to count tokens (slower)
     * @return subcorpus size
     */
    public CorpusSize subcorpusSize(boolean countTokens) {
        if (corpusSize == null || countTokens && !corpusSize.hasTokenCount()) {
            long numberOfTokens;
            int numberOfDocuments;
            if (query != null && queryInfo().index().mainAnnotatedField().hasTokenLengthDocValues()) {
                // Fast approach: use the DocValues for the token length field
//                logger.debug("## DocResults.tokensInMatchingDocs: fast path");
                try {
                    numberOfTokens = countTokens ? 0 : -1;
                    numberOfDocuments = 0;
                    Weight weight = queryInfo().index().searcher().createNormalizedWeight(query, false);
                    int subtractClosingToken = 1; // the count is always 1 too high because of the "extra closing token" (position for closing tags)
                    for (LeafReaderContext r: queryInfo().index().reader().leaves()) {
                        Scorer scorer = weight.scorer(r);
                        if (scorer != null) {
                            DocIdSetIterator it = scorer.iterator();
                            NumericDocValues tokenLengthValues = countTokens ? DocValues.getNumeric(r.reader(), queryInfo().index().mainAnnotatedField().tokenLengthField()) : null;
                            while (true) {
                                int docId = it.nextDoc();
                                if (docId == DocIdSetIterator.NO_MORE_DOCS)
                                    break;
                                numberOfDocuments++;
                                if (countTokens)
                                    numberOfTokens += tokenLengthValues.get(docId) - subtractClosingToken;
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Error determining token count", e);
                }
            } else {
                // Slow approach: get the stored field value from each Document
                //TODO: use DocValues as well (a bit more complex, because we can't re-run the query)
//                logger.debug("## DocResults.tokensInMatchingDocs: SLOW PATH");
                String fieldName = queryInfo().index().mainAnnotatedField().name();
                DocProperty propTokens = new DocPropertyAnnotatedFieldLength(queryInfo().index(), fieldName);
                numberOfTokens = countTokens ? intSum(propTokens) : -1;
                numberOfDocuments = size();
            }
            corpusSize = CorpusSize.get(numberOfDocuments, numberOfTokens);
        }
        return corpusSize;
    }

    public Query query() {
        return query;
    }

}
