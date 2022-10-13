package nl.inl.blacklab.server.lib.results;

import java.io.File;
import java.io.InputStream;
import java.text.Collator;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.index.IndexListenerReportConsole;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFields;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldGroup;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.server.config.DefaultMax;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.index.DocIndexerFactoryUserFormats;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.ResultIndexMetadata;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.WebserviceParamsImpl;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.util.LuceneUtil;

public class WebserviceOperations {

    private static final int MAX_FIELD_VALUES_TO_RETURN = 500;

    private static RuleBasedCollator fieldValueSortCollator = null;

    private WebserviceOperations() {}

    /**
     * Returns a list of metadata fields to write out.
     *
     * By default, all metadata fields are returned.
     * Special fields (pidField, titleField, etc...) are always returned.
     *
     * @return a list of metadata fields to write out, as specified by the "listmetadatavalues" query parameter.
     */
    public static Collection<MetadataField> getMetadataToWrite(WebserviceParams params) {
        BlackLabIndex index = params.blIndex();
        MetadataFields fields = index.metadataFields();
        Collection<String> requestedFields = params.getListMetadataValuesFor();
        Set<MetadataField> ret = new HashSet<>();
        ret.add(optCustomField(index.metadata(), "authorField"));
        ret.add(optCustomField(index.metadata(), "dateField"));
        ret.add(optCustomField(index.metadata(), "titleField"));
        ret.add(fields.pidField());
        for (MetadataField field  : fields) {
            if (requestedFields.isEmpty() || requestedFields.contains(field.name())) {
                ret.add(field);
            }
        }
        ret.remove(null); // for missing special fields.
        return ret;
    }

    private static MetadataField optCustomField(IndexMetadata metadata, String propName) {
        String fieldName = metadata.custom().get(propName, "");
        return fieldName.isEmpty() ? null : metadata.metadataFields().get(fieldName);
    }

    /**
     * Get metadata field groups.
     *
     * This includes adding any uncategorized fields to the "default" group.
     *
     * (part of custom properties; should eventually be removed from the API)
     *
     * @param index index
     * @return metadata field groups
     */
    public static Map<String, List<String>> getMetadataFieldGroupsWithRest(BlackLabIndex index) {
        Map<String, ? extends MetadataFieldGroup> metaGroups = index.metadata().metadataFields().groups();
        Set<MetadataField> metadataFieldsNotInGroups = index.metadata().metadataFields().stream()
                .collect(Collectors.toSet());
        for (MetadataFieldGroup metaGroup1: metaGroups.values()) {
            for (String fieldName: metaGroup1) {
                MetadataField field1 = index.metadata().metadataFields().get(fieldName);
                metadataFieldsNotInGroups.remove(field1);
            }
        }

        Map<String, List<String>> metadataFieldGroups = new LinkedHashMap<>();
        boolean addedRemaining = false;
        for (MetadataFieldGroup metaGroup : metaGroups.values()) {
            List<String> metadataFieldGroup = new ArrayList<>();
            for (String field: metaGroup) {
                metadataFieldGroup.add(field);
            }
            if (!addedRemaining && metaGroup.addRemainingFields()) {
                addedRemaining = true;
                List<MetadataField> rest = new ArrayList<>(metadataFieldsNotInGroups);
                rest.sort(Comparator.comparing(a -> a.name().toLowerCase()));
                for (MetadataField field: rest) {
                    metadataFieldGroup.add(field.name());
                }
            }
            metadataFieldGroups.put(metaGroup.name(), metadataFieldGroup);
        }
        return metadataFieldGroups;
    }

    /**
     * Get the special metadata fields.
     *
     * (special metadata fields except pidField are part of custom properties;
     *  this method should eventually be removed from the API)
     *
     * @param index index
     * @return doc fields
     */
    public static Map<String, String> getDocFields(BlackLabIndex index) {
        IndexMetadata indexMetadata = index.metadata();
        Map<String, String> docFields = new LinkedHashMap<>();
        MetadataField pidField = indexMetadata.metadataFields().pidField();
        if (pidField != null)
            docFields.put("pidField", pidField.name());
        for (String propName: List.of("titleField", "authorField", "dateField")) {
            String fieldName = indexMetadata.custom().get(propName, "");
            if (!fieldName.isEmpty())
                docFields.put(propName, fieldName);
        }
        return docFields;
    }

