package org.ivdnt.blacklab.aggregator.logic.hits;

import java.util.Arrays;
import java.util.Comparator;

import org.ivdnt.blacklab.aggregator.helper.Util;
import org.ivdnt.blacklab.aggregator.representation.Hit;
import org.ivdnt.blacklab.aggregator.representation.HitMin;
import org.ivdnt.blacklab.aggregator.representation.MetadataValues;

import nl.inl.blacklab.util.PropertySerializeUtil;

class HitComparators {

    public static Comparator<HitMin> deserializeMin(String hitProp) {
        // FIXME: use correct collator
        return Comparator.naturalOrder();
    }

    public static Comparator<Hit> deserialize(String hitProp) {
        if (hitProp.isEmpty())
            return null;
        if (PropertySerializeUtil.isMultiple(hitProp)) {
            boolean reverse = false;
            if (hitProp.startsWith("-(") && hitProp.endsWith(")")) {
                reverse = true;
                hitProp = hitProp.substring(2, hitProp.length() - 1);
            }
            Comparator<Hit> result = Arrays.stream(PropertySerializeUtil.splitMultiple(hitProp))
                    .map(HitComparators::deserialize)
                    .reduce(Comparator::thenComparing)
                    .orElseThrow();
            if (reverse)
                result = result.reversed();
            return result;
        }

        String[] parts = PropertySerializeUtil.splitPartFirstRest(hitProp);
        String type = parts[0].toLowerCase();
        boolean reverse = false;
        if (type.length() > 0 && type.charAt(0) == '-') {
            reverse = true;
            type = type.substring(1);
        }
        String info = parts.length > 1 ? parts[1] : "";
        Comparator<Hit> cmp;
        switch(type) {
        case "hitposition": cmp = HIT_POSITION; break;
        case "decade": cmp = docFieldDecade(info); break;
        case "field": cmp = docField(info); break;
        case "doc": case "docid": cmp = DOC_PID; break;
        default:
            // Context property. Find annotation and sensitivity.
            parts = PropertySerializeUtil.splitParts(info);
            String annotation = parts[0];
            if (annotation.length() == 0)
                throw new UnsupportedOperationException("Specify annotation for sort/group prop!");
            boolean sensitive = parts.length <= 1 || parts[1].equals("s");
            switch (type) {
            case "left":
                cmp = hitTextBefore(annotation, sensitive, false);
                break;
            case "wordleft":
                cmp = hitTextBefore(annotation, sensitive, true);
                break;
            case "hit":
                cmp = hitMatchedText(annotation, sensitive);
                break;
            case "right":
                cmp = hitTextAfter(annotation, sensitive, false);
                break;
            case "wordright":
                cmp = hitTextAfter(annotation, sensitive, true);
                break;
            default:
                throw new UnsupportedOperationException("Hit property not supported: " + type);
            }
        }
        return reverse ? cmp.reversed() : cmp;
    }

    private static final Comparator<Hit> DOC_PID = Comparator.comparing(a -> a.docPid);

    private static final Comparator<Hit> HIT_POSITION = Comparator.comparingLong(a -> a.start);

    private static Comparator<Hit> hitMatchedText(String annotation, boolean sensitive) {
        return (a, b) -> a.match.compareTo(b.match, annotation, sensitive, false, false);
    }

    private static Comparator<Hit> hitTextBefore(String annotation, boolean sensitive, boolean stopAfterOne) {
        return (a, b) -> a.left.compareTo(b.left, annotation, sensitive, true, stopAfterOne);
    }

    private static Comparator<Hit> hitTextAfter(String annotation, boolean sensitive, boolean stopAfterOne) {
        return (a, b) -> a.right.compareTo(b.right, annotation, sensitive, false, stopAfterOne);
    }

    /** String compare by metadata field */
    private static Comparator<Hit> docField(String field) {
        return (a, b) -> {
            MetadataValues va = a.docInfo.get(field);
            MetadataValues vb = b.docInfo.get(field);
            String sa = va == null ? "" : va.getValue().get(0);
            String sb = vb == null ? "" : vb.getValue().get(0);
            return Util.DEFAULT_COLLATOR.compare(sa, sb);
        };
    }

    /** Compare by decade for metadata field containing year */
    private static Comparator<Hit> docFieldDecade(String field) {
        return (a, b) -> {
            int da = Integer.parseInt(a.docInfo.get(field).getValue().get(0)) / 10;
            int db = Integer.parseInt(b.docInfo.get(field).getValue().get(0)) / 10;
            return Integer.compare(da, db);
        };
    }
}
