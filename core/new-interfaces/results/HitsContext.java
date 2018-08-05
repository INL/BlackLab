package nl.inl.blacklab.interfaces.results;

import java.util.List;

import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import nl.inl.blacklab.interfaces.ContextSize;
import nl.inl.blacklab.interfaces.struct.Annotation;

/**
 * Context information for a set of hits for one or more annotations.
 */
public interface HitsContext {

    /**
     * What annotations we have context for.
     * 
     * The order annotations appear here determines the index for the annotation
     * in HitContext.
     * 
     * @return annotations
     */
    List<Annotation> annotations();
    
    /**
     * Does this have the specified annotation?
     * 
     * @param annotation the annotation
     * @return true if it does, false if not
     */
    boolean hasAnnotation(Annotation annotation);
    
    /**
     * Determine the index of the specified annotation.
     * 
     * @param annotation the annotation
     * @return its index
     * @throws RuntimeException if annotation not present
     */
    int annotationIndex(Annotation annotation);
    
    /**
     * What context was requested.
     * 
     * Can be full context (left+hit+right), or just one part
     * (left / hit / right) or even both sides but not the hit context
     * (e.g. useful for collocations)
     * 
     * Left/right sizes are maximums; some hits may have smaller contexts due to
     * occurring at the start or end of a document.
     * 
     * @return context size
     */
    ContextSize size();
    
    /**
     * Return a list containing the context(s).
     * 
     * @return context information
     */
    IntArrayList data();
    
    /**
     * Find where the context for each hit starts.
     * 
     * Returned list contains absolute indices into the list returned by data().
     * 
     * From the index pointed to, the first three ints specify the hit start, hit end
     * and context length. After that follows the contexts for each of the annotations.
     * So here's how to determine the location of an annotation context:
     * 
     * <code>
     * // once:
     * IntArrayList data = hitsContext.data();
     * IntArrayList starts = hitsContext.starts();
     * 
     * // for each hit:
     * int startOfContexts = starts.get(hitIndex);
     * int hitStart = data.get(startofContexts + 0);
     * int hitEnd = data.get(startofContexts + 1);
     * int contextLength = data.get(startOfContexts + 2);
     * 
     * // for each annotation:
     * int hitAnnotContextStart = startOfContexts + 3 + annotationIndex * contextLength; // first word of left context
     * int hitAnnotStart = annotationStart + hitStart;  // first word of hit
     * int hitAnnotEnd = annotationStart + hitEnd;      // first word after hit
     * int hitAnnotContextEnd = start + contextLength;  // one beyond last word of right context
     * </code>
     * 
     * @return array of start positions
     */
    IntArrayList starts();
    
}
