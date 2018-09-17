package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntField;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Doc;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Multi-forward index implemented by combining several separate annotation
 * forward indexes.
 */
public class ForwardIndexImplSeparate implements ForwardIndex {

    private BlackLabIndex index;

    private AnnotatedField field;

    private Map<Annotation, AnnotationForwardIndex> fis = new HashMap<>();

    private ExecutorService executorService;

    public ForwardIndexImplSeparate(BlackLabIndex index, AnnotatedField field) {
        this.index = index;
        this.field = field;
        executorService = index.blackLab().initializationExecutorService();
        for (Annotation annotation: field.annotations()) {
            if (!annotation.hasForwardIndex())
                continue;
            AnnotationForwardIndex afi = get(annotation);
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    afi.initialize();
                }
            });
        }
    }

    private static File determineAfiDir(File indexDir, Annotation annotation) {
        return new File(indexDir, "fi_" + annotation.luceneFieldPrefix());
    }

    /**
     * Get any annotation forward index, doesn't matter which.
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
    public FIDoc doc(int docId) {
        return new FIDoc() {
            @Override
            public void delete() {
                synchronized (fis) {
                    for (AnnotationForwardIndex afi: fis.values()) {
                        afi.deleteDocument(docId);
                    }
                }
            }

            @Override
            public List<int[]> retrievePartsInt(Annotation annotation, int[] start, int[] end) {
                synchronized (fis) {
                    AnnotationForwardIndex afi = fis.get(annotation);
                    return afi.retrievePartsInt(docId, start, end);
                }
            }

            @Override
            public int docLength() {
                synchronized (fis) {
                    AnnotationForwardIndex afi = anyAnnotationForwardIndex();
                    return afi.docLength(docId);
                }
            }
        };
    }

    @Override
    public FIDoc doc(Doc doc) {
        return doc(doc.id());
    }

    @Override
    public FIDoc doc(Document doc) {
        throw new UnsupportedOperationException(); // doesn't play well with our fiidLookups...
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
    public void addDocument(Map<Annotation, List<String>> content, Map<Annotation, List<Integer>> posIncr, Document document) {
        for (Entry<Annotation, List<String>> e: content.entrySet()) {
            Annotation annotation = e.getKey();
            AnnotationForwardIndex afi = get(annotation);
            List<Integer> posIncrThisAnnot = posIncr.get(annotation);
            int fiid = afi.addDocument(e.getValue(), posIncrThisAnnot);
            document.add(new IntField(annotation.forwardIndexIdField(), fiid, Store.YES));
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
            return fis.values().stream().mapToLong(afi -> afi.freeSpace()).sum();
        }
    }

    @Override
    public long totalSize() {
        synchronized (fis) {
            if (fis.isEmpty())
                return 0;
            return fis.values().stream().mapToLong(afi -> afi.totalSize()).sum();
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

    /** For common annotations, always build term indexes right away. For less common ones, do it on demand. Saves memory and startup time. */
    private static final Set<String> BUILD_TERMINDEXES_ON_INIT = new HashSet<>(Arrays.asList("word", "lemma", "pos")); 

    private static boolean buildTermIndexesOnInit(Annotation annotation) {
        return BUILD_TERMINDEXES_ON_INIT.contains(annotation.name());
    }

    private AnnotationForwardIndex openAnnotationForwardIndex(Annotation annotation) {
        File dir = determineAfiDir(index.indexDirectory(), annotation);
        boolean create = index.indexMode() && index.isEmpty();
        FiidLookup fiidLookup = new FiidLookup(index.reader(), annotation);
        AnnotationForwardIndex afi = AnnotationForwardIndex.open(dir, index.indexMode(), index.collator(), create, annotation, fiidLookup, buildTermIndexesOnInit(annotation));
        synchronized (fis) {
            fis.put(annotation, afi);
        }
        return afi;
    }

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

}
