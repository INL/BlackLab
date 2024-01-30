package nl.inl.blacklab.resultproperty;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import it.unimi.dsi.fastutil.BigList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.EphemeralHit;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.HitsInternal;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * Base class for HitPropertyHitText, LeftContext, RightContext.
 */
public abstract class HitPropertyContextBase extends HitProperty {

    /** Should we compare context words in reverse?
     *
     * (this actually reverses the arrays containing the context words, and reverses them back
     *  when we construct a displayable value)
     */
    protected boolean compareInReverse;

    /** Used by fetchContext() to get required context part boundaries for a hit */
    @FunctionalInterface
    interface StartEndSetter {
        void setStartEnd(int[] starts, int[] ends, int indexInArrays, Hit hit);
    }

    /** Forward index we're looking at */
    protected AnnotationForwardIndex afi;

    /** Stores the relevant context tokens for each hit index */
    protected BigList<int[]> contextTermId;

    /** Stores the sort order for the relevant context tokens for each hit index */
    protected BigList<int[]> contextSortOrder;

    protected static <T extends HitPropertyContextBase> T deserializeProp(Class<T> cls, BlackLabIndex index,
            AnnotatedField field, String info) {
        String[] parts = PropertySerializeUtil.splitParts(info);
        String propName = parts[0];
        if (propName.length() == 0)
            propName = field.mainAnnotation().name();
        MatchSensitivity sensitivity = parts.length > 1 ? MatchSensitivity.fromLuceneFieldSuffix(parts[1])
                : MatchSensitivity.SENSITIVE;
        Annotation annotation = field.annotation(propName);
        try {
            Constructor<T> ctor = cls.getConstructor(BlackLabIndex.class, Annotation.class, MatchSensitivity.class);
            T t = ctor.newInstance(index, annotation, sensitivity);
            if (parts.length > 2)
                t.deserializeParam(parts[2]);  // e.g. number of tokens
            return t;
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
            throw new BlackLabRuntimeException("Couldn't deserialize hit property: " + cls.getName() + ":" + info, e);
        }
    }

    /** Some context properties, e.g. context before, can get an extra parameter (number of tokens).
     *
     * This method should deserialize that parameter if applicable.
     *
     * @param param extra parameter to deserialize
     */
    void deserializeParam(String param) {
        // just ignore extra param by default when deserializing
    }

    protected final Terms terms;

    protected final Annotation annotation;

    protected MatchSensitivity sensitivity;

    protected String name;

    protected String serializeName;

    protected BlackLabIndex index;

    public HitPropertyContextBase(HitPropertyContextBase prop, Hits hits, boolean invert) {
        super(prop, hits, invert);
        this.terms = prop.terms;
        this.annotation = prop.annotation;
        if (hits != null && !hits.field().equals(this.annotation.field())) {
            throw new IllegalArgumentException(
                    "Hits passed to HitProperty must be in the field it was declared with! (declared with "
                            + this.annotation.field().name() + ", hits has " + hits.field().name() + "; class=" + getClass().getName() + ")");
        }
        this.sensitivity = prop.sensitivity;
        this.name = prop.name;
        this.serializeName = prop.serializeName;
        this.index = hits == null ? prop.index : hits.index();
        this.compareInReverse = prop.compareInReverse;
        initForwardIndex();
        if (prop.hits == hits) {
            // Same hits object; reuse context arrays
            contextTermId = prop.contextTermId;
            contextSortOrder = prop.contextSortOrder;
        }
    }

    public HitPropertyContextBase(String name, String serializeName, BlackLabIndex index, Annotation annotation,
            MatchSensitivity sensitivity, boolean compareInReverse) {
        super();
        this.name = name;
        this.serializeName = serializeName;
        this.index = index;
        this.annotation = annotation == null ? index.mainAnnotatedField().mainAnnotation() : annotation;
        this.terms = index.annotationForwardIndex(this.annotation).terms();
        this.sensitivity = sensitivity == null ? index.defaultMatchSensitivity() : sensitivity;
        this.compareInReverse = compareInReverse;
        initForwardIndex();
    }