    /**
     * Get display names for metadata fields.
     *
     * (part of custom properties; should eventually be removed from the API)
     *
     * @param index index
     * @return display names
     */
    public static Map<String, String> getMetaDisplayNames(BlackLabIndex index) {
        Map<String, String> metaDisplayNames = new LinkedHashMap<>();
        for (MetadataField f: index.metadata().metadataFields()) {
            String displayName = f.displayName();
            if (!f.name().equals("lengthInTokens") && !f.name().equals("mayView")) {
                metaDisplayNames.put(f.name(),displayName);
            }
        }
        return metaDisplayNames;
    }

    /**
     * Get the pid for the specified document.
     *
     * @param index where we got this document from
     * @param luceneDocId Lucene document id
     * @param document the document object
     * @return the pid string (or Lucene doc id in string form if index has no pid
     *         field)
     */
    public static String getDocumentPid(BlackLabIndex index, int luceneDocId, Document document) {
        MetadataField pidField = index.metadataFields().pidField();
        String pid = pidField == null ? null : document.get(pidField.name());
        if (pid == null)
            return Integer.toString(luceneDocId);
        return pid;
    }

    /**
     * Returns the annotations to write out.
     *
     * By default, all annotations are returned.
     * Annotations are returned in requested order, or in their definition/display order.
     *
     * @return the annotations to write out, as specified by the (optional) "listvalues" query parameter.
     */
    public static List<Annotation> getAnnotationsToWrite(WebserviceParams params) {
        BlackLabIndex index = params.blIndex();
        AnnotatedFields fields = index.annotatedFields();
        Collection<String> requestedAnnotations = params.getListValuesFor();

        List<Annotation> ret = new ArrayList<>();
        for (AnnotatedField f : fields) {
            for (Annotation a : f.annotations()) {
                if (requestedAnnotations.isEmpty() || requestedAnnotations.contains(a.name())) {
                    ret.add(a);
                }
            }
        }

        return ret;
    }

    /**
     * Get metadata for a list of documents.
     *
     * @param index index
     * @param luceneDocs documents to get metadata from
     * @param metadataFieldsToList fields to get
     * @return metadata for the documents
     */
    public static Map<String, ResultDocInfo> getDocInfos(BlackLabIndex index, Map<Integer, Document> luceneDocs,
            Collection<MetadataField> metadataFieldsToList) {
        Map<String, ResultDocInfo> docInfos = new LinkedHashMap<>();
        for (Map.Entry<Integer, Document> e: luceneDocs.entrySet()) {
            Integer docId = e.getKey();
            Document luceneDoc = e.getValue();
            String pid = getDocumentPid(index, docId, luceneDoc);
            ResultDocInfo docInfo = new ResultDocInfo(index, null, luceneDoc, metadataFieldsToList);
            docInfos.put(pid, docInfo);
        }
        return docInfos;
    }

    /**
     * Get relevant facets info for display.
     *
     * Returns lists of value+count for every property faceted on.
     * Grouped by descending size.
     *
     * @param counts faceting results
     * @return faceting info for display
     */
    public static Map<String, List<Pair<String, Long>>> getFacetInfo(Map<DocProperty, DocGroups> counts) {
        Map<String, List<Pair<String,  Long>>> facetInfo = new LinkedHashMap<>();
        for (Map.Entry<DocProperty, DocGroups> e : counts.entrySet()) {
            DocProperty facetBy = e.getKey();
            DocGroups facetCounts = e.getValue();
            facetCounts = facetCounts.sort(DocGroupProperty.size());
            String facetName = facetBy.name();
            List<Pair<String,  Long>> facetItems = new ArrayList<>();
            int n = 0, maxFacetValues = 10;
            int totalSize = 0;
            for (DocGroup count : facetCounts) {
                String value = count.identity().toString();
                long size = count.size();
                facetItems.add(Pair.of(value, size));
                totalSize += size;
                n++;
                if (n >= maxFacetValues)
                    break;
            }
            if (totalSize < facetCounts.sumOfGroupSizes()) {
                facetItems.add(Pair.of("[REST]", facetCounts.sumOfGroupSizes() - totalSize));
            }
            facetInfo.put(facetName, facetItems);
        }
        return facetInfo;
    }

