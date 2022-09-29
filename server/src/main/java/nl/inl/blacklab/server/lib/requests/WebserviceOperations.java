package nl.inl.blacklab.server.lib.requests;

import java.io.InputStream;
import java.text.Collator;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.ArrayList;
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

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
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
import nl.inl.blacklab.search.indexmetadata.Annotations;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldGroup;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.index.DocIndexerFactoryUserFormats;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.WebserviceParams;
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
    public static Collection<MetadataField> getMetadataToWrite(BlackLabIndex index, SearchCreator params) {
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

    public static Map<String, List<String>> getMetadataFieldGroupsWithRest(BlackLabIndex index) {
        ResultMetadataGroupInfo metadataGroupInfo = WebserviceOperations.getMetadataGroupInfo(index);

        Map<String, List<String>> metadataFieldGroups = new LinkedHashMap<>();
        boolean addedRemaining = false;
        for (MetadataFieldGroup metaGroup : metadataGroupInfo.getMetaGroups().values()) {
            List<String> metadataFieldGroup = new ArrayList<>();
            for (String field: metaGroup) {
                metadataFieldGroup.add(field);
            }
            if (!addedRemaining && metaGroup.addRemainingFields()) {
                addedRemaining = true;
                List<MetadataField> rest = new ArrayList<>(metadataGroupInfo.getMetadataFieldsNotInGroups());
                rest.sort(Comparator.comparing(a -> a.name().toLowerCase()));
                for (MetadataField field: rest) {
                    metadataFieldGroup.add(field.name());
                }
            }
            metadataFieldGroups.put(metaGroup.name(), metadataFieldGroup);
        }
        return metadataFieldGroups;
    }

    private static ResultMetadataGroupInfo getMetadataGroupInfo(BlackLabIndex index) {
        Map<String, ? extends MetadataFieldGroup> metaGroups = index.metadata().metadataFields().groups();
        Set<MetadataField> metadataFieldsNotInGroups = index.metadata().metadataFields().stream()
                .collect(Collectors.toSet());
        for (MetadataFieldGroup metaGroup : metaGroups.values()) {
            for (String fieldName: metaGroup) {
                MetadataField field = index.metadata().metadataFields().get(fieldName);
                metadataFieldsNotInGroups.remove(field);
            }
        }
        List<MetadataField> rest = new ArrayList<>(metadataFieldsNotInGroups);
        rest.sort(Comparator.comparing(a -> a.name().toLowerCase()));
        return new ResultMetadataGroupInfo(metaGroups, rest);
    }

    public static Map<String, String> getDocFields(IndexMetadata indexMetadata) {
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
     * Get the pid for the specified document
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
    public static List<Annotation> getAnnotationsToWrite(BlackLabIndex index, WebserviceParams params) {
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

    public static Map<String, ResultDocInfo> getDocInfos(BlackLabIndex index, Map<Integer, Document> luceneDocs,
            Collection<MetadataField> metadataFieldsToList) {
        Map<String, ResultDocInfo> docInfos = new LinkedHashMap<>();
        for (Map.Entry<Integer, Document> e: luceneDocs.entrySet()) {
            Integer docId = e.getKey();
            Document luceneDoc = e.getValue();
            String pid = getDocumentPid(index, docId, luceneDoc);
            ResultDocInfo docInfo = ResultDocInfo.get(index, null, luceneDoc, metadataFieldsToList);
            docInfos.put(pid, docInfo);
        }
        return docInfos;
    }

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

    // specific to hits
    public static TermFrequencyList getCollocations(SearchCreator params, Hits originalHits) {
        ContextSize contextSize = ContextSize.get(params.getWordsAroundHit());
        MatchSensitivity sensitivity = MatchSensitivity.caseAndDiacriticsSensitive(params.getSensitive());
        TermFrequencyList tfl = originalHits.collocations(originalHits.field().mainAnnotation(), contextSize,
                sensitivity);
        return tfl;
    }

    // specific to add format
    public static void addUserFileFormat(SearchManager searchMan, User user, String fileName,
            InputStream fileInputStream) {
        DocIndexerFactoryUserFormats formatMan = searchMan.getIndexManager().getUserFormatManager();
        if (formatMan == null)
            throw new BadRequest("CANNOT_CREATE_INDEX ",
                    "Could not create/overwrite format. The server is not configured with support for user content.");
        formatMan.createUserFormat(user, fileName, fileInputStream);
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

    public static Set<String> getTerms(BlackLabIndex index, Annotation annotation,
            boolean[] valueListComplete) {
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

    public static Map<String, ResultAnnotationInfo> getAnnotInfos(SearchCreator params, Annotations annotations) {
        Map<String, ResultAnnotationInfo> annotInfos = new LinkedHashMap<>();
        BlackLabIndex index = params.blIndex();
        for (Annotation annotation: annotations) {
            ResultAnnotationInfo ai = new ResultAnnotationInfo(index, annotation, params.getListValuesFor());
            annotInfos.put(annotation.name(), ai);
        }
        return annotInfos;
    }

    public static CorpusSize findSubcorpusSize(SearchCreator searchParam, Query metadataFilterQuery, DocProperty property, PropertyValue value) {
        if (!property.canConstructQuery(searchParam.blIndex(), value))
            return CorpusSize.EMPTY; // cannot determine subcorpus size of empty value
        // Construct a query that matches this propery value
        Query query = property.query(searchParam.blIndex(), value); // analyzer....!
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
        return searchParam.blIndex().queryDocuments(query).subcorpusSize(true);
    }
}