    public void setSerializeAs(String type) {
        this.serializeName = type;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public MatchSensitivity getSensitivity() {
        return sensitivity;
    }

    @Override
    public String name() {
        return name + ": " + annotation.name();
    }

    public List<String> serializeParts() {
        return List.of(serializeName, annotation.name(), sensitivity.luceneFieldSuffix());
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropertySerializeUtil.combineParts(serializeParts());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((annotation == null) ? 0 : annotation.hashCode());
        result = prime * result + ((index == null) ? 0 : index.hashCode());
        result = prime * result + ((sensitivity == null) ? 0 : sensitivity.hashCode());
        return result;
    }

    void initForwardIndex() {
        if (hits != null) {
            ForwardIndex fi = index.forwardIndex(hits.queryInfo().field());
            afi = fi.get(annotation);
        }
    }

    protected synchronized void fetchContext(StartEndSetter setStartEnd) {
        HitsInternal ha = hits.getInternalHits();
        contextTermId = new ObjectBigArrayBigList<>(ha.size());
        contextSortOrder = new ObjectBigArrayBigList<>(ha.size());
        final long size = ha.size();
        int prevDoc = size == 0 ? -1 : ha.doc(0);
        long firstHitInCurrentDoc = 0;
        if (size > 0) {
            for (long i = 1; i < size; ++i) { // start at 1: variables already have correct values for primed for hit 0
                final int curDoc = ha.doc(i);
                if (curDoc != prevDoc) {
                    try { hits.threadAborter().checkAbort(); } catch (InterruptedException e) { throw new InterruptedSearch(e); }
                    // Process hits in preceding document:
                    fetchContextForDoc(setStartEnd, prevDoc, firstHitInCurrentDoc, i);
                    // start a new document
                    prevDoc = curDoc;
                    firstHitInCurrentDoc = i;
                }
            }
            // Process hits in final document
            fetchContextForDoc(setStartEnd, prevDoc, firstHitInCurrentDoc, size);
        }
    }

    @Override
    public synchronized void disposeContext() {
        contextTermId = contextSortOrder = null;
    }

    private synchronized void fetchContextForDoc(StartEndSetter setStartEnd, int docId, long fromIndex, long toIndexExclusive) {
        assert fromIndex >= 0 && toIndexExclusive > 0;
        assert fromIndex < toIndexExclusive;
        if (toIndexExclusive - fromIndex > Constants.JAVA_MAX_ARRAY_SIZE)
            throw new BlackLabRuntimeException("Cannot handle more than " + Constants.JAVA_MAX_ARRAY_SIZE + " hits in a single doc");
        int n = (int)(toIndexExclusive - fromIndex);

        // Determine which bits of context to get
        int[] startsOfSnippets = new int[n];
        int[] endsOfSnippets = new int[n];
        EphemeralHit hit = new EphemeralHit();
        long hitIndex = fromIndex;
        for (int j = 0; j < n; ++j, ++hitIndex) {
            hits.getEphemeral(hitIndex, hit);
            setStartEnd.setStartEnd(startsOfSnippets, endsOfSnippets, j, hit);
        }

        // Retrieve term ids
        List<int[]> listTermIds = afi.retrievePartsInt(docId, startsOfSnippets, endsOfSnippets);
        // Also determine sort orders so we don't have to do that for each compare
        for (int[] termIds : listTermIds) {
            if (compareInReverse)
                ArrayUtils.reverse(termIds);
            contextTermId.add(termIds);
            int[] sortOrder = new int[termIds.length];
            terms.toSortOrder(termIds, sortOrder, sensitivity);
            contextSortOrder.add(sortOrder);
        }
    }

    @Override
    public PropertyValueContext get(long hitIndex) {
        if (contextTermId == null)
            fetchContext();
        return new PropertyValueContextWords(index, annotation, sensitivity,
                contextTermId.get(hitIndex), contextSortOrder.get(hitIndex), compareInReverse);
    }

    @Override
    public int compare(long indexA, long indexB) {
        if (contextTermId == null)
            fetchContext();
        int[] ca = contextSortOrder.get(indexA);
        int[] cb = contextSortOrder.get(indexB);
        int cmp = Arrays.compare(ca, cb);
        return reverse ? -cmp : cmp;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        HitPropertyContextBase other = (HitPropertyContextBase) obj;
        if (annotation == null) {
            if (other.annotation != null)
                return false;
        } else if (!annotation.equals(other.annotation))
            return false;
        if (index == null) {
            if (other.index != null)
                return false;
        } else if (!index.equals(other.index))
            return false;
        if (sensitivity != other.sensitivity)
            return false;
        return true;
    }
}
