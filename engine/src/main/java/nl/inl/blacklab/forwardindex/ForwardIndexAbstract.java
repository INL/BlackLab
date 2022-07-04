package nl.inl.blacklab.forwardindex;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

public abstract class ForwardIndexAbstract implements ForwardIndex {

    protected static final Logger logger = LogManager.getLogger(ForwardIndexAbstract.class);

    protected static final boolean AUTO_INIT_FORWARD_INDEXES = true;

    protected final BlackLabIndex index;

    protected final AnnotatedField field;

    protected final Map<Annotation, AnnotationForwardIndex> fis = new HashMap<>();

    public ForwardIndexAbstract(BlackLabIndex index, AnnotatedField field) {
        this.index = index;
        this.field = field;
        ExecutorService executorService = index.blackLab().initializationExecutorService();
        for (Annotation annotation: field.annotations()) {
            if (!annotation.hasForwardIndex())
                continue;
            AnnotationForwardIndex afi = get(annotation);
            if (AUTO_INIT_FORWARD_INDEXES) {
                executorService.execute(afi::initialize);
            }
        }
    }

    /**
     * Get any annotation forward index, doesn't matter which.
     *
     * @return an annotation forward index
     */
    private AnnotationForwardIndex anyAnnotationForwardIndex() {
        synchronized (fis) {
            return fis.values().iterator().next();
        }
    }

    @Override
    public boolean canDoNfaMatching() {
        if (!hasAnyForwardIndices())
            return false;
        return anyAnnotationForwardIndex().canDoNfaMatching();
    }

    @Override
    public void close() {
        synchronized (fis) {
            for (AnnotationForwardIndex fi: fis.values()) {
                fi.close();
            }
            fis.clear();
        }
    }

    @Override
    public void addDocument(
            Map<Annotation, List<String>> content, Map<Annotation, List<Integer>> posIncr, Document document) {
        for (Entry<Annotation, List<String>> e: content.entrySet()) {
            Annotation annotation = e.getKey();
            AnnotationForwardIndex afi = get(annotation);
            List<Integer> posIncrThisAnnot = posIncr.get(annotation);
            int fiid = afi.addDocument(e.getValue(), posIncrThisAnnot);
            String fieldName = annotation.forwardIndexIdField();
            document.add(new IntPoint(fieldName, fiid));
            document.add(new StoredField(fieldName, fiid));
            document.add(new NumericDocValuesField(fieldName, fiid)); // for fast retrieval (FiidLookup)
        }
    }

    @Override
    public Terms terms(Annotation annotation) {
        return get(annotation).terms();
    }

    @Override
    public int numDocs() {
        synchronized (fis) {
            if (fis.isEmpty())
                return 0;
            return anyAnnotationForwardIndex().numDocs();
        }
    }

    @Override
    public long freeSpace() {
        synchronized (fis) {
            if (fis.isEmpty())
                return 0;
            return fis.values().stream().mapToLong(AnnotationForwardIndex::freeSpace).sum();
        }
    }

    @Override
    public long totalSize() {
        synchronized (fis) {
            if (fis.isEmpty())
                return 0;
            return fis.values().stream().mapToLong(AnnotationForwardIndex::totalSize).sum();
        }
    }

    @Override
    public AnnotatedField field() {
        return field;
    }

    @Override
    public Iterator<AnnotationForwardIndex> iterator() {
        synchronized (fis) {
            return fis.values().iterator();
        }
    }

    @Override
    public AnnotationForwardIndex get(Annotation annotation) {
        if (!annotation.hasForwardIndex())
            throw new IllegalArgumentException("Annotation has no forward index, according to itself: " + annotation);
        AnnotationForwardIndex afi;
        synchronized (fis) {
            afi = fis.get(annotation);
        }
        if (afi == null)
            afi = openAnnotationForwardIndex(annotation);
        return afi;
    }

    protected abstract AnnotationForwardIndex openAnnotationForwardIndex(Annotation annotation);

    @Override
    public void put(Annotation annotation, AnnotationForwardIndex forwardIndex) {
        synchronized (fis) {
            fis.put(annotation, forwardIndex);
        }
    }

    @Override
    public boolean hasAnyForwardIndices() {
        synchronized (fis) {
            return !fis.isEmpty();
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(" + index.indexDirectory() + "/fi_*)";
    }

    protected void add(Annotation annotation, AnnotationForwardIndex afi) {
        synchronized (fis) {
            fis.put(annotation, afi);
        }
    }
}
