package nl.inl.blacklab.search.results;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Prioritizable;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.util.ThreadPriority;

public interface Hits extends Iterable<Hit>, Prioritizable {

    int hitsProcessedSoFar();

    boolean hitsProcessedAtLeast(int lowerBound);

    int hitsProcessedTotal();

    int hitsCountedSoFar();

    int hitsCountedTotal();

    int docsProcessedSoFar();

    int docsProcessedTotal();

    int docsCountedSoFar();

    int docsCountedTotal();

    boolean doneProcessingAndCounting();

    boolean hitsCountedExceededMaximum();

    boolean hitsProcessedExceededMaximum();

    int size();

    Map<String, Span> getCapturedGroupMap(Hit hit);

    Span[] getCapturedGroups(Hit hit);

    boolean hasCapturedGroups();

    List<String> getCapturedGroupNames();

    Hits getHitsInDoc(int docid);

    Hit get(int i);

    Hit getByOriginalOrder(int i);

    @Override
    Iterator<Hit> iterator();

    @Override
    String toString();

    HitsWindow window(Hit hit);

    HitsWindow window(int first, int windowSize, HitsSettings settings);

    HitsWindow window(int first, int windowSize);

    TermFrequencyList getCollocations(Annotation annotation, QueryExecutionContext ctx, boolean sort);

    TermFrequencyList getCollocations();

    DocResults perDocResults();

    HitGroups groupedBy(HitProperty criteria);

    Hits filteredBy(HitProperty property, HitPropValue value);

    Hits sortedBy(HitProperty sortProp, boolean reverseSort);

    Hits sortedBy(HitProperty sortProp);

    void copyMaxHitsRetrieved(Hits copyFrom);

    Hits copy(HitsSettings settings);

    int getHitsObjId();

    /**
     * Set the thread priority level for this Hits object.
     *
     * Allows us to set a query to low-priority, or to (almost) pause it.
     *
     * @param level the desired priority level
     */
    @Override
    void setPriorityLevel(ThreadPriority.Level level);

    /**
     * Get the thread priority level for this Hits object.
     *
     * Can be normal, low-priority, or (almost) paused.
     *
     * @return the current priority level
     */
    @Override
    ThreadPriority.Level getPriorityLevel();

    /**
     * Returns the searcher object.
     *
     * @return the searcher object.
     */
    BlackLabIndex index();

    AnnotatedField field();

    HitsSettings settings();

    ThreadPriority getThreadPriority();

    Concordances concordances(int contextSize);

    Kwics kwics(int contextSize);

}