    /**
     * Get a map of doc id to document pid for the documents in a list of hits.
     *
     * @param index index
     * @param hits hits we want the doc pids for
     * @param luceneDocs map of doc id to Lucene document, to look up the pids
     */
    public static Map<Integer, String> collectDocsAndPids(BlackLabIndex index, Hits hits,
            Map<Integer, Document> luceneDocs) {
        // Collect Lucene docs (for writing docInfos later) and find pids
        Map<Integer, String> docIdToPid = new HashMap<>();
        for (Hit hit : hits) {
            Document document = luceneDocs.computeIfAbsent(hit.doc(),
                    __ -> index.luceneDoc(hit.doc()));
            String docPid = getDocumentPid(index, hit.doc(), document);
            docIdToPid.put(hit.doc(), docPid);
        }
        return docIdToPid;
    }

    /**
     * Calculate collocations from hits.
     *
     * @param params operation parameters
     * @param hits hits
     * @return collocations
     */
    public static TermFrequencyList getCollocations(WebserviceParams params, Hits hits) {
        ContextSize contextSize = ContextSize.get(params.getWordsAroundHit());
        MatchSensitivity sensitivity = MatchSensitivity.caseAndDiacriticsSensitive(params.getSensitive());
        return hits.collocations(hits.field().mainAnnotation(), contextSize,
                sensitivity);
    }

    /**
     * Add a user file format.
     *
     * @param params operation parameters
     * @param fileName name of the uploaded file
     * @param fileContents contents of the uploaded file
     */
    public static void addUserFileFormat(WebserviceParams params, String fileName, InputStream fileContents) {
        SearchManager searchMan = params.getSearchManager();
        DocIndexerFactoryUserFormats formatMan = searchMan.getIndexManager().getUserFormatManager();
        if (formatMan == null)
            throw new BadRequest("CANNOT_CREATE_INDEX ",
                    "Could not create/overwrite format. The server is not configured with support for user content.");
        formatMan.createUserFormat(params.getUser(), fileName, fileContents);
    }

    /**
     * Get field value distribution in the right order for the response.
     *
     * The right order is: display order first, then sorted by displayValue
     * as a fallback, or regular value as the second fallback.
     *
     * @param fd field to get values for
     * @return properly sorted values
     */
    public static Map<String, Integer> getFieldValuesInOrder(MetadataField fd) {
        Map<String, String> displayValues = fd.custom().get("displayValues", Collections.emptyMap());

        // Show values in display order (if defined)
        // If not all values are mentioned in display order, show the rest at the end,
        // sorted by their displayValue (or regular value if no displayValue specified)
        Map<String, Integer> fieldValues = new LinkedHashMap<>();
        Map<String, Integer> valueDistribution = fd.valueDistribution();
        Set<String> valuesLeft = new HashSet<>(valueDistribution.keySet());
        for (String value : fd.custom().get("displayOrder", Collections.<String>emptyList())) {
            fieldValues.put(value, valueDistribution.get(value));
            valuesLeft.remove(value);
        }
        List<String> sortedLeft = new ArrayList<>(valuesLeft);
        final Collator defaultCollator = getFieldValueSortCollator();
        sortedLeft.sort((o1, o2) -> {
            String d1 = displayValues.getOrDefault(o1, o1);
            String d2 = displayValues.getOrDefault(o2, o2);
            //return d1.compareTo(d2);
            return defaultCollator.compare(d1, d2);
        });
        for (String value : sortedLeft) {
            fieldValues.put(value, valueDistribution.get(value));
        }
        return fieldValues;
    }

