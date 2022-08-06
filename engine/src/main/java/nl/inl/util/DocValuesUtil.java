package nl.inl.util;

import java.io.IOException;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.DocIdSetIterator;

public class DocValuesUtil {

    public static SortedDocValuesCacher cacher(SortedDocValues dv) {
        return dv == null ? null : new SortedDocValuesCacher(dv);
    }

    public static SortedSetDocValuesCacher cacher(SortedSetDocValues dv) {
        return dv == null ? null : new SortedSetDocValuesCacher(dv);
    }

    public static NumericDocValuesCacher cacher(NumericDocValues dv) {
        return dv == null ? null : new NumericDocValuesCacher(dv);
    }

    /**
     * Get the appropriate DocValues instance for the given field.
     *
     * @param r index segment
     * @param fieldName field to get DocValues for
     * @param isNumeric whether the field is numeric
     * @return DocValues instance, or null if field has no values in this segment
     */
    public static DocIdSetIterator docValuesIterator(LeafReader r, String fieldName, boolean isNumeric) throws
            IOException {
        DocIdSetIterator dv;
        if (isNumeric) {
            dv = r.getNumericDocValues(fieldName);
        } else {
            dv = r.getSortedSetDocValues(fieldName);
            if (dv == null) {
                dv = r.getSortedDocValues(fieldName);
            }
        }
        return dv;
    }

    /**
     * Get the current value from a DocValues instance and cast to string.
     *
     * NOTE: For multi-value fields, only returns the first value!
     *
     * @param dv DocValues instance positioned at a valid document
     * @return value for this document
     */
    public static String getCurrentValueAsString(DocIdSetIterator dv) {
        try {
            String key;
            if (dv instanceof NumericDocValues)
                key = Long.toString(((NumericDocValues) dv).longValue());
            else if (dv instanceof SortedSetDocValues) {
                if (((SortedSetDocValues) dv).getValueCount() > 0) {
                    // NOTE: we only count the first value stored (for backward compatibility)
                    // TODO: pros/cons of changing this?
                    SortedSetDocValues ssdv = (SortedSetDocValues) dv;
                    long ord = ssdv.nextOrd();
                    key = ssdv.lookupOrd(ord).utf8ToString();
                } else
                    key = null;
            } else if (dv instanceof SortedDocValues) {
                key = ((SortedDocValues) dv).binaryValue().utf8ToString();
            } else {
                throw new IllegalStateException("Unexpected DocValues type");
            }
            return key;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
