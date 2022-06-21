package org.ivdnt.blacklab.aggregator.representation;

import java.util.Arrays;
import java.util.Objects;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

/** Hit with minimal information for aggregator */
@XmlAccessorType(XmlAccessType.FIELD)
public class HitMin implements Comparable<HitMin> {

    /** What node did we read this hit from? */
    public int nodeId;

    /** What was this hit's index in the node's results? (for requesting concordances, etc.) */
    public int indexOnNode;

    /** What's the document id on the node? (really only used to keep hits from same doc together) */
    public int docIdOnNode;

    /** What is/are the value(s) these hits are sorted by? (used for merging) */
    public String[] sortValues;

    // required for Jersey
    public HitMin() {}

    public HitMin(int nodeId, int indexOnNode, int docIdOnNode, String[] sortValues) {
        this.nodeId = nodeId;
        this.indexOnNode = indexOnNode;
        this.docIdOnNode = docIdOnNode;
        this.sortValues = sortValues;
    }

    @Override
    public int compareTo(HitMin hitMin) {
        return 0; // FIXME SortValueUtil.;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        HitMin hitMin = (HitMin) o;
        return nodeId == hitMin.nodeId && indexOnNode == hitMin.indexOnNode && docIdOnNode == hitMin.docIdOnNode
                && Arrays.equals(sortValues, hitMin.sortValues);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(nodeId, indexOnNode, docIdOnNode);
        result = 31 * result + Arrays.hashCode(sortValues);
        return result;
    }

    @Override
    public String toString() {
        return "HitMin{" +
                "nodeId=" + nodeId +
                ", indexOnNode=" + indexOnNode +
                ", docIdOnNode=" + docIdOnNode +
                ", sortValues=" + Arrays.toString(sortValues) +
                '}';
    }
}