    /**
     * Returns a collator that sort field values "properly", ignoring parentheses.
     *
     * @return the collator
     */
    static Collator getFieldValueSortCollator() {
        if (fieldValueSortCollator == null) {
            fieldValueSortCollator = (RuleBasedCollator) BlackLab.defaultCollator();
            try {
                // Make sure it ignores parentheses when comparing
                String rules = fieldValueSortCollator.getRules();
                // Set parentheses equal to NULL, which is ignored.
                rules += "&\u0000='('=')'";
                fieldValueSortCollator = new RuleBasedCollator(rules);
            } catch (ParseException e) {
                // Oh well, we'll use the collator as-is
                //throw new RuntimeException();//DEBUG
            }
        }
        return fieldValueSortCollator;
    }

    /**
     * Get the list of values for an annotation.
     *
     * No more than {@link #MAX_FIELD_VALUES_TO_RETURN} will be returned.
     * valueListComplete[0] will indicate if all values were returned or not
     *
     * @param index index
     * @param annotation annotation to get values for
     * @param valueListComplete (out) [0] indicates whether the value list is complete or not
     * @return values for this annotation
     */
    public static Set<String> getAnnotationValues(BlackLabIndex index, Annotation annotation, boolean[] valueListComplete) {
        boolean isInlineTagAnnotation = annotation.name().equals(AnnotatedFieldNameUtil.TAGS_ANNOT_NAME);
        final Set<String> terms = new TreeSet<>();
        MatchSensitivity sensitivity = annotation.hasSensitivity(MatchSensitivity.INSENSITIVE) ?
                MatchSensitivity.INSENSITIVE :
                MatchSensitivity.SENSITIVE;
        AnnotationSensitivity as = annotation.sensitivity(sensitivity);
        String luceneField = as.luceneField();
        if (isInlineTagAnnotation) {
            // Tags. Skip attribute values, only show elements.
            LuceneUtil.getFieldTerms(index.reader(), luceneField, null, term -> {
                if (!term.startsWith("@") && !terms.contains(term)) {
                    if (terms.size() >= MAX_FIELD_VALUES_TO_RETURN) {
                        valueListComplete[0] = false;
                        return false;
                    }
                    terms.add(term);
                }
                return true;
            });
        } else {
            // Regular annotated field.
            LuceneUtil.getFieldTerms(index.reader(), luceneField, null, term -> {
                if (!terms.contains(term)) {
                    if (terms.size() >= MAX_FIELD_VALUES_TO_RETURN) {
                        valueListComplete[0] = false;
                        return false;
                    }
                    terms.add(term);
                }
                return true;
            });
        }
        return terms;
    }

    /**
     * Translate a thrown exception into a BlsException.
     *
     * BlsException will eventually be caught and returned as an error response.
     *
     * @param e exception thrown
     * @return translated exception
     */
    public static BlsException translateSearchException(Exception e) {
        if (e instanceof InterruptedException) {
            throw new InterruptedSearch(e);
        } else {
            try {
                throw e.getCause();
            } catch (BlackLabException e1) {
                return new BadRequest("INVALID_QUERY", "Invalid query: " + e1.getMessage());
            } catch (BlsException e1) {
                return e1;
            } catch (Throwable e1) {
                return new InternalServerError("Internal error while searching", "INTERR_WHILE_SEARCHING", e1);
            }
        }
    }

    /**
     * Find the size of documents matching a filter query and/or property+value.
     *
     *
     * @param params operation parameters
     * @param metadataFilterQuery filter query
     * @param property document property to find subcorpus size for
     * @param value value the document property must have to be included
     * @return
     */
    public static CorpusSize findSubcorpusSize(WebserviceParams params, Query metadataFilterQuery,
            DocProperty property, PropertyValue value) {
        if (!property.canConstructQuery(params.blIndex(), value))
            return CorpusSize.EMPTY; // cannot determine subcorpus size of empty value
        // Construct a query that matches this propery value
        Query query = property.query(params.blIndex(), value); // analyzer....!
        if (query == null) {
            query = metadataFilterQuery;
        } else {
            // Combine with subcorpus query
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            builder.add(metadataFilterQuery, BooleanClause.Occur.MUST);
            builder.add(query, BooleanClause.Occur.MUST);
            query = builder.build();
        }
        // Determine number of tokens in this subcorpus
        return params.blIndex().queryDocuments(query).subcorpusSize(true);
    }

