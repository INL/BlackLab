package nl.inl.blacklab.forwardindex;

import java.util.List;

import nl.inl.blacklab.search.indexmetadata.Annotation;

/** A document in the (multi-)forward index. */
public interface FIDoc {
    
    /**
     * Delete document from the forward index
     *
     * @param fiid id of the document to delete
     */
    void delete();
    
    /**
     * Retrieve one or more parts from the document, in the form of token
     * ids.
     *
     * This is more efficient than retrieving the whole content, or retrieving parts
     * in separate calls, because the file is only opened once and random access is
     * used to read only the required parts.
     *
     * NOTE: if offset and length are both -1, retrieves the whole content. This is
     * used by the retrieve(id) method.
     *
     * NOTE2: Mapped file IO on Windows has some issues that sometimes cause an
     * OutOfMemoryError on the FileChannel.map() call (which makes no sense, because
     * memory mapping only uses address space, it doesn't try to read the whole
     * file). Possibly this could be solved by using 64-bit Java, but we haven't
     * tried. For now we just disable memory mapping on Windows.
     * 
     * @param annotation annotation for which to retrieve content
     * @param fiid forward index document id
     * @param start the starting points of the parts to retrieve (in words) (-1 for
     *            start of document)
     * @param end the end points (i.e. first token beyond) of the parts to retrieve
     *            (in words) (-1 for end of document)
     * @return the parts
     */
    List<int[]> retrievePartsInt(Annotation annotation, int[] start, int[] end);
    
    /**
     * Gets the length (in tokens) of the document
     * 
     * @return length of the document
     */
    int docLength();

}