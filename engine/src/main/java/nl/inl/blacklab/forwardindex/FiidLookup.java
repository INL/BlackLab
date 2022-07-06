package nl.inl.blacklab.forwardindex;

import java.util.ArrayList;
import java.util.List;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Class for looking up forward index id, using DocValues or stored fields.
 *
 * Not all implementations are thread-safe (using DocValues without synchronization).
 *
 * CAUTION: if created without random access enabled, the get() method can
 * only be called with ascending doc ids!
 */
@NotThreadSafe
public interface FiidLookup {

    /**
     * The dummy FiidLookup just returns the docId. This is used for the integrated index,
     * where the forward index is part of the Lucene index, so there are no separate ids.
     */
    FiidLookup DUMMY = docId -> docId;

    static FiidLookup get(BlackLabIndex index, Annotation annotation, boolean enableRandomAccess) {
        return index.allFilesInIndex() ?
                DUMMY :
                new FiidLookupExternal(index.reader(), annotation, enableRandomAccess);
    }

    static List<FiidLookup> getList(List<Annotation> annotations, BlackLabIndex index, boolean enableRandomAccess) {
        if (annotations == null)
            return null; // HitPoperty.needsContext() can return null
        List<FiidLookup> fiidLookups = new ArrayList<>();
        for (Annotation annotation: annotations) {
            fiidLookups.add(annotation == null ? null : get(index, annotation, enableRandomAccess));
        }
        return fiidLookups;
    }

    /**
     * Return the forward index id for the given Lucene doc id.
     *
     * Uses DocValues to retrieve the fiid from the Lucene Document.
     *
     * CAUTION: docId must always be equal to or greater than the previous docId
     * this method was called with! (because DocValues API is sequential now)
     *
     * @param docId Lucene doc id
     * @return forward index id (fiid)
     */
    int get(int docId);
}