    public static TermFrequencyList calculateCollocations(WebserviceParams params) {
        ResultHits resultHits = new ResultHits(params, false);
        Hits hits = resultHits.getHits();
        return getCollocations(params, hits);
    }

    public static ResultHits getResultHits(WebserviceParams params) {
        ResultHits resultHits = new ResultHits(params, true);
        resultHits.finishSearch();
        return resultHits;
    }

    public static TermFrequencyList getTermFrequencies(WebserviceParams params) {
        //TODO: use background job?

        BlackLabIndex blIndex = params.blIndex();
        AnnotatedField cfd = blIndex.mainAnnotatedField();
        String annotName = params.getAnnotationName();
        Annotation annotation = cfd.annotation(annotName);
        MatchSensitivity sensitive = MatchSensitivity.caseAndDiacriticsSensitive(params.getSensitive());
        AnnotationSensitivity sensitivity = annotation.sensitivity(sensitive);

        // May be null!
        Query q = params.hasFilter() ? params.filterQuery() : null;
        // May also null/empty to retrieve all terms!
        Set<String> terms = params.getTerms();
        TermFrequencyList tfl = blIndex.termFrequencies(sensitivity, q, terms);

        if (terms == null || terms.isEmpty()) { // apply pagination only when requesting all terms
            long first = params.getFirstResultToShow();
            if (first < 0 || first >= tfl.size())
                first = 0;
            long number = params.getNumberOfResultsToShow();
            DefaultMax pageSize = params.getSearchManager().config().getParameters().getPageSize();
            if (number < 0 || number > pageSize.getMax())
                number = pageSize.getDefaultValue();
            long last = first + number;
            if (last > tfl.size())
                last = tfl.size();

            tfl = tfl.subList(first, last);
        }
        return tfl;
    }

    public static List<String> getUsersToShareWith(WebserviceParams params) {
        IndexManager indexMan = params.getIndexManager();
        String indexName = params.getIndexName();
        User user = params.getUser();
        Index index = indexMan.getIndex(indexName);
        if (!index.userMayRead(user))
            throw new NotAuthorized("You are not authorized to access this index.");
        return index.getShareWithUsers();
    }

    public static void setUsersToShareWith(WebserviceParams params, String[] users) {
        User user = params.getUser();
        IndexManager indexMan = params.getIndexManager();
        String indexName = params.getIndexName();
        Index index = indexMan.getIndex(indexName);
        if (!index.isUserIndex() || (!index.userMayRead(user)))
            throw new NotAuthorized("You can only share your own private indices with others.");
        // Update the list of users to share with
        List<String> shareWithUsers = Arrays.stream(users).map(String::trim).collect(Collectors.toList());
        index.setShareWithUsers(shareWithUsers);
    }

    public static ResultAutocomplete autocomplete(WebserviceParams params) {
        return new ResultAutocomplete(params);
    }

    public static ResultDocContents docContents(WebserviceParams params) throws InvalidQuery {
        return new ResultDocContents(params);
    }

    public static ResultDocInfo docInfo(BlackLabIndex blIndex, String docPid, Document document, Collection<MetadataField> metadataToWrite) {
        return new ResultDocInfo(blIndex, docPid, document, metadataToWrite);
    }

    public static ResultDocsCsv docsCsv(WebserviceParams params) throws InvalidQuery {
        return new ResultDocsCsv(params);
    }

    public static ResultHitsCsv hitsCsv(WebserviceParams params) throws InvalidQuery {
        return new ResultHitsCsv(params);
    }

