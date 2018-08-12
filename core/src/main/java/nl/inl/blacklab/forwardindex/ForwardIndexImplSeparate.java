package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Doc;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Multi-forward index implemented by combining several separate annotation forward indexes.
 */
public class ForwardIndexImplSeparate implements ForwardIndex {

    private BlackLabIndex index;

    private AnnotatedField field;

    private Map<Annotation, AnnotationForwardIndex> fis = new HashMap<>();

    public ForwardIndexImplSeparate(BlackLabIndex index, AnnotatedField field) {
        this.index = index;
        this.field = field;
        for (Annotation annotation: field.annotations()) {
            if (!annotation.hasForwardIndex())
                continue;
            AnnotationForwardIndex afi;
            File afiDir = determineAfiDir(index.indexDirectory(), annotation);
            if (!index.isEmpty() && !afiDir.exists()) {
                if (index.indexMode()) {
                    // We're in index mode, an one annotation that should have a forward index
                    // doesn't have one. Create it now.
                    createAfi(annotation);
                } else {
                    // Forward index doesn't yet exist
                    continue;
                }
            }
            // Open forward index
            afi = AnnotationForwardIndex.open(afiDir, index, annotation);
            fis.put(annotation, afi);
        }
    }

    private static File determineAfiDir(File indexDir, Annotation annotation) {
        return new File(indexDir, "fi_" + annotation.luceneFieldPrefix());
    }
    
    @Override
    public boolean canDoNfaMatching() {
        if (fis.isEmpty())
            return false;
        AnnotationForwardIndex fi = fis.values().iterator().next();
        return fi.canDoNfaMatching();
    }
    
    @Override
    public FIDoc doc(int docId) {
        return new FIDoc() {
            @Override
            public void delete() {
                for (AnnotationForwardIndex afi: fis.values()) {
                    afi.deleteDocument(afi.luceneDocIdToFiid(docId));
                }
            }

            @Override
            public List<int[]> retrievePartsInt(Annotation annotation, int[] start, int[] end) {
                AnnotationForwardIndex afi = fis.get(annotation);
                return afi.retrievePartsInt(afi.luceneDocIdToFiid(docId), start, end);
            }

            @Override
            public int docLength() {
                AnnotationForwardIndex afi = fis.values().iterator().next();
                return afi.getDocLength(afi.luceneDocIdToFiid(docId));
            }
        };
    }
    
    @Override
    public void close() {
        for (AnnotationForwardIndex fi: fis.values()) {
            fi.close();
        }
    }

    @Override
    public void addDocument(Map<Annotation, List<String>> content, List<Integer> posIncr) {
        for (Entry<Annotation, List<String>> e: content.entrySet()) {
            AnnotationForwardIndex afi = get(e.getKey());
            afi.addDocument(e.getValue(), posIncr);
        }
    }

    @Override
    public Terms terms(Annotation annotation) {
        return get(annotation).terms();
    }

    @Override
    public int numDocs() {
        if (fis.isEmpty())
            return 0;
        return fis.values().iterator().next().getNumDocs();
    }

    @Override
    public long freeSpace() {
        if (fis.isEmpty())
            return 0;
        return fis.values().stream().mapToLong(afi -> afi.getFreeSpace()).sum();
    }

    @Override
    public long totalSize() {
        if (fis.isEmpty())
            return 0;
        return fis.values().stream().mapToLong(afi -> afi.getTotalSize()).sum();
    }

    @Override
    public AnnotatedField field() {
        return field;
    }

    @Override
    public Iterator<AnnotationForwardIndex> iterator() {
        return fis.values().iterator();
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
    public AnnotationForwardIndex get(Annotation annotation) {
        if (!annotation.hasForwardIndex())
            throw new IllegalArgumentException("Annotation has no forward index, according to itself: " + annotation);
        AnnotationForwardIndex afi = fis.get(annotation);
        if (afi == null) {
            if (index.indexMode() && index.isEmpty()) {
                afi = createAfi(annotation);
            } else {
                throw new IllegalArgumentException("Annotation should have forward index but directory is missing: " + annotation);
            }
        }
        return afi;
    }

    private AnnotationForwardIndex createAfi(Annotation annotation) {
        AnnotationForwardIndex afi = AnnotationForwardIndex.open(determineAfiDir(index.indexDirectory(), annotation), index, annotation);
        fis.put(annotation, afi);
        return afi;
    }

    @Override
    public void put(Annotation annotation, AnnotationForwardIndex forwardIndex) {
        fis.put(annotation, forwardIndex);
    }

    @Override
    public boolean hasAnyForwardIndices() {
        return !fis.isEmpty();
    }

}
