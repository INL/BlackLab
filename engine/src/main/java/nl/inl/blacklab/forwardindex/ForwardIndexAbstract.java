package nl.inl.blacklab.forwardindex;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

public abstract class ForwardIndexAbstract implements ForwardIndex {

    protected static final Logger logger = LogManager.getLogger(ForwardIndexAbstract.class);

    /** Check that the requested snippet can be taken from a document of this length.
     * @param docLength length of the document
     * @param snippetStart start position of the snippet
     * @param snippetEnd length of the snippet
     */
    public static void validateSnippetParameters(int docLength, int snippetStart, int snippetEnd) {
        if (snippetStart < 0 || snippetEnd < 0) {
            throw new IllegalArgumentException("Illegal values, start = " + snippetStart + ", end = "
                    + snippetEnd);
        }
        if (snippetStart > docLength || snippetEnd > docLength) {
            throw new IllegalArgumentException("Value(s) out of range, start = " + snippetStart
                    + ", end = " + snippetEnd + ", content length = " + docLength);
        }
        if (snippetEnd <= snippetStart) {
            throw new IllegalArgumentException(
                    "Tried to read empty or negative length snippet (from " + snippetStart
                            + " to " + snippetEnd + ")");
        }
    }

    private final BlackLabIndex index;

    private final AnnotatedField field;

    private final Map<Annotation, AnnotationForwardIndex> fis = new HashMap<>();

    public ForwardIndexAbstract(BlackLabIndex index, AnnotatedField field) {
        this.index = index;
        this.field = field;

        // Open forward indexes
        ExecutorService executorService = index.blackLab().initializationExecutorService();
        for (Annotation annotation: field.annotations()) {
            if (!annotation.hasForwardIndex())
                continue;
            AnnotationForwardIndex afi = get(annotation);
            // Automatically initialize forward index (in the background)
            executorService.execute(afi::initialize);
        }
    }

    @Override
    public boolean canDoNfaMatching() {
        for (AnnotationForwardIndex afi: fis.values()) {
            if (afi.canDoNfaMatching())
                return true;
        }
        return false;
    }

    /**
     * Close the forward index. Writes the table of contents to disk if modified.
     * (needed for ForwardIndexExternal only; can eventually be removed)
     */
    public void close() {
        synchronized (fis) {
            for (AnnotationForwardIndex fi: fis.values()) {
                if (fi instanceof AnnotationForwardIndexExternalWriter)
                    ((AnnotationForwardIndexExternalWriter)fi).close();
            }
            fis.clear();
        }
    }

    @Override
    public Terms terms(Annotation annotation) {
        return get(annotation).terms();
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
            afi = openAnnotationForwardIndex(annotation, index);
        return afi;
    }

    protected abstract AnnotationForwardIndex openAnnotationForwardIndex(Annotation annotation, BlackLabIndex index);

    @Override
    public void put(Annotation annotation, AnnotationForwardIndex forwardIndex) {
        synchronized (fis) {
            fis.put(annotation, forwardIndex);
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