    public static ResultHitsGrouped hitsGrouped(WebserviceParams params)
            throws InvalidQuery {
        return new ResultHitsGrouped(params);
    }

    public static String addToIndex(WebserviceParams params, List<FileItem> dataFiles, Map<String, File> linkedFiles) {
        Index index = params.getIndexManager().getIndex(params.getIndexName());
        IndexMetadata indexMetadata = index.getIndexMetadata();

        if (!index.userMayAddData(params.getUser()))
            throw new NotAuthorized("You can only add new data to your own private indices.");

        long maxTokenCount = BlackLab.config().getIndexing().getUserIndexMaxTokenCount();
        if (indexMetadata.tokenCount() > maxTokenCount) {
            throw new NotAuthorized("Sorry, this index is already larger than the maximum of " + maxTokenCount
                    + " tokens allowed in a user index. Cannot add any more data to it.");
        }

        Indexer indexer = index.getIndexer();
        final String[] indexErr = { null }; // array because we set it from closure
        indexer.setListener(new IndexListenerReportConsole() {
            @Override
            public boolean errorOccurred(Throwable e, String path, File f) {
                super.errorOccurred(e, path, f);
                indexErr[0] = e.getMessage() + " in " + path;
                return false; // Don't continue indexing
            }
        });
        String indexError = indexErr[0];

        indexer.setLinkedFileResolver(fileName -> linkedFiles.get(FilenameUtils.getName(fileName).toLowerCase()));

        try {
            for (FileItem file : dataFiles) {
                indexer.index(file.getName(), file.get());
            }
        } finally {
            if (indexError == null) {
                if (indexer.listener().getFilesProcessed() == 0)
                    indexError = "No files were found during indexing.";
                else if (indexer.listener().getDocsDone() == 0)
                    indexError = "No documents were found during indexing, are the files in the correct format?";
                else if (indexer.listener().getTokensProcessed() == 0)
                    indexError = "No tokens were found during indexing, are the files in the correct format?";
            }

            // It's important we roll back on errors, or incorrect index metadata might be written.
            // See Indexer#hasRollback
            if (indexError != null)
                indexer.rollback();

            indexer.close();
        }

        return indexError;
    }

    public static void deleteUserFormat(WebserviceParams params, String formatIdentifier) {
        IndexManager indexMan = params.getIndexManager();
        DocIndexerFactoryUserFormats formatMan = indexMan.getUserFormatManager();
        if (formatMan == null)
            throw new BadRequest("CANNOT_DELETE_INDEX ",
                    "Could not delete format. The server is not configured with support for user content.");

        if (formatIdentifier == null || formatIdentifier.isEmpty()) {
            throw new NotFound("FORMAT_NOT_FOUND", "Specified format was not found");
        }

        for (Index i : indexMan.getAvailablePrivateIndices(params.getUser().getUserId())) {
            if (formatIdentifier.equals(i.getIndexMetadata().documentFormat()))
                throw new BadRequest("CANNOT_DELETE_INDEX ",
                        "Could not delete format. The format is still being used by a corpus.");
        }

        formatMan.deleteUserFormat(params.getUser(), formatIdentifier);
    }

    public static ResultAnnotatedField annotatedField(WebserviceParams params, AnnotatedField fieldDesc, boolean includeIndexName) {
        Map<String, ResultAnnotationInfo> annotInfos = new LinkedHashMap<>();
        BlackLabIndex index = params.blIndex();
        for (Annotation annotation: fieldDesc.annotations()) {
            ResultAnnotationInfo ai = new ResultAnnotationInfo(index, annotation, params.getListValuesFor());
            annotInfos.put(annotation.name(), ai);
        }
        return new ResultAnnotatedField(includeIndexName ? params.getIndexName() : null, fieldDesc, annotInfos);
    }

    public static ResultIndexStatus resultIndexStatus(WebserviceParams params) {
        Index index = params.getIndexManager().getIndex(params.getIndexName());
        return resultIndexStatus(index);
    }

