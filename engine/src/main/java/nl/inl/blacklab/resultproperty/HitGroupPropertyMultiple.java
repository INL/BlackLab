package nl.inl.blacklab.resultproperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import nl.inl.blacklab.search.results.HitGroup;

/**
 * A collection of HitGroupProperty's.
 */
public class HitGroupPropertyMultiple extends HitGroupProperty implements Iterable<HitGroupProperty> {

    public static HitGroupProperty deserializeProp(String info) {
        String[] strValues = PropertySerializeUtil.splitMultiple(info);
        HitGroupProperty[] values = new HitGroupProperty[strValues.length];
        int i = 0;
        for (String strValue: strValues) {
            values[i] = HitGroupProperty.deserialize(strValue);
            i++;
        }
        return new HitGroupPropertyMultiple(values);
    }

    List<HitGroupProperty> criteria;

    HitGroupPropertyMultiple(HitGroupPropertyMultiple mprop, boolean invert) {
        super(mprop, invert);
        this.criteria = mprop.criteria;
    }

    /**
     * Quick way to create group criteria. Just call this method with the
     * GroupCriterium object(s) you want.
     *
     * @param criteria the desired criteria
     */
    public HitGroupPropertyMultiple(List<HitGroupProperty> criteria) {
        this.criteria = new ArrayList<>(criteria);
    }

    /**
     * Quick way to create group criteria. Just call this method with the
     * GroupCriterium object(s) you want.
     *
     * @param criteria the desired criteria
     */
    public HitGroupPropertyMultiple(HitGroupProperty... criteria) {
        this.criteria = new ArrayList<>(Arrays.asList(criteria));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((criteria == null) ? 0 : criteria.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        HitGroupPropertyMultiple other = (HitGroupPropertyMultiple) obj;
        if (criteria == null) {
            if (other.criteria != null)
                return false;
        } else if (!criteria.equals(other.criteria))
            return false;
        return true;
    }

    public void addCriterium(HitGroupProperty crit) {
        criteria.add(crit);
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        int i = 0;
        for (HitGroupProperty prop : criteria) {
            if (i > 0)
                str.append(",");
            str.append(prop.toString());
            i++;
        }
        return str.toString();
    }

    @Override
    public Iterator<HitGroupProperty> iterator() {
        return criteria.iterator();
    }

    @Override
    public PropertyValueMultiple get(HitGroup result) {
        PropertyValue[] rv = new PropertyValue[criteria.size()];
        int i = 0;
        for (HitGroupProperty crit : criteria) {
            rv[i] = crit.get(result);
            i++;
        }
        return new PropertyValueMultiple(rv);
    }

    /**
     * Compares two groups on this property
     *
     * @param a first doc
     * @param b second doc
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    @Override
    public int compare(HitGroup a, HitGroup b) {
        for (HitGroupProperty crit : criteria) {
            int cmp = reverse ? crit.compare(b, a) : crit.compare(a, b);
            if (cmp != 0)
                return cmp;
        }
        return 0;
    }

    @Override
    public String name() {
        StringBuilder b = new StringBuilder();
        for (HitGroupProperty crit : criteria) {
            if (b.length() > 0)
                b.append(", ");
            b.append(crit.name());
        }
        return b.toString();
    }

    @Override
    public String serialize() {
        String[] values = new String[criteria.size()];
        for (int i = 0; i < criteria.size(); i++) {
            values[i] = criteria.get(i).serialize();
        }
        return (reverse ? "-(" : "") + PropertySerializeUtil.combineMultiple(values) + (reverse ? ")" : "");
    }

    @Override
    public boolean isCompound() {
        return true;
    }

    @Override
    public List<HitGroupProperty> props() {
        return Collections.unmodifiableList(criteria);
    }

    public static HitGroupPropertyMultiple deserialize(String info) {
        String[] strValues = PropertySerializeUtil.splitMultiple(info);
        HitGroupProperty[] values = new HitGroupProperty[strValues.length];
        int i = 0;
        for (String strValue : strValues) {
            values[i] = HitGroupProperty.deserialize(strValue);
            i++;
        }
        return new HitGroupPropertyMultiple(values);
    }

    @Override
    public HitGroupProperty reverse() {
        return new HitGroupPropertyMultiple(this, true);
    }

}