    public static ResultIndexStatus resultIndexStatus(Index index) {
        synchronized (index) {
            IndexListener indexerListener = index.getIndexerListener();
            long files = 0;
            long docs = 0;
            long tokens = 0;
            if (indexerListener != null) {
                files = indexerListener.getFilesProcessed();
                docs = indexerListener.getDocsDone();
                tokens = indexerListener.getTokensProcessed();
            }
            return new ResultIndexStatus(index, files, docs, tokens);
        }
    }

    public static ResultDocSnippet docSnippet(WebserviceParams params, SearchManager searchMan) {
        return new ResultDocSnippet(params, searchMan);
    }

    public static ResultListOfHits listOfHits(WebserviceParams params, Hits window, ConcordanceContext concordanceContext,
            Map<Integer, String> docIdToPid) {
        return new ResultListOfHits(params, window, concordanceContext, docIdToPid);
    }

    public static ResultMetadataField metadataField(MetadataField fieldDesc, String indexName) {
        Map<String, Integer> fieldValues = getFieldValuesInOrder(fieldDesc);
        return new ResultMetadataField(indexName, fieldDesc, true, fieldValues);
    }

    public static ResultSummaryNumDocs numResultsSummaryDocs(boolean isViewGroup, DocResults docResults,
            boolean countFailed, CorpusSize subcorpusSize) {
        return new ResultSummaryNumDocs(isViewGroup, docResults, countFailed, subcorpusSize);
    }

    public static ResultSummaryNumHits numResultsSummaryHits(ResultsStats hitsStats, ResultsStats docsStats,
            boolean waitForTotal, boolean countFailed, CorpusSize subcorpusSize) {
        return new ResultSummaryNumHits(hitsStats, docsStats, waitForTotal, countFailed, subcorpusSize);
    }

    public static ResultSummaryCommonFields summaryCommonFields(WebserviceParams params, Index.IndexStatus indexStatus,
            SearchTimings timings, ResultGroups<?> groups, WindowStats window) {
        return new ResultSummaryCommonFields(params, indexStatus, timings, groups, window);
    }

    public static ResultUserInfo userInfo(WebserviceParams params) {
        User user = params.getUser();
        return new ResultUserInfo(user.isLoggedIn(), user.getUserId(), params.getIndexManager().canCreateIndex(user));
    }

    public static ResultDocsResponse viewGroupDocsResponse(WebserviceParams params) throws InvalidQuery {
        return ResultDocsResponse.viewGroupDocsResponse(params);
    }

    public static ResultDocsResponse regularDocsResponse(WebserviceParams params) throws InvalidQuery {
        return ResultDocsResponse.regularDocsResponse(params);
    }

    public static ResultIndexMetadata indexMetadata(WebserviceParams params) {
        ResultIndexStatus progress = resultIndexStatus(params);
        IndexMetadata metadata = progress.getMetadata();

        List<ResultAnnotatedField> afs = new ArrayList<>();
        for (AnnotatedField field: metadata.annotatedFields()) {
            afs.add(annotatedField(params, field, false));
        }
        List<ResultMetadataField> mfs = new ArrayList<>();
        for (MetadataField f: metadata.metadataFields()) {
            mfs.add(metadataField(f, null));
        }

        Map<String, List<String>> metadataFieldGroups = getMetadataFieldGroupsWithRest(
                params.blIndex());

        return new ResultIndexMetadata(progress, afs, mfs, metadataFieldGroups);
    }

    public static ResultServerInfo serverInfo(WebserviceParamsImpl params, boolean debugMode) {
        return new ResultServerInfo(params, debugMode);
    }

    public static ResultDocsGrouped docsGrouped(WebserviceParamsImpl params) throws InvalidQuery {
        return new ResultDocsGrouped(params);
    }

    public static ResultListInputFormats listInputFormats(WebserviceParamsImpl params) {
        return new ResultListInputFormats(params);
    }

    public static ResultInputFormat inputFormat(String formatName) {
        return new ResultInputFormat(formatName);
    }
}
